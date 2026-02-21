import apiClient from './apiClient';

const MOCK_COMPANIES = [
  { symbol: 'RELIANCE', companyName: 'Reliance Industries Limited' },
  { symbol: 'TCS', companyName: 'Tata Consultancy Services Limited' },
  { symbol: 'INFY', companyName: 'Infosys Limited' },
  { symbol: 'HDFCBANK', companyName: 'HDFC Bank Limited' },
  { symbol: 'ICICIBANK', companyName: 'ICICI Bank Limited' },
];

export async function fetchCompanyList() {
  try {
    return await apiClient('/company/list');
  } catch {
    return MOCK_COMPANIES;
  }
}
