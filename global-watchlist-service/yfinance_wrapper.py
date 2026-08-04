#!/usr/bin/env python3
"""
yfinance_wrapper.py — one-shot batch fetch of global market indices/commodities.

Run directly, once, per invocation:
    python yfinance_wrapper.py

Prints one JSON array to stdout and exits — e.g.:
    [
      {"symbol": "^GSPC", "name": "S&P 500", "flagEmoji": "🇺🇸",
       "region": "US_MARKETS", "ltp": 5000.0, "change": 10.0, "changePercent": 0.2},
      ...
    ]

WHY a one-shot script instead of a Flask server: this used to run as a permanent
background process (yfinance-global.service) that the Java side called over HTTP.
That meant Python — and everything pandas/numpy/yfinance load into memory — sat
resident 24/7, contributing to repeated EC2 OOM kills even though the actual work
(one batch fetch of ~18 symbols) takes a few seconds. Java now spawns this script
as a child process on its own schedule (see PythonProcessRunner /
YahooFinanceClient in global-watchlist-service), reads this stdout output, and lets
the process exit — the OS reclaims all of Python's memory immediately afterward.
No server, no port, nothing left running between fetch cycles.

Diagnostics (skipped symbols, batch failures) go to stderr, not stdout, so Java can
cleanly separate "the result" from "noise" when reading the process's output.
"""

import sys
import json
import math

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


GLOBAL_INDICES = [
    # US Markets
    {"symbol": "^GSPC",     "name": "S&P 500",       "flagEmoji": "🇺🇸", "region": "US_MARKETS"},
    {"symbol": "^DJI",      "name": "Dow Jones",      "flagEmoji": "🇺🇸", "region": "US_MARKETS"},
    {"symbol": "^IXIC",     "name": "Nasdaq",         "flagEmoji": "🇺🇸", "region": "US_MARKETS"},
    {"symbol": "^RUT",      "name": "Russell 2000",   "flagEmoji": "🇺🇸", "region": "US_MARKETS"},
    # European Markets
    {"symbol": "^FTSE",     "name": "FTSE 100",       "flagEmoji": "🇬🇧", "region": "EUROPEAN_MARKETS"},
    {"symbol": "^GDAXI",    "name": "DAX",            "flagEmoji": "🇩🇪", "region": "EUROPEAN_MARKETS"},
    {"symbol": "^FCHI",     "name": "CAC 40",         "flagEmoji": "🇫🇷", "region": "EUROPEAN_MARKETS"},
    {"symbol": "^STOXX50E", "name": "Euro Stoxx 50",  "flagEmoji": "🇪🇺", "region": "EUROPEAN_MARKETS"},
    # Asian Markets
    {"symbol": "^N225",     "name": "Nikkei 225",     "flagEmoji": "🇯🇵", "region": "ASIAN_MARKETS"},
    {"symbol": "^HSI",      "name": "Hang Seng",      "flagEmoji": "🇭🇰", "region": "ASIAN_MARKETS"},
    {"symbol": "^KS11",     "name": "KOSPI",          "flagEmoji": "🇰🇷", "region": "ASIAN_MARKETS"},
    {"symbol": "000001.SS", "name": "Shanghai Comp.", "flagEmoji": "🇨🇳", "region": "ASIAN_MARKETS"},
    {"symbol": "^STI",      "name": "Straits Times",  "flagEmoji": "🇸🇬", "region": "ASIAN_MARKETS"},
    # Commodities
    {"symbol": "GC=F",      "name": "Gold",           "flagEmoji": "🪙",  "region": "COMMODITIES", "unit": "USD/oz"},
    {"symbol": "SI=F",      "name": "Silver",         "flagEmoji": "🪙",  "region": "COMMODITIES", "unit": "USD/oz"},
    {"symbol": "HG=F",      "name": "Copper",         "flagEmoji": "🔩",  "region": "COMMODITIES", "unit": "USD/lb"},
    {"symbol": "CL=F",      "name": "Crude Oil",      "flagEmoji": "🛢️",  "region": "COMMODITIES", "unit": "USD/bbl"},
    {"symbol": "NG=F",      "name": "Natural Gas",    "flagEmoji": "🔥",  "region": "COMMODITIES", "unit": "USD/MMBtu"},
]


def fetch_global_indices():
    """
    Fetches live price data for GLOBAL_INDICES in one batch yf.Tickers() call.
    A single bad symbol is logged to stderr and skipped — it never aborts the batch.
    """
    result = []
    symbols = [idx["symbol"] for idx in GLOBAL_INDICES]
    meta = {idx["symbol"]: idx for idx in GLOBAL_INDICES}
    try:
        tickers = yf.Tickers(" ".join(symbols))
        for sym in symbols:
            try:
                info = tickers.tickers[sym].fast_info
                ltp = clean(getattr(info, 'last_price', None))
                prev = clean(getattr(info, 'previous_close', None))
                change = clean(ltp - prev) if ltp is not None and prev is not None else None
                change_pct = clean((change / prev) * 100) if change is not None and prev else None
                entry = dict(meta[sym])
                entry["ltp"] = ltp
                entry["change"] = change
                entry["changePercent"] = change_pct
                result.append(entry)
            except Exception as e:
                print(f"global-indices: skipping {sym} — {e}", file=sys.stderr)
    except Exception as e:
        print(f"global-indices batch fetch failed: {e}", file=sys.stderr)
    return result


if __name__ == '__main__':
    print(json.dumps(fetch_global_indices()))