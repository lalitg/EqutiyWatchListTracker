#!/usr/bin/env python3
"""
OPTIONAL — pull candidate headlines from company_news so the test set uses real
production data instead of invented examples.

READ-ONLY. Issues a single SELECT and writes a CSV. It never writes, updates or
deletes anything in the database, and touches no application code.

Synthetic headlines are systematically cleaner than real ones and will flatter
every model, so prefer real data for the test set.

Usage:
    set DB_PASSWORD=yourpassword
    python extract_candidates.py --limit 120 --out data/candidates.csv
"""

import argparse
import os

import pandas as pd
import psycopg2

QUERY = """
SELECT cn.keyword,
       item->>'summary'  AS headline,
       item->>'link'     AS link,
       item->>'date'     AS published,
       item->>'category' AS importance
FROM company_news cn,
     LATERAL jsonb_array_elements(cn.news) AS item
WHERE item->>'summary' IS NOT NULL
ORDER BY random()
LIMIT %s;
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=120,
                        help="how many candidate headlines to pull")
    parser.add_argument("--out", default="data/candidates.csv")
    args = parser.parse_args()

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)

    conn = psycopg2.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "watchlisttracker"),
        user=os.getenv("DB_USERNAME", "postgres"),
        password=os.getenv("DB_PASSWORD", "postgres"),
    )
    # Belt and braces: this connection must never be able to write.
    conn.set_session(readonly=True, autocommit=True)

    try:
        df = pd.read_sql_query(QUERY, conn, params=(args.limit,))
    finally:
        conn.close()

    df.to_csv(args.out, index=False, encoding="utf-8")

    print(f"Wrote {len(df)} candidate headlines -> {args.out}\n")
    print("IMPORTANT — check what 'summary' actually holds before building the")
    print("test set. If it is the RSS title only, adding the description may be")
    print("the cheapest accuracy win available. Sample:\n")
    for h in df["headline"].head(5):
        print(f"  - {h}")

    print("\nNext: hand-pick ~30 headlines into testset.csv following the strata")
    print("in README.md, and label them BEFORE running any model.")


if __name__ == "__main__":
    main()