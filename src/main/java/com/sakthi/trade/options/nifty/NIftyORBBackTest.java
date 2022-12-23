package com.sakthi.trade.options.nifty;

import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.zerodhatech.models.HistoricalData;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Calendar.DAY_OF_MONTH;

@Component
public class NIftyORBBackTest {
    @Value("${filepath.trend}")
    String trendPath;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    @Autowired
    TransactionService transactionService;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    Map<String,Double> orbHighLow=new HashMap<>();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    public void ORB(int day) throws IOException {
        Calendar calendar1 = Calendar.getInstance();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/nifty_buy_zerodha_back_test_orb"+sdf1.format(calendar1.getTime())+".csv", true));

        while (day <= 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(DAY_OF_MONTH, day);
            String currentDate = dateFormat.format(calendar.getTime());
            String historicURL = "https://api.kite.trade/instruments/historical/256265/3minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:27:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequestTest(historicURL));
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            Map<String, StrikeData> rangeStrike = new HashMap<>();
            String status = json.getString("status");
            TradeData tradeData=new TradeData();
            tradeData.setQty(1000);
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                //    System.out.println(json);
                if (historicalData.dataArrayList.size() > 0) {
                    HistoricalData orb1 = historicalData.dataArrayList.get(0);
                    HistoricalData orb2 = historicalData.dataArrayList.get(1);
                    HistoricalData orb3 = historicalData.dataArrayList.get(2);

                    orbHighLow(orb1, orb2, orb3);
                    double low = orbHighLow.get("LOW");
                    double high = orbHighLow.get("HIGH");
                    historicalData.dataArrayList.forEach(historicalData1 -> {
                        try {
                            Date openDatetime = sdf.parse(historicalData1.timeStamp);
                            String openDate = format.format(openDatetime);
                            if (openDatetime.after(sdf.parse(openDate + "T" + "09:21:00"))) {
                                //   System.out.println(new Gson().toJson(historicalData1));
                                if (historicalData1.close < low && !tradeData.isOrderPlaced) {
                                    tradeData.setEntryType("SELL");
                                    tradeData.isOrderPlaced = true;
                                    tradeData.setSellTime(sdf.format(openDatetime));
                                    tradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                }
                                if (historicalData1.close > high && !tradeData.isOrderPlaced) {
                                    tradeData.setEntryType("BUY");
                                    tradeData.isOrderPlaced = true;
                                    tradeData.setBuyTime(sdf.format(openDatetime));
                                    tradeData.setBuyPrice(new BigDecimal(historicalData1.close));
                                }
                                if (tradeData.isOrderPlaced) {
                                    if (tradeData.getEntryType().equals("SELL") && !tradeData.isExited && (historicalData1.close > high || historicalData1.close>tradeData.getSellPrice().add(new BigDecimal("30")).doubleValue() || openDatetime.equals(sdf.parse(openDate + "T15:21:00")))) {
                                        tradeData.isExited = true;
                                        if (openDatetime.equals(sdf.parse(openDate + "T15:21:00"))) {
                                            tradeData.setBuyPrice(new BigDecimal(historicalData1.close));
                                        } else {
                                            if(historicalData1.close > high ){
                                                tradeData.setBuyPrice(new BigDecimal(high));
                                            }else {
                                                tradeData.setBuyPrice(tradeData.getSellPrice().add(new BigDecimal("30")));
                                            }
                                            tradeData.isSLHit = true;

                                        }
                                        tradeData.setBuyTime(sdf.format(openDatetime));

                                    }
                                    if (tradeData.getEntryType().equals("BUY") && !tradeData.isExited && (historicalData1.close < low || historicalData1.close<tradeData.getBuyPrice().subtract(new BigDecimal("30")).doubleValue() || openDatetime.equals(sdf.parse(openDate + "T15:21:00")))) {
                                        tradeData.isExited = true;
                                        if (openDatetime.equals(sdf.parse(openDate + "T15:21:00"))) {
                                            tradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                        } else {
                                            if(historicalData1.close < low){
                                                tradeData.setSellPrice(new BigDecimal(low));
                                            }else {
                                                tradeData.setSellPrice(tradeData.getBuyPrice().subtract(new BigDecimal("30")));
                                            }
                                            tradeData.isSLHit = true;
                                            //tradeData.setSellPrice(new BigDecimal(low));
                                        }
                                        tradeData.setSellTime(sdf.format(openDatetime));

                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                }
            }
            day++;
            if (tradeData.isOrderPlaced){
                System.out.println("EntryType:"+tradeData.getEntryType()+":"+tradeData.getBuyTime()+":"+tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP)+":"+tradeData.getSellTime()+":"+tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP)+":"+tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(50)).setScale(0, RoundingMode.HALF_UP));
                String[] data = {tradeData.getEntryType(),tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP).toString(),String.valueOf(tradeData.getQty()), tradeData.getBuyTime(), tradeData.getSellTime()};
                csvWriter.writeNext(data);
                csvWriter.flush();
            }
        }

    }
    public void orbHighLow( HistoricalData orb1, HistoricalData orb2, HistoricalData orb3){
    double low=orb1.low;
    double high=orb1.high;
    if(orb2.low<low){
        low=orb2.low;
    }
    if(orb3.low<low){
            low=orb3.low;
    }
    if(orb2.high>high){
            high=orb2.high;
    }
    if(orb3.high<high){
            high=orb3.high;
    }
    orbHighLow.put("LOW",low);
    orbHighLow.put("HIGH",high);

    }


}
