
package com.sakthi.trade.binance;

import com.binance.client.RequestOptions;
import com.binance.client.impl.RestApiRequestImpl;
import com.binance.client.impl.SyncRequestImpl;
import com.binance.client.model.enums.CandlestickInterval;
import com.binance.client.model.market.Candlestick;
import com.binance.client.model.market.ExchangeInformation;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.binance.models.CandlestickExtended;
import com.sakthi.trade.domain.TradeDataCrypto;
import com.sakthi.trade.util.MathUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainCategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class BinanceTrendBacktest {

    @Value("${filepath.trend}")
    String trendPath;
    @Value("${binance.sathiyaseelanrhce.v11.secret}")
    private String binanceSecretKey;
    @Value("${binance.sathiyaseelanrhce.v11.apikey}")
    private String binanceApiKey;
    RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
    SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);


    public static String encode(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
    }

    public String binanceAccountInfo() throws BinanceApiException {
        BinanceRequest binanceRequest = new BinanceRequest("https://fapi.binance.com/fapi/v2/account");
        binanceRequest.sign(binanceApiKey, binanceSecretKey, null);
        binanceRequest.read();
        System.out.println(binanceRequest.lastResponse);
        log.info("Binance Account response:" + binanceRequest.lastResponse);
        return binanceRequest.lastResponse;
    }

    public void binanceTrend() throws Exception {
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trendttnew.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        List<String> symbolList = new ArrayList<>();
        exchn.getSymbols().stream().forEach(symbols -> {
            symbolList.add(symbols.getSymbol());
        });
        int n = 35;

        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtNextStop = today.plusDays(2).atStartOfDay(ZoneId.systemDefault());
            System.out.println(today);
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            //   System.out.println(symbols.getSymbol());
            n--;
            symbolList.stream().forEach(s -> {

                    List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                    if (s.contains("USDT")) {
                        List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                        if (candlestickList.size() > 0) {
                            //  System.out.println(" " + symbols.getSymbol() + ":" + new Gson().toJson(candlestickList));
                            MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                            MathUtils.SMA sma50 = new MathUtils.SMA(50);
                            MathUtils.RSI rsi = new MathUtils.RSI(6);
                            TradeDataCrypto tradeData = new TradeDataCrypto();
                            AtomicInteger count = new AtomicInteger();
                            Candlestick openCandle = candlestickList.get(0);
                            Calendar openCalender = Calendar.getInstance();
                            openCalender.setTimeInMillis(openCandle.getCloseTime());
                            BigDecimal percentMoveDay = MathUtils.percentageMove(candlestickList.get(0).getOpen(), candlestickList.get(candlestickList.size() - 1).getClose());
                            AtomicInteger plusCount = new AtomicInteger();
                            AtomicInteger minusCount = new AtomicInteger();
                   /*     if (percentMoveDay.compareTo(new BigDecimal(10)) > 0) {
                            System.out.println(s + " : " + simple.format(candlestickList.get(0).getOpenTime()) + " percent move : " + percentMoveDay);
                        }*/
                            candlestickList.stream().forEach(candlestick -> {
                                // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                                CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                                candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                                candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                                candlestickListEx.add(candlestickExtended);
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                                //       System.out.println(simple.format(calendar.getTime()));
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                try {
                                    if (calendar.getTime().equals(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:55:00"))) {
                                        BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestickExtended.getClose());
                                        if (percentMoveCurrent.doubleValue() > 0) {
                                            plusCount.getAndAdd(1);
                                        } else {
                                            minusCount.getAndAdd(1);
                                        }
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            });
                            candlestickListEx.stream().forEach(candlestickExtended -> {
                                BigDecimal percentMove = MathUtils.percentageMove(candlestickExtended.getOpen(), candlestickExtended.getClose());

                                BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestickExtended.getClose());

                 /*   if(percentMove.compareTo(new BigDecimal(2))>0){
                        if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(4)))>0){
                         if(!tradeData.isOrderPlaced){
                             tradeData.isOrderPlaced=true;
                             tradeData.setStockName(symbols.getSymbol());
                             int qty= new BigDecimal(100).divide(candlestickExtended.getClose(),RoundingMode.HALF_EVEN).intValue();
                             tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                             tradeData.setQty(qty);
                             tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                             double slPoints = (tradeData.getBuyPrice()*5)/100;
                             double slPrice = tradeData.getBuyPrice()-slPoints;
                             tradeData.setSlPrice(slPrice);
                             tradeData.setSlTrialPoints(slPoints);
                         }
                        }
                    }
                    if(tradeData.isOrderPlaced) {
                        if (!tradeData.isExited) {
                            if (candlestickExtended.getLow().doubleValue() < tradeData.getSlPrice()) {
                                tradeData.isExited = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));

                                //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                if (diff > tradeData.getSlTrialPoints()) {
                                    double mod = (diff / tradeData.getSlTrialPoints());
                                    double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                    if (newSL > tradeData.getSlPrice()) {
                                        tradeData.setSlPrice(newSL);
                                    }
                                }
                            }
                        }
                    }*/

                                count.getAndAdd(1);
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                                //       System.out.println(simple.format(calendar.getTime()));
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                                try {
                                    if (percentMoveCurrent.doubleValue() >= 5 && calendar.getTime().after(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:00:00"))
                                            && calendar.getTime().before(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 10:00:00"))) {

                                        //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){
                                        if (!tradeData.isOrderPlaced && tradeData.getStockName() == null) {
                                            // System.out.println(s+":"+openCandle.getOpen()+":"+ candlestickExtended.getClose()+":"+percentMoveCurrent.doubleValue());
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setStockName(s);
                                            double qty = 100 / candlestickExtended.getClose().doubleValue();
                                            tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setQty(qty);
                                            tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                            double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                            double slPrice = tradeData.getBuyPrice() - slPoints;
                                            tradeData.setSlPrice(slPrice);
                                            tradeData.setEntryType("BUY");
                                            tradeData.setSlTrialPoints(slPoints);
                                        }
                                        //  }
                                    } else if (percentMoveCurrent.doubleValue() <= -5 && calendar.getTime().after(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:00:00"))
                                            && calendar.getTime().before(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 10:00:00"))) {
                                        if (!tradeData.isOrderPlaced && tradeData.getStockName() == null) {
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setStockName(s);
                                            double qty = 100 / candlestickExtended.getClose().doubleValue();
                                            tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setQty(qty);
                                            tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                            double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                            double slPrice = tradeData.getBuyPrice() - slPoints;
                                            tradeData.setSlPrice(slPrice);
                                            tradeData.setEntryType("Inverted-BUY");
                                            tradeData.setSlTrialPoints(slPoints);
                                        }
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                                if (tradeData.isOrderPlaced) {

                                    if (!tradeData.isExited) {
                                        double percentTradeMove = MathUtils.percentageMove(tradeData.getBuyPrice(), candlestickExtended.getClose().doubleValue());

                                        if (tradeData.getSlPrice() > candlestickExtended.getClose().doubleValue()) {
                                            tradeData.isExited = true;
                                            tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));

                                            double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                            String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType()};
                                            //  System.out.println(Arrays.toString(data));
                                            csvWriter.writeNext(data);
                                            try {
                                                csvWriter.flush();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        } else {
                                            double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                            if (diff > tradeData.getSlTrialPoints()) {
                                                double mod = (diff / tradeData.getSlTrialPoints());
                                                double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                                if (newSL > tradeData.getSlPrice()) {
                                                    tradeData.setSlPrice(newSL);
                                                }
                                            }
                                        }

                                    }
                                }

                            });
                            //  System.out.println(new Gson().toJson(candlestickListEx));
                            Date date = new Date();
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
                            //    saveChart(s, simpleDateFormat.format(date), candlestickListEx);
                            CandlestickExtended lastCandle = candlestickListEx.get(candlestickListEx.size() - 1);

                            //    System.out.println("Symbol:"+symbols.getSymbol()+" RSI:"+lastCandle.getRsi()+" Open Time:"+simple.format(lastCandle.getOpenTime())+" Close Time:"+simple.format(lastCandle.getCloseTime())+" Open:"+lastCandle.getOpen()+" Close:"+lastCandle.getClose()+" High:"+lastCandle.getHigh()+" Low:"+lastCandle.getLow()+" Volume:"+lastCandle.getVolume());
                            if (tradeData.isOrderPlaced && !tradeData.isExited) {
                                double profitLossday = (lastCandle.getClose().doubleValue() - tradeData.getBuyPrice()) * tradeData.getQty();


                                tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                                tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));


                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType()};
                                //  System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }
                        }
                    }

            });
        }
    }

    public void binanceRSITrendBacktest() throws Exception {
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trendRSI60.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        List<String> symbolList = new ArrayList<>();
        exchn.getSymbols().stream().forEach(symbols -> {
            symbolList.add(symbols.getSymbol());
        });
        int n = 2;
        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            System.out.println(zdtStart.toInstant().toEpochMilli() + ":" + zdtStop.toInstant().toEpochMilli());

            //   System.out.println(symbols.getSymbol());
            n--;                  AtomicInteger plusCount = new AtomicInteger();
            AtomicInteger minusCount = new AtomicInteger();

            symbolList.stream().forEach(s -> {

                if (s.contains("USDT")) {
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    if (candlestickList.size() > 0) {
                        //  System.out.println(" " + symbols.getSymbol() + ":" + new Gson().toJson(candlestickList));
                        MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                        MathUtils.SMA sma50 = new MathUtils.SMA(50);
                        MathUtils.RSI rsi = new MathUtils.RSI(6);
                        TradeDataCrypto tradeData = new TradeDataCrypto();
                        AtomicInteger count = new AtomicInteger();
                        Candlestick openCandle = candlestickList.get(0);
                        Calendar openCalender = Calendar.getInstance();
                        openCalender.setTimeInMillis(openCandle.getCloseTime());


                        candlestickList.stream().forEach(candlestick -> {
                            // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                            //       System.out.println(simple.format(calendar.getTime()));
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                                if (dateTimeFormat.format(calendar.getTime()).equals(dateFormat.format(openCalender.getTime()) + " 02:59:59")) {
                                    BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestickExtended.getClose());
                                    if (percentMoveCurrent.doubleValue() > 0) {
                                        plusCount.getAndAdd(1);
                                    } else {
                                        minusCount.getAndAdd(1);
                                    }
                                }

                        });
                    }
                }
                });
            boolean buy = plusCount.get() > minusCount.get() ? true : false;
            System.out.println("ratio: " + today.format(DateTimeFormatter.BASIC_ISO_DATE)+ ": " + plusCount.get() + " : " + minusCount.get());
                        symbolList.stream().forEach(s -> {
                            List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                            if (s.contains("USDT")) {
                                List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                                MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                                MathUtils.SMA sma50 = new MathUtils.SMA(50);
                                MathUtils.RSI rsi = new MathUtils.RSI(6);
                                TradeDataCrypto tradeData = new TradeDataCrypto();
                                AtomicInteger count = new AtomicInteger();
                                Candlestick openCandle = candlestickList.get(0);
                                Calendar openCalender = Calendar.getInstance();
                                openCalender.setTimeInMillis(openCandle.getCloseTime());
                                candlestickList.stream().forEach(candlestick -> {
                                            // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                                            candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                                            candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                                            candlestickListEx.add(candlestickExtended);
                                            Calendar calendar = Calendar.getInstance();
                                            calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                                        });
                                candlestickListEx.stream().forEach(candlestickExtended->{
                            BigDecimal percentMove = MathUtils.percentageMove(candlestickExtended.getOpen(), candlestickExtended.getClose());

                            BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestickExtended.getClose());
                 /*   if(percentMove.compareTo(new BigDecimal(2))>0){
                        if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(4)))>0){
                         if(!tradeData.isOrderPlaced){
                             tradeData.isOrderPlaced=true;
                             tradeData.setStockName(symbols.getSymbol());
                             int qty= new BigDecimal(100).divide(candlestickExtended.getClose(),RoundingMode.HALF_EVEN).intValue();
                             tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                             tradeData.setQty(qty);
                             tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                             double slPoints = (tradeData.getBuyPrice()*5)/100;
                             double slPrice = tradeData.getBuyPrice()-slPoints;
                             tradeData.setSlPrice(slPrice);
                             tradeData.setSlTrialPoints(slPoints);
                         }
                        }
                    }
                    if(tradeData.isOrderPlaced) {
                        if (!tradeData.isExited) {
                            if (candlestickExtended.getLow().doubleValue() < tradeData.getSlPrice()) {
                                tradeData.isExited = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));

                                //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                if (diff > tradeData.getSlTrialPoints()) {
                                    double mod = (diff / tradeData.getSlTrialPoints());
                                    double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                    if (newSL > tradeData.getSlPrice()) {
                                        tradeData.setSlPrice(newSL);
                                    }
                                }
                            }
                        }
                    }*/

                            count.getAndAdd(1);
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                            //       System.out.println(simple.format(calendar.getTime()));
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                            try {
                                if (
                                         count.get() > 20 && percentMoveCurrent.doubleValue()>0 &&  calendar.getTime().after(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:55:00"))) {
                                    //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){

                                    if(candlestickExtended.getRsi() > 40 && buy) {
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setStockName(s);
                                        double qty = 100 / candlestickExtended.getClose().doubleValue();
                                        tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                        tradeData.setQty(qty);
                                        tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                        double slPoints = (tradeData.getSellPrice() * 5) / 100;
                                        double slPrice = tradeData.getSellPrice() - slPoints;
                                        tradeData.setSlPrice(slPrice);
                                        tradeData.setSlTrialPoints(slPoints);
                                    }else if (candlestickExtended.getRsi()  <40 && !buy){
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setStockName(s);
                                        double qty = 100 / candlestickExtended.getClose().doubleValue();
                                        tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                        tradeData.setQty(qty);
                                        tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                        double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                        double slPrice = tradeData.getBuyPrice() + slPoints;
                                        tradeData.setSlPrice(slPrice);
                                        tradeData.setSlTrialPoints(slPoints);
                                    }
                                    //  }
                                }
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            /*if (tradeData.isOrderPlaced) {
                                double percentMoveAfterTrade = MathUtils.percentageMove(tradeData.getBuyPrice(), candlestickExtended.getClose().doubleValue());
                                if (candlestickExtended.getRsi() > 60 || percentMoveAfterTrade>5) {
                                    if (tradeData.isOrderPlaced && !tradeData.isExited) {
                                        tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                        tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                        tradeData.isExited = true;
                                        tradeData.isOrderPlaced = false;
                                        //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                        double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                        String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                        System.out.println(Arrays.toString(data));
                                        csvWriter.writeNext(data);
                                        try {
                                            csvWriter.flush();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                }
                            }*/
                        });
                        //  System.out.println(new Gson().toJson(candlestickListEx));
                        Date date = new Date();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
                        //    saveChart(s, simpleDateFormat.format(date), candlestickListEx);
                        CandlestickExtended lastCandle = candlestickListEx.get(candlestickListEx.size() - 1);

                        //    System.out.println("Symbol:"+symbols.getSymbol()+" RSI:"+lastCandle.getRsi()+" Open Time:"+simple.format(lastCandle.getOpenTime())+" Close Time:"+simple.format(lastCandle.getCloseTime())+" Open:"+lastCandle.getOpen()+" Close:"+lastCandle.getClose()+" High:"+lastCandle.getHigh()+" Low:"+lastCandle.getLow()+" Volume:"+lastCandle.getVolume());
                                if (tradeData.isOrderPlaced && !tradeData.isExited) {
                                    if(!buy) {
                                        tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                        tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                                        tradeData.setEntryType("SHORT");
                                    }else {
                                        tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                                        tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                                        tradeData.setEntryType("BUY");
                                    }
                                    double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(),tradeData.getEntryType()};
                                    //  System.out.println(Arrays.toString(data));
                                    csvWriter.writeNext(data);
                                    try {
                                        csvWriter.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                }
                    }

            });

        }
    }

    public void binanceRSITrendBacktestShort() throws Exception {
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trendRSI60Short.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        List<String> symbolList = new ArrayList<>();
        exchn.getSymbols().stream().forEach(symbols -> {
            symbolList.add(symbols.getSymbol());
        });
        int n = 120;
        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            LocalDate previous = LocalDate.now(ZoneId.systemDefault()).minusDays(n+1);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtPreStart = previous.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtPreStop = previous.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            System.out.println(zdtStart.toInstant().toEpochMilli() + ":" + zdtStop.toInstant().toEpochMilli());

            //   System.out.println(symbols.getSymbol());
            n--;
            symbolList.stream().forEach(s -> {
                List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                if (s.contains("USDT")) {
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    List<Candlestick> daycandlestickList = syncRequest.getCandlestick(s, CandlestickInterval.DAILY, zdtPreStart.toInstant().toEpochMilli(), zdtPreStop.toInstant().toEpochMilli(), 500);

                    if (candlestickList.size() > 0) {
                        //  System.out.println(" " + symbols.getSymbol() + ":" + new Gson().toJson(candlestickList));
                        MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                        MathUtils.SMA sma50 = new MathUtils.SMA(50);
                        MathUtils.RSI rsi = new MathUtils.RSI(6);
                        TradeDataCrypto tradeData = new TradeDataCrypto();
                        AtomicInteger count = new AtomicInteger();
                        Candlestick openCandle = candlestickList.get(0);
                        Calendar openCalender = Calendar.getInstance();
                        openCalender.setTimeInMillis(openCandle.getCloseTime());
                        BigDecimal percentMoveDay = MathUtils.percentageMove(candlestickList.get(0).getOpen(), candlestickList.get(candlestickList.size() - 1).getClose());
                        if (percentMoveDay.compareTo(new BigDecimal(10)) > 0) {
                        //    System.out.println(s + " : " + simple.format(candlestickList.get(0).getOpenTime()) + " percent move : " + percentMoveDay);
                        }
                        candlestickList.stream().forEach(candlestick -> {
                            BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestick.getClose());

                            // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                            candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                            candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                            candlestickListEx.add(candlestickExtended);
                            BigDecimal percentMove = MathUtils.percentageMove(candlestickExtended.getOpen(), candlestickExtended.getClose());
                 /*   if(percentMove.compareTo(new BigDecimal(2))>0){
                        if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(4)))>0){
                         if(!tradeData.isOrderPlaced){
                             tradeData.isOrderPlaced=true;
                             tradeData.setStockName(symbols.getSymbol());
                             int qty= new BigDecimal(100).divide(candlestickExtended.getClose(),RoundingMode.HALF_EVEN).intValue();
                             tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                             tradeData.setQty(qty);
                             tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                             double slPoints = (tradeData.getBuyPrice()*5)/100;
                             double slPrice = tradeData.getBuyPrice()-slPoints;
                             tradeData.setSlPrice(slPrice);
                             tradeData.setSlTrialPoints(slPoints);
                         }
                        }
                    }
                    if(tradeData.isOrderPlaced) {
                        if (!tradeData.isExited) {
                            if (candlestickExtended.getLow().doubleValue() < tradeData.getSlPrice()) {
                                tradeData.isExited = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));

                                //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                if (diff > tradeData.getSlTrialPoints()) {
                                    double mod = (diff / tradeData.getSlTrialPoints());
                                    double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                    if (newSL > tradeData.getSlPrice()) {
                                        tradeData.setSlPrice(newSL);
                                    }
                                }
                            }
                        }
                    }*/

                            count.getAndAdd(1);
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(candlestick.getCloseTime());
                            //       System.out.println(simple.format(calendar.getTime()));
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            if(daycandlestickList.size()>0) {
                                Candlestick candlestickPrevious = daycandlestickList.get(0);
                                double previousDayMove = ((candlestickPrevious.getClose().doubleValue() - candlestickPrevious.getOpen().doubleValue()) / candlestickPrevious.getOpen().doubleValue()) * 100;
                                if (!tradeData.isOrderPlaced) {
                                if (candlestickExtended.getRsi() > 70 && count.get() > 20 && percentMoveCurrent.doubleValue() < 0 ) {
                                    //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){



                                            tradeData.isOrderPlaced = true;
                                            tradeData.setStockName(s);
                                            double qty = 100 / candlestickExtended.getClose().doubleValue();
                                            tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setQty(qty);
                                            tradeData.setEntryType("SELL");
                                            tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                        }
                                        else         if (candlestickExtended.getRsi()< 25 && count.get() > 20 && percentMoveCurrent.doubleValue() > 0 ) {
                                        {
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setStockName(s);
                                            tradeData.setEntryType("BUY");
                                            double qty = 100 / candlestickExtended.getClose().doubleValue();
                                            tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setQty(qty);
                                            tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                        }
                                        //  }
                                    }

                                }
                            }
                            if (tradeData.isOrderPlaced) {
                                double percentMoveAfterTrade = 0 ;
                                if("SELL".equals(tradeData.getEntryType())) {
                                    percentMoveAfterTrade=MathUtils.percentageMove(tradeData.getSellPrice(), candlestickExtended.getClose().doubleValue());
                                }
                                else
                                {
                                    percentMoveAfterTrade=MathUtils.percentageMove(tradeData.getBuyPrice(), candlestickExtended.getClose().doubleValue());
                                }
                                if (percentMoveAfterTrade< -5 || percentMoveAfterTrade>5) {
                                    if (tradeData.isOrderPlaced && !tradeData.isExited) {
                                        if("SELL".equals(tradeData.getEntryType())) {
                                            tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                        }else
                                        {
                                            tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                        }
                                        tradeData.isExited = true;
                                        tradeData.isOrderPlaced = false;
                                        //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                        double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                        String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(),tradeData.getEntryType()};
                                        System.out.println(Arrays.toString(data));
                                        csvWriter.writeNext(data);
                                        try {
                                            csvWriter.flush();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                }
                            }
                        });
                        //  System.out.println(new Gson().toJson(candlestickListEx));
                        Date date = new Date();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
                        //    saveChart(s, simpleDateFormat.format(date), candlestickListEx);
                        CandlestickExtended lastCandle = candlestickListEx.get(candlestickListEx.size() - 1);

                        //    System.out.println("Symbol:"+symbols.getSymbol()+" RSI:"+lastCandle.getRsi()+" Open Time:"+simple.format(lastCandle.getOpenTime())+" Close Time:"+simple.format(lastCandle.getCloseTime())+" Open:"+lastCandle.getOpen()+" Close:"+lastCandle.getClose()+" High:"+lastCandle.getHigh()+" Low:"+lastCandle.getLow()+" Volume:"+lastCandle.getVolume());
                        if (tradeData.isOrderPlaced && !tradeData.isExited) {
                            if("SELL".equals(tradeData.getEntryType())) {
                                tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                            }else
                            {
                                tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                                tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                            }
                            double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                            String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(),tradeData.getEntryType()};
                            //  System.out.println(Arrays.toString(data));
                            csvWriter.writeNext(data);
                            try {
                                csvWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }
            });

        }
    }
    public void binanceVolumeTrend() throws Exception {
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trendVolume.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        List<String> symbolList = new ArrayList<>();
        exchn.getSymbols().stream().forEach(symbols -> {
            symbolList.add(symbols.getSymbol());
        });
        int n = 10;
        while (n >= 0) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());

            AtomicInteger plusCount=new AtomicInteger();
            AtomicInteger minusCount=new AtomicInteger();
            //   System.out.println(symbols.getSymbol());
            n--;

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            symbolList.stream().forEach(s -> {
                List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                if (s.contains("USDT")) {
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    if (candlestickList.size() > 0) {
                        //  System.out.println(" " + symbols.getSymbol() + ":" + new Gson().toJson(candlestickList));
                        Candlestick openCandle = candlestickList.get(0);
                        Calendar openCalender = Calendar.getInstance();
                        openCalender.setTimeInMillis(openCandle.getOpenTime());

                        candlestickList.stream().forEach(candlestick -> {
                            // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                            candlestickListEx.add(candlestickExtended);
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                            //       System.out.println(simple.format(calendar.getTime()));
                            //   System.out.println(dateTimeFormat.format(calendar.getTime()));
                            if (dateTimeFormat.format(calendar.getTime()).equals(dateFormat.format(openCalender.getTime()) + " 02:59:59")) {
                                BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestickExtended.getClose());
                                if (percentMoveCurrent.doubleValue() > 0) {
                                    plusCount.getAndAdd(1);
                                } else {
                                    minusCount.getAndAdd(1);
                                }
                            }
                        });


                    }
                }
                    });
            boolean buy = plusCount.get() > minusCount.get() ? true : false;
            System.out.println("ratio: " + today.format(DateTimeFormatter.BASIC_ISO_DATE)+ ": " + plusCount.get() + " : " + minusCount.get());
            symbolList.stream().forEach(s -> {
                List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                if (s.contains("USDT")) {
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    if (candlestickList.size() > 0) {
                        //  System.out.println(" " + symbols.getSymbol() + ":" + new Gson().toJson(candlestickList));
                        MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                        MathUtils.SMA sma50 = new MathUtils.SMA(50);
                        MathUtils.RSI rsi = new MathUtils.RSI(6);
                        TradeDataCrypto tradeData = new TradeDataCrypto();
                        AtomicInteger count = new AtomicInteger();
                        Candlestick openCandle = candlestickList.get(0);
                        Calendar openCalender = Calendar.getInstance();
                        openCalender.setTimeInMillis(openCandle.getOpenTime());


                        candlestickList.stream().forEach(candlestick -> {
                            // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                            candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                            candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                            candlestickListEx.add(candlestickExtended);
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                            //       System.out.println(simple.format(calendar.getTime()));
                         //   System.out.println(dateTimeFormat.format(calendar.getTime()));
                        });
                        candlestickListEx.stream().forEach(candlestickExtended->{

                            BigDecimal percentMoveCurrent = MathUtils.percentageMove(openCandle.getOpen(), candlestickExtended.getClose());
                            AtomicDouble atomicPercentMove = new AtomicDouble();
                            AtomicInteger noofVolume = new AtomicInteger();
                            if (count.get() > 4) {
                                List<CandlestickExtended> checkList = candlestickListEx.subList(count.get() - 5, count.get());

                                double percentMove = MathUtils.percentageMove(checkList.get(0).getOpen().doubleValue(), checkList.get(checkList.size() - 1).getClose().doubleValue());
                                atomicPercentMove.getAndAdd(percentMove);

                                checkList.stream().forEach(candlestickSub -> {

                                    if (candlestickSub.getVolume().doubleValue() > candlestickSub.getVolumema20() * 2) {
                                        noofVolume.getAndAdd(1);
                                    }
                                });
                 /*   if(percentMove.compareTo(new BigDecimal(2))>0){
                        if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(4)))>0){
                         if(!tradeData.isOrderPlaced){
                             tradeData.isOrderPlaced=true;
                             tradeData.setStockName(symbols.getSymbol());
                             int qty= new BigDecimal(100).divide(candlestickExtended.getClose(),RoundingMode.HALF_EVEN).intValue();
                             tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                             tradeData.setQty(qty);
                             tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                             double slPoints = (tradeData.getBuyPrice()*5)/100;
                             double slPrice = tradeData.getBuyPrice()-slPoints;
                             tradeData.setSlPrice(slPrice);
                             tradeData.setSlTrialPoints(slPoints);
                         }
                        }
                    }
                    if(tradeData.isOrderPlaced) {
                        if (!tradeData.isExited) {
                            if (candlestickExtended.getLow().doubleValue() < tradeData.getSlPrice()) {
                                tradeData.isExited = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));

                                //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                if (diff > tradeData.getSlTrialPoints()) {
                                    double mod = (diff / tradeData.getSlTrialPoints());
                                    double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                    if (newSL > tradeData.getSlPrice()) {
                                        tradeData.setSlPrice(newSL);
                                    }
                                }
                            }
                        }
                    }*/


                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(candlestickExtended.getCloseTime());
                                //       System.out.println(simple.format(calendar.getTime()));
                                double percentMoveDay = MathUtils.percentageMove(openCandle.getOpen().doubleValue(), candlestickExtended.getClose().doubleValue());
                                try {
                                    if ( noofVolume.get() >= 3 && calendar.getTime().after(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:55:00"))  && calendar.getTime().before(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 12:00:00"))) {
                                        //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){
                                        if (!tradeData.isOrderPlaced) {
                                            if(atomicPercentMove.get() < 5) {
                                                tradeData.isOrderPlaced = true;
                                                tradeData.setStockName(s);
                                                double qty = 100 / candlestickExtended.getClose().doubleValue();
                                                tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                                tradeData.setQty(qty);
                                                tradeData.setEntryType("SHORT");
                                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                                double slPoints = (tradeData.getSellPrice() * 10) / 100;
                                                double slPrice = tradeData.getSellPrice() + slPoints;
                                                tradeData.setSlPrice(slPrice);
                                                tradeData.setSlTrialPoints(slPoints);
                                            }else if (atomicPercentMove.get() > 5){
                                                tradeData.isOrderPlaced = true;
                                                tradeData.setStockName(s);
                                                double qty = 100 / candlestickExtended.getClose().doubleValue();
                                                tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                                tradeData.setQty(qty);
                                                tradeData.setEntryType("BUY");
                                                tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                                double slPoints = (tradeData.getBuyPrice() * 10) / 100;
                                                double slPrice = tradeData.getBuyPrice() - slPoints;
                                                tradeData.setSlPrice(slPrice);
                                                tradeData.setSlTrialPoints(slPoints);
                                            }
                                        }
                                        //  }
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                                if (tradeData.isOrderPlaced) {
                                    if (!tradeData.isExited) {
                                       /* if (candlestickExtended.getHigh().doubleValue() > tradeData.getSlPrice()) {
                                            tradeData.isExited = true;
                                            tradeData.setBuyPrice(tradeData.getSlPrice());
                                            tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));

                                            //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                            double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                            String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                            System.out.println(Arrays.toString(data));
                                            csvWriter.writeNext(data);
                                            try {
                                                csvWriter.flush();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        } *//*else {
                                            double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                            if (diff > tradeData.getSlTrialPoints()) {
                                                double mod = (diff / tradeData.getSlTrialPoints());
                                                double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                                if (newSL > tradeData.getSlPrice()) {
                                                    tradeData.setSlPrice(newSL);
                                                }
                                            }
                                        }*/
                                        //}
                                    }
                                }
                            }
                            count.getAndAdd(1);
                        });
                        //  System.out.println(new Gson().toJson(candlestickListEx));
                        Date date = new Date();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
                        //    saveChart(s, simpleDateFormat.format(date), candlestickListEx);
                        CandlestickExtended lastCandle = candlestickListEx.get(candlestickListEx.size() - 1);

                        //    System.out.println("Symbol:"+symbols.getSymbol()+" RSI:"+lastCandle.getRsi()+" Open Time:"+simple.format(lastCandle.getOpenTime())+" Close Time:"+simple.format(lastCandle.getCloseTime())+" Open:"+lastCandle.getOpen()+" Close:"+lastCandle.getClose()+" High:"+lastCandle.getHigh()+" Low:"+lastCandle.getLow()+" Volume:"+lastCandle.getVolume());
                        if (tradeData.isOrderPlaced && !tradeData.isExited) {
                            if(tradeData.getEntryType().equals("SHORT")) {
                                tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                              //  tradeData.setEntryType("SHORT");
                            }else {
                                tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                                tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                                //   tradeData.setEntryType("BUY");
                            }
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(),tradeData.getEntryType()};
                                //  System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                        }
                    }
                }
            });

        }
    }

    public void saveChart(String name, String date, List<CandlestickExtended> historicalDataExtendedList) {
//https://github.com/Arction/lcjs-showcase-audio
        DateAxis domainAxis = new DateAxis("Date");
        OHLCDataset priceDataset = getPriceDataSet(name, date, historicalDataExtendedList);
        NumberAxis priceAxis = new NumberAxis("Price");
        CandlestickRenderer priceRenderer = new CandlestickRenderer();
        XYPlot pricePlot = new XYPlot(priceDataset, domainAxis, priceAxis, priceRenderer);
        priceRenderer.setSeriesPaint(0, Color.BLACK);
        priceRenderer.setDrawVolume(true);
        priceAxis.setAutoRangeIncludesZero(false);
        XYDataset otherDataSet = createRSIXYDataSet(name, date, historicalDataExtendedList);
        XYDataset otherDataSet1 = createOIXYDataSet(name, date, historicalDataExtendedList);
        NumberAxis rsiAxis = new NumberAxis("RSI");
        XYItemRenderer rsiRenderer = new XYLineAndShapeRenderer(true, false);
        rsiRenderer.setSeriesPaint(0, Color.blue);
        XYPlot rsiPlot = new XYPlot(otherDataSet, domainAxis, rsiAxis, rsiRenderer);
        NumberAxis OIAxis = new NumberAxis("OI");
        XYItemRenderer oiRenderer = new XYLineAndShapeRenderer(true, false);
        oiRenderer.setSeriesPaint(0, Color.blue);
        oiRenderer.setSeriesPaint(1, Color.red);
        XYPlot oiPlot = new XYPlot(otherDataSet1, domainAxis, OIAxis, oiRenderer);

        //create the plot
        CategoryPlot plot = new CategoryPlot();

//add the first dataset, and render as bar values
        CategoryDataset volumeDataSet = createVolumeDataSet(name, date, historicalDataExtendedList);
        XYDataset volumeMADataSet = createVolumeMADataSet(name, date, historicalDataExtendedList);
        CategoryItemRenderer renderer = new BarRenderer();
        plot.setDataset(0, volumeDataSet);
        plot.setRenderer(0, renderer);

//add the second dataset, render as lines

/*  CategoryItemRenderer renderer2 = new LineAndShapeRenderer();
        plot.setDataset(1, dataset);
        plot.setRenderer(1, renderer2);*/


        CombinedDomainXYPlot mainPlot = new CombinedDomainXYPlot(domainAxis);
        final CategoryAxis domainAxis1 = new CategoryAxis("Category");
        CombinedDomainCategoryPlot mainPlotC = new CombinedDomainCategoryPlot(domainAxis1);
        mainPlot.add(pricePlot);
        mainPlot.add(rsiPlot);
        mainPlot.add(oiPlot);
//       mainPlot.add(plot);

        JFreeChart chart = new JFreeChart(name, (Font) null, mainPlot, false);

        try {
            ChartUtilities.saveChartAsPNG(new File(trendPath + "/" + name + "_" + date + ".png"), chart, 1200, 600);
        } catch (Exception var22) {
            var22.printStackTrace();
        }
    }

    public XYDataset createVolumeMADataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        //if (cdate.equals(format.format(openDatetime))) {
                        s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.volumema50);
                        //   }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }

    public OHLCDataset getPriceDataSet(String name, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        List<OHLCDataItem> dataItems = new ArrayList();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {
                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        //    System.out.println(sdfformat.format(openDatetime));
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        //     if (cdate.equals(format.format(openDatetime))) {
                        double open = historicalDataExtended.getOpen().doubleValue();
                        double high = historicalDataExtended.getHigh().doubleValue();
                        double low = historicalDataExtended.getLow().doubleValue();
                        double close = historicalDataExtended.getClose().doubleValue();
                        double volume = historicalDataExtended.getVolume().doubleValue();
                        OHLCDataItem item = new OHLCDataItem(date, open, high, low, close, volume);
                        dataItems.add(item);
                        //    }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        Collections.reverse(dataItems);
        OHLCDataItem[] data = (OHLCDataItem[]) dataItems.toArray(new OHLCDataItem[dataItems.size()]);
        return new DefaultOHLCDataset(name, data);

    }

    public XYDataset createRSIXYDataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {

        TimeSeries s1 = new TimeSeries("RSI", Minute.class);


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        //   if (cdate.equals(format.format(openDatetime))) {
                        s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.rsi);

                        //   }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }

    public XYDataset createOIXYDataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        //  if (cdate.equals(format.format(openDatetime))) {
                        s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.volumema20);
                        s2.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.getVolume());
                        //  }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        dataset1.addSeries(s2);
        return dataset1;
    }

    public CategoryDataset createVolumeDataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "Volume";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        //   if (cdate.equals(format.format(openDatetime))) {
                        //      s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(),day,month,year), historicalDataExtended.volume);
                        dataset.addValue(historicalDataExtended.getVolume(), series1, date);
                        //  }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset;
    }


    public void summaryReport() throws Exception {
        CSVReader csvReaderReport = new CSVReader(new FileReader(trendPath + "/Crypto/trendRSI.csv"));
        int linecount = 0;
        int maxDD = 0;
        double maxDDAmount = 0;
        int currentDD = 0;
        double currentDDAmount = 0;
        String maxDDDate = "";
        String maxDDAmountDate = "";
        String[] line;
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        DateFormat simple1 = new SimpleDateFormat("dd-MMM-yyyy");
        Map<String, Double> data = new HashMap<>();
        while ((line = csvReaderReport.readNext()) != null) {
            if (linecount > 0) {

                Date d = simple.parse(line[5]);
                String da = simple1.format(d);
                if (data.containsKey(da)) {
                    Double value = data.get(da) + Double.valueOf(line[4]);
                    data.put(da, value);
                } else {
                    data.put(da, Double.valueOf(line[4]));
                }

            }
            linecount++;
        }
        data.entrySet().stream().forEach(stringDoubleEntry -> {
            System.out.println(stringDoubleEntry.getKey() + ":" + stringDoubleEntry.getValue());
        });
        String logMessage = MessageFormat.format("maxDD:{0}, maxDDAmount:{1}, maxDDDate:{2}, maxDDAmountDate:{3}", maxDD, maxDDAmount, maxDDDate, maxDDAmountDate);
        log.info(logMessage);
    }
}

