package com.equity.watchlist.controller;

import com.equity.watchlist.dto.CompanyResponse;
import com.equity.watchlist.repository.CompanyRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for NSE company master data.
 *
 * <p>Exposes a single endpoint used by the frontend autocomplete feature
 * to search and select companies when adding entries to the watchlist.
 */
@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {

    private static final Logger logger = LogManager.getLogger(CompanyController.class);

    private final CompanyRepository companyRepository;

    /**
     * Constructs the controller with the required repository dependency.
     *
     * @param companyRepository the repository for company_master table access
     */
    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Returns all active NSE-listed companies ordered alphabetically by symbol.
     * Used by the frontend autocomplete when adding a company to the watchlist.
     *
     * @return {@code 200 OK} with a list of {@link CompanyResponse} containing symbol and name
     */
    @GetMapping("/list")
    public ResponseEntity<List<CompanyResponse>> getCompanyList() {
        logger.info("GET /api/v1/company/list — fetching active company list");
        List<CompanyResponse> result = companyRepository.findByActiveTrueOrderBySymbolAsc()
                .stream()
                .map(c -> new CompanyResponse(c.getSymbol(), c.getCompanyName()))
                .collect(Collectors.toList());
        logger.debug("Returning {} companies", result.size());
        return ResponseEntity.ok(result);
    }
}
