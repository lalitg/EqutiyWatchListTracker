#!/usr/bin/env python3
"""
Model comparison harness — 3 candidate sentiment models x 5 input strategies.

ISOLATED PoC. Reads only testset.csv, writes only results/. Touches nothing
in the main application and is not part of any build.

Usage:
    python run_comparison.py
    python run_comparison.py --testset testset.csv --out results/raw_results.csv
"""

import argparse
import os
import re
import time

import pandas as pd
import psutil
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

# ─── Candidates ───────────────────────────────────────────────────────────────
# Total download on first run is ~1.3 GB across the three models (cached after).
MODELS = {
    "macro-sentiment": "peyterho/finbert-macro-sentiment",
    "prosus-finbert":  "ProsusAI/finbert",
    "distilroberta":   "mrm8488/distilroberta-finetuned-financial-news-sentiment-analysis",
}

# Identical for every model so we compare models, not truncation policies.
# finbert-macro-sentiment caps at 128; headlines are ~32-64 tokens, so this is ample.
MAX_LENGTH = 128

CANONICAL = ("positive", "neutral", "negative")


# ─── Label handling ───────────────────────────────────────────────────────────

def build_label_map(model, model_key):
    """
    Map each output index to a canonical label by READING the model's own config.

    Never hardcode index positions: finbert-macro-sentiment uses a different
    ordering to the others, and a wrong assumption silently inverts every score
    with no error raised.
    """
    id2label = model.config.id2label
    mapping = {}

    for idx, raw in id2label.items():
        norm = str(raw).strip().lower()
        if norm.startswith("pos"):
            mapping[int(idx)] = "positive"
        elif norm.startswith("neg"):
            mapping[int(idx)] = "negative"
        elif norm.startswith("neu"):
            mapping[int(idx)] = "neutral"
        else:
            raise ValueError(
                f"\n[{model_key}] Cannot interpret label {idx!r} -> {raw!r}.\n"
                f"Full id2label = {id2label}\n"
                f"Inspect the model card and map it manually before trusting any result."
            )

    if sorted(mapping.values()) != sorted(CANONICAL):
        raise ValueError(f"[{model_key}] Incomplete label map: {mapping}")

    print(f"  label map: {mapping}")
    return mapping


# ─── Input strategies ─────────────────────────────────────────────────────────
# All three models are plain sequence classifiers with no "target entity"
# parameter, so the ONLY way to signal the target is by changing the input text.
# These five strategies are what we are actually comparing.

CONNECTIVES = re.compile(
    r"\s+(?:as|while|but|after|amid|despite|though|whereas|however|and)\s+",
    re.IGNORECASE,
)


def split_others(raw):
    """other_entities column is pipe-separated, possibly empty."""
    if not raw or (isinstance(raw, float) and pd.isna(raw)):
        return []
    return [e.strip() for e in str(raw).split("|") if e.strip()]


def strat_raw(headline, target, others):
    """Baseline: model has no idea which company we care about."""
    return headline


def strat_prefix(headline, target, others):
    return f"{target}: {headline}"


def strat_template(headline, target, others):
    return f"Sentiment for {target}. {headline}"


def strat_mask(headline, target, others):
    """Replace competing entities with a placeholder (entity-masking)."""
    text = headline
    for other in others:
        text = re.sub(re.escape(other), "[OTHER]", text, flags=re.IGNORECASE)
    return text


def strat_clause(headline, target, others):
    """Keep only the clause mentioning the target; fall back to the full headline."""
    parts = [p.strip() for p in CONNECTIVES.split(headline) if p.strip()]
    for part in parts:
        if target.lower() in part.lower():
            return part
    return headline


STRATEGIES = {
    "raw":      strat_raw,
    "prefix":   strat_prefix,
    "template": strat_template,
    "mask":     strat_mask,
    "clause":   strat_clause,
}


# ─── Scoring ──────────────────────────────────────────────────────────────────

def to_score(p_pos, p_neg):
    """
    Linear map onto the symmetric -5..+5 scale.

        p_pos - p_neg  in [-1, +1]  ->  score in [-5, +5]

    Using the probability difference rather than the argmax label keeps the score
    continuous: a hedged headline yields a small magnitude, a decisive one a large
    magnitude. Perfect neutrality (p_pos == p_neg) maps to exactly 0.

    A symmetric range needs only one linear factor, and — unlike an asymmetric
    range — a keyword receiving equal amounts of strongly-positive and
    strongly-negative news correctly converges to 0 rather than to a positive bias.
    """
    return (p_pos - p_neg) * 5.0


def predict(text, tokenizer, model, label_map):
    inputs = tokenizer(
        text, return_tensors="pt", truncation=True, max_length=MAX_LENGTH
    )
    with torch.no_grad():
        logits = model(**inputs).logits
    probs = torch.softmax(logits, dim=-1)[0]

    out = {label_map[i]: float(probs[i]) for i in range(len(probs))}
    predicted = max(out, key=out.get)
    return out, predicted, float(max(out.values()))


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--testset", default="testset.csv")
    parser.add_argument("--out", default="results/raw_results.csv")
    parser.add_argument("--resources", default="results/resources.csv")
    args = parser.parse_args()

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)

    df = pd.read_csv(args.testset)
    required = {"id", "headline", "target_entity", "true_label", "category"}
    missing = required - set(df.columns)
    if missing:
        raise SystemExit(f"testset.csv missing columns: {missing}")

    print(f"Loaded {len(df)} test cases from {args.testset}")
    print(f"Categories: {df['category'].value_counts().to_dict()}\n")

    proc = psutil.Process(os.getpid())
    rows, resource_rows = [], []

    for model_key, model_id in MODELS.items():
        print(f"=== {model_key}  ({model_id})")

        rss_before = proc.memory_info().rss
        t0 = time.perf_counter()
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        model = AutoModelForSequenceClassification.from_pretrained(model_id)
        model.eval()
        load_secs = time.perf_counter() - t0
        rss_after = proc.memory_info().rss

        label_map = build_label_map(model, model_key)
        n_params = sum(p.numel() for p in model.parameters())

        print(f"  params: {n_params/1e6:.1f}M | load: {load_secs:.1f}s "
              f"| RSS delta: {(rss_after-rss_before)/1e6:.0f} MB")

        # Warm-up so the first timed inference isn't penalised by lazy init.
        for _ in range(3):
            predict("Warm up inference call.", tokenizer, model, label_map)

        latencies = []
        for _, row in df.iterrows():
            others = split_others(row.get("other_entities"))

            for strat_name, strat_fn in STRATEGIES.items():
                text = strat_fn(str(row["headline"]), str(row["target_entity"]), others)

                t1 = time.perf_counter()
                probs, predicted, confidence = predict(text, tokenizer, model, label_map)
                latencies.append((time.perf_counter() - t1) * 1000)

                rows.append({
                    "id": row["id"],
                    "headline": row["headline"],
                    "target_entity": row["target_entity"],
                    "category": row["category"],
                    "true_label": str(row["true_label"]).strip().lower(),
                    "model": model_key,
                    "strategy": strat_name,
                    "model_input": text,
                    "p_positive": round(probs["positive"], 4),
                    "p_neutral": round(probs["neutral"], 4),
                    "p_negative": round(probs["negative"], 4),
                    "predicted": predicted,
                    "confidence": round(confidence, 4),
                    "mapped_score": round(to_score(probs["positive"], probs["negative"]), 2),
                })

        lat = pd.Series(latencies)
        resource_rows.append({
            "model": model_key,
            "model_id": model_id,
            "params_millions": round(n_params / 1e6, 1),
            "fp32_size_mb_est": round(n_params * 4 / 1e6),
            "int8_size_mb_est": round(n_params / 1e6),
            "load_seconds": round(load_secs, 2),
            "rss_delta_mb": round((rss_after - rss_before) / 1e6),
            "latency_p50_ms": round(lat.quantile(0.50), 1),
            "latency_p95_ms": round(lat.quantile(0.95), 1),
            "latency_mean_ms": round(lat.mean(), 1),
        })

        print(f"  latency p50 {lat.quantile(0.50):.1f} ms | p95 {lat.quantile(0.95):.1f} ms\n")

        del model, tokenizer

    pd.DataFrame(rows).to_csv(args.out, index=False, encoding="utf-8")
    pd.DataFrame(resource_rows).to_csv(args.resources, index=False, encoding="utf-8")

    print(f"Wrote {len(rows)} predictions -> {args.out}")
    print(f"Wrote resource stats      -> {args.resources}")
    print("\nNext: python analyze.py")


if __name__ == "__main__":
    main()