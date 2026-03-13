
package com.watchlist.marketdata.scheduler;

import com.watchlist.marketdata.repository.CompanyRepository;
import com.watchlist.marketdata.repository.MarketSnapshotRepository;
import com.watchlist.marketdata.entity.Company;
import com.watchlist.marketdata.entity.MarketDailySnapshot;
import com.watchlist.marketdata.client.NSEMarketDataClient;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDate;
import java.util.List;

@Component
public class MarketDataScheduler{

    private CompanyRepository companyRepository;
    private MarketSnapshotRepository snapshotRepository;
    private NSEMarketDataClient client;

    public MarketDataScheduler(
        CompanyRepository c,
        MarketSnapshotRepository s,
        NSEMarketDataClient cl){

        companyRepository=c;
        snapshotRepository=s;
        client=cl;
    }

    @Scheduled(cron="0 0 19 * * ?")
    //@Scheduled(fixedDelay = 60000)
    public void run(){

        List<Company> companies=companyRepository.findByActiveTrue();

        for(Company company:companies){

            if(snapshotRepository.existsByCompanyIdAndSnapshotDate(
                company.getId(),LocalDate.now()))
                continue;

            NSEMarketDataClient.MarketData data=client.fetch(company.getSymbol());

            MarketDailySnapshot snapshot=new MarketDailySnapshot();

            snapshot.setCompanyId(company.getId());
            snapshot.setSnapshotDate(LocalDate.now());

            snapshot.setCurrentPrice(data.currentPrice);
            snapshot.setWeek52Low(data.week52Low);
            snapshot.setWeek52High(data.week52High);

            snapshot.setAllTimeLow(data.allTimeLow);
            snapshot.setAllTimeHigh(data.allTimeHigh);

            snapshot.setTradedVolume(data.tradedVolume);

            snapshotRepository.save(snapshot);
        }
    }
}
