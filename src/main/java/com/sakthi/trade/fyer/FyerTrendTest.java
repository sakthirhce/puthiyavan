package com.sakthi.trade.fyer;

import com.google.common.util.concurrent.AtomicDouble;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.StockDataEntity;
import com.sakthi.trade.entity.StockEntity;
import com.sakthi.trade.fyer.model.Candlestick;
import com.sakthi.trade.repo.StockDataRepository;
import com.sakthi.trade.repo.StockRepository;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Calendar.DAY_OF_MONTH;

@Service
@Slf4j
public class FyerTrendTest {
    @Value("${filepath.trend}")
    String trendPath;

    @Autowired
    Account fyerAccount;

    @Autowired
    StockDataRepository stockDataRepository;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    CommonUtil commonUtil;

    public void trendScheduler(int days, boolean isOpenPriceSL,String slPer,String gainPer, String marigin,int topNumber, boolean isPyramid, boolean shortTest) throws Exception, KiteException {
        Date date=new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat dateTimeCandleFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMddHHmm");
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE");
        final String filePath=trendPath + "/Trend_Backtest/trend_"+fileFormat.format(date)+".csv";
        System.out.println("fyer trend processor started");
        while(days<=0) {
            final int dayss=days;

            Calendar fromcalendar = Calendar.getInstance();
            fromcalendar.add(DAY_OF_MONTH, dayss);
            Date fromdate = fromcalendar.getTime();
            System.out.println(dateTimeFormat.format(fromdate));/*

            String fromDate=String.valueOf(dateTimeFormat.parse(dateFormat.format(fromdate) + " 09:10:00").getTime()/1000);
            String toDate = String.valueOf(dateTimeFormat.parse(dateFormat.format(fromdate) + " 15:05:00").getTime()/1000);*/

            String fromDate=dateFormat.format(fromdate) + " 09:10:00";
            String toDate = dateFormat.format(fromdate) + " 15:05:00";

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
                List<StockEntity> stockEntityList=stockRepository.findAll();
                stockEntityList.forEach(stockEntity -> {
                    try {

                        List<StockDataEntity> stockDataEntities=stockDataRepository.findSymbol(stockEntity.getSymbol(),fromDate,toDate);
                      //  List<Candlestick> candlestickList = fyerAccount.getHistory(stockEntity.getSymbol(),"5",fromDate, toDate);

                        // log.info("response trend quote:" + new Gson().toJson(historicalData));
                        AtomicLong cumulatedVolume = new AtomicLong();

                        if (null !=stockDataEntities && stockDataEntities.size() > 0) {
                            StockDataEntity historicalDataOpen = stockDataEntities.get(0);
                            Date openCandle =  historicalDataOpen.getTradeTime();
                            stockDataEntities.stream().forEach(quoteK -> {
                                if (quoteK.getClose().doubleValue() > 100) {
                                    Date candleDate = null;
                                    try {
                                        candleDate = quoteK.getTradeTime();

                                       // if (candleDate.before(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:10:00"))) {
                                            cumulatedVolume.getAndAdd(quoteK.getVolume().longValue());
                                       // }
                                        if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T10:25:00")) {
                                      //      cumulatedVolume.getAndAdd(quoteK.volume.longValue());
                                            BigDecimal diff = new BigDecimal(quoteK.getClose()).subtract(new BigDecimal(historicalDataOpen.getOpen()));
                                            DecimalFormat df = new DecimalFormat("0.00");

                                            BigDecimal perCh = diff.divide(new BigDecimal(historicalDataOpen.getOpen()), 2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
                                            System.out.println(stockEntity.getSymbol()+":"+perCh.doubleValue()+" Open: "+new BigDecimal(historicalDataOpen.getOpen()) + " :Open time: "+dateTimeFormat.format(openCandle.getTime())+" Close: "+quoteK.getClose());
                                            if (perCh.compareTo(new BigDecimal(gainPer)) > 0)
                                                positiveList.put(stockEntity.getSymbol(), quoteK.getClose() * cumulatedVolume.get());
                                            if (perCh.compareTo(new BigDecimal("0")) < 0)
                                                negList.put(stockEntity.getSymbol(), -(quoteK.getClose() * cumulatedVolume.get()));
                                        }
                                        if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:25:00")) {
                                          //  cumulatedVolume.getAndAdd(quoteK.volume.longValue());
                                            BigDecimal diff = new BigDecimal(quoteK.getClose()).subtract(new BigDecimal(historicalDataOpen.getOpen()));
                                            DecimalFormat df = new DecimalFormat("0.00");

                                            BigDecimal perCh = diff.divide(new BigDecimal(historicalDataOpen.getOpen()), 2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
                                            if (perCh.compareTo(new BigDecimal(gainPer)) > 0)
                                                positiveList1130.put(stockEntity.getSymbol(), quoteK.getClose() * cumulatedVolume.get());
                                            if (perCh.compareTo(new BigDecimal("0")) < 0)
                                                negList1130.put(stockEntity.getSymbol(), -(quoteK.getClose() * cumulatedVolume.get()));
                                        }
                                        if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:55:00")) {
                                          //  cumulatedVolume.getAndAdd(quoteK.volume.longValue());
                                            BigDecimal diff = new BigDecimal(quoteK.getClose()).subtract(new BigDecimal(historicalDataOpen.getOpen()));
                                            DecimalFormat df = new DecimalFormat("0.00");

                                            BigDecimal perCh = diff.divide(new BigDecimal(historicalDataOpen.getOpen()), 2, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
                                            if (perCh.compareTo(new BigDecimal(gainPer)) > 0)
                                                positiveList1200.put(stockEntity.getSymbol(), quoteK.getClose() * cumulatedVolume.get());
                                            if (perCh.compareTo(new BigDecimal("0")) < 0)
                                                negList1200.put(stockEntity.getSymbol(), -(quoteK.getClose() * cumulatedVolume.get()));
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }

                    } catch (Exception e) {
                        log.info(stockEntity.getSymbol() + ":" + e.getMessage());
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
                        AtomicInteger atomicInteger=new AtomicInteger();
                        System.out.println("positve top: " + map.getKey() + ":" + map.getValue());
                        List<StockDataEntity> stockDataEntities=stockDataRepository.findSymbol(map.getKey(),fromDate,toDate);

                        TradeData tradeData = new TradeData();
                        if (null != stockDataEntities && stockDataEntities.size() > 0) {
                            StockDataEntity historicalDataOpen = stockDataEntities.get(0);
                            Long cumulatedVolume = 0L;
                            AtomicDouble highValue=new AtomicDouble();
                            highValue.getAndSet(historicalDataOpen.getHigh());
                            stockDataEntities.stream().forEach(quoteK -> {
                                atomicInteger.getAndAdd(1);
                                Date candleDate = null;
                                try {
                                    candleDate = quoteK.getTradeTime();

                                    if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T10:25:00")) {
                                        //int index=stockDataEntities.indexOf(candleDate);
                                        List<StockDataEntity> stockDataEntitiesSub= stockDataEntities.subList(0,atomicInteger.get());
                                        String opt= commonUtil.findHHLHorHLLL(stockDataEntitiesSub,3,5,false);
                                        tradeData.setComment(opt);
                                        int quantity = (int) (Double.parseDouble(marigin) / quoteK.getClose());
                                        tradeData.setBuyPrice(new BigDecimal(quoteK.getClose()));
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


                                        tradeData.setBuyTime(dateTimeFormat.format(quoteK.getTradeTime()));
                                        tradeData.isOrderPlaced = true;
                                        tradeData.isSlPlaced = true;
                                        tradeData.setEntryType("BUY");
                                        if (isOpenPriceSL) {
                                            log.info("open:" + historicalDataOpen.getOpen());
                                            tradeData.setSlPrice(new BigDecimal(historicalDataOpen.getOpen()));
                                        } else {
                                            BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), tradeData.getBuyPrice());
                                            BigDecimal slPrice = tradeData.getBuyPrice().subtract(slValue);
                                            tradeData.setSlPrice(slPrice);
                                        }
                                    }
                               /*     if(!tradeData.isOrderPlaced && candleDate.after(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:10:00")) ){
                                        tradeData.setBuyTime(dateTimeFormat.format(quoteK.getTradeTime()));
                                        tradeData.isOrderPlaced = true;
                                        tradeData.isSlPlaced = true;
                                        tradeData.setEntryType("BUY");
                                        int quantity = (int) (Double.parseDouble(marigin) / quoteK.getClose());
                                        tradeData.setBuyPrice(new BigDecimal(quoteK.getClose()));
                                        tradeData.setStockName(map.getKey());
                                        tradeData.setQty(quantity);
                                        if (isOpenPriceSL) {
                                            log.info("open:" + historicalDataOpen.getOpen());
                                            tradeData.setSlPrice(new BigDecimal(historicalDataOpen.getOpen()));
                                        } else {
                                            BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), tradeData.getBuyPrice());
                                            BigDecimal slPrice = tradeData.getBuyPrice().subtract(slValue);
                                            tradeData.setSlPrice(slPrice);
                                        }
                                    }*/
                                    if(highValue.get()<= quoteK.getHigh()){
                                        highValue.getAndSet(quoteK.getHigh());
                                    }
                                    if (candleDate.after(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T10:25:00")) && tradeData.isOrderPlaced && candleDate.before(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T15:05:00"))) {

                                        BigDecimal perch = ((new BigDecimal(quoteK.getClose()).subtract(tradeData.getBuyPrice())).multiply(new BigDecimal("100"))).divide(tradeData.getBuyPrice(), 2, RoundingMode.HALF_UP);
                                        if (isPyramid) {

                                            if (new BigDecimal(quoteK.getClose()).compareTo(tradeData.getBuyPrice()) > 0 && candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:25:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
                                                boolean isPyra = false;
                                                for (Map.Entry<String, Double> map1130 : sortedPositiveMapSub1130) {
                                                    if (map1130.getKey().equals(tradeData.getStockName())) {
                                                        isPyra = true;
                                                    }
                                                }
                                                if (isPyra) {
                                                    System.out.println(" before pyramidQty:" + tradeData.getPyramidCount() + ":" + tradeData.getQty());
                                                    BigDecimal price = tradeData.getBuyPrice().multiply(new BigDecimal(tradeData.getQty()));
                                                    tradeData.setPyramidCount(tradeData.getPyramidCount() + 1);
                                                    tradeData.setPyramidTime(dateTimeFormat.format(quoteK.getTradeTime()));
                                                    int newQty = tradeData.getPyramidQty();
                                                    tradeData.setQty(tradeData.getQty() + newQty);
                                                    BigDecimal prPrice = new BigDecimal(quoteK.getClose()).multiply(new BigDecimal(newQty));
                                                    tradeData.setBuyPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                    if (isOpenPriceSL) {
                                                    } else {
                                                        BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), new BigDecimal(quoteK.getClose()));
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

                                                    tradeData.setPyramidTime1(dateTimeFormat.format(dateTimeFormat.format(quoteK.getTradeTime() )));
                                                    int newQty = tradeData.getPyramidQty();
                                                    if (tradeData.getPyramidCount() == 1) {
                                                        newQty = 2 * tradeData.getPyramidQty();
                                                    }
                                                    tradeData.setPyramidCount(tradeData.getPyramidCount() + 1);
                                                    tradeData.setQty(tradeData.getQty() + newQty);
                                                    BigDecimal prPrice = new BigDecimal(quoteK.getClose()).multiply(new BigDecimal(newQty));
                                                    tradeData.setBuyPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                    if (isOpenPriceSL) {
                                                    } else {
                                                        BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), new BigDecimal(quoteK.getClose()));
                                                        BigDecimal slPrice = tradeData.getBuyPrice().subtract(slValue);
                                                        tradeData.setSlPrice(slPrice);
                                                    }
                                                    System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                }
                                            }
                                        }
                                       /* if (new BigDecimal(quoteK.getClose()).compareTo(tradeData.getSlPrice()) < 0 && tradeData.isOrderPlaced && !tradeData.isSLHit) {
                                            tradeData.setSellTime(dateTimeFormat.format(quoteK.getTradeTime()));
                                            tradeData.setSellPrice(new BigDecimal(quoteK.getClose()));
                                            tradeData.isSLHit = true;
                                            log.info("test");
                                            Brokerage brokerage = MathUtils.calculateBrokerage(tradeData, false, true, false, "0.05");
                                            CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath, true));
                                            BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                            BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                            String[] data = {dateFormat.format(fromdate), tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), String.valueOf(tradeData.isSLHit), tradeData.getPyramidTime(), tradeData.getPyramidTime1(),tradeData.getComment()};
                                            csvWriter.writeNext(data);
                                            csvWriter.flush();
                                        }*/

                                    }
                                    if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T15:05:00") && tradeData.isOrderPlaced  && !tradeData.isSLHit && !tradeData.isExited) {
                                        tradeData.setSellTime(dateTimeFormat.format(quoteK.getTradeTime()));
                                        tradeData.setSellPrice(new BigDecimal(quoteK.getClose()));
                                        tradeData.isExited = true;
                                        System.out.println("test exit");
                                        Brokerage brokerage = MathUtils.calculateBrokerage(tradeData, false, true, false, "0.25");
                                        CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath, true));
                                        BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                        BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                        String[] data = {dateFormat.format(fromdate), tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), String.valueOf(tradeData.isSLHit), tradeData.getPyramidTime(), tradeData.getPyramidTime1(),tradeData.getComment()};
                                        csvWriter.writeNext(data);
                                       csvWriter.flush();
                                    }

                                } catch (ParseException | IOException e) {
                                    e.printStackTrace();
                                }

                            });
                        }
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

                                List<Candlestick> historicalDataPos = fyerAccount.getHistory(fyerAccount.lsFyerSymbols.get(map.getKey()), "5", fromDate, toDate);
                                TradeData tradeData = new TradeData();
                                if (null != historicalDataPos && historicalDataPos.size() > 0) {
                                    Candlestick historicalDataOpen = historicalDataPos.get(0);

                                    historicalDataPos.stream().forEach(quoteK -> {
                                        Date candleDate = null;
                                        try {
                                            candleDate = new Date(quoteK.time.longValue() * 1000);
                                            if (dateTimeCandleFormat.format(candleDate).equals(dateFormat.format(fromdate) + "T11:10:00")) {
                                                int quantity = (int) (Double.parseDouble(marigin) / quoteK.close.doubleValue());
                                                tradeData.setSellPrice(quoteK.close);
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
                                                tradeData.setSellTime(dateTimeFormat.format(new Date(quoteK.time.longValue() * 1000)));
                                                tradeData.isOrderPlaced = true;
                                                tradeData.isSlPlaced = true;
                                                tradeData.setEntryType("SELL");
                                                if (isOpenPriceSL) {
                                                    log.info("open:" + historicalDataOpen.open);
                                                    tradeData.setSlPrice(historicalDataOpen.open);
                                                } else {
                                                    BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), tradeData.getSellPrice());
                                                    BigDecimal slPrice = tradeData.getSellPrice().add(slValue);
                                                    tradeData.setSlPrice(slPrice);
                                                }
                                            }
                                            if (candleDate.after(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:10:00")) && tradeData.isOrderPlaced && candleDate.before(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T15:05:00"))) {

                                                BigDecimal perch = (((tradeData.getSellPrice().subtract(quoteK.close)).multiply(new BigDecimal("100")))).divide(quoteK.close, 2, RoundingMode.HALF_UP);
                                                if (isPyramid) {
                                                    if (quoteK.close.compareTo(tradeData.getSellPrice()) < 0 && candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:25:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
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
                                                            tradeData.setPyramidTime(dateTimeFormat.format(new Date(quoteK.time.longValue() * 1000)));
                                                            BigDecimal price = tradeData.getSellPrice().multiply(new BigDecimal(tradeData.getQty()));
                                                            int newQty = tradeData.getPyramidQty();
                                                            tradeData.setQty(tradeData.getQty() + newQty);
                                                            BigDecimal prPrice = quoteK.close.multiply(new BigDecimal(newQty));
                                                            tradeData.setSellPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                            if (isOpenPriceSL) {
                                                            } else {
                                                                BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), quoteK.close);
                                                                BigDecimal slPrice = quoteK.close.add(slValue);
                                                                tradeData.setSlPrice(slPrice);
                                                            }
                                                            System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                        }
                                                    }
                                                    if (quoteK.close.compareTo(tradeData.getSellPrice()) < 0 && candleDate.equals(dateTimeCandleFormat.parse(dateFormat.format(fromdate) + "T11:55:00")) && tradeData.getPyramidCount() < 3 && !tradeData.isSLHit) {
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
                                                            tradeData.setPyramidTime1(dateTimeFormat.format(new Date(quoteK.time.longValue() * 1000)));
                                                            BigDecimal price = tradeData.getSellPrice().multiply(new BigDecimal(tradeData.getQty()));
                                                            int newQty = tradeData.getPyramidQty();
                                                            tradeData.setQty(tradeData.getQty() + newQty);
                                                            BigDecimal prPrice = quoteK.close.multiply(new BigDecimal(newQty));
                                                            tradeData.setSellPrice((price.add(prPrice)).divide(new BigDecimal(tradeData.getQty()), 2, RoundingMode.HALF_UP));
                                                            if (isOpenPriceSL) {
                                                            } else {
                                                                BigDecimal slValue = MathUtils.percentageValueOfAmount(new BigDecimal(slPer), quoteK.close);
                                                                BigDecimal slPrice = quoteK.close.add(slValue);
                                                                tradeData.setSlPrice(slPrice);
                                                            }
                                                            System.out.println(" after pyramidQty:" + tradeData.getQty());
                                                        }
                                                    }
                                                }
                                                if (quoteK.close.compareTo(tradeData.getSlPrice()) > 0 && !tradeData.isSLHit) {
                                                    tradeData.setBuyTime(dateTimeFormat.format(new Date(quoteK.time.longValue() * 1000)));
                                                    tradeData.setBuyPrice(quoteK.close);
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
                                                tradeData.setBuyTime(dateTimeFormat.format(new Date(quoteK.time.longValue() * 1000)));
                                                tradeData.setBuyPrice(quoteK.close);
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

