#!/usr/bin/env python3
"""
yfinance_wrapper.py — Python Flask REST wrapper for Yahoo Finance fundamentals data.

This script runs as a local HTTP server on port 5001.
The Java fundamentals-service calls it to get quarterly results and balance sheets
for NSE-listed companies via the yfinance library.

SETUP (run once):
    pip install flask yfinance

START:
    python yfinance_wrapper.py

ENDPOINTS:
    GET /quarterly?symbol=RELIANCE.NS
        Returns last 4 quarters of P&L data.

    GET /balance-sheet?symbol=RELIANCE.NS
        Returns last 3 annual balance sheets.

    GET /health
        Returns {"status": "ok"} — use this to verify the wrapper is running.

SYMBOL FORMAT:
    Append .NS to the NSE symbol: RELIANCE → RELIANCE.NS
    The Java client does this automatically — you don't need to add .NS yourself
    when calling from the Java service.

NOTES:
    - yfinance fetches from Yahoo Finance. No API key required.
    - Yahoo Finance can be slow for some symbols — RestTemplate timeout is 30s.
    - If a symbol is not found on Yahoo Finance, returns empty lists (not an error).
    - Run this before starting the Java fundamentals-service.
    - On EC2, run as a background process: nohup python yfinance_wrapper.py &
"""

from flask import Flask, jsonify, request
import yfinance as yf
import math

app = Flask(__name__)


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

    Example: get_value(df, 'Total Revenue', 'TotalRevenue', 'Revenue')
    Returns the first key that exists in df.index, or None.
    """
    for key in keys:
        if key in df.index:
            return df.loc[key]
    return None


@app.route('/health')
def health():
    return jsonify({"status": "ok"})


@app.route('/quarterly')
def quarterly():
    """
    Returns last 4 quarters of P&L data for the given symbol.

    EPS is resolved using a 3-tier fallback chain:
      1. REPORTED  — direct from quarterly income statement (most accurate)
      2. CALCULATED — Net Income / sharesOutstanding when reported EPS is absent
      3. ESTIMATED  — trailingEps / 4 when neither income stmt EPS nor net income is usable
      null          — all three tiers failed; no EPS data available for this quarter

    Response format:
    {
      "symbol": "RELIANCE.NS",
      "quarters": [
        {
          "periodDate":   "2024-09-30",
          "totalRevenue": 239628000000.0,
          "grossProfit":  82000000000.0,
          "ebitda":       51000000000.0,
          "netIncome":    16563000000.0,
          "eps":          24.47,
          "epsSource":    "REPORTED"
        },
        ...
      ]
    }

    epsSource is returned for transparency but ignored by the Java layer
    (Spring Boot Jackson silently drops unknown fields).
    """
    symbol = request.args.get('symbol', '').strip()
    if not symbol:
        return jsonify({"error": "symbol parameter is required"}), 400

    try:
        ticker = yf.Ticker(symbol)
        df = ticker.quarterly_income_stmt  # newer yfinance versions

        if df is None or df.empty:
            df = ticker.quarterly_financials  # fallback for older yfinance versions

        if df is None or df.empty:
            return jsonify({"symbol": symbol, "quarters": []})

        # ── Fetch ticker.info once for Approach 2 and 3 fallbacks ────────────
        # ticker.info is a single HTTP call — fetch outside the per-quarter loop
        # to avoid calling Yahoo Finance 4 times per company.
        ticker_info = {}
        try:
            ticker_info = ticker.info or {}
        except Exception:
            pass

        # Approach 2: shares outstanding for Net Income / shares calculation
        shares_outstanding = (ticker_info.get('sharesOutstanding')
                              or ticker_info.get('impliedSharesOutstanding'))

        # Approach 3: Yahoo Finance's pre-computed trailing 12-month EPS
        trailing_eps = ticker_info.get('trailingEps')

        # ── Extract series once — they cover all quarters in df ──────────────
        revenue_series      = get_value(df, 'Total Revenue', 'TotalRevenue', 'Revenue')
        gross_profit_series = get_value(df, 'Gross Profit', 'GrossProfit')
        ebitda_series       = get_value(df, 'EBITDA', 'Operating Income', 'OperatingIncome',
                                        'Ebitda', 'NormalizedEBITDA')
        net_income_series   = get_value(df, 'Net Income', 'NetIncome',
                                        'Net Income Common Stockholders')

        # Approach 1: expanded list of known EPS field names across yfinance versions
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

            # ── 3-tier EPS fallback chain ─────────────────────────────────────

            # Tier 1 — reported EPS directly from income statement
            eps_value  = clean(eps_series[col] if eps_series is not None else None)
            eps_source = 'REPORTED' if eps_value is not None else None

            # Tier 2 — calculate: Net Income / current shares outstanding
            # Note: shares_outstanding is today's count, not the historical quarter count.
            # Difference is small for most companies (no frequent equity dilution).
            if eps_value is None:
                net_income_val = clean(net_income_series[col] if net_income_series is not None else None)
                if (net_income_val is not None
                        and shares_outstanding is not None
                        and shares_outstanding > 0):
                    eps_value  = clean(net_income_val / shares_outstanding)
                    eps_source = 'CALCULATED' if eps_value is not None else None

            # Tier 3 — estimate: Yahoo's trailing annual EPS divided equally across 4 quarters.
            # Assumes even earnings distribution — less accurate for seasonal businesses.
            if eps_value is None:
                trailing_clean = clean(trailing_eps)
                if trailing_clean is not None:
                    eps_value  = trailing_clean / 4
                    eps_source = 'ESTIMATED'

            quarters.append({
                "periodDate":   period_date,
                "totalRevenue": clean(revenue_series[col] if revenue_series is not None else None),
                "grossProfit":  clean(gross_profit_series[col] if gross_profit_series is not None else None),
                "ebitda":       clean(ebitda_series[col] if ebitda_series is not None else None),
                "netIncome":    clean(net_income_series[col] if net_income_series is not None else None),
                "eps":          eps_value,
                "epsSource":    eps_source,
            })

        return jsonify({"symbol": symbol, "quarters": quarters})

    except Exception as e:
        app.logger.warning("quarterly fetch failed for %s: %s", symbol, str(e))
        return jsonify({"symbol": symbol, "quarters": [], "error": str(e)})


@app.route('/balance-sheet')
def balance_sheet():
    """
    Returns last 3 annual balance sheets for the given symbol.

    Response format:
    {
      "symbol": "RELIANCE.NS",
      "years": [
        {
          "periodDate": "2024-03-31",
          "totalAssets": 1234567000000.0,
          "currentAssets": 234567000000.0,
          "cashAndEquivalents": 12345000000.0,
          "totalInvestments": 89000000000.0,
          "fixedAssets": 560000000000.0,
          "totalLiabilities": 890000000000.0,
          "currentLiabilities": 123000000000.0,
          "totalDebt": 345000000000.0,
          "longTermDebt": 222000000000.0,
          "shareholdersEquity": 344567000000.0,
          "retainedEarnings": 234000000000.0,
          "shareCapital": 6765000000.0
        },
        ...
      ]
    }
    """
    symbol = request.args.get('symbol', '').strip()
    if not symbol:
        return jsonify({"error": "symbol parameter is required"}), 400

    try:
        ticker = yf.Ticker(symbol)
        df = ticker.balance_sheet  # annual balance sheet

        if df is None or df.empty:
            return jsonify({"symbol": symbol, "years": []})

        years = []
        # df columns are timestamps (most recent first); take up to 3 annual periods
        for col in list(df.columns)[:3]:
            period_date = col.strftime('%Y-%m-%d') if hasattr(col, 'strftime') else str(col)[:10]

            total_assets        = get_value(df, 'Total Assets', 'TotalAssets')
            current_assets      = get_value(df, 'Current Assets', 'CurrentAssets')
            cash                = get_value(df, 'Cash And Cash Equivalents',
                                            'Cash Cash Equivalents And Short Term Investments',
                                            'CashAndCashEquivalents', 'Cash')
            investments         = get_value(df, 'Investments', 'Long Term Investments',
                                            'Available For Sale Securities')
            fixed_assets        = get_value(df, 'Net PPE', 'Property Plant Equipment',
                                            'PropertyPlantEquipmentNet', 'NetPPE')
            total_liab          = get_value(df, 'Total Liabilities Net Minority Interest',
                                            'Total Liabilities', 'TotalLiabilitiesNetMinorityInterest')
            current_liab        = get_value(df, 'Current Liabilities', 'CurrentLiabilities')
            total_debt          = get_value(df, 'Total Debt', 'TotalDebt')
            long_term_debt      = get_value(df, 'Long Term Debt', 'LongTermDebt')
            equity              = get_value(df, 'Stockholders Equity', 'Total Equity Gross Minority Interest',
                                            'StockholdersEquity', 'CommonStockEquity')
            retained_earnings   = get_value(df, 'Retained Earnings', 'RetainedEarnings')
            share_capital       = get_value(df, 'Common Stock', 'Share Capital', 'CommonStock',
                                            'Ordinary Shares Number')

            def val(series):
                return clean(series[col] if series is not None else None)

            years.append({
                "periodDate":        period_date,
                "totalAssets":       val(total_assets),
                "currentAssets":     val(current_assets),
                "cashAndEquivalents": val(cash),
                "totalInvestments":  val(investments),
                "fixedAssets":       val(fixed_assets),
                "totalLiabilities":  val(total_liab),
                "currentLiabilities": val(current_liab),
                "totalDebt":         val(total_debt),
                "longTermDebt":      val(long_term_debt),
                "shareholdersEquity": val(equity),
                "retainedEarnings":  val(retained_earnings),
                "shareCapital":      val(share_capital),
            })

        return jsonify({"symbol": symbol, "years": years})

    except Exception as e:
        app.logger.warning("balance-sheet fetch failed for %s: %s", symbol, str(e))
        return jsonify({"symbol": symbol, "years": [], "error": str(e)})


@app.route('/closing-price')
def closing_price():
    """
    Returns the previous trading day's closing price for the given symbol.

    Response format:
    {
      "symbol": "RELIANCE.NS",
      "date": "2026-05-04",
      "closingPrice": 2487.50
    }

    Uses ticker.info['previousClose'] as the primary source — this is Yahoo
    Finance's official previous-day closing price, updated after market close.
    Falls back to the last row of ticker.history(period="5d") if info is unavailable.
    Returns closingPrice: null if the symbol is not found or price is unavailable.
    """
    symbol = request.args.get('symbol', '').strip()
    if not symbol:
        return jsonify({"error": "symbol parameter is required"}), 400

    try:
        ticker = yf.Ticker(symbol)

        # Primary: previousClose from ticker.info (Yahoo's official previous-day close)
        previous_close = None
        trade_date = None

        try:
            info = ticker.info
            previous_close = info.get('previousClose') or info.get('regularMarketPreviousClose')
        except Exception:
            pass

        # Fallback: last close from recent history
        if previous_close is None:
            try:
                hist = ticker.history(period="5d")
                if hist is not None and not hist.empty:
                    previous_close = float(hist['Close'].iloc[-1])
                    trade_date = hist.index[-1].date().strftime('%Y-%m-%d')
            except Exception:
                pass

        # Derive trade date from history if not already set
        if trade_date is None:
            try:
                hist = ticker.history(period="5d")
                if hist is not None and not hist.empty:
                    trade_date = hist.index[-1].date().strftime('%Y-%m-%d')
            except Exception:
                from datetime import date, timedelta
                # default to yesterday if history unavailable
                trade_date = (date.today() - timedelta(days=1)).strftime('%Y-%m-%d')

        return jsonify({
            "symbol": symbol,
            "date": trade_date,
            "closingPrice": clean(previous_close)
        })

    except Exception as e:
        app.logger.warning("closing-price fetch failed for %s: %s", symbol, str(e))
        return jsonify({"symbol": symbol, "closingPrice": None, "date": None, "error": str(e)})


if __name__ == '__main__':
    print("Starting yfinance wrapper on port 5001...")
    print("Endpoints: /health  /quarterly?symbol=RELIANCE.NS  /balance-sheet?symbol=RELIANCE.NS  /closing-price?symbol=RELIANCE.NS")
    app.run(host='0.0.0.0', port=5001, debug=False)
