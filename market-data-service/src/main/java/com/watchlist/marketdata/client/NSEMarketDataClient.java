
package com.watchlist.marketdata.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

@Component
public class NSEMarketDataClient {

    private RestTemplate restTemplate=new RestTemplate();

    public MarketData fetch(String symbol){

        MarketData data=new MarketData();

        try{

            HttpHeaders headers=new HttpHeaders();
            headers.set("User-Agent","Mozilla/5.0");
            headers.set("Accept","application/json");
            headers.set("Referer","https://www.nseindia.com");

            HttpEntity<String> entity=new HttpEntity<>(headers);

            String url="https://www.nseindia.com/api/quote-equity?symbol="+symbol;

            ResponseEntity<JsonNode> response=restTemplate.exchange(
                url,HttpMethod.GET,entity,JsonNode.class);

            JsonNode body=response.getBody();

            if(body!=null){

                data.currentPrice=body.get("priceInfo").get("lastPrice").decimalValue();

                data.week52High=body.get("priceInfo")
                    .get("weekHighLow").get("max").decimalValue();

                data.week52Low=body.get("priceInfo")
                    .get("weekHighLow").get("min").decimalValue();

                data.allTimeHigh=data.week52High;
                data.allTimeLow=data.week52Low;
            }

            String tradeUrl="https://www.nseindia.com/api/quote-equity?symbol="+symbol+"&section=trade_info";

            ResponseEntity<JsonNode> tradeResponse=restTemplate.exchange(
                tradeUrl,HttpMethod.GET,entity,JsonNode.class);

            JsonNode tradeBody=tradeResponse.getBody();

            if(tradeBody!=null && tradeBody.has("marketDeptOrderBook")){

                JsonNode tradeInfo=tradeBody.get("marketDeptOrderBook").get("tradeInfo");

                if(tradeInfo!=null && tradeInfo.has("totalTradedVolume"))
                    data.tradedVolume=tradeInfo.get("totalTradedVolume").decimalValue();
            }

        }catch(Exception e){

            System.out.println("Error fetching NSE data for "+symbol+" "+e.getMessage());
        }

        return data;
    }

    public static class MarketData{

        public BigDecimal currentPrice;
        public BigDecimal week52Low;
        public BigDecimal week52High;

        public BigDecimal allTimeLow;
        public BigDecimal allTimeHigh;

        public BigDecimal tradedVolume;
    }
}
