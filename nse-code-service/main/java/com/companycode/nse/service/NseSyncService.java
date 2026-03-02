package com.companycode.nse.service;

import com.companycode.nse.repository.CompanyMasterRepository;
import com.companycode.nse.entity.CompanyMaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NseSyncService {

    private final CompanyMasterRepository repository;

    public NseSyncService(CompanyMasterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void syncCompanies() {
        try {
            String url = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new URL(url).openStream()));

            Map<String, CompanyMaster> existingBySymbol = repository.findAll()
                    .stream()
                    .collect(Collectors.toMap(CompanyMaster::getSymbol, c -> c));

            List<CompanyMaster> batch = new ArrayList<>();
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }

                String[] data = line.split(",");
                String symbol = data[0].trim();

                if (!existingBySymbol.containsKey(symbol)) {
                    CompanyMaster company = new CompanyMaster();
                    company.setSymbol(symbol);
                    company.setCompanyName(data[1].trim());
                    company.setIsin(data[2].trim());
                    company.setLastUpdated(LocalDateTime.now());
                    batch.add(company);
                }
            }

            repository.saveAll(batch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sync NSE companies", e);
        }
    }
}
