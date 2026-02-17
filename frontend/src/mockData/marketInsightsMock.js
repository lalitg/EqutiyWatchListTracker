const globalRegions = {
  US: {
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'S&P 500 hits new all-time high; tech leads the rally.' },
      mediumTerm: { direction: 'NEUTRAL', summary: 'Mixed signals from Fed on rate trajectory; markets range-bound.' },
      longTerm: { direction: 'BULLISH', summary: 'US GDP growth forecast revised upward to 2.8% for 2025.' },
    },
    news: [
      { id: 1, title: 'US Fed holds rates steady, signals potential cut in Q3', date: '2025-05-10', source: 'Reuters' },
      { id: 2, title: 'Nasdaq crosses 18,000 for the first time on AI optimism', date: '2025-05-09', source: 'CNBC' },
      { id: 3, title: 'US jobs report beats expectations with 275K new positions', date: '2025-05-07', source: 'Wall Street Journal' },
      { id: 4, title: 'US Treasury yields decline as inflation cools to 2.4%', date: '2025-05-06', source: 'Bloomberg' },
    ],
    events: [
      { id: 1, title: 'US CPI Data Release', date: '2025-05-14', type: 'Economic' },
      { id: 2, title: 'FOMC Meeting Minutes', date: '2025-05-21', type: 'Central Bank' },
      { id: 3, title: 'US GDP Q1 Revision', date: '2025-05-29', type: 'Economic' },
    ],
  },
  Europe: {
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'STOXX 600 rallies on stronger-than-expected earnings season.' },
      mediumTerm: { direction: 'NEUTRAL', summary: 'ECB rate path uncertain; geopolitical risks persist.' },
      longTerm: { direction: 'NEUTRAL', summary: 'Sluggish growth outlook; structural reforms needed across eurozone.' },
    },
    news: [
      { id: 1, title: 'European markets rally on stronger-than-expected earnings', date: '2025-05-08', source: 'Financial Times' },
      { id: 2, title: 'ECB signals June rate cut as inflation eases to 2.1%', date: '2025-05-07', source: 'Reuters' },
      { id: 3, title: 'UK economy grows 0.6% in Q1, beating forecasts', date: '2025-05-06', source: 'BBC Business' },
      { id: 4, title: 'German DAX hits record high on export boom', date: '2025-05-05', source: 'Bloomberg' },
    ],
    events: [
      { id: 1, title: 'Bank of England Rate Decision', date: '2025-05-22', type: 'Central Bank' },
      { id: 2, title: 'ECB Monetary Policy Meeting', date: '2025-06-06', type: 'Central Bank' },
      { id: 3, title: 'EU Economic Summit', date: '2025-06-15', type: 'Geopolitical' },
    ],
  },
  Asia: {
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'Asian markets surge on China stimulus and strong Japan data.' },
      mediumTerm: { direction: 'BULLISH', summary: 'Regional growth momentum strong; capital inflows rising.' },
      longTerm: { direction: 'BULLISH', summary: 'Asia-Pacific GDP growth forecast at 4.5%, leading global recovery.' },
    },
    news: [
      { id: 1, title: 'China manufacturing PMI expands for third straight month', date: '2025-05-09', source: 'Bloomberg' },
      { id: 2, title: 'Nikkei 225 crosses 42,000 on BOJ policy clarity', date: '2025-05-08', source: 'Nikkei Asia' },
      { id: 3, title: 'China announces $140B infrastructure stimulus package', date: '2025-05-07', source: 'South China Morning Post' },
      { id: 4, title: 'South Korea exports surge 18% on chip demand rebound', date: '2025-05-05', source: 'Reuters' },
    ],
    events: [
      { id: 1, title: 'Bank of Japan Rate Decision', date: '2025-05-16', type: 'Central Bank' },
      { id: 2, title: 'China Q2 GDP Release', date: '2025-06-10', type: 'Economic' },
      { id: 3, title: 'G7 Finance Ministers Meeting', date: '2025-06-05', type: 'Geopolitical' },
    ],
  },
};

const domesticSectors = {
  All: {
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'Nifty 50 crosses 23,500; broad-based buying seen.' },
      mediumTerm: { direction: 'BULLISH', summary: 'FII inflows turn positive after 3 months of selling.' },
      longTerm: { direction: 'BULLISH', summary: 'India GDP growth at 7.2% supports long-term equity story.' },
    },
    news: [
      { id: 1, title: 'Sensex rallies 600 points on strong FII buying', date: '2025-05-10', source: 'Economic Times' },
      { id: 2, title: 'RBI keeps repo rate unchanged at 6.5%', date: '2025-05-08', source: 'LiveMint' },
      { id: 3, title: 'India manufacturing PMI hits 12-month high', date: '2025-05-06', source: 'Moneycontrol' },
    ],
    events: [
      { id: 1, title: 'RBI MPC Meeting', date: '2025-06-06', type: 'Monetary Policy' },
      { id: 2, title: 'Union Budget Session', date: '2025-07-01', type: 'Policy' },
    ],
  },
  IT: {
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'IT index up 3.8% on strong deal pipeline reports.' },
      mediumTerm: { direction: 'NEUTRAL', summary: 'Sector trading in range; awaiting Q4 results clarity.' },
      longTerm: { direction: 'BULLISH', summary: 'AI and digital transformation driving long-term growth.' },
    },
    news: [
      { id: 1, title: 'IT sector leads market rally with 3% gains', date: '2025-05-10', source: 'NDTV Profit' },
      { id: 2, title: 'Mid-cap IT companies report 20%+ revenue growth', date: '2025-05-07', source: 'Business Standard' },
    ],
    events: [
      { id: 1, title: 'NASSCOM Technology Conference', date: '2025-06-12', type: 'Industry' },
    ],
  },
  Banking: {
    trend: {
      shortTerm: { direction: 'NEUTRAL', summary: 'Bank Nifty flat as NPA concerns weigh on sentiment.' },
      mediumTerm: { direction: 'BULLISH', summary: 'Credit growth at 16% supports earnings visibility.' },
      longTerm: { direction: 'BULLISH', summary: 'Financial inclusion and digital banking drive structural growth.' },
    },
    news: [
      { id: 1, title: 'SBI reports record quarterly profit of Rs 18,000 Cr', date: '2025-05-09', source: 'Economic Times' },
      { id: 2, title: 'RBI tightens norms for unsecured lending', date: '2025-05-06', source: 'LiveMint' },
    ],
    events: [
      { id: 1, title: 'Banking Sector Q4 Results Season', date: '2025-05-20', type: 'Earnings' },
    ],
  },
  Pharma: {
    trend: {
      shortTerm: { direction: 'BEARISH', summary: 'Pharma index down 2% on US FDA import alerts.' },
      mediumTerm: { direction: 'NEUTRAL', summary: 'Mixed bag - domestic growth offsets US pricing pressure.' },
      longTerm: { direction: 'BULLISH', summary: 'Biosimilar pipeline and CDMO opportunity support outlook.' },
    },
    news: [
      { id: 1, title: 'Sun Pharma receives FDA approval for new generic drug', date: '2025-05-10', source: 'Moneycontrol' },
      { id: 2, title: 'Indian pharma exports cross $28B milestone', date: '2025-05-07', source: 'Business Standard' },
    ],
    events: [
      { id: 1, title: 'FDA Advisory Committee Meeting', date: '2025-06-01', type: 'Regulatory' },
    ],
  },
  Auto: {
    trend: {
      shortTerm: { direction: 'BULLISH', summary: 'Auto index at 52-week high on strong May sales data.' },
      mediumTerm: { direction: 'BULLISH', summary: 'EV adoption accelerating; OEMs guide for 15% volume growth.' },
      longTerm: { direction: 'BULLISH', summary: 'India to become 3rd largest auto market by 2027.' },
    },
    news: [
      { id: 1, title: 'Tata Motors EV sales double year-on-year in May', date: '2025-05-10', source: 'Economic Times' },
      { id: 2, title: 'Maruti Suzuki launches new hybrid SUV at Rs 12.5L', date: '2025-05-08', source: 'Autocar India' },
    ],
    events: [
      { id: 1, title: 'Auto Expo 2025', date: '2025-06-20', type: 'Industry' },
    ],
  },
  FMCG: {
    trend: {
      shortTerm: { direction: 'NEUTRAL', summary: 'FMCG index flat; rural recovery slower than expected.' },
      mediumTerm: { direction: 'NEUTRAL', summary: 'Input cost inflation capping margin expansion.' },
      longTerm: { direction: 'BULLISH', summary: 'Rising disposable income and premiumization trend intact.' },
    },
    news: [
      { id: 1, title: 'HUL reports 8% volume growth in Q4', date: '2025-05-09', source: 'LiveMint' },
      { id: 2, title: 'ITC FMCG business turns profitable for first time', date: '2025-05-06', source: 'Moneycontrol' },
    ],
    events: [
      { id: 1, title: 'Consumer Goods Forum India', date: '2025-06-18', type: 'Industry' },
    ],
  },
};

export function getGlobalInsights(region = 'US') {
  return globalRegions[region] || globalRegions.US;
}

export function getDomesticInsights(sector = 'All') {
  return domesticSectors[sector] || domesticSectors.All;
}
