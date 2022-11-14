package com.sakthi.trade.util;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.models.CandlestickExtended;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.StockDataEntity;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CommonUtil {

    @Value("${home.path}")
    String homeFilePath;

    public static String[] suffixes =
            //    0     1     2     3     4     5     6     7     8     9
            {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    10    11    12    13    14    15    16    17    18    19
                    "th", "th", "th", "th", "th", "th", "th", "th", "th", "th",
                    //    20    21    22    23    24    25    26    27    28    29
                    "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    30    31
                    "th", "st"};

    public static Brokerage calculateBrokerage(TradeData tradeData, boolean isOptions, boolean isEquityIntraday, boolean isFutures, String slipage){
        Brokerage brokerage=new Brokerage();
        BigDecimal brokerC=new BigDecimal("0");
        if(isOptions){
            brokerC=new BigDecimal("40");
            brokerage.setBrokerCharge(brokerC);
        }else if(isEquityIntraday){
            brokerC=tradeData.getSellPrice().multiply(new BigDecimal("0.03")).multiply(new BigDecimal(tradeData.getQty()));
        }
        brokerage.setQty(tradeData.getQty());
        //STT
        BigDecimal stt=new BigDecimal("40");

        if(isOptions){
            stt=tradeData.getSellPrice().multiply(new BigDecimal("0.05")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));

        }else if(isEquityIntraday){
            stt=tradeData.getSellPrice().multiply(new BigDecimal("0.025")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));

        }

        brokerage.setStt(stt);
        //transaction charges
        BigDecimal transactionCharges=new BigDecimal("0");
        if(isOptions){
            transactionCharges=tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.053"));
        }else if(isEquityIntraday){
            transactionCharges= tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.00345"));

        }
        BigDecimal slippagePoints=new BigDecimal(slipage).divide(new BigDecimal("100"));
        BigDecimal buyslippageCost= tradeData.getSellPrice().multiply(slippagePoints).multiply(new BigDecimal(tradeData.getQty()));
        BigDecimal sellSlippageCost= tradeData.getBuyPrice().multiply(slippagePoints).multiply(new BigDecimal(tradeData.getQty()));
        brokerage.setSlipageCost(buyslippageCost.add(sellSlippageCost));
        brokerage.setTransactionCharges(transactionCharges);
        //GST: 18%
        BigDecimal gst=transactionCharges.add(brokerC).multiply(new BigDecimal("0.18"));
        brokerage.setGst(gst);
        BigDecimal stampDuty=new BigDecimal(.003).multiply(tradeData.getBuyPrice());
        brokerage.setGst(stampDuty);
        BigDecimal totalcharges=brokerC.add(stt).add(transactionCharges).add(gst).add(stampDuty);
        brokerage.setTotalCharges(totalcharges);
        return brokerage;
    }
    public HistoricalDataExtended mapCSVtoHistoric(CSVReader csvReader) {
        String[] lineS;
        HistoricalDataExtended historicalDataOp = new HistoricalDataExtended();
        try {
            int i=0;
            while ((lineS = csvReader.readNext()) != null) {
                if(i>0){
                    HistoricalDataExtended historicalData = new HistoricalDataExtended();
                    historicalData.timeStamp = lineS[0];
                    historicalData.open = Double.valueOf(lineS[1]);
                    historicalData.high = Double.valueOf(lineS[2]);
                    historicalData.low = Double.valueOf(lineS[3]);
                    historicalData.close = Double.valueOf(lineS[4]);
                    historicalData.volume = Long.parseLong(lineS[5]);
                    if (lineS.length > 6 && lineS[6]!=null && lineS[6].length() > 0 && lineS[6]!="") {
                        historicalData.oi = new Double(lineS[6]).longValue();
                    }
                    historicalDataOp.dataArrayList.add(historicalData);
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return historicalDataOp;

    }


    public static void calculateVwap(List<HistoricalDataExtended> historicalDataExtendeds) {
        int i = 0;
        double pre_cumulativeTotal = 0;
        double pre_volume = 0;
        while (i < historicalDataExtendeds.size()) {
            HistoricalDataExtended historicalDataExtended = historicalDataExtendeds.get(i);
            if (i == 0) {

                double averagePrice = (historicalDataExtended.high + historicalDataExtended.low + historicalDataExtended.close) / 3;
                historicalDataExtendeds.get(i).vwap = averagePrice;
                pre_cumulativeTotal = averagePrice * historicalDataExtended.volume;
                pre_volume = historicalDataExtended.volume;
            } else {
                double averagePrice = (historicalDataExtended.high + historicalDataExtended.low + historicalDataExtended.close) / 3;

                double current_cumulativeTotal = averagePrice * historicalDataExtended.volume;
                double cumulativeTotal = current_cumulativeTotal + pre_cumulativeTotal;
                double vwap = cumulativeTotal / (historicalDataExtended.volume + pre_volume);
                pre_cumulativeTotal = cumulativeTotal;
                pre_volume = historicalDataExtended.volume + pre_volume;
                historicalDataExtendeds.get(i).vwap = vwap;
            }
            i++;
        }

    }
    public void bankNiftyTradeReport(Map<String,TradeData> tradeDataHashMap,String file) throws IOException {
        CSVWriter csvWriter=new CSVWriter(new FileWriter(homeFilePath+file,true));
        CSVWriter csvWriter1=new CSVWriter(new FileWriter(homeFilePath+"/trade_report/bank_nifty_trade_report_new.csv",true));
        tradeDataHashMap.entrySet().stream().forEach(tradeDataMap->
        {
            try {
                TradeData tradeData=tradeDataMap.getValue();
                System.out.println(new Gson().toJson(tradeData));
                String[] data={tradeDataMap.getKey(),tradeData.getEntryType(),tradeData.getBuyPrice()!=null?tradeData.getBuyPrice().toString():"0",tradeData.getBuyTradedPrice()!=null?tradeData.getBuyTradedPrice().toString():"0",tradeData.getSlPrice()!=null?tradeData.getSlPrice().toString():"0",tradeData.getSlTradedPrice()!=null?tradeData.getSlTradedPrice().toString():"0",tradeData.getSellPrice()!=null?tradeData.getSellPrice().toString():"0",tradeData.getSellTradedPrice()!=null?tradeData.getSellTradedPrice().toString():"0",String.valueOf(tradeData.getQty()),String.valueOf(tradeData.isSLHit),String.valueOf(tradeData.isReverseSLPlaced)};
                csvWriter.writeNext(data);
                csvWriter.flush();
                String[] data1={tradeDataMap.getKey(),tradeData.getEntryType(),tradeData.getSlTradedPrice()!=null?tradeData.getSlTradedPrice().toString():(tradeData.getBuyTradedPrice()!=null?tradeData.getBuyTradedPrice().toString():"0"),tradeData.getSlTradedPrice()!=null?tradeData.getSlTime():tradeData.getBuyTime(),tradeData.getSellTradedPrice()!=null?tradeData.getSellTradedPrice().toString():"0",tradeData.getSellTime(),String.valueOf(tradeData.getQty()),tradeData.getSellTradedPrice()!=null?"true":"false"};
                csvWriter1.writeNext(data1);
                csvWriter1.flush();
                if(tradeData.isReverseTradePlaced){
                    String[] dataL={tradeDataMap.getKey(),"LONG",tradeData.getSlTradedPrice()!=null?tradeData.getSlTradedPrice().toString():"0",tradeData.getSlTime(),tradeData.isReverseSLHit && tradeData.getReverseSlTradedPrice()!=null?tradeData.getReverseSlTradedPrice().toString():(tradeData.getReverseSellTradedPrice()!=null?tradeData.getReverseSellTradedPrice().toString():"0"),tradeData.isReverseSLHit? tradeData.getReverseSlTime() : tradeData.getReverseExitTime(),String.valueOf(tradeData.getQty()),String.valueOf(tradeData.isReverseSLHit)};
                    csvWriter1.writeNext(dataL);
                    csvWriter1.flush();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter1.close();
        csvWriter.close();

    }
    public void tradeReport(Map<String,TradeData> tradeDataHashMap,String file) throws IOException {
        CSVWriter csvWriter=new CSVWriter(new FileWriter(homeFilePath+file,true));
        tradeDataHashMap.entrySet().stream().forEach(tradeDataMap->
        {
            try {
                TradeData tradeData=tradeDataMap.getValue();
                String[] data={tradeDataMap.getKey(),tradeData.getEntryType(),tradeData.getBuyTradedPrice()!=null?tradeData.getBuyTradedPrice().toString():"0",tradeData.getSellTradedPrice()!=null?tradeData.getSellTradedPrice().toString():"0",String.valueOf(tradeData.getQty()),String.valueOf(tradeData.isSLHit)};
                csvWriter.writeNext(data);
                csvWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter.close();

    }
    public int findATM(int currentValue) {
        int a = (Integer.valueOf(currentValue) / 100) * 100;
        int b = a + 100;
        return (Integer.valueOf(currentValue) - a > b - Integer.valueOf(currentValue)) ? b : a;
    }
     static public HistoricalDataExtended to_larger_timeframe(HistoricalDataExtended historicalDataExtended, int min,String tradeDate) {
         HistoricalDataExtended output = new HistoricalDataExtended();
         int current_tick = 1;
         String timestamp = null;
         double current_bar_open = 0;
         double current_bar_high = 0;
         double current_bar_low = 0;
         double current_bar_close = 0;
         for (HistoricalDataExtended historic : historicalDataExtended.dataArrayList) {
             SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
             SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
             try {
                 java.util.Date datee = sdf.parse(historic.timeStamp);
                 if (sdf1.format(datee).equals(tradeDate)) {
                     if (current_tick % min == 1) {
                         timestamp = historic.getTimeStamp();
                         current_bar_open = historic.open;
                         current_bar_high = historic.high;
                         current_bar_low = historic.low;
                         current_bar_close = historic.close;
                     } else {
                         current_bar_high = historic.high > current_bar_high ? historic.high : current_bar_high;
                         current_bar_low = historic.low < current_bar_low ? historic.low : current_bar_low;
                         current_bar_close = historic.close;
                     }

                     if (current_tick % min == 0) {

                         HistoricalDataExtended output1 = new HistoricalDataExtended();
                         output1.setOpen(current_bar_open);
                         output1.setTimeStamp(timestamp);
                         output1.setClose(current_bar_close);
                         output1.setLow(current_bar_low);
                         output1.setHigh(current_bar_high);
                         output.dataArrayList.add(output1);
                     }
                     current_tick++;
                 }

             } catch (ParseException e) {
                 e.printStackTrace();
             }

         }
         return  output;
     }
     public String findHHLHorHLLLEx(List<CandlestickExtended> candlestickExtendedList, int chunkSize, boolean trendSideBullish){
         List<List<CandlestickExtended>> chunkList = Lists.partition(candlestickExtendedList, chunkSize);
         List<List<Double>> hL=HigherHighHigherLowValues(chunkList);
         List<Integer> bullishtrendCount=HHLH(hL);
         List<Integer> bearishTrend=HLLL(hL);
         int hhcount=bullishtrendCount.get(0);
         int hlcount=bullishtrendCount.get(0);
         int bb=((chunkSize-hhcount)/hhcount)*100;
         int ll=((chunkSize-hlcount)/hlcount)*100;
         if (bb >= 7 && ll >=7){
             return "BUY";
         }else {
             return "NO";
         }
     }


    public String findHHLHorHLLL(List<StockDataEntity> candlestickExtendedList, int chunkSize, int listSize, boolean trendSideBullish){
        List<List<StockDataEntity>> chunkList = Lists.partition(candlestickExtendedList, chunkSize);
        List<List<Double>> hL=HigherHighHigherLowValue(chunkList);
        List<Integer> bullishtrendCount=HHLH(hL);
        List<Integer> bearishTrend=HLLL(hL);
        int hhcount=bullishtrendCount.get(0);
        int hlcount=bullishtrendCount.get(0);
        BigDecimal bb=new BigDecimal(Integer.valueOf(hhcount)).divide(new BigDecimal(Integer.valueOf(chunkList.size()))).multiply(new BigDecimal(Integer.valueOf(100)));
     //   double ll=((hlcount)/chunkSize)*100;
        if (bb.doubleValue() >= 60){
            return "BUY";
        }else {
            return "NO";
        }
    }
    public List<Integer> HHLH(List<List<Double>> hLValues){
        List<Integer> count=new ArrayList<>();
        List<Double> highValues=hLValues.get(0);
        List<Double> lowValues=hLValues.get(1);
        AtomicDouble previousHigh=new AtomicDouble(highValues.get(0));
        AtomicDouble previousLow=new AtomicDouble(lowValues.get(0));
        AtomicInteger highcount=new AtomicInteger();
        AtomicInteger lowcount=new AtomicInteger();
        highValues.stream().forEach(highValue->{
            if(previousHigh.get()<highValue){
                highcount.getAndAdd(1);

            }
            previousHigh.getAndSet(highValue);
        });
        lowValues.stream().forEach(lowValue->{
            if(previousLow.get()<lowValue){
                lowcount.getAndAdd(1);

            }
            previousLow.getAndSet(lowValue);
        });
        count.add(highcount.get());
        count.add(lowcount.get());
        return count;
    }

    public List<Integer> HLLL(List<List<Double>> hLValues){
        List<Integer> count=new ArrayList<>();
        List<Double> highValues=hLValues.get(0);
        List<Double> lowValues=hLValues.get(1);
        AtomicDouble previousHigh=new AtomicDouble(highValues.get(0));
        AtomicDouble previousLow=new AtomicDouble(lowValues.get(0));
        AtomicInteger highcount=new AtomicInteger();
        AtomicInteger lowcount=new AtomicInteger();
        highValues.stream().forEach(highValue->{
            if(previousHigh.get()>highValue){
                highcount.getAndAdd(1);
            }
            previousHigh.getAndSet(highValue);
        });
        lowValues.stream().forEach(lowValue->{
            if(previousLow.get()>lowValue){
                lowcount.getAndAdd(1);
            }
            previousLow.getAndSet(lowValue);
        });
        count.add(highcount.get());
        count.add(lowcount.get());
        return count;
    }
     public List<List<Double>> HigherHighHigherLowValues(List<List<CandlestickExtended>> chunkList){
        List<Double> hValues = new ArrayList<>();
         List<Double> lValues = new ArrayList<>();
         chunkList.stream().forEach(chunk->
         {
             AtomicDouble lH=new AtomicDouble();
             AtomicDouble hH=new AtomicDouble();
             CandlestickExtended candlestickExtendedOpen=chunk.get(0);
             lH.getAndSet(candlestickExtendedOpen.getLow().doubleValue());
             hH.getAndSet(candlestickExtendedOpen.getHigh().doubleValue());
             chunk.stream().forEach(candlestickExtended -> {
                 if (candlestickExtended.getHigh().doubleValue()>hH.get()){
                     hH.getAndSet(candlestickExtended.getHigh().doubleValue());
                 }
                 if (candlestickExtended.getLow().doubleValue()<lH.get()){
                     lH.getAndSet(candlestickExtended.getHigh().doubleValue());
                 }
             });
             hValues.add(hH.get());
             lValues.add(lH.get());
         });
         List<List<Double>> hLValues=new ArrayList<>();
         hLValues.add(hValues);
         hLValues.add(lValues);
        return hLValues;
     }

    public List<List<Double>> HigherHighHigherLowValue(List<List<StockDataEntity>> chunkList){
        List<Double> hValues = new ArrayList<>();
        List<Double> lValues = new ArrayList<>();
        chunkList.stream().forEach(chunk->
        {
            AtomicDouble lH=new AtomicDouble();
            AtomicDouble hH=new AtomicDouble();
            StockDataEntity candlestickExtendedOpen=chunk.get(0);
            lH.getAndSet(candlestickExtendedOpen.getLow().doubleValue());
            hH.getAndSet(candlestickExtendedOpen.getHigh().doubleValue());
            chunk.stream().forEach(candlestickExtended -> {
                if (candlestickExtended.getHigh().doubleValue()>hH.get()){
                    hH.getAndSet(candlestickExtended.getHigh().doubleValue());
                }
                if (candlestickExtended.getLow().doubleValue()<lH.get()){
                    lH.getAndSet(candlestickExtended.getHigh().doubleValue());
                }
            });
            hValues.add(hH.get());
            lValues.add(lH.get());
        });
        List<List<Double>> hLValues=new ArrayList<>();
        hLValues.add(hValues);
        hLValues.add(lValues);
        return hLValues;
    }
}
