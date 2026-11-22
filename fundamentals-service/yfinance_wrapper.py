#!/usr/bin/env python3
"""
yfinance_wrapper.py — one-shot batch fetch of quarterly results, balance sheets,
or closing prices for a list of NSE symbols.

Run once per invocation, for one mode, against a file of symbols (one per line,
already suffixed with ".NS"):

    python yfinance_wrapper.py --mode quarterly      --symbols-file symbols.txt
    python yfinance_wrapper.py --mode balance-sheet  --symbols-file symbols.txt
    python yfinance_wrapper.py --mode closing-price  --symbols-file symbols.txt

Optional: --delay-seconds (default 1.5) — sleep between symbols. This is the SAME
politeness delay the old design applied between HTTP calls (fundamentals.rate-limit
.delay-ms in application.properties) — batching into one process does not change
how fast/often we hit Yahoo Finance, only how long Python's memory stays resident.

Prints one JSON array to stdout — one object per symbol — and exits:

    quarterly / balance-sheet mode:
      [{"symbol": "RELIANCE.NS", "quarters": [...]}, {"symbol": "TCS.NS", "quarters": [...]}, ...]
      [{"symbol": "RELIANCE.NS", "years":    [...]}, ...]

    closing-price mode:
      [{"symbol": "RELIANCE.NS", "date": "2026-08-01", "closingPrice": 1234.5}, ...]

A symbol that fails is logged to stderr and still gets an entry with an empty
list / null price — it never aborts the batch (same resilience the old per-request
try/except already had, just now inside a loop instead of inside a Flask route).

WHY a one-shot script instead of a Flask server: this used to run as a permanent
background process (yfinance-fundamentals.service) that Java called over HTTP —
2000+ times per full sync, three data types. Python — and pandas/numpy/yfinance —
sat resident 24/7 regardless of whether a request was in flight, and was a repeated
contributor to EC2 OOM kills. Java now spawns this script per chunk of symbols
(see PythonProcessRunner / YFinanceWrapperClient in fundamentals-service), reads
its stdout, and lets it exit — the OS reclaims all of Python's memory immediately.
"""

import sys
import json
import math
import time
import argparse

import yfinance as yf


def clean(value):
    """
    Convert a float/int value to a safe Python float, or None if NaN/Inf.
    BigDecimal in Java cannot handle NaN or Infinity — we must exclude them.
    """
    if value is None:
        return None
    try:
        f = float(value)
        if math.isnan(f) or math.isinf(f):
            return None
        return f
    except (TypeError, ValueError):
        return None


def get_value(df, *keys):
    """
    Safely extract a value from a DataFrame by trying multiple key names.
    yfinance field names vary between versions and company types.
    Returns the first key that exists in df.index, or None.
    """
    for key in keys:
        if key in df.index:
            return df.loc[key]
    return None


def fetch_quarterly_for_symbol(symbol):
    """
    Returns last 4 quarters of P&L data for one symbol.

    EPS is resolved using a 3-tier fallback chain:
      1. REPORTED   — direct from quarterly income statement (most accurate)
      2. CALCULATED — Net Income / sharesOutstanding when reported EPS is absent
      3. ESTIMATED  — trailingEps / 4 when neither income stmt EPS nor net income is usable
      null          — all three tiers failed
    """
    ticker = yf.Ticker(symbol)
    df = ticker.quarterly_income_stmt  # newer yfinance versions

    if df is None or df.empty:
        df = ticker.quarterly_financials  # fallback for older yfinance versions

    if df is None or df.empty:
        return []

    # ticker.info is a single HTTP call — fetch once, outside the per-quarter loop
    ticker_info = {}
    try:
        ticker_info = ticker.info or {}
    except Exception:
        pass

    shares_outstanding = (ticker_info.get('sharesOutstanding')
                          or ticker_info.get('impliedSharesOutstanding'))
    trailing_eps = ticker_info.get('trailingEps')

    revenue_series      = get_value(df, 'Total Revenue', 'TotalRevenue', 'Revenue')
    gross_profit_series = get_value(df, 'Gross Profit', 'GrossProfit')
    ebitda_series       = get_value(df, 'EBITDA', 'Operating Income', 'OperatingIncome',
                                    'Ebitda', 'NormalizedEBITDA')
    net_income_series   = get_value(df, 'Net Income', 'NetIncome',
                                    'Net Income Common Stockholders')
    eps_series = get_value(df,
        'Diluted EPS', 'Basic EPS', 'EPS', 'DilutedEPS', 'BasicEPS',
        'Diluted', 'Basic',
        'Earnings Per Share', 'EarningsPerShare',
        'Basic Earnings Per Share', 'Diluted Earnings Per Share',
        'Net Income Per Share',
        'Normalized Basic EPS', 'Normalized Diluted EPS',
    )

    quarters = []
    for col in list(df.columns)[:4]:
        period_date = col.strftime('%Y-%m-%d') if hasattr(col, 'strftime') else str(col)[:10]

        # Tier 1 — reported EPS directly from income statement
        eps_value = clean(eps_series[col] if eps_series is not None else None)

        # Tier 2 — calculate: Net Income / current shares outstanding
        if eps_value is None:
            net_income_val = clean(net_income_series[col] if net_income_series is not None else None)
            if (net_income_val is not None
                    and shares_outstanding is not None
                    and shares_outstanding > 0):
                eps_value = clean(net_income_val / shares_outstanding)

        # Tier 3 — estimate: Yahoo's trailing annual EPS divided equally across 4 quarters
        if eps_value is None:
            trailing_clean = clean(trailing_eps)
            if trailing_clean is not None:
                eps_value = trailing_clean / 4

        quarters.append({
            "periodDate":   period_date,
            "totalRevenue": clean(revenue_series[col] if revenue_series is not None else None),
            "grossProfit":  clean(gross_profit_series[col] if gross_profit_series is not None else None),
            "ebitda":       clean(ebitda_series[col] if ebitda_series is not None else None),
            "netIncome":    clean(net_income_series[col] if net_income_series is not None else None),
            "eps":          eps_value,
        })

    return quarters


def fetch_balance_sheet_for_symbol(symbol):
    """Returns last 3 annual balance sheets for one symbol."""
    ticker = yf.Ticker(symbol)
    df = ticker.balance_sheet

    if df is None or df.empty:
        return []

    total_assets      = get_value(df, 'Total Assets', 'TotalAssets')
    current_assets     = get_value(df, 'Current Assets', 'CurrentAssets')
    cash               = get_value(df, 'Cash And Cash Equivalents',
                                   'Cash Cash Equivalents And Short Term Investments',
                                   'CashAndCashEquivalents', 'Cash')
    investments        = get_value(df, 'Investments', 'Long Term Investments',
                                   'Available For Sale Securities')
    fixed_assets       = get_value(df, 'Net PPE', 'Property Plant Equipment',
                                   'PropertyPlantEquipmentNet', 'NetPPE')
    total_liab         = get_value(df, 'Total Liabilities Net Minority Interest',
                                   'Total Liabilities', 'TotalLiabilitiesNetMinorityInterest')
    current_liab       = get_value(df, 'Current Liabilities', 'CurrentLiabilities')
    total_debt         = get_value(df, 'Total Debt', 'TotalDebt')
    long_term_debt     = get_value(df, 'Long Term Debt', 'LongTermDebt')
    equity             = get_value(df, 'Stockholders Equity', 'Total Equity Gross Minority Interest',
                                   'StockholdersEquity', 'CommonStockEquity')
    retained_earnings  = get_value(df, 'Retained Earnings', 'RetainedEarnings')
    share_capital      = get_value(df, 'Common Stock', 'Share Capital', 'CommonStock',
                                   'Ordinary Shares Number')

    def val(series, col):
        return clean(series[col] if series is not None else None)

    years = []
    for col in list(df.columns)[:3]:
        period_date = col.strftime('%Y-%m-%d') if hasattr(col, 'strftime') else str(col)[:10]
        years.append({
            "periodDate":         period_date,
            "totalAssets":        val(total_assets, col),
            "currentAssets":      val(current_assets, col),
            "cashAndEquivalents": val(cash, col),
            "totalInvestments":   val(investments, col),
            "fixedAssets":        val(fixed_assets, col),
            "totalLiabilities":   val(total_liab, col),
            "currentLiabilities": val(current_liab, col),
            "totalDebt":          val(total_debt, col),
            "longTermDebt":       val(long_term_debt, col),
            "shareholdersEquity": val(equity, col),
            "retainedEarnings":   val(retained_earnings, col),
            "shareCapital":       val(share_capital, col),
        })

    return years


def fetch_closing_price_for_symbol(symbol):
    """Returns the previous trading day's closing price for one symbol."""
    ticker = yf.Ticker(symbol)

    previous_close = None
    trade_date = None

    try:
        info = ticker.info
        previous_close = info.get('previousClose') or info.get('regularMarketPreviousClose')
    except Exception:
        pass

    if previous_close is None:
        try:
            hist = ticker.history(period="5d")
            if hist is not None and not hist.empty:
                previous_close = float(hist['Close'].iloc[-1])
                trade_date = hist.index[-1].date().strftime('%Y-%m-%d')
        except Exception:
            pass

    if trade_date is None:
        try:
            hist = ticker.history(period="5d")
            if hist is not None and not hist.empty:
                trade_date = hist.index[-1].date().strftime('%Y-%m-%d')
        except Exception:
            from datetime import date, timedelta
            trade_date = (date.today() - timedelta(days=1)).strftime('%Y-%m-%d')

    return {"date": trade_date, "closingPrice": clean(previous_close)}


MODE_HANDLERS = {
    "quarterly":      ("quarters", fetch_quarterly_for_symbol),
    "balance-sheet":  ("years",    fetch_balance_sheet_for_symbol),
}


def run_batch(mode, symbols, delay_seconds):
    result = []
    for i, symbol in enumerate(symbols):
        try:
            if mode == "closing-price":
                entry = {"symbol": symbol}
                entry.update(fetch_closing_price_for_symbol(symbol))
            else:
                list_key, fetch_fn = MODE_HANDLERS[mode]
                entry = {"symbol": symbol, list_key: fetch_fn(symbol)}
            result.append(entry)
        except Exception as e:
            print(f"{mode}: failed for {symbol} — {e}", file=sys.stderr)
            if mode == "closing-price":
                result.append({"symbol": symbol, "date": None, "closingPrice": None})
            else:
                list_key = MODE_HANDLERS[mode][0]
                result.append({"symbol": symbol, list_key: []})

        # Same politeness delay the old per-HTTP-call design applied — batching
        # into one process changes memory footprint, not how fast we hit Yahoo.
        if i < len(symbols) - 1:
            time.sleep(delay_seconds)

    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', required=True, choices=['quarterly', 'balance-sheet', 'closing-price'])
    parser.add_argument('--symbols-file', required=True)
    parser.add_argument('--delay-seconds', type=float, default=1.5)
    args = parser.parse_args()

    with open(args.symbols_file, 'r') as f:
        symbols = [line.strip() for line in f if line.strip()]

    print(json.dumps(run_batch(args.mode, symbols, args.delay_seconds)))