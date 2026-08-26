# Model Comparison PoC — Sentiment Model Bake-Off

Compares three candidate Hugging Face sentiment models on **real NSE / Google-RSS
headlines**, with special attention to **multi-entity headlines** where sentiment
differs per company.

---

## Isolation guarantee

This folder is completely separate from the application:

- **No existing file was modified.** Everything here is new.
- **Not part of any build** — no Maven module, no Spring component scan, nothing
  imported by any Java service.
- **Database access is read-only** and optional (`extract_candidates.py` opens the
  connection with `readonly=True` and issues a single `SELECT`).
- **Deleting this folder removes every trace.** Nothing depends on it.
- `.gitignore` here keeps data and results out of version control. If you want a
  zero footprint entirely, add `ml-poc/` to the repo-root `.gitignore`.

---

## What is actually being compared

| Model | Params | Notes |
|---|---|---|
| `peyterho/finbert-macro-sentiment` | 109M | Fine-tune of FinBERT; the only one with a published headline OOD score (0.676 macro-F1 on 30,150 stock headlines) |
| `ProsusAI/finbert` | 110M | Industry standard; known severe neutral bias (47% negative recall) |
| `mrm8488/distilroberta-...` | 82M | Lightest; explicit Apache 2.0 |

**Important:** all three are plain sequence classifiers — text in, one 3-class
distribution out. **None has a "target entity" parameter.** The only way to tell
the model which company you care about is to change the *input text*, which is why
this harness tests **5 input strategies** as well as 3 models.

| Strategy | Input for target=TCS |
|---|---|
| `raw` | `TCS gains as Infosys slumps` |
| `prefix` | `TCS: TCS gains as Infosys slumps` |
| `template` | `Sentiment for TCS. TCS gains as Infosys slumps` |
| `mask` | `TCS gains as [OTHER] slumps` |
| `clause` | `TCS gains` |

`mask` and `clause` are the ones most likely to work. Expect `raw`, `prefix` and
`template` to produce near-identical scores for both companies in a pair — that is
itself a finding, and it tells you entity-awareness has to come from fine-tuning
rather than prompt formatting.

---

## Setup (Windows PowerShell)

```powershell
cd ml-poc

# Optional but recommended — keeps these deps out of your global Python
python -m venv .venv
.\.venv\Scripts\Activate.ps1
# If PowerShell blocks activation, either run
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# or just skip the venv and pip install globally.

pip install -r requirements.txt
```

First run downloads **~1.3 GB** of model weights (cached in `%USERPROFILE%\.cache\huggingface`
thereafter). Nothing is downloaded again on subsequent runs.

**No GPU required.** 24 test cases x 5 strategies x 3 models is ~360 inferences —
a couple of minutes on CPU.

---

## Workflow

### Step 1 — Get real headlines (optional but strongly recommended)

```powershell
$env:DB_PASSWORD="yourpassword"
python extract_candidates.py --limit 120 --out data/candidates.csv
```

Then hand-pick ~30 into `testset.csv`. **Use real headlines** — invented ones are
systematically easier and will flatter every model.

The script also prints a sample of what `summary` actually contains. Check this:
if it holds only the RSS *title* and not the *description*, appending the
description is likely the cheapest accuracy win available to you, with no new
infrastructure at all.

### Step 2 — Build and label the test set

`testset.csv` ships with 24 example rows showing the format. Extend to ~36 cases
following these strata:

| Category | Target count | Probes |
|---|---|---|
| `clear_positive` | 4 | Baseline sanity |
| `clear_negative` | 4 | Baseline + **negative recall** (FinBERT's known weakness) |
| `neutral` | 4 | Neutral over-prediction |
| `multi_entity` | **6** (= 3 headlines x 2 targets) | **Your priority case** |
| `negation` | 3 | "not a good quarter" |
| `idiom` | 3 | "profit booking", "block deal" |
| `comparative` | 3 | "beats but guides lower" |
| `nse_announcement` | 3 | Formal corporate register |

**Columns:**

| Column | Meaning |
|---|---|
| `id` | Any unique integer |
| `headline` | The text as it appears in `company_news` |
| `target_entity` | The company this row's label refers to |
| `other_entities` | Pipe-separated competing entities (`INFY\|WIPRO`), blank if none |
| `true_label` | `positive` / `negative` / `neutral` |
| `category` | One of the strata above |

**Multi-entity headlines appear TWICE** — once per company, with `target_entity`
and `other_entities` swapped and *opposite* labels. See rows 7–12 for the pattern.

**Labeling rules:**

1. **Label before running any model.** Labeling after seeing predictions
   contaminates the comparison.
2. **Label independently with your mentor, then reconcile.** If you two only agree
   on 85% of these, no model can meaningfully beat 85% — that is your ceiling, and
   knowing it stops you chasing an unreachable number.
3. Label sentiment **for the target entity only**, not for the sentence overall.

### Step 3 — Run the comparison

```powershell
python run_comparison.py
```

Writes `results/raw_results.csv` (one row per case x model x strategy) and
`results/resources.csv` (size, load time, memory, latency).

### Step 4 — Analyse

```powershell
python analyze.py
```

Prints nine sections: overall metrics, best strategy per model, multi-entity
accuracy, neutral rate, confidence calibration, multi-entity detail, confusion
matrix, resource comparison, and a sample-size warning.

### Step 5 — Decide

Agree this rule **before** looking at results, so you can't rationalise toward a
model you already favour:

```
1. Multi-entity accuracy differs by >15 points   -> that model wins
2. Else macro-F1 differs by >5 points            -> higher macro-F1 wins
3. Else negative recall differs by >10 points    -> higher negative recall wins
4. Else                                          -> smallest model wins
```

---

## How to read the output

| Metric | What it tells you |
|---|---|
| **macro_f1** | The headline number. Weights all three classes equally so a neutral-collapsing model cannot hide behind accuracy |
| **recall_neg** | Base FinBERT scores ~47% here. If a model misses half your negative headlines it is unusable for this feature |
| **neutral_rate** | % of predictions that are neutral. A high rate means the cumulative score will flatline near 0 |
| **multi_entity_acc** | Your priority case. Compare across strategies, not just models |
| **separation** (calibration) | `conf_when_correct - conf_when_wrong`. Above ~0.05 means confidence is informative and confidence-weighted EMA is worth building. Near 0 means drop that idea |
| **Multi-entity detail** | The two rows of each pair should have **opposite signs**. Identical scores mean the model ignored the target entity entirely |

### Cost

All three models are **free, open-weight, and run locally — no per-call cost, ever.**
"Cost" here is purely resources: memory, latency, and whether any of them forces a
larger EC2 instance. The expected answer is that the ~27 MB spread is ~1.4% of a
t3.small and **cost is not a differentiator** — measure it, confirm it, then decide
on accuracy alone.

---

## ⚠️ Sample-size reality check

With ~36 test cases the 95% confidence interval on accuracy is roughly **±15 points**.
Two models differing by 10 points here are **statistically indistinguishable**.

This run is a **qualitative smoke test**: it reveals behaviour patterns, catastrophic
failures, and which input strategy works. If no model wins decisively, expand to
**150–300 labeled cases** before making the final call. That is ~3–4 extra hours of
labeling and it is what makes the decision defensible.

---

## Gotchas

1. **Label order differs per model.** `finbert-macro-sentiment` uses a different
   ordering to the others. `run_comparison.py` reads `id2label` from each model's
   config and raises a loud error if a label can't be interpreted — **never hardcode
   indices.** Getting this wrong silently inverts every score with no error raised.
   It is by far the most likely way to get a wrong answer from this experiment.

2. **FinBERT is uncased, DistilRoBERTa is cased.** FinBERT lowercases `RELIANCE`
   to `reliance`, discarding ticker capitalisation. This is a genuine confound
   between the models, and it matters most for the `prefix` strategy.

3. **`max_length` is pinned to 128 for all three** so you compare models rather
   than truncation policies. Headlines are ~32–64 tokens, so nothing is cut.

4. **Neutral dominance is expected.** Financial headlines genuinely skew neutral.
   That is why `macro_f1` and `neutral_rate` matter more than raw accuracy.

5. **Nothing here validates the Java path.** ONNX export and Java/Python parity
   testing come later, after a model is chosen.

---

## Running on Google Colab instead

If you'd rather not install locally: create a notebook, upload `testset.csv`, and
paste `run_comparison.py` then `analyze.py` into two cells. Prefix the first cell
with:

```python
!pip install transformers torch pandas scikit-learn psutil -q
```

Everything else works unchanged. Colab gives a free GPU, though CPU is more than
sufficient at this scale.

---

## Cleanup

```powershell
Remove-Item -Recurse -Force ml-poc
```

Nothing in the application depends on this folder. To also reclaim the model cache:
`Remove-Item -Recurse -Force $env:USERPROFILE\.cache\huggingface`