#!/usr/bin/env python3
"""
Analyse the model-comparison results.

Reads results/raw_results.csv (produced by run_comparison.py) and prints the
metrics that actually decide the choice. Accuracy alone is misleading here
because financial headlines skew heavily neutral.

Usage:
    python analyze.py
    python analyze.py --results results/raw_results.csv
"""

import argparse

import pandas as pd
from sklearn.metrics import confusion_matrix, f1_score, recall_score

LABELS = ["positive", "neutral", "negative"]
pd.set_option("display.width", 200)
pd.set_option("display.max_columns", 50)


def banner(title):
    print("\n" + "=" * 78)
    print(title)
    print("=" * 78)


def per_group_metrics(df):
    """One row per (model, strategy) with the metrics that matter."""
    out = []
    for (model, strategy), g in df.groupby(["model", "strategy"]):
        y_true, y_pred = g["true_label"], g["predicted"]

        multi = g[g["category"] == "multi_entity"]
        multi_acc = (multi["true_label"] == multi["predicted"]).mean() if len(multi) else float("nan")

        out.append({
            "model": model,
            "strategy": strategy,
            "accuracy": round((y_true == y_pred).mean(), 3),
            # Macro-F1 is the headline number: it weights all three classes
            # equally, so a model that collapses to "neutral" cannot hide.
            "macro_f1": round(f1_score(y_true, y_pred, labels=LABELS,
                                       average="macro", zero_division=0), 3),
            "recall_pos": round(recall_score(y_true, y_pred, labels=["positive"],
                                             average="micro", zero_division=0), 3),
            "recall_neg": round(recall_score(y_true, y_pred, labels=["negative"],
                                             average="micro", zero_division=0), 3),
            # % of predictions that are "neutral" — directly measures the
            # neutral-collapse failure mode.
            "neutral_rate": round((y_pred == "neutral").mean(), 3),
            "multi_entity_acc": round(multi_acc, 3) if multi_acc == multi_acc else None,
            "mean_conf": round(g["confidence"].mean(), 3),
        })
    return pd.DataFrame(out).sort_values(["macro_f1", "multi_entity_acc"], ascending=False)


def confidence_calibration(df):
    """
    Does confidence actually separate right from wrong answers?

    If it does not, confidence-weighted EMA buys nothing and should be dropped
    from the aggregation design.
    """
    out = []
    for model, g in df.groupby("model"):
        correct = g[g["true_label"] == g["predicted"]]["confidence"]
        wrong = g[g["true_label"] != g["predicted"]]["confidence"]
        out.append({
            "model": model,
            "conf_when_correct": round(correct.mean(), 3) if len(correct) else None,
            "conf_when_wrong": round(wrong.mean(), 3) if len(wrong) else None,
            "separation": round(correct.mean() - wrong.mean(), 3)
                          if len(correct) and len(wrong) else None,
        })
    return pd.DataFrame(out)


def multi_entity_detail(df, strategy):
    """Side-by-side view of the opposing-sentiment headlines."""
    sub = df[(df["category"] == "multi_entity") & (df["strategy"] == strategy)]
    if sub.empty:
        return None
    piv = sub.pivot_table(
        index=["id", "target_entity", "true_label"],
        columns="model",
        values="mapped_score",
        aggfunc="first",
    )
    return piv.reset_index()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", default="results/raw_results.csv")
    parser.add_argument("--resources", default="results/resources.csv")
    args = parser.parse_args()

    df = pd.read_csv(args.results)
    df["true_label"] = df["true_label"].str.strip().str.lower()
    df["predicted"] = df["predicted"].str.strip().str.lower()

    banner("1. OVERALL METRICS  (one row per model x input strategy)")
    metrics = per_group_metrics(df)
    print(metrics.to_string(index=False))

    banner("2. BEST STRATEGY PER MODEL  (by macro-F1)")
    best = metrics.loc[metrics.groupby("model")["macro_f1"].idxmax()]
    print(best.to_string(index=False))

    banner("3. MULTI-ENTITY ACCURACY  (the priority case)")
    me = metrics.pivot_table(index="strategy", columns="model",
                             values="multi_entity_acc")
    print(me.to_string())
    print("\nIf every cell is ~equal and low, entity-awareness cannot be fixed by")
    print("input formatting alone — it has to come from fine-tuning with the")
    print("target keyword in the training labels, or from clause splitting.")

    banner("4. NEUTRAL PREDICTION RATE  (lower is usually better)")
    nr = metrics.pivot_table(index="strategy", columns="model",
                             values="neutral_rate")
    print(nr.to_string())
    print("\nA model predicting neutral for most headlines will flatline the EMA near 0.")

    banner("5. CONFIDENCE CALIBRATION")
    print(confidence_calibration(df).to_string(index=False))
    print("\n'separation' > ~0.05 means confidence is informative and")
    print("confidence-weighted EMA is worth implementing. Near 0 means it is not.")

    banner("6. MULTI-ENTITY DETAIL  (mapped scores, -5..+5)")
    for strategy in ["raw", "mask", "clause"]:
        detail = multi_entity_detail(df, strategy)
        if detail is not None:
            print(f"\n--- strategy: {strategy} ---")
            print(detail.to_string(index=False))
    print("\nFor a correct result the two rows of each headline pair should have")
    print("OPPOSITE signs. Identical scores mean the model ignored the target entity.")

    banner("7. CONFUSION MATRIX  (best model+strategy overall)")
    top = metrics.iloc[0]
    sub = df[(df["model"] == top["model"]) & (df["strategy"] == top["strategy"])]
    cm = confusion_matrix(sub["true_label"], sub["predicted"], labels=LABELS)
    print(f"{top['model']} / {top['strategy']}   (rows = true, cols = predicted)")
    print(pd.DataFrame(cm, index=[f"true_{l}" for l in LABELS],
                       columns=[f"pred_{l}" for l in LABELS]).to_string())

    banner("8. RESOURCE / COST COMPARISON")
    try:
        print(pd.read_csv(args.resources).to_string(index=False))
        print("\nAll three are free, open-weight, and run locally: no per-call cost.")
        print("The only real cost difference is memory, and the spread is ~27 MB —")
        print("about 1.4% of a t3.small. Confirm, then decide on accuracy alone.")
    except FileNotFoundError:
        print(f"(no {args.resources} found)")

    banner("9. SAMPLE SIZE WARNING")
    n = df.groupby(["model", "strategy"]).size().iloc[0]
    print(f"Each model/strategy cell was scored on {n} test cases.")
    if n < 100:
        print("\n  ** With fewer than ~100 cases the 95% CI on accuracy is roughly")
        print("  ** +/- 15 points. Differences below that are NOISE, not signal.")
        print("  ** Treat this run as a qualitative smoke test. If no model wins")
        print("  ** decisively, expand the test set to 150-300 before deciding.")


if __name__ == "__main__":
    main()