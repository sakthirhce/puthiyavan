
package com.sakthi.trade.trend;

import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Calendar.DAY_OF_MONTH;

@Component
@Slf4j
public class ZerodhaTrendSchedulerTest {


    @Value("${filepath.trend}")
    String trendPath;


    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;


    @Autowired
    ZerodhaAccount zerodhaAccount;

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    public void trendScheduler(int days, boolean isOpenPriceSL,String slPer,String gainPer, String marigin,int topNumber, boolean isPyramid, boolean shortTest) throws Exception, KiteException {
        Date date=new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat dateTimeCandleFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMddHHmm");
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE");
        final String filePath=trendPath + "/Trend_Backtest/trend_"+fileFormat.format(date)+".csv";

        while(days<=0) {
            final int dayss=days;

                    Calendar fromcalendar = Calendar.getInstance();
                    fromcalendar.add(DAY_OF_MONTH, dayss);
                    Date fromdate = fromcalendar.getTime();
                    log.info("zerodha trend processor started");
        /*FileReader fileSecBan = new FileReader(secBan + localDate.format(simpleDateFormat1) + ".csv");
        //FileReader fileSecBan = new FileReader(secBan+"14092020.csv");
        BufferedReader readerSecBan = new BufferedReader(fileSecBan);
        String lineSecBan = "";
        String csvSplitBy = ",";
        List<String> secBanList = new ArrayList<>();
        int k = 0;
        while ((lineSecBan = readerSecBan.readLine()) != null) {
            if (k > 0) {
                String[] data = lineSecBan.split(csvSplitBy);
                secBanList.add(data[1]);
            }
            k++;

        }*/
                    if (dayFormat.format(fromdate) != "Sunday" && dayFormat.format(fromdate) != "Saturday") {

                        Map<String, Double> positiveList = new HashMap<>();
                        Map<String, Double> negList = new HashMap<>();
                        Map<String, Double> positiveList1130 = new HashMap<>();
                        Map<String, Double> negList1130 = new HashMap<>();
                        Map<String, Double> positiveList1200 = new HashMap<>();
                        Map<String, Double> negList1200 = new HashMap<>();
                        zerodhaTransactionService.lsSymbols.entrySet().stream().forEach(symbolMap -> {
                            try {
                    HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(dateFormat.format(fromdate) + " 09:15:00"), dateTimeFormat.parse(dateFormat.format(fromdate) + " 15:05:00"), symbolMap.getValue(), "5minute", false, false);

                    // log.info("response trend quote:" + new Gson().toJson(historicalData));
                    AtomicLong cumulatedVolume = new AtomicLong();

                    if (historicalData.dataArrayList.size() > 0) {
                        HistoricalData historicalDataOpen = historicalData.dataArrayList.get(0);
                        historicalData.dataArrayList.stream().forEach(quoteK -> {
                            if (quoteK.close > 100) {
                                Date candleDate = null;
                                try {
                                    candleDate = dateTimeCandleFormat.parse(quoteK.timeStamp);

                                    if (candleDate.before(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:10:00"))) {
                                        cumulatedVolume.getAndAdd(quoteK.volume);
                                    }
                                    if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:10:00")) {
                                        cumulatedVolume.getAndAdd(quoteK.volume);
                                        BigDecimal diff = new BigDecimal(quoteK.close).subtract(new BigDecimal(historicalDataOpen.open));
                                        DecimalFormat df = new DecimalFormat("0.00");

                                        BigDecimal perCh = diff.divide(new BigDecimal(historicalDataOpen.open), 2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
                                        if (perCh.compareTo(new BigDecimal(gainPer)) > 0)
                                            positiveList.put(symbolMap.getKey(), quoteK.close * cumulatedVolume.get());
                                        if (perCh.compareTo(new BigDecimal("0")) < 0)
                                            negList.put(symbolMap.getKey(), -(quoteK.close * cumulatedVolume.get()));
                                    }
                                    if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:25:00")) {
                                        cumulatedVolume.getAndAdd(quoteK.volume);
                                        BigDecimal diff = new BigDecimal(quoteK.close).subtract(new BigDecimal(historicalDataOpen.open));
                                        DecimalFormat df = new DecimalFormat("0.00");

                                        BigDecimal perCh = diff.divide(new BigDecimal(historicalDataOpen.open), 2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
                                        if (perCh.compareTo(new BigDecimal(gainPer)) > 0)
                                            positiveList1130.put(symbolMap.getKey(), quoteK.close * cumulatedVolume.get());
                                        if (perCh.compareTo(new BigDecimal("0")) < 0)
                                            negList1130.put(symbolMap.getKey(), -(quoteK.close * cumulatedVolume.get()));
                                    }
                                    if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:55:00")) {
                                        cumulatedVolume.getAndAdd(quoteK.volume);
                                        BigDecimal diff = new BigDecimal(quoteK.close).subtract(new BigDecimal(historicalDataOpen.open));
                                        DecimalFormat df = new DecimalFormat("0.00");

                                        BigDecimal perCh = diff.divide(new BigDecimal(historicalDataOpen.open), 2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
                                        if (perCh.compareTo(new BigDecimal(gainPer)) > 0)
                                            positiveList1200.put(symbolMap.getKey(), quoteK.close * cumulatedVolume.get());
                                        if (perCh.compareTo(new BigDecimal("0")) < 0)
                                            negList1200.put(symbolMap.getKey(), -(quoteK.close * cumulatedVolume.get()));
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }

                            } catch (Exception | KiteException e) {
                                log.info(symbolMap.getKey() + ":" + e.getMessage());
                            }
                        });

                        List<Map.Entry<String, Double>> sortedPositiveMap = sortByValue(positiveList);
                        List<Map.Entry<String, Double>> sortedNegativeMap = sortByValue(negList);
                        List<Map.Entry<String, Double>> sortedPositiveMap1130 = sortByValue(positiveList1130);
                        List<Map.Entry<String, Double>> sortedNegativeMap1130 = sortByValue(negList1130);
                        List<Map.Entry<String, Double>> sortedPositiveMap1200 = sortByValue(positiveList1200);
                        List<Map.Entry<String, Double>> sortedNegativeMap1200 = sortByValue(negList1200);

                        int startIndex = sortedPositiveMap.size() - topNumber;
                        if (startIndex < 0) {
                            startIndex = 0;
                        }
                        int startIndex1130 = sortedPositiveMap1130.size() - topNumber;
                        if (startIndex1130 < 0) {
                            startIndex1130 = 0;
                        }
                        List<Map.Entry<String, Double>> sortedPositiveMapSub11=null;
                        if (sortedPositiveMap1130.size() > 0) {
                            sortedPositiveMapSub11 = sortedPositiveMap1130.subList(startIndex1130, sortedPositiveMap1130.size());
                        }
                        final List<Map.Entry<String, Double>> sortedPositiveMapSub1130=sortedPositiveMapSub11;
                        int startIndex1200 = sortedPositiveMap1200.size() - topNumber;
                        if (startIndex1200 < 0) {
                            startIndex1200 = 0;
                        }
                        List<Map.Entry<String, Double>> sortedPositiveMapSub12=new ArrayList<>();
                        if (sortedPositiveMap1130.size() > 0) {
                            sortedPositiveMapSub12 = sortedPositiveMap1200.subList(startIndex1200, sortedPositiveMap1200.size());
                        }
                        final List<Map.Entry<String, Double>> sortedPositiveMapSub1200=sortedPositiveMapSub12;
                        if (sortedPositiveMap.size() > 0) {
                            List<Map.Entry<String, Double>> sortedPositiveMapSub = sortedPositiveMap.subList(startIndex, sortedPositiveMap.size());
                            for (Map.Entry<String, Double> map : sortedPositiveMapSub) {
                                log.info("positve top: " + map.getKey() + ":" + map.getValue());
                                HistoricalData historicalDataPos = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(dateFormat.format(fromdate) + " 09:15:00"), dateTimeFormat.parse(dateFormat.format(fromdate) + " 15:05:00"), zerodhaTransactionService.lsSymbols.get(map.getKey()), "5minute", false, false);
                                TradeData tradeData = new TradeData();
                                HistoricalData historicalDataOpen = historicalDataPos.dataArrayList.get(0);
                                Long cumulatedVolume = 0L;
                                historicalDataPos.dataArrayList.stream().forEach(quoteK -> {
                                    Date candleDate = null;
                                    try {
                                        candleDate = dateTimeCandleFormat.parse(quoteK.timeStamp);

                                        if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:10:00")) {
                                            int quantity = (int) (Double.parseDouble(marigin) / quoteK.close);
                                            tradeData.setBuyPrice(new BigDecimal(quoteK.close));
                                            tradeData.setStockName(map.getKey());
                                            if (isPyramid) {
                                                int pyramidQty = quantity / 3;
                                                System.out.println("initial pyramidQty:" + pyramidQty);
                                                tradeData.setQty(pyramidQty);
                                                tradeData.setPyramidCount(1);
                                                tradeData.setPyramidQty(pyramidQty);
                                            } else {
                                                tradeData.setQty(quantity);
                                            }


                                            tradeData.setBuyTime(quoteK.timeStamp);
                                            tradeData.isOrderPlaced = true;
                                            tradeData.isSlPlaced = true;
                                            tradeData.setEntryType("BUY");
                                            if (isOpenPriceSL) {
                                                log.info("open:" + historicalDataOpen.open);
                                                tradeData.setSlPrice(new BigDecimal(historicalDataOpen.open));
                                            } else {
                                                BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), tradeData.getBuyPrice());
                                                BigDecimal slPrice = tradeData.getBuyPrice().subtract(slValue);
                                                tradeData.setSlPrice(slPrice);
                                            }
                                        }
                                        if (candleDate.after(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:10:00")) && tradeData.isOrderPlaced && candleDate.before(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T15:05:00"))) {

                                            BigDecimal perch = ((new BigDecimal(quoteK.close).subtract(tradeData.getBuyPrice())).multiply(new BigDecimal("100"))).divide(tradeData.getBuyPrice(), 2, RoundingMode.HALF_UP);
                                            if (isPyramid) {

                                                if (new BigDecimal(quoteK.close).compareTo(tradeData.getBuyPrice()) > 0 && candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:25:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
                                                    boolean isPyra = false;
                                                    for (Map.Entry<String, Double> map1130 : sortedPositiveMapSub1130) {
                                                        if (map1130.getKey().equals(tradeData.getStockName())) {
                                                            isPyra = true;
                                                        }
                                                    }
                                                    if (isPyra) {
                                                        System.out.println(" before pyramidQty:" + tradeData.getPyramidCount() + ":" + tradeData.getQty());
                                                        BigDecimal price = tradeData.getBuyPrice().multiply(new BigDecimal(tradeData.getQty()));
                                                        tradeData.setPyramidCount(tradeData.getPyramidCount()+1);
                                                        tradeData.setPyramidTime(quoteK.timeStamp);
                                                        int newQty = tradeData.getPyramidQty();
                                                        tradeData.setQty(tradeData.getQty() + newQty);
                                                        BigDecimal prPrice = new BigDecimal(quoteK.close).multiply(new BigDecimal(newQty));
                                                        tradeData.setBuyPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                        if (isOpenPriceSL) {
                                                        } else {
                                                            BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), new BigDecimal(quoteK.close));
                                                            BigDecimal slPrice = tradeData.getBuyPrice().subtract(slValue);
                                                            tradeData.setSlPrice(slPrice);
                                                        }
                                                        System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                    }
                                                }

                                                if (candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:55:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
                                                    boolean isPyra = false;
                                                    for (Map.Entry<String, Double> map1200 : sortedPositiveMapSub1200) {
                                                        if (map1200.getKey().equals(tradeData.getStockName())) {
                                                            isPyra = true;
                                                        }
                                                    }
                                                    if (isPyra) {
                                                        System.out.println(" before pyramidQty:" + tradeData.getPyramidCount() + ":" + tradeData.getQty());
                                                        BigDecimal price = tradeData.getBuyPrice().multiply(new BigDecimal(tradeData.getQty()));

                                                        tradeData.setPyramidTime1(quoteK.timeStamp);
                                                        int newQty = tradeData.getPyramidQty();
                                                        if(tradeData.getPyramidCount()==1){
                                                            newQty = 2*tradeData.getPyramidQty();
                                                        }
                                                        tradeData.setPyramidCount(tradeData.getPyramidCount()+1);
                                                        tradeData.setQty(tradeData.getQty() + newQty);
                                                        BigDecimal prPrice = new BigDecimal(quoteK.close).multiply(new BigDecimal(newQty));
                                                        tradeData.setBuyPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                        if (isOpenPriceSL) {
                                                        } else {
                                                            BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), new BigDecimal(quoteK.close));
                                                            BigDecimal slPrice = tradeData.getBuyPrice().subtract(slValue);
                                                            tradeData.setSlPrice(slPrice);
                                                        }
                                                        System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                    }
                                                }
                                            }
                                            if (new BigDecimal(quoteK.close).compareTo(tradeData.getSlPrice()) < 0 && !tradeData.isSLHit) {
                                                tradeData.setSellTime(quoteK.timeStamp);
                                                tradeData.setSellPrice(new BigDecimal(quoteK.close));
                                                tradeData.isSLHit = true;
                                                log.info("test");
                                                Brokerage brokerage = MathUtils.calculateBrokerage(tradeData, false, true, false, "0.05");
                                                CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath, true));
                                                BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                                BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                                String[] data = {dateFormat.format(fromdate), tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), String.valueOf(tradeData.isSLHit), tradeData.getPyramidTime(), tradeData.getPyramidTime1()};
                                                csvWriter.writeNext(data);
                                                csvWriter.flush();
                                            }

                                        }
                                        if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T15:05:00") && !tradeData.isSLHit && !tradeData.isExited) {
                                            tradeData.setSellTime(quoteK.timeStamp);
                                            tradeData.setSellPrice(new BigDecimal(quoteK.close));
                                            tradeData.isExited = true;
                                            log.info("test exit");
                                            Brokerage brokerage = MathUtils.calculateBrokerage(tradeData, false, true, false, "0.25");
                                            CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath, true));
                                            BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                            BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                            String[] data = {dateFormat.format(fromdate), tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), String.valueOf(tradeData.isSLHit), tradeData.getPyramidTime(), tradeData.getPyramidTime1()};
                                            csvWriter.writeNext(data);
                                            csvWriter.flush();
                                        }

                                    } catch (ParseException | IOException e) {
                                        e.printStackTrace();
                                    }

                                });
                            }
                            if (shortTest) {
                                int endIndex = sortedNegativeMap.size();
                                if (endIndex > topNumber) {
                                    endIndex = topNumber;
                                }
                                int endIndex11 = sortedNegativeMap1130.size();
                                if (endIndex11 > topNumber) {
                                    endIndex11 = topNumber;
                                }
                                int endIndex12 = sortedNegativeMap1200.size();
                                if (endIndex12 > topNumber) {
                                    endIndex12 = topNumber;
                                }
                                List<Map.Entry<String, Double>> sortedNegMapSub11 = new ArrayList<>();
                                if (endIndex11 > 0) {
                                    sortedNegMapSub11 = sortedNegativeMap1130.subList(0, endIndex11);
                                }
                                final List<Map.Entry<String, Double>> sortedNegMapSub1130 = sortedNegMapSub11;
                                List<Map.Entry<String, Double>> sortedNegMapSub12 = new ArrayList<>();
                                if (endIndex12 > 0) {
                                    sortedNegMapSub12 = sortedNegativeMap1200.subList(0, endIndex12);
                                }
                                final List<Map.Entry<String, Double>> sortedNegMapSub1200 = sortedNegMapSub12;

                                if (endIndex > 0) {
                                    List<Map.Entry<String, Double>> sortedNegMapSub = sortedNegativeMap.subList(0, endIndex);
                                    for (Map.Entry<String, Double> map : sortedNegMapSub) {
                                        log.info("negative top: " + map.getKey() + ":" + map.getValue());

                                        HistoricalData historicalDataPos = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(dateFormat.format(fromdate) + " 09:15:00"), dateTimeFormat.parse(dateFormat.format(fromdate) + " 15:05:00"), zerodhaTransactionService.lsSymbols.get(map.getKey()), "5minute", false, false);
                                        TradeData tradeData = new TradeData();
                                        HistoricalData historicalDataOpen = historicalDataPos.dataArrayList.get(0);
                                        historicalDataPos.dataArrayList.stream().forEach(quoteK -> {
                                            Date candleDate = null;
                                            try {
                                                candleDate = dateTimeCandleFormat.parse(quoteK.timeStamp);
                                                if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:10:00")) {
                                                    int quantity = (int) (Double.parseDouble(marigin) / quoteK.close);
                                                    tradeData.setSellPrice(new BigDecimal(quoteK.close));
                                                    tradeData.setStockName(map.getKey());
                                                    if (isPyramid) {
                                                        int pyramidQty = quantity / 3;
                                                        System.out.println("initial pyramidQty:" + pyramidQty);
                                                        tradeData.setQty(pyramidQty);
                                                        tradeData.setPyramidQty(pyramidQty);
                                                        tradeData.setPyramidCount(1);
                                                    } else {
                                                        tradeData.setQty(quantity);
                                                    }
                                                    tradeData.setSellTime(quoteK.timeStamp);
                                                    tradeData.isOrderPlaced = true;
                                                    tradeData.isSlPlaced = true;
                                                    tradeData.setEntryType("SELL");
                                                    if (isOpenPriceSL) {
                                                        log.info("open:" + historicalDataOpen.open);
                                                        tradeData.setSlPrice(new BigDecimal(historicalDataOpen.open));
                                                    } else {
                                                        BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), tradeData.getSellPrice());
                                                        BigDecimal slPrice = tradeData.getSellPrice().add(slValue);
                                                        tradeData.setSlPrice(slPrice);
                                                    }
                                                }
                                                if (candleDate.after(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:10:00")) && tradeData.isOrderPlaced && candleDate.before(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T15:05:00"))) {

                                                    BigDecimal perch = (((tradeData.getSellPrice().subtract(new BigDecimal(quoteK.close))).multiply(new BigDecimal("100")))).divide(new BigDecimal(quoteK.close), 2, RoundingMode.HALF_UP);
                                                    if (isPyramid) {
                                                        if (new BigDecimal(quoteK.close).compareTo(tradeData.getSellPrice()) < 0 && candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:25:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
                                                            System.out.println("inside negative prymid:" + tradeData.getStockName());
                                                            boolean isPyra = false;
                                                            for (Map.Entry<String, Double> map1200 : sortedNegMapSub1130) {
                                                                System.out.println(":" + map1200.getKey());
                                                                if (map1200.getKey().equals(tradeData.getStockName())) {
                                                                    isPyra = true;
                                                                }
                                                            }
                                                            if (isPyra) {
                                                                System.out.println(" before pyramidQty:" + tradeData.getPyramidCount() + ":" + tradeData.getQty());
                                                                tradeData.setPyramidCount(2);
                                                                tradeData.setPyramidTime(quoteK.timeStamp);
                                                                BigDecimal price = tradeData.getSellPrice().multiply(new BigDecimal(tradeData.getQty()));
                                                                int newQty = tradeData.getPyramidQty();
                                                                tradeData.setQty(tradeData.getQty() + newQty);
                                                                BigDecimal prPrice = new BigDecimal(quoteK.close).multiply(new BigDecimal(newQty));
                                                                tradeData.setSellPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                                if (isOpenPriceSL) {
                                                                } else {
                                                                    BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), new BigDecimal(quoteK.close));
                                                                    BigDecimal slPrice = new BigDecimal(quoteK.close).add(slValue);
                                                                    tradeData.setSlPrice(slPrice);
                                                                }
                                                                System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                            }
                                                        }
                                                        if (new BigDecimal(quoteK.close).compareTo(tradeData.getSellPrice()) < 0 && candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:55:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
                                                            System.out.println("inside negative prymid:" + tradeData.getStockName());
                                                            boolean isPyra = false;
                                                            for (Map.Entry<String, Double> map1200 : sortedNegMapSub1200) {
                                                                System.out.println(":" + map1200.getKey());
                                                                if (map1200.getKey().equals(tradeData.getStockName())) {
                                                                    isPyra = true;
                                                                }
                                                            }
                                                            if (isPyra) {
                                                                System.out.println(" before pyramidQty:" + tradeData.getPyramidCount() + ":" + tradeData.getQty());
                                                                tradeData.setPyramidCount(3);
                                                                tradeData.setPyramidTime1(quoteK.timeStamp);
                                                                BigDecimal price = tradeData.getSellPrice().multiply(new BigDecimal(tradeData.getQty()));
                                                                int newQty = tradeData.getPyramidQty();
                                                                tradeData.setQty(tradeData.getQty() + newQty);
                                                                BigDecimal prPrice = new BigDecimal(quoteK.close).multiply(new BigDecimal(newQty));
                                                                tradeData.setSellPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                                if (isOpenPriceSL) {
                                                                } else {
                                                                    BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), new BigDecimal(quoteK.close));
                                                                    BigDecimal slPrice = new BigDecimal(quoteK.close).add(slValue);
                                                                    tradeData.setSlPrice(slPrice);
                                                                }
                                                                System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                            }
                                                        }
                                                    }
                                                    if (new BigDecimal(quoteK.close).compareTo(tradeData.getSlPrice()) > 0 && !tradeData.isSLHit) {
                                                        tradeData.setBuyTime(quoteK.timeStamp);
                                                        tradeData.setBuyPrice(new BigDecimal(quoteK.close));
                                                        tradeData.isSLHit = true;
                                                        Brokerage brokerage = MathUtils.calculateBrokerage(tradeData, false, true, false, "0.25");
                                                        CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath, true));
                                                        BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                                        BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                                        String[] data = {dateFormat.format(fromdate), tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), String.valueOf(tradeData.isSLHit), tradeData.getPyramidTime(), tradeData.getPyramidTime1()};
                                                        csvWriter.writeNext(data);
                                                        csvWriter.flush();
                                                    }

                                                }
                                                if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T15:05:00") && !tradeData.isSLHit && !tradeData.isExited) {
                                                    tradeData.setBuyTime(quoteK.timeStamp);
                                                    tradeData.setBuyPrice(new BigDecimal(quoteK.close));
                                                    tradeData.isExited = true;
                                                    Brokerage brokerage = MathUtils.calculateBrokerage(tradeData, false, true, false, "0.25");
                                                    CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath, true));
                                                    BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                                    BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                                    String[] data = {dateFormat.format(fromdate), tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), String.valueOf(tradeData.isSLHit), tradeData.getPyramidTime(), tradeData.getPyramidTime1()};
                                                    csvWriter.writeNext(data);
                                                    csvWriter.flush();
                                                }

                                            } catch (ParseException | IOException e) {
                                                e.printStackTrace();
                                            }

                                        });
                                    }
                                }
                            }
                        }
                    }
             /*   }});*/
days++;
        }
    }

    public static List<Map.Entry<String, Double>> sortByValue(Map<String, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        // put data from sorted list to hashmap
        HashMap<String, Double> temp = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return list;
    }
}
/*
        historicWebsocket.preOpenDetailsMap.entrySet().stream().forEach(preOpenData->{
                    PreOpenDetails preOpenDetails=preOpenData.getValue();
                    Double stockPrice=preOpenDetails.getOpenPrice();
                    if(!secBanList.contains(preOpenDetails.getStockName()) && stockPrice>100 && stockPrice<10000) {
                        preopenData.put(preOpenDetails.getStockName(), preOpenDetails.getOpenPrice());
                }
            });*//*

        orbScheduler.preOpenData=preopenData;
        long endTime = System.nanoTime();
        long processDuration = (endTime - startTime) / 1000000;
        log.info("Successfully retrived pre open fo data from nse with time: " + processDuration);
        return preopenData;
    }
    //@Scheduled(cron = "${trend.scheduler}")
    public void trendScheduler() throws Exception, KiteException {
        log.info("trend scheduler started process");
        Map<String,Double> stockList=getPreOpenStockList();
        Session session=historicWebsocket.session;
        if (session==null){
            session = historicWebsocket.createHistoricWebSocket();
        }
        for (Map.Entry<String, Double> entry : stockList.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());

            try {
                String payload = orbIntraday15minHistoricInput("5min", entry.getKey());
               // log.info("5 min payload: "+ payload);
                session.getBasicRemote().sendText(payload);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
   // @Scheduled(cron = "${trend.scheduler.live}")
    public void trendLive() throws Exception, KiteException {
        Map<String,EventDayData> eventDayMap=eventDayConfiguration.eventDayConfig();
      //  trendScheduler();
       // Thread.sleep(120000);
        Date currentDate= new Date();
        String strCurrentDate=new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if( !trendEnabled || (eventDayMap.containsKey(strCurrentDate)&& eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE"))){
            log.info("trend diabled | no trade day. not starting trend");
        }else {
            log.info("trend processor started");
            DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
            LocalDate localDate = LocalDate.now();
            Set<String> symbolSet=zerodhaTransactionService.lsSymbols.keySet();

            String[] arrayOfSym = new String[symbolSet.size()];
            int index = 0;
            for (String str : symbolSet)
                arrayOfSym[index++] = "NSE:"+str;

            Map<String, Quote>  quoteMap=zerodhaAccount.kiteSdk.getQuote(arrayOfSym);
            quoteMap.entrySet().parallelStream().forEach(quote->{
                System.out.println(quote.getValue());

            });
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/trend_" + dtf1.format(localDate) + ".csv"));

            Map<String, Double> stockList = new HashMap<>();
            Map<String, Double> stockPriceList = new HashMap<>();
            String[] line;
            int i = 0;
            while ((line = csvReader.readNext()) != null) {
                if (i > 0) {
                    stockList.put(line[0], Double.valueOf(line[1]));
                    stockPriceList.put(line[0], Double.valueOf(line[3]));
                }
                i++;
            }

            List<Map.Entry<String, Double>> list =
                    new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());

            // Sort the list
            Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
                public int compare(Map.Entry<String, Double> o1,
                                   Map.Entry<String, Double> o2) {
                    return (o1.getValue()).compareTo(o2.getValue());
                }
            });
            String[] marginInfo = margin.split(":");
            int startIndex = list.size() - 5;
            if (startIndex < 0) {
                startIndex = 0;
            }
            for (Map.Entry<String, Double> map : list.subList(startIndex, list.size())) {
                try {
                    double allocatedAmount = (Double.valueOf(marginInfo[1]) / Double.valueOf(marginInfo[0])) * Double.valueOf(marginInfo[2]);
                    int quantity = (int) (allocatedAmount / stockPriceList.get(map.getKey()).doubleValue());
                    System.out.println(map.getKey() + ":" + quantity);
                    PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(map.getKey(), Order.BUY, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, quantity, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                    Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                    String response = transactionService.callAPI(request);
                    System.out.println("buy response: " + response);
                    OrderResponseDTO orderResponseDTO = new Gson().fromJson(response, OrderResponseDTO.class);
                    TradeData trendTradeData = new TradeData();
                    trendTradeData.setStockName(map.getKey());
                    trendTradeData.setSlPrice(new BigDecimal(stockPriceList.get(map.getKey())));
                    Date date = new Date();
                    trendTradeData.setCreateTimestamp(date);
                    if (orderResponseDTO.getS().equals("ok")) {
                        trendTradeData.setEntryOrderId(orderResponseDTO.getId());
                        trendTradeData.setQty(quantity);
                        trendTradeData.isOrderPlaced = true;

                        trendTradeData.setEntryType("BUY");
                        sendMessage.sendToTelegram("Bought stock: " + map.getKey() + ": Quantity :" + quantity, telegramToken);
                    } else {
                        trendTradeData.isErrored = true;
                        sendMessage.sendToTelegram("Error occurred while placing trending stocks: " + map.getKey() + ": Status :" + orderResponseDTO.getS() + ": Error Message :" + orderResponseDTO.getMessage(), telegramToken);
                    }
                    trendTradeMap.put(map.getKey(), trendTradeData);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendMessage.sendToTelegram("error while processing trend stocks", telegramToken);
                }
            }
        }
    }

    public void trendLiveBackTest() throws Exception {
        Map<String,EventDayData> eventDayMap=eventDayConfiguration.eventDayConfig();
        //  trendScheduler();
        // Thread.sleep(120000);
        Date currentDate= new Date();
        String strCurrentDate=new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if(eventDayMap.containsKey(strCurrentDate)&& eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")){
            log.info("no trade day. not starting trend");
        }else {
            log.info("trend processor started");
            DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
            LocalDate localDate = LocalDate.now();
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/trend_" + dtf1.format(localDate) + ".csv"));
            CSVReader csvReaderNeg = new CSVReader(new FileReader(trendPath + "/trend_negative_" + dtf1.format(localDate) + ".csv"));
            Map<String, Double> stockList = new HashMap<>();
            Map<String, Double> stockPriceList = new HashMap<>();
            Map<String, Double> stockClosePriceList = new HashMap<>();
            Map<String, Double> stockNegList = new HashMap<>();
            Map<String, Double> stockPriceNegList = new HashMap<>();
            String[] line;
            int i = 0;
            while ((line = csvReader.readNext()) != null) {
                if (i > 0) {
                    stockList.put(line[0], Double.valueOf(line[1]));
                    stockPriceList.put(line[0], Double.valueOf(line[3]));
                    stockClosePriceList.put(line[0], Double.valueOf(line[2]));
                }
                i++;
            }
            int j = 0;
            while ((line = csvReaderNeg.readNext()) != null) {
                if (j > 0) {
                    stockNegList.put(line[0], Double.valueOf(line[1]));
                    stockPriceNegList.put(line[0], Double.valueOf(line[3]));
                    stockClosePriceList.put(line[0], Double.valueOf(line[2]));
                }
                j++;
            }

            List<Map.Entry<String, Double>> list =
                    new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());
            List<Map.Entry<String, Double>> Neglist =
                    new LinkedList<Map.Entry<String, Double>>(stockNegList.entrySet());
            // Sort the list
            Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
                public int compare(Map.Entry<String, Double> o1,
                                   Map.Entry<String, Double> o2) {
                    return (o1.getValue()).compareTo(o2.getValue());
                }
            });
            Collections.sort(Neglist, new Comparator<Map.Entry<String, Double>>() {
                public int compare(Map.Entry<String, Double> o1,
                                   Map.Entry<String, Double> o2) {
                    return (o1.getValue()).compareTo(o2.getValue());
                }
            });


            String[] marginInfo = margin.split(":");
            int startIndex = list.size() - 5;
            if (startIndex < 0) {
                startIndex = 0;
            }
            System.out.println("total size:"+list.size()+"sub list"+list.subList(startIndex,list.size()));
            System.out.println("total pos :"+list);
            int toNegIndex=5-list.subList(startIndex,list.size()).size();
            if(toNegIndex>0) {
                System.out.println("total neg size:" + Neglist.size() + "sub list" + Neglist.subList(0, toNegIndex));
                System.out.println("total neg :" + Neglist);
            }

            for (Map.Entry<String, Double> map : list.subList(startIndex, list.size())) {
                try {
                    double allocatedAmount = (Double.valueOf(marginInfo[1]) / Double.valueOf(marginInfo[0])) * Double.valueOf(marginInfo[2]);
                    int quantity = (int) (allocatedAmount / stockPriceList.get(map.getKey()).doubleValue());
                    System.out.println(map.getKey() + ":" + quantity);
                    TradeData trendTradeData = new TradeData();
                    trendTradeData.setStockName(map.getKey());
                    trendTradeData.setSlPrice(new BigDecimal(stockPriceList.get(map.getKey())));
                    Date date = new Date();
                    trendTradeData.setCreateTimestamp(date);
                    trendTradeData.isOrderPlaced = true;
                    trendTradeData.setBuyPrice(new BigDecimal(stockClosePriceList.get(map.getKey())));
                    trendTradeData.setEntryType("BUY");

                } catch (Exception e) {
                    e.printStackTrace();
                    sendMessage.sendToTelegram("error while processing trend stocks", telegramToken);
                }
            }
        }
    }
   // @Scheduled(cron = "${trend.sl.scheduler}")
    public void sLMonitorScheduler() {
      //  log.info("Trend SLMonitor scheduler started");
        if (trendTradeMap != null) {
            trendTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && !map.getValue().isSlPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {
                        TradeData trendTradeData = map.getValue();
                        Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getEntryOrderId());
                        String response = transactionService.callAPI(request);
                     //   System.out.println(response);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                            Order order;
                            OrderDetailsDTO orderDetailsDTO=orderStatusResponseDTO.getOrderDetails();
                           // BigDecimal price = (orderDetailsDTO.getTradedPrice().divide(new BigDecimal(2))).setScale(0, RoundingMode.HALF_UP);
                            if (trendTradeData.getEntryType() == "BUY") {

                                order = Order.SELL;
                            } else {
                                order = Order.BUY;
                            }
                            trendTradeData.setBuyTradedPrice(orderDetailsDTO.getTradedPrice());
                            trendTradeData.setFyersSymbol(orderStatusResponseDTO.getOrderDetails().getSymbol());
                            PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(trendTradeData.getStockName(), order, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, orderStatusResponseDTO.getOrderDetails().getQty(), new BigDecimal(0), trendTradeData.getSlPrice(), new BigDecimal(0));
                            String payload = new Gson().toJson(placeOrderRequestDTO);
                            Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                            String slResponse = transactionService.callAPI(slRequest);
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                            if (orderResponseDTO.getS().equals("ok")) {
                                trendTradeData.setSlOrderId(orderResponseDTO.getId());
                                trendTradeData.isSlPlaced = true;
                            }
                            System.out.println(slResponse);
                        }
                    });
            if(trendTradeMap!=null) {
                trendTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().isSlPlaced && !map.getValue().isSLCancelled && !map.getValue().isExited && map.getValue().getEntryOrderId() != null && map.getValue().getSlOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getSlOrderId());
                            String response = transactionService.callAPI(request);
                         //   System.out.println(response);
                            OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                            if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() != 6) {
                                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                                    map.getValue().isExited = true;
                                    map.getValue().setSlTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                    map.getValue().isSLHit=true;
                                    String message = MessageFormat.format("Stop loss Hit stock {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }
                                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 1) {
                                    map.getValue().isSLCancelled = true;
                                    String message = MessageFormat.format("Broker Cancelled SL Order of {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }

                            }
                        });
            }
        }
    }

  //  @Scheduled(cron = "${trend.exit.position.scheduler}")
    public void exitPositions() {
        log.info("Trend Exit positions scheduler started");
        try {
            Request request = transactionService.createGetRequest(positionsURL, null);
            String response = transactionService.callAPI(request);
            trendTradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited).forEach(trendMap -> {
                try {
                    if (trendMap.getValue().getSlOrderId() != null && trendMap.getValue().getSlOrderId() != "") {
                        Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, trendMap.getValue().getSlOrderId());
                        String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() != 2 && orderStatusResponseDTO.getOrderDetails().getStatus() == 6) {
                            CancelRequestDTO cancelRequestDTO = fyerTransactionMapper.prepareCancelRequest(Long.parseLong(trendMap.getValue().getSlOrderId()));
                            Request cancelRequest = transactionService.createPostPutDeleteRequest(HttpMethod.DELETE, orderPlaceAPIUrl, new Gson().toJson(cancelRequestDTO));
                            String cancelResponse = transactionService.callAPI(cancelRequest);
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(cancelResponse, OrderResponseDTO.class);
                            if (orderResponseDTO.getS().equals("ok")) {
                                trendMap.getValue().isSLCancelled = true;
                                String message = MessageFormat.format("System Cancelled SL {0}", trendMap.getValue().getStockName());
                                log.info(message);
                                sendMessage.sendToTelegram(message,telegramToken);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
            openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
                try {
                    if(netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType()=="INTRADAY" ) {
                        Integer openQty = netPositionDTO.getBuyQty() - netPositionDTO.getSellQty();
                        if(openQty>0 || openQty<0) {
                        Optional<Map.Entry<String, TradeData>> trendMap = trendTradeMap.entrySet().stream().filter(tradeData -> tradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
                        if (trendMap.isPresent()) {
                            String id = netPositionDTO.getId();
                            ExitPositionRequestDTO exitPositionRequestDTO = new ExitPositionRequestDTO(id);
                            Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, exitPositionsURL, new Gson().toJson(exitPositionRequestDTO));
                            String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                            String message = MessageFormat.format("Exited Positions response: {0}", exitPositionsResponse);
                            System.out.println(message);
                            try {
                                OrderResponseDTO orderResponseDTO = new Gson().fromJson(exitPositionsResponse, OrderResponseDTO.class);
                                if (orderResponseDTO.getS().equals("ok")) {
                                    log.info(message);
                                    trendMap.get().getValue().setExitOrderId(orderResponseDTO.getId());
                                    String teleMessage = netPositionDTO.getSymbol() + ":Closed Postion:" + openQty;
                                    sendMessage.sendToTelegram(teleMessage, telegramToken);
                                } else {
                                    String teleMessage = netPositionDTO.getSymbol() + ":exit position call failed with status :" + orderResponseDTO.getS() + " Error :" + orderResponseDTO.getMessage();
                                    sendMessage.sendToTelegram(teleMessage, telegramToken);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String teleMessage = netPositionDTO.getSymbol() + ":Error while trying to close Postion";
                    sendMessage.sendToTelegram(teleMessage,telegramToken);
                }
            });
        } catch (Exception e){
            e.printStackTrace();
            String teleMessage = "Entire Exit position process failed. review and close positions manually";
            sendMessage.sendToTelegram(teleMessage,telegramToken);
        }
    }
    public String orbIntraday15minHistoricInput(String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.now());
        String fromDate = LocalDate.now() + "T09:15:00";
        if (LocalDateTime.parse(currentDate).isBefore(LocalDateTime.parse(LocalDate.now() + "T09:15:00"))) {
            fromDate = LocalDate.now().minus(Period.ofDays(1)) + "T09:15:00";
        }
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(currentDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }
    @PreDestroy
    public void saveDatatoFile() throws IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/trend_trade_data_"+dtf1.format(localDate)+".csv",false));
        trendTradeMap.entrySet().stream().forEach(trendTrade->
        {

            try {
                String[] data={trendTrade.getKey(),new Gson().toJson(trendTrade)};
                csvWriter.writeNext(data);
                csvWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter.close();

    }

    @Scheduled(cron = "${orb.exit.position.details.report}")
    public void trendDetailsReport() throws IOException {
           commonUtil.tradeReport(trendTradeMap,"/trade_report/trend_trade_report.csv");

    }
    @Scheduled(cron = "${orb.position.close.price}")
    public void positionClosePrice() {
        log.info("positions close price scheduler started");
        trendTradeMap.entrySet().stream().forEach(orbTradeData -> {
            log.info("positions close price tradedata:"+new Gson().toJson(orbTradeData));
            if (orbTradeData.getValue().getExitOrderId() != null && orbTradeData.getValue().getExitOrderId() != "") {
                Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeData.getValue().getExitOrderId());
                String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                log.info("positions close price response:"+slOrderStatusResponse);
                OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                    if (orbTradeData.getValue().getEntryType() == "BUY") {
                        orbTradeData.getValue().setSellTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                        orbTradeData.getValue().setSellTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                    } else {
                        orbTradeData.getValue().setBuyTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                        orbTradeData.getValue().setBuyTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                    }

                }

            }
        });
    }

    public void newTrend() throws IOException, CsvValidationException {

        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;
        int i = 0;
        Calendar fromcalendar = Calendar.getInstance();
        fromcalendar.add(DAY_OF_MONTH, -5);
        Date fromdate = fromcalendar.getTime();
        Calendar tocalendar = Calendar.getInstance();
        tocalendar.add(DAY_OF_MONTH, -1);
        // tocalendar.add(DAY_OF_MONTH, -2);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date todate = tocalendar.getTime();
        String uri = "";
        while ((line = csvReader.readNext()) != null) {
            if (i == 3) {

                uri = uri + "i=NSE:" + line[1].trim();
            } else if (i > 3) {
                uri = uri + "&i=NSE:" + line[1].trim();
            }
            i++;
        }

        Map<String, Double> stockList = new HashMap<>();
        Map<String, Double> stockNegList = new HashMap<>();
        zerodhaTransactionService.lsSymbols.entrySet().stream().forEach(fostock -> {
            System.out.println(fostock.getKey() + ":");
            String historicURL = "https://api.kite.trade/instruments/historical/" + fostock.getValue() + "/5minute?from=2020-12-23+09:00:00&to=2020-12-23+11:15:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            System.out.print(response);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            String status = json.getString("status");

            if(!status.equals("error")) {
                historicalData.parseResponse(json);

                Map<String, Double> stockPrice = new HashMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                HistoricalData historicalData1 = historicalData.dataArrayList.get(0);
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = sdf1.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T09:15:00")) {
                        stockPrice.put("OPEN", historicalData1.open);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                historicalData.dataArrayList.stream().forEach(ohlc -> {
                    //  System.out.println(ohlc.getKey()+":"+ohlc.getValue().instrumentToken+":"+ohlc.getValue().lastPrice+":"+ohlc.getValue().ohlc.open);
                    Date dateTime = null;
                    try {
                     //   System.out.println(ohlc.timeStamp);
                        dateTime = sdf.parse(ohlc.timeStamp);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    String openDate = sdf1.format(dateTime);
                    if (sdf.format(dateTime).equals(openDate + "T11:10:00")) {
                        BigDecimal diff = new BigDecimal(ohlc.close).subtract(new BigDecimal(stockPrice.get("OPEN")));
                        DecimalFormat df = new DecimalFormat("0.00");
                        double perCh = (diff.doubleValue() / stockPrice.get("OPEN")) * 100;
                        if (perCh > 3) {
                            stockList.put(fostock.getKey(), Double.parseDouble(df.format(perCh)));
                        }
                        if (perCh < -3) {
                            stockNegList.put(fostock.getKey(), Double.parseDouble(df.format(perCh)));
                        }
                    }


                });
            }
        */
/*String  ohlcQuotesURI="https://api.kite.trade/quote/ohlc?"+uri;
        System.out.println(ohlcQuotesURI);
        String response1=transactionService.callAPI(transactionService.createZerodhaGetRequest(ohlcQuotesURI));
        System.out.println(response1);
        final OHLCQuoteResponse fromJson = new Gson().fromJson(response1,
                new TypeToken<OHLCQuoteResponse>(){}.getType());
        System.out.println(fromJson.getStatus());
        Map<String, Double> stockList = new HashMap<>();
        Map<String, Double> stockNegList = new HashMap<>();
        System.out.println("size:"+fromJson.getOhlcQuoteMap().size());
        fromJson.getOhlcQuoteMap().entrySet().parallelStream().forEach(ohlc->{
          //  System.out.println(ohlc.getKey()+":"+ohlc.getValue().instrumentToken+":"+ohlc.getValue().lastPrice+":"+ohlc.getValue().ohlc.open);
            BigDecimal diff = new BigDecimal(ohlc.getValue().lastPrice).subtract(new BigDecimal(ohlc.getValue().ohlc.open));
            DecimalFormat df = new DecimalFormat("0.00");
            double perCh = (diff.doubleValue() / ohlc.getValue().ohlc.open) * 100;
           // System.out.println(ohlc.getKey()+":"+perCh);
            if(perCh>3){
            stockList.put(ohlc.getKey(),perCh);}
            if(perCh<-3){
                stockNegList.put(ohlc.getKey(),perCh);}
        });
        System.out.println("stockList size:"+stockList.size());
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());
        List<Map.Entry<String, Double>> negList =
                new LinkedList<Map.Entry<String, Double>>(stockNegList.entrySet());
        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        Collections.sort(negList, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        System.out.println("list size:"+list.size());
        int fromPStart=0;
        if(list.size()>5){
            fromPStart=list.size()-5;
        }
        for (Map.Entry<String, Double> map : list.subList(fromPStart, list.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
        System.out.println("negList size:"+negList.size());
        int fromStart=0;
        if(negList.size()>5){
            fromStart=negList.size()-5;
        }
        for (Map.Entry<String, Double> map : negList.subList(fromStart, negList.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
        stopWatch.stop();
        System.out.println("total time:"+stopWatch.getTotalTimeMillis());*//*

        });
        System.out.println("stockList size:"+stockList.size());
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());
        List<Map.Entry<String, Double>> negList =
                new LinkedList<Map.Entry<String, Double>>(stockNegList.entrySet());
        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        Collections.sort(negList, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        System.out.println("list size:"+list.size());
        int fromPStart=0;
        if(list.size()>5){
            fromPStart=list.size()-5;
        }
        for (Map.Entry<String, Double> map : list.subList(fromPStart, list.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
        System.out.println("negList size:"+negList.size());
        int fromStart=0;
        if(negList.size()>5){
            fromStart=negList.size()-5;
        }
        for (Map.Entry<String, Double> map : negList.subList(fromStart, negList.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
    }

}*/