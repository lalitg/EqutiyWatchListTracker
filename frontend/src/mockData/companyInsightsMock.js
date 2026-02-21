const mockStore = {
  INFY: {
    companyCode: 'INFY',
    companyName: 'Infosys Ltd',
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'Stock up 4.2% this week on strong Q3 guidance.' },
      mediumTerm: { direction: 'NEUTRAL', summary: 'Consolidating in 1450-1520 range for the past month.' },
      longTerm: { direction: 'BULLISH', summary: '12-month target raised to 1800 by multiple analysts.' },
    },
    news: [
      { id: 1, title: 'Infosys wins $2B deal with European telecom major', date: '2025-05-10', source: 'Economic Times' },
      { id: 2, title: 'Infosys Q3 results beat street estimates', date: '2025-05-08', source: 'Moneycontrol' },
      { id: 3, title: 'Infosys expands AI practice with 500 new hires', date: '2025-05-05', source: 'LiveMint' },
    ],
    events: [
      { id: 1, title: 'Q4 FY25 Earnings Call', date: '2025-06-15', type: 'Earnings' },
      { id: 2, title: 'Annual General Meeting', date: '2025-07-20', type: 'AGM' },
    ],
  },
  TCS: {
    companyCode: 'TCS',
    companyName: 'Tata Consultancy Services',
    trend: {
      shortTerm: { direction: 'NEUTRAL', summary: 'Trading flat after ex-dividend adjustment.' },
      mediumTerm: { direction: 'BULLISH', summary: 'Steady uptrend from 3400 to 3700 over 3 months.' },
      longTerm: { direction: 'BULLISH', summary: 'Consistent revenue growth trajectory maintained.' },
    },
    news: [
      { id: 1, title: 'TCS bags $1.5B deal from UK-based financial services firm', date: '2025-05-09', source: 'NDTV Profit' },
      { id: 2, title: 'TCS announces special dividend of Rs 67 per share', date: '2025-05-06', source: 'Business Standard' },
    ],
    events: [
      { id: 1, title: 'Q4 FY25 Results', date: '2025-06-10', type: 'Earnings' },
    ],
  },
};

const defaultInsights = (companyCode) => ({
  companyCode,
  companyName: companyCode,
  trend: {
    shortTerm: { direction: 'NEUTRAL', summary: 'No recent trend data available.' },
    mediumTerm: { direction: 'NEUTRAL', summary: 'Insufficient data for medium-term analysis.' },
    longTerm: { direction: 'NEUTRAL', summary: 'Long-term outlook not yet assessed.' },
  },
  news: [
    { id: 1, title: `Latest updates for ${companyCode}`, date: '2025-05-10', source: 'Market Watch' },
  ],
  events: [
    { id: 1, title: 'Upcoming quarterly results', date: '2025-06-15', type: 'Earnings' },
  ],
});

export function getCompanyInsights(companyCode) {
  return mockStore[companyCode?.toUpperCase()] || defaultInsights(companyCode);
}
