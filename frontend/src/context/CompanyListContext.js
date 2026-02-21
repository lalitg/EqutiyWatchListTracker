import React, { createContext, useContext, useState, useEffect } from 'react';
import { fetchCompanyList } from '../services/companyListService';

const CompanyListContext = createContext(null);

export function CompanyListProvider({ children }) {
  const [companies, setCompanies] = useState([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    fetchCompanyList().then((data) => {
      setCompanies(data);
      setLoaded(true);
    });
  }, []);

  return (
    <CompanyListContext.Provider value={{ companies, loaded }}>
      {children}
    </CompanyListContext.Provider>
  );
}

export function useCompanyList() {
  return useContext(CompanyListContext);
}
