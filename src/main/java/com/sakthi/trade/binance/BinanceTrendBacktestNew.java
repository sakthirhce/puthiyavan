
package com.sakthi.trade.binance;

import com.binance.client.RequestOptions;
import com.binance.client.impl.RestApiRequestImpl;
import com.binance.client.impl.SyncRequestImpl;
import com.binance.client.model.enums.CandlestickInterval;
import com.binance.client.model.market.Candlestick;
import com.binance.client.model.market.ExchangeInformation;
import com.binance.client.model.market.Trade;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.binance.models.CandlestickExtended;
import com.sakthi.trade.domain.Historic;
import com.sakthi.trade.domain.TradeDataCrypto;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.repo.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Calendar.DAY_OF_MONTH;

@Component
@Slf4j
public class BinanceTrendBacktestNew {

    @Value("${filepath.trend}")
    String trendPath;
    @Value("${binance.sathiyaseelanrhce.v11.secret}")
    private String binanceSecretKey;
    @Value("${binance.sathiyaseelanrhce.v11.apikey}")
    private String binanceApiKey;


    @Autowired
    CryptoFuturesDataRepository cryptoFuturesDataRepository;

    @Autowired
    CryptoFuturesDayDataRepository cryptoFuturesDayDataRepository;
    @Autowired
    CryptoRepository cryptoRepository;


    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

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

    public void loadHistory(int day, String symbol) throws ExecutionException, InterruptedException {
        int i = day;
        //  int j=0;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        System.out.println(symbol);

        LocalDate startday = LocalDate.now(ZoneId.systemDefault()).minusDays(i);
        ZonedDateTime zdtStartDay = startday.atStartOfDay(ZoneId.systemDefault());
        LocalDate totoday = LocalDate.now(ZoneId.systemDefault());
        ZonedDateTime ztdt = totoday.atStartOfDay(ZoneId.systemDefault());
        String lastDate = cryptoFuturesDataRepository.findLastDate(symbol);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (lastDate == null) {
            List<Candlestick> candlestickListDAy = syncRequest.getCandlestick(symbol, CandlestickInterval.DAILY, zdtStartDay.toInstant().toEpochMilli(), ztdt.toInstant().toEpochMilli(), 500);
            if (candlestickListDAy.size() > 0) {
                Candlestick candlestickDay = candlestickListDAy.get(0);
                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date datest = new Date(candlestickDay.getOpenTime());
                LocalDateTime startistoryDate = LocalDateTime.parse(dateTimeFormat.format(datest), df);
                Long daysBetween = Duration.between(startistoryDate, LocalDateTime.now()).toDays();
                System.out.println("day differ:" + daysBetween);
                AtomicInteger ii = new AtomicInteger();
                executor.submit(() -> {
                    while (ii.get() <= daysBetween) {
                        final int j = ii.get();
                        LocalDate today = startistoryDate.plusDays(j).toLocalDate();
                        System.out.println(symbol + ":" + today);
                        ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
                        ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusMinutes(3);
                        ZonedDateTime zdtNextStop = today.plusDays(2).atStartOfDay(ZoneId.systemDefault());
                        System.out.println(zdtStart);


                        try {
                            Thread.sleep(1500);
                            List<Candlestick> candlestickList = syncRequest.getCandlestick(symbol, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                            if (candlestickList != null && candlestickList.size() > 0) {
                                List<CryptoFuturesDataEntity> stockDataEntities = new ArrayList<>();
                                candlestickList.stream().forEach(candlestick -> {
                                    CryptoFuturesDataEntity stockDataEntity = new CryptoFuturesDataEntity();
                                    String dataKey = UUID.randomUUID().toString();
                                    stockDataEntity.setDataKey(dataKey);
                                    Date date = new Date(candlestick.getOpenTime());
                                    try {
                                        stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                    stockDataEntity.setSymbol(symbol);
                                    stockDataEntity.setOpen(candlestick.getOpen());
                                    stockDataEntity.setHigh(candlestick.getHigh());
                                    stockDataEntity.setLow(candlestick.getLow());
                                    stockDataEntity.setClose(candlestick.getClose());
                                    stockDataEntity.setVolume(candlestick.getVolume().intValue());
                                    stockDataEntities.add(stockDataEntity);

                                });
                                cryptoFuturesDataRepository.saveAll(stockDataEntities);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        //   });
                        // futures.add(f);
                        ii.getAndAdd(1);
                    }
                    //   i = i - 1;
                });


       /* for (Future<Runnable> ff : futures)
        {
            ff.get();
        }
        service.shutdownNow();*/
            }
        } else {
            DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime localDateTime = LocalDateTime.parse(lastDate.substring(0, 19), df);
            ZonedDateTime zdtStart = localDateTime.atZone(ZoneId.systemDefault()).plusMinutes(2);
            //   executor.submit(() -> {
            while (zdtStart.compareTo(ZonedDateTime.now()) < 0) {
                ZonedDateTime zdtStop = zdtStart.plusDays(1).minusMinutes(3);

                try {
                    Thread.sleep(1500);
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(symbol, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<CryptoFuturesDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            CryptoFuturesDataEntity stockDataEntity = new CryptoFuturesDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.getOpenTime());
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setSymbol(symbol);
                            stockDataEntity.setOpen(candlestick.getOpen());
                            stockDataEntity.setHigh(candlestick.getHigh());
                            stockDataEntity.setLow(candlestick.getLow());
                            stockDataEntity.setClose(candlestick.getClose());
                            stockDataEntity.setVolume(candlestick.getVolume().intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        cryptoFuturesDataRepository.saveAll(stockDataEntities);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //   });
                // futures.add(f);
                zdtStart = zdtStart.plusDays(1);
            }
            //   i = i - 1;
            //   });
        }
    }

    public void loadDayHistory(int day, String symbol) throws ExecutionException, InterruptedException {
        int i = day;
        //  int j=0;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        System.out.println(symbol);

        LocalDate startday = LocalDate.now(ZoneId.systemDefault()).minusDays(i);
        ZonedDateTime zdtStartDay = startday.atStartOfDay(ZoneId.systemDefault());
        LocalDate totoday = LocalDate.now(ZoneId.systemDefault());
        ZonedDateTime ztdt = totoday.atStartOfDay(ZoneId.systemDefault());
        String lastDate = cryptoFuturesDayDataRepository.findLastDate(symbol);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (lastDate == null) {
            Thread.sleep(1500);
            List<Candlestick> candlestickListDAy = syncRequest.getCandlestick(symbol, CandlestickInterval.DAILY, zdtStartDay.toInstant().toEpochMilli(), ztdt.toInstant().toEpochMilli(), 500);
            if (candlestickListDAy.size() > 0) {
                Candlestick candlestickDay = candlestickListDAy.get(0);
                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date datest = new Date(candlestickDay.getOpenTime());
                LocalDateTime startistoryDate = LocalDateTime.parse(dateTimeFormat.format(datest), df);
                Long daysBetween = Duration.between(startistoryDate, LocalDateTime.now()).toDays();
                System.out.println("day differ:" + daysBetween);
                AtomicInteger ii = new AtomicInteger();
                executor.submit(() -> {
                    //    while (ii.get() <= daysBetween) {


                    try {

                        if (candlestickListDAy != null && candlestickListDAy.size() > 0) {
                            List<CryptoFuturesDayDataEntity> stockDataEntities = new ArrayList<>();
                            candlestickListDAy.stream().forEach(candlestick -> {
                                CryptoFuturesDayDataEntity stockDataEntity = new CryptoFuturesDayDataEntity();
                                String dataKey = UUID.randomUUID().toString();
                                stockDataEntity.setDataKey(dataKey);
                                Date date = new Date(candlestick.getOpenTime());
                                try {
                                    stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                                stockDataEntity.setSymbol(symbol);
                                stockDataEntity.setOpen(candlestick.getOpen());
                                stockDataEntity.setHigh(candlestick.getHigh());
                                stockDataEntity.setLow(candlestick.getLow());
                                stockDataEntity.setClose(candlestick.getClose());
                                stockDataEntity.setVolume(candlestick.getVolume().intValue());
                                stockDataEntities.add(stockDataEntity);

                            });
                            cryptoFuturesDayDataRepository.saveAll(stockDataEntities);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //   });
                    // futures.add(f);
                    ii.getAndAdd(1);
                    //      }
                    //   i = i - 1;
                });


       /* for (Future<Runnable> ff : futures)
        {
            ff.get();
        }
        service.shutdownNow();*/
            }
            // }
        } else {
            DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime localDateTime = LocalDateTime.parse(lastDate.substring(0, 19), df);
            ZonedDateTime zdtStart = localDateTime.atZone(ZoneId.systemDefault()).plusMinutes(2);
            //   executor.submit(() -> {

                ZonedDateTime zdtStop = zdtStart.plusDays(500);
            while (zdtStop.compareTo(ZonedDateTime.now()) > 0) {
                zdtStop = ZonedDateTime.now().minusDays(1).plusHours(6);
            }
                try {

                    try {
                        List<Candlestick> candlestickListDAy = syncRequest.getCandlestick(symbol, CandlestickInterval.DAILY, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                        if (candlestickListDAy.size() > 0) {

                            if (candlestickListDAy != null && candlestickListDAy.size() > 0) {
                                List<CryptoFuturesDayDataEntity> stockDataEntities = new ArrayList<>();
                                candlestickListDAy.stream().forEach(candlestick -> {
                                    CryptoFuturesDayDataEntity stockDataEntity = new CryptoFuturesDayDataEntity();
                                    String dataKey = UUID.randomUUID().toString();
                                    stockDataEntity.setDataKey(dataKey);
                                    Date date = new Date(candlestick.getOpenTime());
                                    try {
                                        stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                    stockDataEntity.setSymbol(symbol);
                                    stockDataEntity.setOpen(candlestick.getOpen());
                                    stockDataEntity.setHigh(candlestick.getHigh());
                                    stockDataEntity.setLow(candlestick.getLow());
                                    stockDataEntity.setClose(candlestick.getClose());
                                    stockDataEntity.setVolume(candlestick.getVolume().intValue());
                                    stockDataEntities.add(stockDataEntity);

                                });
                                cryptoFuturesDayDataRepository.saveAll(stockDataEntities);
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //   });
                    // futures.add(f);
                //    zdtStart = zdtStart.plusDays(1);
                } catch (Exception e) {

                    e.printStackTrace();
                }
          //  }
        }

            //   i = i - 1;
            //   });
        //}
    }
    public void binanceTrend(int n, String perGain) throws Exception {
        //   ExchangeInformation exchn = syncRequest.getExchangeInformation();
        Date date = new Date();
        SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMddHHmm");
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trendnewttwith" + fileFormat.format(date) + ".csv"));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateTimeFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<String> symbolList = new ArrayList<>();
        List<CryptoFuturesEntity> stockEntityList = cryptoRepository.findAll();
        AtomicDouble previous_total = new AtomicDouble();
        AtomicDouble pre_previous_total = new AtomicDouble();
        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusMinutes(3);
            ZonedDateTime prezdtStart = today.atStartOfDay(ZoneId.systemDefault()).minusDays(1);
            ZonedDateTime prezdtStop = today.atStartOfDay(ZoneId.systemDefault()).minusMinutes(3);
            ZonedDateTime zdtNextStop = today.plusDays(2).atStartOfDay(ZoneId.systemDefault());
            System.out.println(today);

            ZonedDateTime tradeStart = zdtStart.plusHours(2);
            AtomicDouble total = new AtomicDouble();
            n--;
            Map<String, TradeDataCrypto> tradeDataCryptoList = new HashMap<>();
            Map<String, TradeDataCrypto> tradeDataBuyCryptoList = new HashMap<>();
            final double pre_total = previous_total.get();
            final double pre_pre_total = pre_previous_total.get();

            while (tradeStart.isBefore(zdtStop)) {
                final ZonedDateTime tradeFinal = tradeStart;
                stockEntityList.forEach(s -> {
                    AtomicDouble percentageGain=new AtomicDouble();
                    if (s.getSymbol().contains("USDT")) {
                        List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(s.getSymbol(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                        //    double profitLossPre = cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size()-1).getClose().doubleValue()-cryptoFuturesDataEntityListPre.get(0).getOpen().doubleValue();
                        double profitLossPre = 0;
                        if(cryptoFuturesDataEntityListPre.size()>0) {
                            profitLossPre=MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                        }
                      //  if((profitLossPre > -20 && profitLossPre < -5) || (profitLossPre> 5 )) {
                            List<CryptoFuturesDataEntity> cryptoFuturesDataEntityList = cryptoFuturesDataRepository.findSymbol(s.getSymbol(), zdtStart.format(dateTimeFormater), tradeFinal.format(dateTimeFormater));
                            if (null != cryptoFuturesDataEntityList && cryptoFuturesDataEntityList.size() > 0) {

                                CryptoFuturesDataEntity historicalDataOpen = cryptoFuturesDataEntityList.get(0);
                                AtomicDouble low = new AtomicDouble();
                                low.getAndSet(historicalDataOpen.getLow().doubleValue());
                                CryptoFuturesDataEntity quoteK = cryptoFuturesDataEntityList.get(cryptoFuturesDataEntityList.size() - 1);

                                if (low.get() > quoteK.getLow().doubleValue()) {
                                    low.getAndSet(quoteK.getLow().doubleValue());
                                }
                                Date candleDate = quoteK.getTradeTime();

                                BigDecimal percentGain = MathUtils.percentageMove(historicalDataOpen.getOpen(), quoteK.getClose());
                                try {
                                    TradeDataCrypto tradeData = tradeDataCryptoList.get(s.getSymbol());
                                    if (tradeData == null) {
                                        tradeData = new TradeDataCrypto();
                                    }

                                    if (percentGain.doubleValue() > 5 && (profitLossPre > 5 ) && candleDate.after(dateTimeFormat.parse(today + " 02:00:00"))
                                            && candleDate.before(dateTimeFormat.parse(today + " 10:00:00"))) {
                                        percentageGain.getAndSet(percentGain.doubleValue());
                                         tradeData = tradeDataBuyCryptoList.get(s.getSymbol());
                                        if (tradeData == null) {
                                            tradeData = new TradeDataCrypto();
                                        }
                                    int stoplosshit = stopLossHitCount(tradeDataBuyCryptoList);
                                          if (!tradeData.isOrderPlaced && tradeData.getStockName() ==null && stoplosshit <= 4 && tradeDataBuyCryptoList.size() <= 20) {
                                                   /* if(profitLossPre <10) {
                                                        tradeData.isOrderPlaced = true;
                                                        tradeData.setStockName(s.getSymbol());
                                                        double qty = 100 / quoteK.getClose().doubleValue();
                                                        tradeData.setSellPrice(quoteK.getClose().doubleValue());
                                                        tradeData.setQty(qty);
                                                        tradeData.setSellTime(simple.format(quoteK.getTradeTime()));
                                                        double slPoints = (tradeData.getSellPrice() * 5) / 100;
                                                        double slPrice = tradeData.getSellPrice() + slPoints;
                                                        tradeData.setSlPrice(slPrice);
                                                        tradeData.setEntryType("Short");
                                                        tradeData.setSlTrialPoints(slPoints);
                                                        tradeDataBuyCryptoList.put(s.getSymbol(), tradeData);
                                                    }*/
                                                  /* else  if(profitLossPre >10 && profitLossPre <20) {
                                                        tradeData.isOrderPlaced = true;
                                                        tradeData.setStockName(s.getSymbol());
                                                        double qty = 100 / quoteK.getClose().doubleValue();
                                                        tradeData.setBuyPrice(quoteK.getClose().doubleValue());
                                                        tradeData.setQty(qty);
                                                        tradeData.setBuyTime(simple.format(quoteK.getTradeTime()));
                                                        double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                                        double slPrice = tradeData.getBuyPrice() - slPoints;
                                                        tradeData.setSlPrice(slPrice);
                                                        tradeData.setEntryType("Long");
                                                        tradeData.setSlTrialPoints(slPoints);
                                                        tradeDataBuyCryptoList.put(s.getSymbol(), tradeData);
                                                    }*/


                                            }
                                    }

                                    if (percentGain.doubleValue() <= -5 &&  profitLossPre < 0 && candleDate.after(dateTimeFormat.parse(today + " 02:00:00"))
                                            && candleDate.before(dateTimeFormat.parse(today + " 10:00:00"))) {
                                        percentageGain.getAndSet(percentGain.doubleValue());
                                        int stoplosshit = stopLossHitCount(tradeDataCryptoList);
                                        if (!tradeData.isOrderPlaced && tradeData.getStockName() == null && stoplosshit <= 4 && tradeDataCryptoList.size() <= 50) {
                                            System.out.println("condition satisfied:" + s.getSymbol());
                                            //    System.out.println(s.getSymbol()+":"+percentGain.doubleValue()+": open candle time:"+dateTimeFormat.format(historicalDataOpen.getTradeTime())+": close candle time:"+dateTimeFormat.format(quoteK.getTradeTime())+": open candle price:"+historicalDataOpen.getClose()+": close candle price:"+quoteK.getClose());
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setStockName(s.getSymbol());
                                            double qty = 0;
                                         qty = 400 / quoteK.getClose().doubleValue();
                                                tradeData.setMarginTotal(400);

                                            tradeData.setBuyPrice(quoteK.getClose().doubleValue());
                                            tradeData.setQty(qty);
                                            tradeData.setBuyTime(simple.format(quoteK.getTradeTime()));
                                            double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                            double slPrice = tradeData.getBuyPrice() - slPoints;
                                            tradeData.setSlPrice(slPrice);
                                            tradeData.setEntryType("Long");
                                            tradeData.setSlTrialPoints(slPoints);
                                            tradeDataCryptoList.put(s.getSymbol(), tradeData);
                                        }

                                    }

                                    if (tradeData.isOrderPlaced ) {

                                        if (!tradeData.isExited) {

                                            double percentGainAfterBuy = MathUtils.percentageMove(new BigDecimal(tradeData.getBuyPrice()), quoteK.getClose()).doubleValue();
                                           /* if(percentGainAfterBuy>10 && tradeData.getPyramidCount()<1){
                                                tradeData.setPyramidCount(1);
                                                double newBuyPrice=(tradeData.getBuyPrice()+quoteK.getClose().doubleValue())/2;
                                                tradeData.setQty(tradeData.getQty()*2);
                                                tradeData.setMarginTotal(400);
                                                tradeData.setBuyPrice(newBuyPrice);
                                                tradeData.setPyramidTime(simple.format(quoteK.getTradeTime()));

                                            }
*/
                                            if ("Long".equals(tradeData.getEntryType())) {
                                                double loss =(quoteK.getClose().doubleValue()-tradeData.getBuyPrice())*tradeData.getQty();

                                                if ( ((loss<-40 && tradeData.getPyramidCount()==1) || (loss<-20 && tradeData.getPyramidCount()==0 ))&& date.after(simple.parse(tradeData.getBuyTime()))) {
                                                    tradeData.isExited = true;
                                                    tradeData.isSLHit = true;
                                                    tradeData.setSellPrice(quoteK.getClose().doubleValue());
                                                    tradeData.setSellTime(simple.format(quoteK.getTradeTime()));
                                                    double perceGain = MathUtils.percentageMove(historicalDataOpen.getOpen(), new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                                                    double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                                    total.getAndAdd(1);
                                                    String tt = null;
                                                    if (pre_total >= 10 || pre_pre_total >= 10) {
                                                        tt = "loss_next";
                                                    }

                                                    if (cryptoFuturesDataEntityListPre.size() > 0) {
                                                        profitLossPre = MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                                                    }
                                                    String MeanCondition = "No";
                                                    if (profitLossPre < 0 && profitLossPre > -25) {
                                                        MeanCondition = "Yes";
                                                    }
                                                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(perceGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount())};
                                                    csvWriter.writeNext(data);
                                                    try {
                                                        csvWriter.flush();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }/* else if ("Short".equals(tradeData.getEntryType())) {
                                                if (tradeData.getSlPrice() < quoteK.getClose().doubleValue() && date.after(simple.parse(tradeData.getSellTime()))) {
                                                    tradeData.isExited = true;
                                                    tradeData.isSLHit = true;
                                                    tradeData.setBuyPrice(quoteK.getClose().doubleValue());
                                                    tradeData.setBuyTime(simple.format(quoteK.getTradeTime()));
                                                    double perceGain = MathUtils.percentageMove(historicalDataOpen.getOpen(), new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                                                    double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                                    total.getAndAdd(1);
                                                    String tt = null;
                                                    if (pre_total >= 10 || pre_pre_total >= 10) {
                                                        tt = "loss_next";
                                                    }

                                                    if (cryptoFuturesDataEntityListPre.size() > 0) {
                                                        profitLossPre = MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                                                    }
                                                    String MeanCondition = "No";
                                                    if (profitLossPre < 0 && profitLossPre > -25) {
                                                        MeanCondition = "Yes";
                                                    }
                                                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(perceGain)};
                                                    csvWriter.writeNext(data);
                                                    try {
                                                        csvWriter.flush();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                            }*//*else {
                                                        double diff = quoteK.getClose().doubleValue() - tradeData.getBuyPrice();
                                                        if (diff > tradeData.getSlTrialPoints()) {
                                                            double mod = (diff / tradeData.getSlTrialPoints());
                                                            double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                                            if (newSL > tradeData.getSlPrice()) {
                                                                tradeData.setSlPrice(newSL);
                                                            }
                                                        }
                                                    }*/
                                        }
                                    }



                            } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                                //    });


                            }
                        }

                  //  }
                });
                tradeStart = tradeStart.plusMinutes(5);
            }

            tradeDataCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                TradeDataCrypto tradeData = tradeDataCryptoMap.getValue();
                if (!tradeData.isExited) {
                    tradeData.isExited = true;

                    List<CryptoFuturesDataEntity> candlestickExtendeds = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), zdtStart.format(dateTimeFormater), zdtStop.format(dateTimeFormater));
                    CryptoFuturesDataEntity candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                    CryptoFuturesDataEntity openCandle = candlestickExtendeds.get(0);
                    double percentGain=MathUtils.percentageMove(openCandle.getOpen(),new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                    tradeData.setSellTime(simple.format(candlestickExtended.getTradeTime()));
                    tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                    double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                    String tt=null;
                    if (pre_total >=10 || pre_pre_total>=10) {
                        tt = "loss_next";
                    }

                    List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                //    double profitLossPre = cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size()-1).getClose().doubleValue()-cryptoFuturesDataEntityListPre.get(0).getOpen().doubleValue();
                    double profitLossPre = 0;
                    if(cryptoFuturesDataEntityListPre.size()>0) {
                        profitLossPre=MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                    }
                    String MeanCondition="No";
                    if(profitLossPre<0 && profitLossPre>-25) {
                        MeanCondition="Yes";
                    }
                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(percentGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount())};
                    csvWriter.writeNext(data);
                    try {
                        csvWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            tradeDataBuyCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                TradeDataCrypto tradeData = tradeDataCryptoMap.getValue();
                if (!tradeData.isExited) {
                    if (!"Long".equals(tradeData.getEntryType())) {
                        try {
                        tradeData.isExited = true;
                        List<CryptoFuturesDataEntity> candlestickExtendeds = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), zdtStart.format(dateTimeFormater), zdtStop.format(dateTimeFormater));
                        CryptoFuturesDataEntity openCandle = candlestickExtendeds.get(0);
                        double percentGain = MathUtils.percentageMove(openCandle.getOpen(), new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                        CryptoFuturesDataEntity candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                        tradeData.setBuyTime(simple.format(candlestickExtended.getTradeTime()));
                        tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                        double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                        List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                        double profitLossPre = 0;
                        if (cryptoFuturesDataEntityListPre.size() > 0) {
                            profitLossPre = MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                        }
                        String MeanCondition = "No";
                        if (profitLossPre < 0 && profitLossPre > -25) {
                            MeanCondition = "Yes";
                        }
                            String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), "", String.valueOf(profitLossPre), MeanCondition, String.valueOf(percentGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount())};
                            csvWriter.writeNext(data);

                            csvWriter.flush();
                        } catch (Exception e) {
                            System.out.println(tradeData.getStockName()+":"+e.getMessage());
                        }
                    }else
                    {

                        tradeData.isExited = true;
                        List<CryptoFuturesDataEntity> candlestickExtendeds = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), zdtStart.format(dateTimeFormater), zdtStop.format(dateTimeFormater));
                        CryptoFuturesDataEntity candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                        CryptoFuturesDataEntity openCandle = candlestickExtendeds.get(0);
                        double percentGain=MathUtils.percentageMove(openCandle.getOpen(),new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                        tradeData.setSellTime(simple.format(candlestickExtended.getTradeTime()));
                        tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                        double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                        String tt=null;
                        if (pre_total >=10 || pre_pre_total>=10) {
                            tt = "loss_next";
                        }

                        List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                        //    double profitLossPre = cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size()-1).getClose().doubleValue()-cryptoFuturesDataEntityListPre.get(0).getOpen().doubleValue();
                        double profitLossPre = 0;
                        if(cryptoFuturesDataEntityListPre.size()>0) {
                            profitLossPre=MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                        }
                        String MeanCondition="No";
                        if(profitLossPre<0 && profitLossPre>-25) {
                            MeanCondition="Yes";
                        }
                        String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(percentGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount())};
                        csvWriter.writeNext(data);
                        try {
                            csvWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            pre_previous_total =previous_total;
            previous_total = total;

        }
    }

    public Historic historicClose(AtomicInteger higherDayDiff,List<CryptoFuturesDayDataEntity> cryptoFuturesDayDataEntityList){
        Historic historic=new Historic();
        cryptoFuturesDayDataEntityList.stream().forEach(cryptoFuturesDayDataEntity -> {
            if(historic.closeHigh < cryptoFuturesDayDataEntity.getClose().doubleValue()){
                higherDayDiff.getAndSet(0);
                historic.closeHigh= cryptoFuturesDayDataEntity.getClose().doubleValue();
            }else {
                higherDayDiff.getAndAdd(1);
            }
            if(historic.closeLow > cryptoFuturesDayDataEntity.getClose().doubleValue()){

                historic.closeLow= cryptoFuturesDayDataEntity.getClose().doubleValue();
            }

        });
        historic.day=higherDayDiff.get();
        return historic;
    }
    public Historic historicRecentClose(Historic historic,List<CryptoFuturesDayDataEntity> cryptoFuturesDayDataEntityList){
        cryptoFuturesDayDataEntityList.stream().forEach(cryptoFuturesDayDataEntity -> {
            if(historic.recentClose < cryptoFuturesDayDataEntity.getClose().doubleValue()){

                historic.recentClose= cryptoFuturesDayDataEntity.getClose().doubleValue();
            }

        });
        return historic;
    }
    public void binanceBullTrend(int n, String perGain) throws Exception {
        //   ExchangeInformation exchn = syncRequest.getExchangeInformation();
        Date date = new Date();
        SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMddHHmm");
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trendnewttwith" + fileFormat.format(date) + ".csv"));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateTimeFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<String> symbolList = new ArrayList<>();
        List<CryptoFuturesEntity> stockEntityList = cryptoRepository.findAll();
        AtomicDouble previous_total = new AtomicDouble();
        AtomicDouble pre_previous_total = new AtomicDouble();
        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusMinutes(3);
            ZonedDateTime prezdtStart = today.atStartOfDay(ZoneId.systemDefault()).minusDays(1);
            ZonedDateTime prezdtStop = today.atStartOfDay(ZoneId.systemDefault()).minusMinutes(3);
            ZonedDateTime zdtNextStop = today.plusDays(2).atStartOfDay(ZoneId.systemDefault());
            System.out.println(today);

            ZonedDateTime tradeStart = zdtStart.plusHours(2);
            AtomicDouble total = new AtomicDouble();
            n--;
            Map<String, TradeDataCrypto> tradeDataCryptoList = new HashMap<>();
            Map<String, TradeDataCrypto> tradeDataBuyCryptoList = new HashMap<>();
            final double pre_total = previous_total.get();
            final double pre_pre_total = pre_previous_total.get();
            Map<String, Historic> historicList = new HashMap<>();   AtomicInteger higherDayDiff=new AtomicInteger();
            stockEntityList.forEach(s -> {
                System.out.println(s.getSymbol()+":");
                StopWatch stopWatch=new StopWatch();
                stopWatch.start();
                List<CryptoFuturesDayDataEntity>  cryptoFuturesDayDataEntityList= cryptoFuturesDayDataRepository.findSymbolData(s.getSymbol(),today);

                Historic historic=historicClose(higherDayDiff,cryptoFuturesDayDataEntityList);
                if(cryptoFuturesDayDataEntityList.size()>10) {
                    List<CryptoFuturesDayDataEntity> cryptoFuturesDayDataEntitySubList = cryptoFuturesDayDataEntityList.subList(cryptoFuturesDayDataEntityList.size() - 10, cryptoFuturesDayDataEntityList.size());
                    historicRecentClose(historic, cryptoFuturesDayDataEntitySubList);
                }
                stopWatch.stop();
                System.out.println(s.getSymbol()+":"+historic+":"+stopWatch.getTotalTimeSeconds());
                historicList.put(s.getSymbol(),historic);
            });
            while (tradeStart.isBefore(zdtStop)) {
                final ZonedDateTime tradeFinal = tradeStart;
                stockEntityList.forEach(s -> {


                    AtomicDouble percentageGain=new AtomicDouble();
                    if (s.getSymbol().contains("USDT")) {
                        List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(s.getSymbol(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                           //    double profitLossPre = cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size()-1).getClose().doubleValue()-cryptoFuturesDataEntityListPre.get(0).getOpen().doubleValue();
                        double profitLossPre = 0;
                        if(cryptoFuturesDataEntityListPre.size()>0) {
                            profitLossPre=MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                        }
                        //  if((profitLossPre > -20 && profitLossPre < -5) || (profitLossPre> 5 )) {
                            ZonedDateTime zonedDateTime=zdtStart.minusDays(5);
                           List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListDB = cryptoFuturesDataRepository.findSymbol(s.getSymbol(), zonedDateTime.format(dateTimeFormater), tradeFinal.format(dateTimeFormater));
                        List<CryptoFuturesDataExtended> cryptoFuturesDataEntityList=new ArrayList<>();
                        cryptoFuturesDataEntityListDB.forEach(cryptoFuturesDataEntity -> {
                            CryptoFuturesDataExtended cryptoFuturesDataExtended= new CryptoFuturesDataExtended();
                            cryptoFuturesDataExtended.setSymbol(cryptoFuturesDataEntity.getSymbol());
                            cryptoFuturesDataExtended.setTradeTime(cryptoFuturesDataEntity.getTradeTime());
                            cryptoFuturesDataExtended.setVolume(cryptoFuturesDataEntity.getVolume());
                            cryptoFuturesDataExtended.setClose(cryptoFuturesDataEntity.getClose());
                            cryptoFuturesDataExtended.setOpen(cryptoFuturesDataEntity.getOpen());
                            cryptoFuturesDataExtended.setHigh(cryptoFuturesDataEntity.getHigh());
                            cryptoFuturesDataExtended.setLow(cryptoFuturesDataEntity.getLow());
                            cryptoFuturesDataEntityList.add(cryptoFuturesDataExtended);
                        });
                           MathUtils.SMA sma20 = new MathUtils.SMA(20);
                        cryptoFuturesDataEntityList.forEach(cryptoFuturesDataEntity -> {
                           double sma20Value= sma20.compute(cryptoFuturesDataEntity.getVolume());
                           cryptoFuturesDataEntity.setVolumema20(sma20Value);

                        });

                        if (null != cryptoFuturesDataEntityList && cryptoFuturesDataEntityList.size() > 0) {

                            CryptoFuturesDataExtended historicalDataOpen = cryptoFuturesDataEntityList.get(0);
                            AtomicDouble low = new AtomicDouble();
                            low.getAndSet(historicalDataOpen.getLow().doubleValue());
                            CryptoFuturesDataExtended quoteK = cryptoFuturesDataEntityList.get(cryptoFuturesDataEntityList.size() - 1);

                            if (low.get() > quoteK.getLow().doubleValue()) {
                                low.getAndSet(quoteK.getLow().doubleValue());
                            }
                            Date candleDate = quoteK.getTradeTime();

                            BigDecimal percentGain = MathUtils.percentageMove(historicalDataOpen.getOpen(), quoteK.getClose());
                            try {
                                TradeDataCrypto tradeData = tradeDataCryptoList.get(s.getSymbol());
                                if (tradeData == null) {
                                    tradeData = new TradeDataCrypto();
                                }
                                Historic historic=historicList.get(s.getSymbol());

                                if ( historic.closeHigh<quoteK.getClose().doubleValue()/*percentGain.doubleValue() <= -5 &&  profitLossPre < 0 && candleDate.after(dateTimeFormat.parse(today + " 02:00:00"))
                                        && candleDate.before(dateTimeFormat.parse(today + " 10:00:00"))*/) {
                                    percentageGain.getAndSet(percentGain.doubleValue());
                                    int stoplosshit = stopLossHitCount(tradeDataCryptoList);
                                    if (!tradeData.isOrderPlaced && tradeData.getStockName() == null && stoplosshit <= 4 && tradeDataCryptoList.size() <= 50) {
                                        System.out.println("condition satisfied:" + s.getSymbol());
                                        //    System.out.println(s.getSymbol()+":"+percentGain.doubleValue()+": open candle time:"+dateTimeFormat.format(historicalDataOpen.getTradeTime())+": close candle time:"+dateTimeFormat.format(quoteK.getTradeTime())+": open candle price:"+historicalDataOpen.getClose()+": close candle price:"+quoteK.getClose());
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setStockName(s.getSymbol());
                                        double qty = 0;
                                        qty = 400 / quoteK.getClose().doubleValue();
                                        tradeData.setMarginTotal(400);

                                        tradeData.setBuyPrice(quoteK.getClose().doubleValue());
                                        tradeData.setQty(qty);
                                        tradeData.setBuyTime(simple.format(quoteK.getTradeTime()));
                                        double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                        double slPrice = tradeData.getBuyPrice() - slPoints;
                                        tradeData.setSlPrice(slPrice);
                                        tradeData.setEntryType("Long");
                                        tradeData.setSlTrialPoints(slPoints);
                                        tradeDataCryptoList.put(s.getSymbol(), tradeData);
                                    }

                                }

                                if (tradeData.isOrderPlaced ) {

                                    if (!tradeData.isExited) {

                                        double percentGainAfterBuy = MathUtils.percentageMove(new BigDecimal(tradeData.getBuyPrice()), quoteK.getClose()).doubleValue();
                                        if(percentGainAfterBuy>10 && tradeData.getPyramidCount()<1){
                                            tradeData.setPyramidCount(1);
                                            double newBuyPrice=(tradeData.getBuyPrice()+quoteK.getClose().doubleValue())/2;
                                            tradeData.setQty(tradeData.getQty()*2);
                                            tradeData.setMarginTotal(400);
                                            tradeData.setBuyPrice(newBuyPrice);
                                            tradeData.setPyramidTime(simple.format(quoteK.getTradeTime()));

                                        }

                                        if ("Long".equals(tradeData.getEntryType())) {
                                            double loss =(quoteK.getClose().doubleValue()-tradeData.getBuyPrice())*tradeData.getQty();

                                            if ( ((loss<-40 && tradeData.getPyramidCount()==1) || (loss<-20 && tradeData.getPyramidCount()==0 ))&& date.after(simple.parse(tradeData.getBuyTime()))) {
                                                tradeData.isExited = true;
                                                tradeData.isSLHit = true;
                                                tradeData.setSellPrice(quoteK.getClose().doubleValue());
                                                tradeData.setSellTime(simple.format(quoteK.getTradeTime()));
                                                double perceGain = MathUtils.percentageMove(historicalDataOpen.getOpen(), new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                                total.getAndAdd(1);
                                                String tt = null;
                                                if (pre_total >= 10 || pre_pre_total >= 10) {
                                                    tt = "loss_next";
                                                }

                                                if (cryptoFuturesDataEntityListPre.size() > 0) {
                                                    profitLossPre = MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                                                }
                                                String MeanCondition = "No";
                                                if (profitLossPre < 0 && profitLossPre > -25) {
                                                    MeanCondition = "Yes";
                                                }
                                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(perceGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount()),String.valueOf(historic.closeHigh),String.valueOf(historic.day)};
                                                csvWriter.writeNext(data);
                                                try {
                                                    csvWriter.flush();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }/* else if ("Short".equals(tradeData.getEntryType())) {
                                                if (tradeData.getSlPrice() < quoteK.getClose().doubleValue() && date.after(simple.parse(tradeData.getSellTime()))) {
                                                    tradeData.isExited = true;
                                                    tradeData.isSLHit = true;
                                                    tradeData.setBuyPrice(quoteK.getClose().doubleValue());
                                                    tradeData.setBuyTime(simple.format(quoteK.getTradeTime()));
                                                    double perceGain = MathUtils.percentageMove(historicalDataOpen.getOpen(), new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                                                    double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                                    total.getAndAdd(1);
                                                    String tt = null;
                                                    if (pre_total >= 10 || pre_pre_total >= 10) {
                                                        tt = "loss_next";
                                                    }

                                                    if (cryptoFuturesDataEntityListPre.size() > 0) {
                                                        profitLossPre = MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                                                    }
                                                    String MeanCondition = "No";
                                                    if (profitLossPre < 0 && profitLossPre > -25) {
                                                        MeanCondition = "Yes";
                                                    }
                                                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(perceGain)};
                                                    csvWriter.writeNext(data);
                                                    try {
                                                        csvWriter.flush();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                            }*//*else {
                                                        double diff = quoteK.getClose().doubleValue() - tradeData.getBuyPrice();
                                                        if (diff > tradeData.getSlTrialPoints()) {
                                                            double mod = (diff / tradeData.getSlTrialPoints());
                                                            double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                                            if (newSL > tradeData.getSlPrice()) {
                                                                tradeData.setSlPrice(newSL);
                                                            }
                                                        }
                                                    }*/
                                    }
                                }



                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            //    });


                        }
                    }

                    //  }
                });
                tradeStart = tradeStart.plusMinutes(5);
            }

            tradeDataCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                TradeDataCrypto tradeData = tradeDataCryptoMap.getValue();
                if (!tradeData.isExited) {
                    tradeData.isExited = true;

                    List<CryptoFuturesDataEntity> candlestickExtendeds = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), zdtStart.format(dateTimeFormater), zdtStop.format(dateTimeFormater));
                    CryptoFuturesDataEntity candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                    CryptoFuturesDataEntity openCandle = candlestickExtendeds.get(0);
                    double percentGain=MathUtils.percentageMove(openCandle.getOpen(),new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                    tradeData.setSellTime(simple.format(candlestickExtended.getTradeTime()));
                    tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                    double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                    String tt=null;
                    if (pre_total >=10 || pre_pre_total>=10) {
                        tt = "loss_next";
                    }

                    List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                    //    double profitLossPre = cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size()-1).getClose().doubleValue()-cryptoFuturesDataEntityListPre.get(0).getOpen().doubleValue();
                    double profitLossPre = 0;
                    if(cryptoFuturesDataEntityListPre.size()>0) {
                        profitLossPre=MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                    }
                    String MeanCondition="No";
                    if(profitLossPre<0 && profitLossPre>-25) {
                        MeanCondition="Yes";
                    }
                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(percentGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount()),String.valueOf(historicList.get(tradeData.getStockName()).closeHigh),String.valueOf(historicList.get(tradeData.getStockName()).day)};
                    csvWriter.writeNext(data);
                    try {
                        csvWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            tradeDataBuyCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                TradeDataCrypto tradeData = tradeDataCryptoMap.getValue();
                if (!tradeData.isExited) {
                    if (!"Long".equals(tradeData.getEntryType())) {
                        try {
                            tradeData.isExited = true;
                            List<CryptoFuturesDataEntity> candlestickExtendeds = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), zdtStart.format(dateTimeFormater), zdtStop.format(dateTimeFormater));
                            CryptoFuturesDataEntity openCandle = candlestickExtendeds.get(0);
                            double percentGain = MathUtils.percentageMove(openCandle.getOpen(), new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                            CryptoFuturesDataEntity candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                            tradeData.setBuyTime(simple.format(candlestickExtended.getTradeTime()));
                            tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                            double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                            List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                            double profitLossPre = 0;
                            if (cryptoFuturesDataEntityListPre.size() > 0) {
                                profitLossPre = MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                            }
                            String MeanCondition = "No";
                            if (profitLossPre < 0 && profitLossPre > -25) {
                                MeanCondition = "Yes";
                            }
                            String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), "", String.valueOf(profitLossPre), MeanCondition, String.valueOf(percentGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount())};
                            csvWriter.writeNext(data);

                            csvWriter.flush();
                        } catch (Exception e) {
                            System.out.println(tradeData.getStockName()+":"+e.getMessage());
                        }
                    }else
                    {

                        tradeData.isExited = true;
                        List<CryptoFuturesDataEntity> candlestickExtendeds = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), zdtStart.format(dateTimeFormater), zdtStop.format(dateTimeFormater));
                        CryptoFuturesDataEntity candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                        CryptoFuturesDataEntity openCandle = candlestickExtendeds.get(0);
                        double percentGain=MathUtils.percentageMove(openCandle.getOpen(),new BigDecimal(tradeData.getBuyPrice())).doubleValue();
                        tradeData.setSellTime(simple.format(candlestickExtended.getTradeTime()));
                        tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                        double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                        String tt=null;
                        if (pre_total >=10 || pre_pre_total>=10) {
                            tt = "loss_next";
                        }

                        List<CryptoFuturesDataEntity> cryptoFuturesDataEntityListPre = cryptoFuturesDataRepository.findSymbol(tradeData.getStockName(), prezdtStart.format(dateTimeFormater), prezdtStop.format(dateTimeFormater));
                        //    double profitLossPre = cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size()-1).getClose().doubleValue()-cryptoFuturesDataEntityListPre.get(0).getOpen().doubleValue();
                        double profitLossPre = 0;
                        if(cryptoFuturesDataEntityListPre.size()>0) {
                            profitLossPre=MathUtils.percentageMove(cryptoFuturesDataEntityListPre.get(0).getOpen(), cryptoFuturesDataEntityListPre.get(cryptoFuturesDataEntityListPre.size() - 1).getClose()).doubleValue();
                        }
                        String MeanCondition="No";
                        if(profitLossPre<0 && profitLossPre>-25) {
                            MeanCondition="Yes";
                        }
                        String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType(), tt, String.valueOf(profitLossPre), MeanCondition, String.valueOf(percentGain),String.valueOf(tradeData.getPyramidTime()),String.valueOf(tradeData.getPyramidCount())};
                        csvWriter.writeNext(data);
                        try {
                            csvWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            pre_previous_total =previous_total;
            previous_total = total;

        }
    }
    public void binanceTrendTop() throws Exception {
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trend_new_top.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateTimeFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<String> symbolList = new ArrayList<>();
        exchn.getSymbols().stream().forEach(symbols -> {
            symbolList.add(symbols.getSymbol());
        });
        int n = 30;
        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            System.out.println(today);

            ZonedDateTime tradeStart = zdtStart.plusHours(2);


            n--;
            Map<String, List<CandlestickExtended>> historic = new HashMap<>();
            symbolList.stream().forEach(s -> {
                List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                if (s.contains("USDT")) {
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    if (candlestickList.size() > 0) {
                        MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                        MathUtils.RSI rsi = new MathUtils.RSI(6);

                        candlestickList.stream().forEach(candlestick -> {
                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                            candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                            candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                            candlestickListEx.add(candlestickExtended);

                        });
                        historic.put(s, candlestickListEx);

                    }
                }
            });
            Map<String, Double> positiveList1130 = new HashMap<>();
            Map<String, Double> negList1130 = new HashMap<>();
            Map<String, Double> positiveList1200 = new HashMap<>();
            Map<String, Double> negList1200 = new HashMap<>();
            Map<String, TradeDataCrypto> tradeDataCryptoList = new HashMap<>();
            while (tradeStart.compareTo(zdtStop) <= 0) {

                Map<String, Double> positiveList = new HashMap<>();
                Map<String, Double> negList = new HashMap<>();
                System.out.println(tradeStart);
                final ZonedDateTime tradeStartFinal = tradeStart;
                AtomicDouble low = new AtomicDouble();
                historic.entrySet().forEach(historicData -> {
                            AtomicLong cumulatedVolume = new AtomicLong();
                            List<CandlestickExtended> candlestickExtendedEx = historicData.getValue();
                            CandlestickExtended opencandlestickExtendedEx = candlestickExtendedEx.get(0);
                            low.getAndSet(opencandlestickExtendedEx.getLow().doubleValue());
                            Calendar openCalender = Calendar.getInstance();
                            openCalender.setTimeInMillis(opencandlestickExtendedEx.getCloseTime());
                            candlestickExtendedEx.stream().forEach(candlestickExtended -> {
                                cumulatedVolume.getAndAdd(candlestickExtended.getVolume().longValue());
                                Calendar calendar = Calendar.getInstance();
                                if (low.get() > candlestickExtended.getLow().doubleValue()) {
                                    low.getAndSet(candlestickExtended.getLow().doubleValue());
                                }
                                calendar.setTimeInMillis(candlestickExtended.getOpenTime());
                                try {
                                    if (calendar.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                                            && calendar.getTime().before(dateTimeFormat.parse(today + " 10:00:00")) && dateTimeFormat.format(calendar.getTime()).equals(tradeStartFinal.format(dateTimeFormater))) {
                                        double percentGain = MathUtils.percentageMove(opencandlestickExtendedEx.getOpen(), candlestickExtended.getClose()).doubleValue();
                                        try {


                                            if (percentGain > 5) {
                                                positiveList.put(historicData.getKey(), candlestickExtended.getClose().doubleValue() * cumulatedVolume.get());
                                            }
                                            if (percentGain < 0) {
                                                negList.put(historicData.getKey(), candlestickExtended.getClose().doubleValue() * cumulatedVolume.get());
                                            }

                                           /* if(percentGain <=-5 && calendar.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                                                    && calendar.getTime().before(dateTimeFormat.parse(today + " 10:00:00"))){
                                                int stoplosshit=stopLossHitCount(tradeDataCryptoList);
                                                if (!tradeData.isOrderPlaced && tradeData.getStockName() == null && stoplosshit<=5) {

                                                    tradeData.isOrderPlaced = true;
                                                    tradeData.setStockName(historicData.getKey());
                                                    double qty = 100 / candlestickExtended.getClose().doubleValue();
                                                    tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                                    tradeData.setQty(qty);
                                                    tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                                    double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                                    double slPrice = tradeData.getBuyPrice() - slPoints;
                                                    tradeData.setSlPrice(slPrice);
                                                    tradeData.setEntryType("INVERTED-BUY");
                                                    tradeData.setSlTrialPoints(slPoints);
                                                    tradeDataCryptoList.put(historicData.getKey(),tradeData);
                                                }

                                            }*/


                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            });

                        }
                );

                int topNumber = 5;
                List<Map.Entry<String, Double>> sortedPositiveMap = sortByValue(positiveList);
                List<Map.Entry<String, Double>> sortedNegativeMap = sortByValue(negList);/*
            List<Map.Entry<String, Double>> sortedPositiveMap1130 = sortByValue(positiveList1130);
            List<Map.Entry<String, Double>> sortedNegativeMap1130 = sortByValue(negList1130);
            List<Map.Entry<String, Double>> sortedPositiveMap1200 = sortByValue(positiveList1200);
            List<Map.Entry<String, Double>> sortedNegativeMap1200 = sortByValue(negList1200);*/

                int startIndex = sortedPositiveMap.size() - topNumber;
                if (startIndex < 0) {
                    startIndex = 0;
                }
                int endIndex = sortedNegativeMap.size();
                if (endIndex > topNumber) {
                    endIndex = topNumber;
                }
                List<Map.Entry<String, Double>> sortedNegMapSub11 = new ArrayList<>();
                if (endIndex > 0) {
                    sortedNegMapSub11 = sortedNegativeMap.subList(0, endIndex);
                }
                List<Map.Entry<String, Double>> sortedPositiveMapSub11 = null;
                if (sortedPositiveMap.size() > 0) {
                    sortedPositiveMapSub11 = sortedPositiveMap.subList(startIndex, sortedPositiveMap.size());
                }
                final List<Map.Entry<String, Double>> sortedPositiveMapSub1130 = sortedPositiveMapSub11;


                //  }

                if (sortedPositiveMapSub1130 != null && sortedPositiveMapSub1130.size() > 0) {
                    for (Map.Entry<String, Double> map : sortedPositiveMapSub1130) {
                        TradeDataCrypto tradeDataTemp = tradeDataCryptoList.get(map.getKey());

                        ZonedDateTime tradeStartDate = tradeStartFinal;

                        List<CandlestickExtended> historicData = historic.get(map.getKey());
                        Optional<CandlestickExtended> candlestickExtendedCurr = historicData.stream().filter(candlestickExtended -> {
                            Calendar curTime = Calendar.getInstance();
                            curTime.setTimeInMillis(candlestickExtended.getOpenTime());
                            if (dateTimeFormat.format(curTime.getTime()).equals(tradeStartFinal.format(dateTimeFormater)))
                                return true;
                            else return false;
                        }).findFirst();
                        CandlestickExtended openCandle = candlestickExtendedCurr.get();
                        if (tradeDataTemp == null) {
                            tradeDataTemp = new TradeDataCrypto();
                            tradeDataTemp.isOrderPlaced = true;
                            tradeDataTemp.setStockName(map.getKey());
                            double qty = 100 / openCandle.getClose().doubleValue();
                            tradeDataTemp.setBuyPrice(openCandle.getClose().doubleValue());
                            tradeDataTemp.setQty(qty);
                            tradeDataTemp.setBuyTime(simple.format(openCandle.getCloseTime()));
                            double slPoints = (tradeDataTemp.getBuyPrice() * 5) / 100;
                            double slPrice = tradeDataTemp.getBuyPrice() - slPoints;
                            tradeDataTemp.setSlPrice(slPrice);
                            tradeDataTemp.setEntryType("BUY");
                            tradeDataTemp.setSlTrialPoints(slPoints);
                            tradeDataCryptoList.put(map.getKey(), tradeDataTemp);
                        }


                        Calendar openCalender = Calendar.getInstance();
                        openCalender.setTimeInMillis(openCandle.getCloseTime());

                    }
                }/*
                tradeDataCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                    TradeDataCrypto tradeData1 = tradeDataCryptoMap.getValue();
                    List<CandlestickExtended> historicData1 = historic.get(tradeDataCryptoMap.getKey());
                    Optional<CandlestickExtended> candlestickExtendedCurr1 = historicData1.stream().filter(candlestickExtended -> {
                        Calendar curTime = Calendar.getInstance();
                        curTime.setTimeInMillis(candlestickExtended.getOpenTime());
                        if (dateTimeFormat.format(curTime.getTime()).equals(tradeStartFinal.format(dateTimeFormater)))
                            return true;
                        else return false;
                    }).findFirst();
                    CandlestickExtended openCandle1 = candlestickExtendedCurr1.get();
                    Calendar curTime = Calendar.getInstance();
                    curTime.setTimeInMillis(openCandle1.getOpenTime());
                    if (!tradeData1.isExited *//*&& curTime.getTime().after(openCalender.getTime()))*//*) {

                        if (tradeData1.getSlPrice() > openCandle1.getLow().doubleValue()) {
                            tradeData1.isExited = true;
                            tradeData1.isSLHit = true;
                            tradeData1.setSellPrice(tradeData1.getSlPrice());
                            tradeData1.setSellTime(simple.format(openCandle1.getCloseTime()));

                            double profitLoss = (tradeData1.getSellPrice() - tradeData1.getBuyPrice()) * tradeData1.getQty();
                            String[] data = {tradeData1.getStockName(), String.valueOf(tradeData1.getBuyPrice()), String.valueOf(tradeData1.getSellPrice()), String.valueOf(tradeData1.getQty()), String.valueOf(profitLoss), tradeData1.getBuyTime(), tradeData1.getSellTime(), tradeData1.getEntryType()};
                            //  System.out.println(Arrays.toString(data));
                            csvWriter.writeNext(data);
                            try {
                                csvWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                });*//*
                if (sortedNegMapSub11 != null && sortedNegMapSub11.size() > 0) {
                    for (Map.Entry<String, Double> map : sortedNegMapSub11) {
                        ZonedDateTime tradeStartDate = zdtStart.plusHours(5);
                        List<Candlestick> candlestickList = syncRequest.getCandlestick(map.getKey(), CandlestickInterval.FIVE_MINUTES, tradeStartDate.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                        Candlestick openCandle = candlestickList.get(0);
                        Candlestick lastCandle = candlestickList.get(candlestickList.size() - 1);
                        TradeDataCrypto tradeData = new TradeDataCrypto();

                        tradeData.isOrderPlaced = true;
                        tradeData.setStockName(map.getKey());
                        double qty = 100 / openCandle.getClose().doubleValue();
                        tradeData.setSellPrice(openCandle.getClose().doubleValue());
                        tradeData.setQty(qty);
                        tradeData.setSellTime(simple.format(openCandle.getCloseTime()));
                        double slPoints = (tradeData.getSellPrice() * 5) / 100;
                        double slPrice = tradeData.getSellPrice() + slPoints;
                        tradeData.setSlPrice(slPrice);
                        tradeData.setEntryType("SELL");
                        tradeData.setSlTrialPoints(slPoints);
                        Calendar openCalender = Calendar.getInstance();
                        openCalender.setTimeInMillis(openCandle.getCloseTime());
                        candlestickList.stream().forEach(candlestick -> {
                            try {
                                Calendar curr = Calendar.getInstance();
                                curr.setTimeInMillis(candlestick.getCloseTime());
                                if (tradeData.isOrderPlaced && (curr.getTime().after(openCalender.getTime()))) {

                                    if (!tradeData.isExited) {

                                        if (tradeData.getSlPrice() < candlestick.getHigh().doubleValue()) {
                                            tradeData.isExited = true;
                                            tradeData.isSLHit = true;
                                            tradeData.setBuyPrice(tradeData.getSlPrice());
                                            tradeData.setBuyTime(simple.format(candlestick.getCloseTime()));

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
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        if (!tradeData.isExited) {


                            tradeData.isExited = true;
                            tradeData.isSLHit = true;
                            tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                            tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));

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
                        try {
                            csvWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            csvWriter.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }*/
                tradeStart = tradeStart.plusMinutes(5);
            }
            tradeDataCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                TradeDataCrypto tradeData1 = tradeDataCryptoMap.getValue();
                List<CandlestickExtended> historicData1 = historic.get(tradeDataCryptoMap.getKey());

                CandlestickExtended openCandle1 = historicData1.get(historicData1.size() - 1);
                Calendar curTime = Calendar.getInstance();
                curTime.setTimeInMillis(openCandle1.getOpenTime());
                if (!tradeData1.isExited /*&& curTime.getTime().after(openCalender.getTime()))*/) {
                    tradeData1.isExited = true;

                    tradeData1.isExited = true;
                    tradeData1.isSLHit = true;
                    tradeData1.setSellPrice(openCandle1.getClose().doubleValue());
                    tradeData1.setSellTime(simple.format(openCandle1.getCloseTime()));

                    double profitLoss = (tradeData1.getSellPrice() - tradeData1.getBuyPrice()) * tradeData1.getQty();
                    String[] data = {tradeData1.getStockName(), String.valueOf(tradeData1.getBuyPrice()), String.valueOf(tradeData1.getSellPrice()), String.valueOf(tradeData1.getQty()), String.valueOf(profitLoss), tradeData1.getBuyTime(), tradeData1.getSellTime(), tradeData1.getEntryType()};
                    //  System.out.println(Arrays.toString(data));
                    csvWriter.writeNext(data);
                    try {
                        csvWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


            });
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

    public void binanceTrendSell() throws Exception {
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/trend_sell.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateTimeFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<String> symbolList = new ArrayList<>();
        exchn.getSymbols().stream().forEach(symbols -> {
            symbolList.add(symbols.getSymbol());
        });
        int n = 250;
        while (n >= -2) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime zdtNextStop = today.plusDays(2).atStartOfDay(ZoneId.systemDefault());
            System.out.println(today);

            ZonedDateTime tradeStart = zdtStart.plusHours(2);
            ZonedDateTime tradeStop = zdtStart.plusHours(10);


            n--;
            Map<String, List<CandlestickExtended>> historic = new HashMap<>();
            symbolList.stream().forEach(s -> {
                List<CandlestickExtended> candlestickListEx = new ArrayList<>();
                if (s.contains("USDT")) {
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s, CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    if (candlestickList.size() > 0) {
                        MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                        MathUtils.RSI rsi = new MathUtils.RSI(6);

                        candlestickList.stream().forEach(candlestick -> {
                            CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                            candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                            candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                            candlestickListEx.add(candlestickExtended);

                        });
                        historic.put(s, candlestickListEx);

                    }
                }
            });

            Map<String, TradeDataCrypto> tradeDataCryptoList = new HashMap<>();
            while (tradeStart.compareTo(zdtStop) <= 0) {
                System.out.println(tradeStart);
                final ZonedDateTime tradeStartFinal = tradeStart;
                historic.entrySet().forEach(historicData -> {
                            List<CandlestickExtended> candlestickExtendedEx = historicData.getValue();
                            CandlestickExtended opencandlestickExtendedEx = candlestickExtendedEx.get(0);

                            Calendar openCalender = Calendar.getInstance();
                            openCalender.setTimeInMillis(opencandlestickExtendedEx.getCloseTime());
                            candlestickExtendedEx.stream().forEach(candlestickExtended -> {
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(candlestickExtended.getOpenTime());
                                if (dateTimeFormat.format(calendar.getTime()).equals(tradeStartFinal.format(dateTimeFormater))) {
                                    double percentGain = MathUtils.percentageMove(opencandlestickExtendedEx.getOpen(), candlestickExtended.getClose()).doubleValue();
                                    try {
                                        TradeDataCrypto tradeData = tradeDataCryptoList.get(historicData.getKey());
                                        if (tradeData == null) {
                                            tradeData = new TradeDataCrypto();
                                        }

                                        if (percentGain >= 10 && calendar.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                                                && calendar.getTime().before(dateTimeFormat.parse(today + " 10:00:00"))) {

                                            int stoplosshit = stopLossHitCount(tradeDataCryptoList);

                                            if (!tradeData.isOrderPlaced && tradeData.getStockName() == null) {

                                                tradeData.isOrderPlaced = true;
                                                tradeData.setStockName(historicData.getKey());
                                                double qty = 100 / candlestickExtended.getClose().doubleValue();
                                                tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                                tradeData.setQty(qty);
                                                tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                                double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                                double slPrice = tradeData.getBuyPrice() + slPoints;
                                                tradeData.setSlPrice(slPrice);
                                                tradeData.setTargetPrice(tradeData.getBuyPrice() + slPoints);
                                                tradeData.setEntryType("BUY");
                                                tradeData.setSlTrialPoints(slPoints);
                                                tradeDataCryptoList.put(historicData.getKey(), tradeData);

                                            }
                                        }
                                        if (percentGain <= -10 && calendar.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                                                && calendar.getTime().before(dateTimeFormat.parse(today + " 10:00:00"))) {
                                            int stoplosshit = stopLossHitCount(tradeDataCryptoList);
                                            if (!tradeData.isOrderPlaced && tradeData.getStockName() == null) {

                                                tradeData.isOrderPlaced = true;
                                                tradeData.setStockName(historicData.getKey());
                                                double qty = 100 / candlestickExtended.getClose().doubleValue();
                                                tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                                tradeData.setQty(qty);
                                                tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                                double slPoints = (tradeData.getBuyPrice() * 5) / 100;
                                                double slPrice = tradeData.getBuyPrice() + slPoints;
                                                tradeData.setSlPrice(slPrice);
                                                tradeData.setTargetPrice(tradeData.getBuyPrice() + slPoints);
                                                tradeData.setEntryType("INVERTED-BUY");
                                                tradeData.setSlTrialPoints(slPoints);
                                                tradeDataCryptoList.put(historicData.getKey(), tradeData);
                                            }

                                        }

                                        if (tradeData.isOrderPlaced && calendar.getTime().after(simple.parse(tradeData.getBuyTime()))) {

                                            if (!tradeData.isExited) {
                                                double percentTradeMove = MathUtils.percentageMove(tradeData.getBuyPrice(), candlestickExtended.getClose().doubleValue());


                                                if (tradeData.getTargetPrice() < candlestickExtended.getLow().doubleValue()) {
                                                    tradeData.isExited = true;
                                                    tradeData.setSellPrice(tradeData.getSlPrice());
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
                                                }
                                            }
                                        }


                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

                        }
                );
                tradeStart = tradeStart.plusMinutes(5);
            }
            tradeDataCryptoList.entrySet().forEach(tradeDataCryptoMap -> {
                TradeDataCrypto tradeData = tradeDataCryptoMap.getValue();
                if (!tradeData.isExited) {
                    tradeData.isExited = true;

                    List<CandlestickExtended> candlestickExtendeds = historic.get(tradeData.getStockName());
                    CandlestickExtended candlestickExtended = candlestickExtendeds.get(candlestickExtendeds.size() - 1);
                    tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                    tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
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
            });


        }
    }

    public int stopLossHitCount(Map<String, TradeDataCrypto> tradeDataCryptoList) {
        AtomicInteger stoplosscount = new AtomicInteger();
        tradeDataCryptoList.entrySet().stream().forEach(tradeDataCrypto -> {
            if (tradeDataCrypto.getValue().isSLHit) {
                stoplosscount.getAndAdd(1);
            }
        });
        return stoplosscount.get();
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
            n--;
            AtomicInteger plusCount = new AtomicInteger();
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
            System.out.println("ratio: " + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ": " + plusCount.get() + " : " + minusCount.get());
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
                            if (
                                    count.get() > 20 && percentMoveCurrent.doubleValue() > 0 && calendar.getTime().after(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:55:00"))) {
                                //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){

                                if (candlestickExtended.getRsi() > 40 && buy) {
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
                                } else if (candlestickExtended.getRsi() < 40 && !buy) {
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
                        if (!buy) {
                            tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                            tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                            tradeData.setEntryType("SHORT");
                        } else {
                            tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                            tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                            tradeData.setEntryType("BUY");
                        }
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
            LocalDate previous = LocalDate.now(ZoneId.systemDefault()).minusDays(n + 1);
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
                            if (daycandlestickList.size() > 0) {
                                Candlestick candlestickPrevious = daycandlestickList.get(0);
                                double previousDayMove = ((candlestickPrevious.getClose().doubleValue() - candlestickPrevious.getOpen().doubleValue()) / candlestickPrevious.getOpen().doubleValue()) * 100;
                                if (!tradeData.isOrderPlaced) {
                                    if (candlestickExtended.getRsi() > 70 && count.get() > 20 && percentMoveCurrent.doubleValue() < 0) {
                                        //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){


                                        tradeData.isOrderPlaced = true;
                                        tradeData.setStockName(s);
                                        double qty = 100 / candlestickExtended.getClose().doubleValue();
                                        tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                        tradeData.setQty(qty);
                                        tradeData.setEntryType("SELL");
                                        tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                    } else if (candlestickExtended.getRsi() < 25 && count.get() > 20 && percentMoveCurrent.doubleValue() > 0) {
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
                                double percentMoveAfterTrade = 0;
                                if ("SELL".equals(tradeData.getEntryType())) {
                                    percentMoveAfterTrade = MathUtils.percentageMove(tradeData.getSellPrice(), candlestickExtended.getClose().doubleValue());
                                } else {
                                    percentMoveAfterTrade = MathUtils.percentageMove(tradeData.getBuyPrice(), candlestickExtended.getClose().doubleValue());
                                }
                                if (percentMoveAfterTrade < -5 || percentMoveAfterTrade > 5) {
                                    if (tradeData.isOrderPlaced && !tradeData.isExited) {
                                        if ("SELL".equals(tradeData.getEntryType())) {
                                            tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                                        } else {
                                            tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                            tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                        }
                                        tradeData.isExited = true;
                                        tradeData.isOrderPlaced = false;
                                        //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                        double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                        String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime(), tradeData.getEntryType()};
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
                            if ("SELL".equals(tradeData.getEntryType())) {
                                tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                            } else {
                                tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                                tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                            }
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

            AtomicInteger plusCount = new AtomicInteger();
            AtomicInteger minusCount = new AtomicInteger();
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
            System.out.println("ratio: " + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ": " + plusCount.get() + " : " + minusCount.get());
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
                        candlestickListEx.stream().forEach(candlestickExtended -> {

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
                                    if (noofVolume.get() >= 3 && calendar.getTime().after(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 02:55:00")) && calendar.getTime().before(dateTimeFormat.parse(dateFormat.format(openCalender.getTime()) + " 12:00:00"))) {
                                        //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){
                                        if (!tradeData.isOrderPlaced) {
                                            if (atomicPercentMove.get() < 5) {
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
                                            } else if (atomicPercentMove.get() > 5) {
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
                            if (tradeData.getEntryType().equals("SHORT")) {
                                tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                                //  tradeData.setEntryType("SHORT");
                            } else {
                                tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                                tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                                //   tradeData.setEntryType("BUY");
                            }
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

