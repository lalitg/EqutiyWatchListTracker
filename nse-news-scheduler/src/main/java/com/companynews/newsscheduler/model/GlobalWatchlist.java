package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

@Entity
@Table(name = "global_watchlist")
public class GlobalWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The company symbol column — e.g. "INFY", "RELIANCE"
    // Column name must exactly match what is in your actual table
    @Column(name = "symbol")
    private String symbol;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
