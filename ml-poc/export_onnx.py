#!/usr/bin/env python3
"""
Export ProsusAI/finbert to INT8 ONNX for in-JVM inference.

Produces three files the Java service needs:

    models/sentiment-model.onnx   FP16 weights (~219 MB, down from ~438 MB)
    models/tokenizer.json         read by DJL's tokenizer binding in Java
    models/config.json            read for id2label -> canonical label mapping

Run once. Copy the output directory to EC2 and point SENTIMENT_MODEL_PATH at it.
The artefact is deliberately NOT bundled in the jar: it is ~219 MB and changes at
most a couple of times a year, so packaging it would slow every build.

Usage:
    python export_onnx.py
    python export_onnx.py --model ProsusAI/finbert --out models --max-length 64
"""

import argparse
import json
import os
import sys

# torch's ONNX exporter prints Unicode status glyphs. On a Windows console using the
# legacy cp1252 code page that raises UnicodeEncodeError *after* the export has already
# succeeded, which looks like a failure but is not. Force UTF-8 on stdout/stderr.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        try:
            _stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

import numpy as np
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

# Same headlines used by the Java parity test. Kept short and varied so a broken
# export shows up as a sign flip or a collapsed distribution rather than noise.
# INT8 quantization always costs a little precision. Anything beyond this on the
# probability distribution means the recipe is wrong, not that INT8 is inherently lossy.
MAX_ACCEPTABLE_DELTA = 0.05

PARITY_SENTENCES = [
    "Reliance Industries Q1 profit beats estimates on strong Jio subscriber growth",
    "Tata Motors shares plunge 8% after weak JLR sales data",
    "HDFC Bank to hold board meeting on July 22 to consider results",
    "It was not a good quarter for Bajaj Finance as provisions rose sharply",
    "Maruti Suzuki beats profit estimates but guides margins lower for H2",
]


def export(model_id, out_dir, max_length, opset):
    os.makedirs(out_dir, exist_ok=True)

    print(f"Loading {model_id} ...")
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    model = AutoModelForSequenceClassification.from_pretrained(model_id)
    model.eval()

    n_params = sum(p.numel() for p in model.parameters())
    print(f"  {n_params/1e6:.1f}M parameters")
    print(f"  id2label = {model.config.id2label}")

    # ── 1. Export FP32 ONNX ───────────────────────────────────────────────────
    fp32_path = os.path.join(out_dir, "sentiment-model-fp32.onnx")
    sample = tokenizer(
        "sample headline for tracing",
        return_tensors="pt", truncation=True, max_length=max_length,
    )

    # Only export the inputs this model actually takes. BERT wants token_type_ids;
    # some architectures do not, and exporting an input the graph ignores makes the
    # Java side guess at what to feed it.
    input_names = [n for n in ("input_ids", "attention_mask", "token_type_ids") if n in sample]
    args = tuple(sample[n] for n in input_names)

    # Dynamic axes on both batch and sequence: headlines vary in length, and pinning
    # the sequence dimension would force padding every input to max_length, wasting
    # compute on short headlines.
    dynamic_axes = {n: {0: "batch", 1: "sequence"} for n in input_names}
    dynamic_axes["logits"] = {0: "batch"}

    print(f"Exporting FP32 ONNX (inputs={input_names}) ...")
    # dynamo=False forces the legacy TorchScript exporter. The newer dynamo exporter
    # writes weights to a separate .onnx.data file, which leaves a 2 MB graph plus a
    # 438 MB sidecar. That breaks quantize_dynamic (shape inference fails on the
    # detached initialisers) and would mean shipping two coupled files to EC2 instead
    # of one. The legacy exporter emits a single self-contained file, which is also
    # the well-trodden path for BERT quantization.
    torch.onnx.export(
        model,
        args,
        fp32_path,
        input_names=input_names,
        output_names=["logits"],
        dynamic_axes=dynamic_axes,
        opset_version=opset,
        do_constant_folding=True,
        dynamo=False,
    )

    size_mb = os.path.getsize(fp32_path) / 1e6
    print(f"  {size_mb:.0f} MB")
    if size_mb < 100:
        raise SystemExit(
            f"FP32 export is only {size_mb:.0f} MB — weights were almost certainly written "
            f"to an external .data file instead of being embedded. Quantization and the Java "
            f"loader both expect a single self-contained model."
        )

    # -- 2. FP16 conversion ---------------------------------------------------
    #
    # WHY FP16 AND NOT INT8. INT8 was the original plan (110 MB instead of 219 MB) and
    # it was abandoned on evidence, not preference. Every dynamic-quantization variant
    # tried -- QInt8/QUInt8, per-tensor and per-channel, with and without reduce_range,
    # and MatMul-only op selection -- inverted the sign on negation headlines. The worst
    # case scored "It was not a good quarter for Bajaj Finance" at +3.4 where PyTorch
    # says -0.5. Static quantization calibrated on our own headlines was worse still:
    # the model collapsed entirely, returning +0.4 for every input.
    #
    # The FP32 ONNX export itself matches PyTorch exactly, so the damage was purely
    # from quantization, not from the export.
    #
    # FP16 halves the file with essentially no accuracy cost: max probability delta
    # 0.0007 and zero sign flips across the parity set. ONNX Runtime on CPU computes in
    # FP32 internally via inserted Cast nodes, so this costs some speed -- around 37 ms
    # per headline, which is irrelevant when a 15-minute cycle scores a few hundred.
    #
    # A model that silently inverts sentiment is worse than no sentiment feature at all,
    # so 109 MB of extra memory is the right trade.
    import onnx
    from onnxconverter_common import float16

    fp16_path = os.path.join(out_dir, "sentiment-model.onnx")
    print("Converting FP32 -> FP16 ...")
    # keep_io_types leaves the int64 inputs and float32 logits alone, so the Java side
    # feeds and reads exactly the same tensor types it would for an FP32 model.
    onnx.save(
        float16.convert_float_to_float16(onnx.load(fp32_path), keep_io_types=True),
        fp16_path,
    )
    int8_path = fp16_path   # name kept for the existing return signature
    print(f"  {os.path.getsize(fp16_path)/1e6:.0f} MB")


    # ── 3. Tokenizer + config ─────────────────────────────────────────────────
    tokenizer.save_pretrained(out_dir)
    tok_json = os.path.join(out_dir, "tokenizer.json")
    if not os.path.exists(tok_json):
        raise SystemExit(
            "tokenizer.json was not produced — this model has no fast tokenizer.\n"
            "The Java side requires it; a slow tokenizer cannot be used from DJL."
        )

    with open(os.path.join(out_dir, "config.json"), "w", encoding="utf-8") as f:
        json.dump(model.config.to_dict(), f, indent=2)

    return tokenizer, model, fp32_path, int8_path


def verify(tokenizer, model, int8_path, max_length):
    """
    Compare PyTorch and FP16-ONNX outputs on the parity sentences.

    Catches a broken export before it reaches Java, where the same failure would be
    much harder to diagnose: the model loads, inference runs, and the scores are
    simply wrong.
    """
    import onnxruntime as ort

    print("\nVerifying PyTorch vs FP16 ONNX ...")
    session = ort.InferenceSession(int8_path, providers=["CPUExecutionProvider"])
    onnx_inputs = {i.name for i in session.get_inputs()}

    id2label = model.config.id2label
    max_delta = 0.0
    sign_flips = 0

    for text in PARITY_SENTENCES:
        enc = tokenizer(text, return_tensors="pt", truncation=True, max_length=max_length)

        with torch.no_grad():
            torch_probs = torch.softmax(model(**enc).logits, dim=-1)[0].numpy()

        feed = {k: v.numpy() for k, v in enc.items() if k in onnx_inputs}
        onnx_logits = session.run(None, feed)[0][0]
        e = np.exp(onnx_logits - onnx_logits.max())
        onnx_probs = e / e.sum()

        delta = float(np.abs(torch_probs - onnx_probs).max())
        max_delta = max(max_delta, delta)

        def score(p):
            pos = next(i for i, l in id2label.items() if l.lower().startswith("pos"))
            neg = next(i for i, l in id2label.items() if l.lower().startswith("neg"))
            return (p[pos] - p[neg]) * 5.0

        torch_score = score(torch_probs)
        onnx_score  = score(onnx_probs)

        # Only count a flip when both sides are decisive. A headline PyTorch scores
        # at -0.05 crossing to +0.05 is noise around neutral, not an inversion.
        flipped = (torch_score * onnx_score < 0
                   and max(abs(torch_score), abs(onnx_score)) > 0.5)
        if flipped:
            sign_flips += 1

        marker = "FLIP " if flipped else "     "
        print(f"  torch={torch_score:+.2f}  onnx={onnx_score:+.2f}  "
              f"delta={delta:.4f}  {marker}{text[:44]}")

    print(f"\nMax probability delta: {max_delta:.4f}")
    print(f"Sign flips:            {sign_flips}")

    # Hard gate rather than a warning. A quantized model that inverts sentiment on a
    # negation case is worse than no sentiment at all, and a printed warning is far
    # too easy to scroll past when the file sizes look right.
    if sign_flips:
        raise SystemExit(
            f"\nFAILED: quantization flipped the sign on {sign_flips} headline(s). "
            f"Do not ship this artefact."
        )
    if max_delta > MAX_ACCEPTABLE_DELTA:
        raise SystemExit(
            f"\nFAILED: max probability delta {max_delta:.4f} exceeds the "
            f"{MAX_ACCEPTABLE_DELTA:.2f} budget. Do not ship this artefact."
        )

    print("  OK - quantization error is within the acceptable range.")
    return max_delta


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="ProsusAI/finbert")
    parser.add_argument("--out", default="models")
    parser.add_argument("--max-length", type=int, default=64)
    parser.add_argument("--opset", type=int, default=18)
    parser.add_argument("--keep-fp32", action="store_true",
                        help="keep the intermediate FP32 file (4x larger, not needed at runtime)")
    args = parser.parse_args()

    tokenizer, model, fp32_path, int8_path = export(
        args.model, args.out, args.max_length, args.opset
    )
    verify(tokenizer, model, int8_path, args.max_length)

    if not args.keep_fp32:
        os.remove(fp32_path)

    print(f"\nArtefacts written to {os.path.abspath(args.out)}/")
    for name in ("sentiment-model.onnx", "tokenizer.json", "config.json"):
        path = os.path.join(args.out, name)
        print(f"  {name:24} {os.path.getsize(path)/1e6:8.2f} MB")

    print("\nNext:")
    print("  1. Copy this directory to the machine running nse-news-scheduler")
    print("  2. Set SENTIMENT_MODEL_PATH to its absolute path")
    print("  3. Run the Java parity test to confirm Java matches these scores")


if __name__ == "__main__":
    main()
