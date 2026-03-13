
package com.watchlist.marketdata.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="market_daily_snapshot")
public class MarketDailySnapshot {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    private Long companyId;
    private LocalDate snapshotDate;

    private BigDecimal currentPrice;
    private BigDecimal week52Low;
    private BigDecimal week52High;

    private BigDecimal allTimeLow;
    private BigDecimal allTimeHigh;

    private BigDecimal peRatio;
    private BigDecimal eps;

    public void setCompanyId(Long v){companyId=v;}
    public void setSnapshotDate(LocalDate v){snapshotDate=v;}
    public void setCurrentPrice(BigDecimal v){currentPrice=v;}
    public void setWeek52Low(BigDecimal v){week52Low=v;}
    public void setWeek52High(BigDecimal v){week52High=v;}
    public void setAllTimeLow(BigDecimal v){allTimeLow=v;}
    public void setAllTimeHigh(BigDecimal v){allTimeHigh=v;}
    public void setPeRatio(BigDecimal v){peRatio=v;}
    public void setEps(BigDecimal v){eps=v;}
}
