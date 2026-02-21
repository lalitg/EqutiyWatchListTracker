package com.equity.watchlist.controller;

import com.equity.watchlist.dto.CompanyResponse;
import com.equity.watchlist.entity.CompanyMaster;
import com.equity.watchlist.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for NSE company master data.
 *
 * GET /api/v1/company/list — returns all active companies for autocomplete
 */
@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {

    private static final Logger logger = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping("/list")
    public ResponseEntity<List<CompanyResponse>> getCompanyList() {
        logger.info("GET /api/v1/company/list");
        List<CompanyResponse> result = companyRepository.findByActiveTrueOrderBySymbolAsc()
                .stream()
                .map(c -> new CompanyResponse(c.getSymbol(), c.getCompanyName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
