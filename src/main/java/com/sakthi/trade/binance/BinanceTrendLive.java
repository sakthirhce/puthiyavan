
package com.sakthi.trade.binance;

import com.binance.client.RequestOptions;
import com.binance.client.impl.RestApiRequestImpl;
import com.binance.client.impl.SyncRequestImpl;
import com.binance.client.model.enums.*;
import com.binance.client.model.market.Candlestick;
import com.binance.client.model.market.ExchangeInfoEntry;
import com.binance.client.model.market.ExchangeInformation;
import com.binance.client.model.trade.AccountBalance;
import com.binance.client.model.trade.MyTrade;
import com.binance.client.model.trade.Order;
import com.binance.client.model.trade.PositionRisk;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.binance.models.CandlestickExtended;
import com.sakthi.trade.domain.TradeDataCrypto;
import com.sakthi.trade.entity.CryptoFuturesDayDataEntity;
import com.sakthi.trade.entity.CryptoFuturesEntity;
import com.sakthi.trade.repo.CryptoFuturesDayDataRepository;
import com.sakthi.trade.repo.CryptoRepository;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.MathUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class BinanceTrendLive {

    @Value("${filepath.trend}")
    String trendPath;
    @Value("${binance.sathiyaseelanrhce.v11.secret}")
    private String binanceSecretKey;
    @Value("${binance.sathiyaseelanrhce.v11.apikey}")
    private String binanceApiKey;
    private Map<String, TradeDataCrypto> tradeDataCryptoMap = new HashMap<>();
    List<ExchangeInfoEntry> symbolList = new ArrayList<>();
    RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
    SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;
    @Value("${binance.futures.mean.reversion.tradeAmountLimit}")
    int tradeAmountLimit;
    @Value("${binance.futures.trade.max.limit}")
    int tradeMaxLimit;
    @Value("${binance.sathiyaseelanrhce.leverage}")
    private int leverage;
    @Autowired
    SendMessage sendMessage;

    public static String encode(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
    }

    //@PostConstruct
    public void binanceExchangeInformation() throws BinanceApiException {
        RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
        SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);
        ExchangeInformation exchn = syncRequest.getExchangeInformation();
        if (symbolList.size() == 0) {
            ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
            exchn.getSymbols().stream().filter(s -> "TRADING".equals(s.getStatus()) && "PERPETUAL".equals(s.getContractType())).forEach(symbols -> {
                executorService.submit(() -> {
                    symbolList.add(symbols);
                    //  System.out.println("changing margin type and leverage:" + symbols.getSymbol());
                    try {
                        List<PositionRisk> positions = syncRequest.getPositionRisk();
                        Optional<PositionRisk> positionRiskOptional=positions.stream().filter(position->symbols.getSymbol().equals(position.getSymbol())).findFirst();
                        if (positionRiskOptional.isPresent()) {
                            PositionRisk positionRisk=positionRiskOptional.get();
                            String marginType=positionRisk.getMarginType();
                            if ("isolated".equals(marginType)) {
                                syncRequest.changeMarginType(symbols.getSymbol(), "CROSSED");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error while changing margin type for: " + symbols.getSymbol() + ":" + e.getMessage());
                    }
                    syncRequest.changeInitialLeverage(symbols.getSymbol(), leverage);
                });
            });
        }
        List<AccountBalance> accountBalanceList = syncRequest.getBalance();
        System.out.println(new Gson().toJson(accountBalanceList));
        Optional<AccountBalance> accountBalanceOptional = accountBalanceList.stream().filter(accountBalanceTemp -> "USDT".equals(accountBalanceTemp.getAsset())).findFirst();
      /*  if (accountBalanceOptional.isPresent()) {
            AccountBalance accountBalance = accountBalanceOptional.get();
            tradeAmountLimit = accountBalance.getBalance().intValue() / 100;
        }*/
        System.out.println("Today USDT tradeAmountLimit: " + tradeAmountLimit);
    }
    Map<String, TradeDataCrypto> tradeDataCryptoList = new HashMap<>();

    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  //  @Scheduled(cron = "${binance.futures.rsi.schedule}")
    public void binanceRSITrendLive() throws BinanceApiException {
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        System.out.println("started binance reversion:" + simple.format(new Date()));

        ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
        if (symbolList.size() == 0) {
            binanceExchangeInformation();
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime latest = ZonedDateTime.now();

        symbolList.stream().forEach(s -> {
            List<CandlestickExtended> candlestickListEx = new ArrayList<>();
            if (s.getSymbol().contains("USDT")) {
                List<Candlestick> candlestickList = syncRequest.getCandlestick(s.getSymbol(), CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), latest.toInstant().toEpochMilli(), 500);
                if (candlestickList.size() > 0) {
                    MathUtils.RSI rsi = new MathUtils.RSI(6);
                    TradeDataCrypto tradeData = new TradeDataCrypto();
                    AtomicInteger count = new AtomicInteger();
                    Candlestick openCandle = candlestickList.get(0);
                    Calendar openCalender = Calendar.getInstance();
                    openCalender.setTimeInMillis(openCandle.getCloseTime());
                    BigDecimal percentMoveDay = MathUtils.percentageMove(candlestickList.get(0).getOpen(), candlestickList.get(candlestickList.size() - 1).getClose());
                    candlestickList.stream().forEach(candlestick -> {
                        CandlestickExtended candlestickExtended = new Gson().fromJson(new Gson().toJson(candlestick), CandlestickExtended.class);
                        candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                        candlestickListEx.add(candlestickExtended);
                        count.getAndAdd(1);
                    });
                    CandlestickExtended lastCandle = candlestickListEx.get(candlestickListEx.size() - 1);
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(lastCandle.getCloseTime());
                    //   System.out.println(s.getSymbol() + " Rsi:" + lastCandle.getRsi()+":" + simple.format(calendar.getTime()));
                    if (lastCandle.getRsi() <= 25 && count.get() > 20 & percentMoveDay.doubleValue() > 0) {
                        if (!tradeData.isOrderPlaced && !tradeDataCryptoMap.containsKey(s)) {
                            System.out.println(s.getSymbol() + " Rsi below  25:" + new BigDecimal(lastCandle.getRsi()).setScale(2, RoundingMode.HALF_EVEN) + " triggering buy");
                            try {
                                tradeData.isOrderPlaced = true;
                                tradeData.setStockName(s.getSymbol());
                                //  double qty = 100 / lastCandle.getClose().doubleValue();
                                RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
                                SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);

                                String qty = (new BigDecimal(tradeAmountLimit).multiply(new BigDecimal(leverage))).divide(lastCandle.getClose(), s.getQuantityPrecision().intValue(), RoundingMode.HALF_EVEN)
                                        .setScale(s.getQuantityPrecision().intValue(), BigDecimal.ROUND_HALF_UP)
                                        .toString();
                                Order order = syncRequest.postOrder(s.getSymbol(), OrderSide.BUY, null, OrderType.MARKET, null, qty, null, null, null, null, null, null);
                                System.out.println(new Gson().toJson(order));
                                tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                tradeData.setQty(Double.valueOf(qty));
                                tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                                tradeDataCryptoMap.put(s.getSymbol(), tradeData);
                                sendMessage.sendToTelegram("crypto strike bought: " + s.getSymbol(), telegramToken);
                            } catch (Exception e) {
                                sendMessage.sendToTelegram("error while buying crypto strike: " + s.getSymbol() + " Error message:" + e.getMessage(), telegramToken);
                                System.out.println("error while buying crypto strike: " + s.getSymbol() + " Error message:" + e.getMessage());
                            }
                        }
                    }
                }
            }

        });

    }
    @Autowired
    CryptoFuturesDayDataRepository cryptoFuturesDayDataRepository;

    //@Scheduled(cron = "${binance.futures.mean.reversion.schedule}")
    public void binanceMeanReversionTrendLive() throws BinanceApiException, ParseException {

        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        System.out.println("started binance mean reversion:" + simple.format(new Date()));

        ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
        if (symbolList.size() == 0) {
            binanceExchangeInformation();
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ZonedDateTime zdtStart = today.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime zdtStop = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        Calendar calendarcheck=Calendar.getInstance();
        if(calendarcheck.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                && calendarcheck.getTime().before(dateTimeFormat.parse(today + " 10:00:00"))) {
            symbolList.stream().forEach(s -> {
                if (s.getSymbol().contains("USDT")) {
                    List<CryptoFuturesDayDataEntity>  cryptoFuturesDayDataEntityList= cryptoFuturesDayDataRepository.findSymbolData(s.getSymbol(),today);
                    CryptoFuturesDayDataEntity cryptoFuturesDayDataEntity=cryptoFuturesDayDataEntityList.get(cryptoFuturesDayDataEntityList.size()-1);
                    List<Candlestick> candlestickList = syncRequest.getCandlestick(s.getSymbol(), CandlestickInterval.FIVE_MINUTES, zdtStart.toInstant().toEpochMilli(), zdtStop.toInstant().toEpochMilli(), 500);
                    BigDecimal percentPreMoveDay = MathUtils.percentageMove(cryptoFuturesDayDataEntity.getOpen(), cryptoFuturesDayDataEntity.getClose());
                    if (candlestickList.size() > 0) {
                        TradeDataCrypto tradeData = tradeDataCryptoMap.get(s.getSymbol());

                        AtomicInteger count = new AtomicInteger();
                        Candlestick openCandle = candlestickList.get(0);
                        Candlestick lastCandle = candlestickList.get(candlestickList.size() - 1);
                        BigDecimal percentMoveDay = MathUtils.percentageMove(openCandle.getOpen(), lastCandle.getClose());

                        Calendar calendar = Calendar.getInstance();
                        calendar.setTimeInMillis(lastCandle.getCloseTime());
                        System.out.println(s.getSymbol()+":"+percentMoveDay.doubleValue());
                        try {
                            if (percentMoveDay.doubleValue() <= -5 && percentPreMoveDay.doubleValue()<0 && calendar.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                                    && calendar.getTime().before(dateTimeFormat.parse(today + " 10:00:00"))) {
                                int stoplosshit = stopLossHitCount(tradeDataCryptoMap);
                                System.out.println("condition satisfied:"+s.getSymbol());
                                if ( tradeData == null && stoplosshit <= 4) {
                                    tradeData = new TradeDataCrypto();
                                    try {
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setStockName(s.getSymbol());
                                        RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
                                        SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);

                                        String qty = (new BigDecimal(tradeAmountLimit).multiply(new BigDecimal(leverage))).divide(lastCandle.getClose(), s.getQuantityPrecision().intValue(), RoundingMode.HALF_EVEN)
                                                .setScale(s.getQuantityPrecision().intValue(), BigDecimal.ROUND_HALF_UP)
                                                .toString();
                                        Order order = syncRequest.postOrder(s.getSymbol(), OrderSide.BUY, null, OrderType.MARKET, null, qty, null, null, null, null, null, null);
                                        System.out.println(new Gson().toJson(order));
                                        tradeData.setBuyPrice(lastCandle.getClose().doubleValue());
                                        tradeData.setQty(Double.valueOf(qty));
                                        tradeData.setBuyTime(simple.format(lastCandle.getCloseTime()));
                                        tradeDataCryptoMap.put(s.getSymbol(), tradeData);
                                        sendMessage.sendToTelegram("crypto strike bought: " + s.getSymbol(), telegramToken);
                                    } catch (Exception e) {
                                        sendMessage.sendToTelegram("error while buying crypto strike: " + s.getSymbol() + " Error message:" + e.getMessage(), telegramToken);
                                        System.out.println("error while buying crypto strike: " + s.getSymbol() + " Error message:" + e.getMessage());
                                    }
                                }
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
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
  //  @Scheduled(cron = "${binance.futures.mean.reversion.sl.schedule}")
    public void slClose() {
        RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
        SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);
        List<PositionRisk> positions = syncRequest.getPositionRisk();
      //  System.out.println(positions);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZonedDateTime startTime = LocalDateTime.parse(today + " 02:00:00", formatter).atZone(ZoneId.of("Asia/Kolkata"));
        ZonedDateTime endTime = LocalDateTime.parse(today + " 23:59:00", formatter).atZone(ZoneId.of("Asia/Kolkata"));
        positions.stream().forEach(position -> {
            if(position.getPositionAmt().doubleValue()>0) {
                TradeDataCrypto tradeDataCrypto = tradeDataCryptoMap.get(position.getSymbol());

                try {
                    if(tradeDataCrypto!=null && tradeDataCrypto.getQty()!=position.getPositionAmt().doubleValue()){
                        tradeDataCrypto.setQty(position.getPositionAmt().doubleValue());
                        tradeDataCrypto.setBuyPrice(position.getEntryPrice().doubleValue());
                    }
                    if (position.getUnrealizedProfit().doubleValue() < 0) {
                      if (tradeDataCrypto != null){
                          if(position.getUnrealizedProfit().abs().doubleValue() >= tradeAmountLimit && (!tradeDataCrypto.isExited || !tradeDataCrypto.isSLHit)) {
                            tradeDataCrypto.isSLHit = true;
                            tradeDataCrypto.isExited = true;
                            Order order = syncRequest.postOrder(position.getSymbol(), OrderSide.SELL, null, OrderType.MARKET, null, String.valueOf(tradeDataCrypto.getQty()), null, null, null, null, null, null);
                            sendMessage.sendToTelegram("crypto strike closed: " + position.getSymbol() +" pl:"+position.getUnrealizedProfit(), telegramToken);
                        }
                      }else{
                          double margin=position.getEntryPrice().multiply(position.getPositionAmt()).doubleValue();
                          double capital=margin/leverage;
                          List<MyTrade> trade = syncRequest.getAccountTrades(position.getSymbol(), startTime.toInstant().toEpochMilli(), endTime.toInstant().toEpochMilli(), null, 1);
                          if (position.getUnrealizedProfit().doubleValue() < 0) {
                              if (position.getUnrealizedProfit().abs().doubleValue() >= capital) {
                                  OrderSide positionSide;
                                  if("SHORT".equals(trade.get(0).getPositionSide())){
                                      positionSide=OrderSide.BUY;
                                  }else
                                  {
                                      positionSide=OrderSide.SELL;
                                  }
                                  Order order = syncRequest.postOrder(position.getSymbol(), positionSide, null, OrderType.MARKET, null, String.valueOf(position.getPositionAmt()), null, null, null, null, null, null);
                                  sendMessage.sendToTelegram("crypto strike closed: " + position.getSymbol() + " pl:" + position.getUnrealizedProfit(), telegramToken);
                              }
                          }
                      }
                    }

                        double limit=position.getEntryPrice().multiply(position.getPositionAmt()).doubleValue();
                    Order order =null;
                    try {
                        if (limit > tradeMaxLimit) {
                            List<MyTrade> trade = syncRequest.getAccountTrades(position.getSymbol(), startTime.toInstant().toEpochMilli(), endTime.toInstant().toEpochMilli(), null, 1);
                            OrderSide positionSide;
                            if ("SHORT".equals(trade.get(0).getPositionSide())) {
                                positionSide = OrderSide.BUY;
                            } else {
                                positionSide = OrderSide.SELL;
                            }
                            order = syncRequest.postOrder(position.getSymbol(), positionSide, null, OrderType.MARKET, null, String.valueOf(tradeDataCrypto.getQty()), null, null, null, null, null, null);

                        }
                    }catch (Exception e) {
                        sendMessage.sendToTelegram("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e, telegramToken);
                        System.out.println("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage()+":"+new Gson().toJson(order));

                    }

                } catch (Exception e) {
                    sendMessage.sendToTelegram("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage(), telegramToken);
                    System.out.println("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage());

                }
            }
            else if(position.getUnrealizedProfit().doubleValue()==0){
                TradeDataCrypto tradeDataCrypto = tradeDataCryptoMap.get(position.getSymbol());
                if(tradeDataCrypto !=null && (!tradeDataCrypto.isExited || !tradeDataCrypto.isSLHit)){
                    tradeDataCrypto.isSLHit = true;
                    tradeDataCrypto.isExited = true;
                }
            }

        });

    }

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    @Autowired
    CryptoRepository cryptoRepository;
 //   @Scheduled(cron = "${binance.futures.daily.data.schedule}")
    public void loadDayHistory() throws ExecutionException, InterruptedException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");



        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<CryptoFuturesEntity> cryptoFuturesEntities = cryptoRepository.findAll();

        for (ExchangeInfoEntry symbold : syncRequest.getExchangeInformation().getSymbols()) {
            try {


            String  symbol=symbold.getSymbol();
                System.out.println(symbol);
            String lastDate = cryptoFuturesDayDataRepository.findLastDate(symbol);

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
            }catch (Exception e) {

                e.printStackTrace();
            }
        }
            //   i = i - 1;
            //   });
            //}

    }
   // @PreDestroy
    public void exportData() throws ParseException, IOException {
        Calendar calendar = Calendar.getInstance();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if(calendar.getTime().after(dateTimeFormat.parse(today + " 02:00:00"))
                && calendar.getTime().before(dateTimeFormat.parse(today + " 23:59:00"))) {
            DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
            LocalDate localDate=LocalDate.now();
            CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/bin_mean_trade_data_"+dtf1.format(localDate)+".csv",false));
            tradeDataCryptoMap.entrySet().stream().forEach(tradeData->
            {

                try {
                    String[] data={tradeData.getKey(),new Gson().toJson(tradeData)};
                    csvWriter.writeNext(data);
                    csvWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            csvWriter.close();
        }
    }

    //@PostConstruct
    public void importData() throws ParseException, IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        try {
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/bin_mean_trade_data_" + dtf1.format(localDate) + ".csv"));
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                tradeDataCryptoMap.put(line[0], new Gson().fromJson(line[1], TradeDataCrypto.class));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
  //  @Scheduled(cron = "${binance.futures.mean.reversion.exit.schedule}")
    public void closeAll() {
        RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
        SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);
        List<PositionRisk> positions = syncRequest.getPositionRisk();
        System.out.println(positions);
        positions.stream().filter(positionRisk -> positionRisk.getPositionAmt().doubleValue()>0).forEach(position -> {
            TradeDataCrypto tradeDataCrypto = tradeDataCryptoMap.get(position.getSymbol());
            try {
                if (tradeDataCrypto != null) {
                    tradeDataCrypto.isExited=true;
                    Order order = syncRequest.postOrder(position.getSymbol(), OrderSide.SELL, null, OrderType.MARKET, null, String.valueOf(tradeDataCrypto.getQty()), null, null, null, null, null, null);
                    sendMessage.sendToTelegram("crypto strike closed: " + position.getSymbol() +" pl:"+position.getUnrealizedProfit(), telegramToken);
                }
            } catch (Exception e) {
                sendMessage.sendToTelegram("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage(), telegramToken);
                System.out.println("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage());

            }
        });

        tradeDataCryptoMap = new HashMap<>();
    }
    @Scheduled(cron = "${binance.futures.mean.reversion.reform}")
    public void reformTradeDetails() {

        RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
        SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);
        List<PositionRisk> positions = syncRequest.getPositionRisk();
        //  System.out.println(positions);
        if (tradeDataCryptoMap.size() == 0) {
            try {

                LocalDate today = LocalDate.now(ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                ZonedDateTime startTime = LocalDateTime.parse(today + " 02:00:00", formatter).atZone(ZoneId.of("Asia/Kolkata"));
                ZonedDateTime endTime = LocalDateTime.parse(today + " 10:00:00", formatter).atZone(ZoneId.of("Asia/Kolkata"));
                positions.stream().filter(positionRisk ->
                        positionRisk.getPositionAmt().doubleValue() > 0
                ).forEach(position -> {

                    TradeDataCrypto tradeDataCrypto = tradeDataCryptoMap.get(position.getSymbol());
                    try {
                        if (tradeDataCrypto == null) {

                            tradeDataCrypto = new TradeDataCrypto();
                          /*  List<MyTrade> trade = syncRequest.getAccountTrades(position.getSymbol(), startTime.toInstant().toEpochMilli(), endTime.toInstant().toEpochMilli(), null, 1);
                            if (trade != null && trade.size() > 0) {
                                tradeDataCrypto.setQty(trade.get(0).getQty().doubleValue());
                                tradeDataCrypto.setEntryType(trade.get(0).getSide());
                            }*/
                            tradeDataCrypto.setQty(position.getPositionAmt().doubleValue());
                            tradeDataCrypto.setBuyPrice(position.getEntryPrice().doubleValue());
                            tradeDataCrypto.isOrderPlaced = true;
                            //   tradeDataCrypto.setQty(Double.valueOf(tradeDataCrypto.getQty()));
                            tradeDataCrypto.setStockName(position.getSymbol());
                            // tradeDataCrypto.setBuyTime(position.);
                            tradeDataCryptoMap.put(position.getSymbol(), tradeDataCrypto);
                            System.out.println(new Gson().toJson(tradeDataCrypto));
                        }
                    } catch (Exception e) {
                        sendMessage.sendToTelegram("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage(), telegramToken);
                        System.out.println("error while closing crypto strike: " + position.getSymbol() + " Error message:" + e.getMessage());

                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }

            //  System.out.println(tradeDataCryptoMap.size());
        }
    }


}

