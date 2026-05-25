#!/usr/bin/env python3
"""
yfinance_wrapper.py — Python Flask REST wrapper for global market indices.

Runs as a local HTTP server on port 5002.
The Java global-watchlist-service calls it to get live data for the 10 major
global indices via the yfinance library (which handles Yahoo Finance auth internally).

SETUP (run once):
    pip install flask yfinance

START:
    python yfinance_wrapper.py

ENDPOINTS:
    GET /global-indices
        Returns live LTP, change, and change% for 10 major global indices.

    GET /health
        Returns {"status": "ok"} — use this to verify the wrapper is running.

NOTES:
    - yfinance handles Yahoo Finance cookie/crumb auth automatically.
    - Run this before starting global-watchlist-service.
    - On EC2, run as a background process: nohup python yfinance_wrapper.py &
"""

from flask import Flask, jsonify
import yfinance as yf
import math

app = Flask(__name__)

INDICES = [
    # (yahoo_symbol, display_name, flag_emoji, region)
    ("^DJI",   "Dow Jones",     "\U0001f1fa\U0001f1f8", "US_MARKETS"),
    ("^GSPC",  "S&P 500",       "\U0001f1fa\U0001f1f8", "US_MARKETS"),
    ("^IXIC",  "Nasdaq",         "\U0001f1fa\U0001f1f8", "US_MARKETS"),
    ("^FTSE",  "FTSE 100",       "\U0001f1ec\U0001f1e7", "EUROPEAN_MARKETS"),
    ("^FCHI",  "CAC 40",         "\U0001f1eb\U0001f1f7", "EUROPEAN_MARKETS"),
    ("^GDAXI", "DAX",            "\U0001f1e9\U0001f1ea", "EUROPEAN_MARKETS"),
    ("^N225",  "Nikkei 225",     "\U0001f1ef\U0001f1f5", "ASIAN_MARKETS"),
    ("^HSI",   "Hang Seng",      "\U0001f1ed\U0001f1f0", "ASIAN_MARKETS"),
    ("^STI",   "Straits Times",  "\U0001f1f8\U0001f1ec", "ASIAN_MARKETS"),
    ("^KS11",  "KOSPI",          "\U0001f1f0\U0001f1f7", "ASIAN_MARKETS"),
]


def clean(value):
    """Convert to safe float or None — guards against NaN/Inf."""
    if value is None:
        return None
    try:
        f = float(value)
        return None if (math.isnan(f) or math.isinf(f)) else round(f, 2)
    except (TypeError, ValueError):
        return None


@app.route('/health')
def health():
    return jsonify({"status": "ok"})


@app.route('/global-indices')
def global_indices():
    symbols = [idx[0] for idx in INDICES]
    meta    = {idx[0]: idx for idx in INDICES}
    result  = []

    try:
        tickers = yf.Tickers(' '.join(symbols))
        for sym in symbols:
            m = meta[sym]
            entry = {
                "symbol":      sym,
                "name":        m[1],
                "flagEmoji":   m[2],
                "region":      m[3],
                "ltp":         None,
                "change":      None,
                "changePercent": None,
            }
            try:
                fi = tickers.tickers[sym].fast_info
                ltp  = fi.last_price
                prev = fi.previous_close
                entry["ltp"]           = clean(ltp)
                entry["change"]        = clean(ltp - prev) if ltp and prev else None
                entry["changePercent"] = clean(((ltp - prev) / prev) * 100) if ltp and prev else None
            except Exception as e:
                app.logger.warning("fast_info failed for %s: %s", sym, e)

            result.append(entry)

    except Exception as e:
        app.logger.error("Failed to fetch global indices batch: %s", e)

    return jsonify(result)


if __name__ == '__main__':
    print("yfinance global-indices wrapper starting on port 5002 …")
    print("Endpoints: /health   /global-indices")
    app.run(host='0.0.0.0', port=5002, debug=False)
