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

    Response format:
    {
      "symbol": "RELIANCE.NS",
      "quarters": [
        {
          "periodDate": "2024-09-30",
          "totalRevenue": 239628000000.0,
          "grossProfit": 82000000000.0,
          "ebitda": 51000000000.0,
          "netIncome": 16563000000.0,
          "eps": 24.47
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
        df = ticker.quarterly_income_stmt  # newer yfinance versions use income_stmt

        if df is None or df.empty:
            # fallback for older yfinance versions
            df = ticker.quarterly_financials

        if df is None or df.empty:
            return jsonify({"symbol": symbol, "quarters": []})

        quarters = []
        # df columns are timestamps (most recent first); take up to 4
        for col in list(df.columns)[:4]:
            period_date = col.strftime('%Y-%m-%d') if hasattr(col, 'strftime') else str(col)[:10]

            revenue_series      = get_value(df, 'Total Revenue', 'TotalRevenue', 'Revenue')
            gross_profit_series = get_value(df, 'Gross Profit', 'GrossProfit')
            ebitda_series       = get_value(df, 'EBITDA', 'Operating Income', 'OperatingIncome',
                                            'Ebitda', 'NormalizedEBITDA')
            net_income_series   = get_value(df, 'Net Income', 'NetIncome', 'Net Income Common Stockholders')
            eps_series          = get_value(df, 'Diluted EPS', 'Basic EPS', 'EPS', 'DilutedEPS',
                                            'BasicEPS', 'Diluted', 'Basic')

            quarters.append({
                "periodDate":   period_date,
                "totalRevenue": clean(revenue_series[col] if revenue_series is not None else None),
                "grossProfit":  clean(gross_profit_series[col] if gross_profit_series is not None else None),
                "ebitda":       clean(ebitda_series[col] if ebitda_series is not None else None),
                "netIncome":    clean(net_income_series[col] if net_income_series is not None else None),
                "eps":          clean(eps_series[col] if eps_series is not None else None),
            })

        return jsonify({"symbol": symbol, "quarters": quarters})

    except Exception as e:
        # Return empty list so Java logs it as FAILED but does not crash
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


if __name__ == '__main__':
    print("Starting yfinance wrapper on port 5001...")
    print("Endpoints: /health  /quarterly?symbol=RELIANCE.NS  /balance-sheet?symbol=RELIANCE.NS")
    app.run(host='0.0.0.0', port=5001, debug=False)
