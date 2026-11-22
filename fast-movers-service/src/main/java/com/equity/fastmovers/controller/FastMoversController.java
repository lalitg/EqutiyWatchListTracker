package com.equity.fastmovers.controller;

import com.equity.fastmovers.dto.FastMoversResponse;
import com.equity.fastmovers.service.FastMoversService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fast-movers")
public class FastMoversController {

    private static final Logger logger = LogManager.getLogger(FastMoversController.class);

    private final FastMoversService fastMoversService;

    public FastMoversController(FastMoversService fastMoversService) {
        this.fastMoversService = fastMoversService;
    }

    /**
     * GET /api/fast-movers?range=TODAY|1D|2D|3D|4D|1W
     */
    @GetMapping
    public ResponseEntity<FastMoversResponse> getMovers(@RequestParam String range) {
        logger.info("GET /api/fast-movers?range={}", range);
        try {
            FastMoversResponse response = fastMoversService.getMovers(range);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid range '{}': {}", range, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
