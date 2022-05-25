package com.sakthi.trade.options.banknifty;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ReverseEntry;
import com.sakthi.trade.zerodha.account.TelegramBot;
import com.sakthi.trade.zerodha.account.UserList;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
import com.zerodhatech.models.Order;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;


@Component
@Slf4j
public class ZerodhaBankNiftyShortStraddle {

    @Autowired
    EventDayConfiguration eventDayConfiguration;

    @Value("${filepath.trend}")
    String trendPath;
    @Value("${home.path}")
    String homeFilePath;

    @Autowired
    CommonUtil commonUtil;
    String expDate = "";


    @Value("${telegram.straddle.bot.token}")
    String telegramToken;

    @Value("${straddle.banknifty.lot}")
    String bankniftyLot;

    @Value("${banknifty.historic.straddle.flying}")
    String bankniftyFlyingLot;
    AtomicInteger doubleTopCount = new AtomicInteger(0);


    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;
    private java.util.concurrent.Executors Executors;

    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    public Map<String,String> atmStrikeMap=new HashMap<>();


    @Autowired
    UserList userList;

    @Scheduled(cron = "${banknifty.historic.straddle.quote}")
    public void zerodhaBankNifty() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat weekDay = new SimpleDateFormat("EEE");
        String currentDate = format.format(date);
        String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+11:15:00";
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.print(response);
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            System.out.println();
            historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T09:15:00")) {
                        System.out.println(historicalData1.close);
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        log.info("Bank Nifty:" + atmStrike);
                        //check usuage of bankniftyFlyingLot
                     //   int qty = 25 * (Integer.valueOf(bankniftyLot));
                   /* if("Mon".equals(weekDay.format(date))){
                        qty= qty+(Integer.valueOf(bankniftyFlyingLot)*25);
                    }*/
                        final Map<String, String> atmStrikesStraddle = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                        atmStrikesStraddle.entrySet().forEach(entry -> {
                            System.out.println(entry.getKey() + " " + entry.getValue());
                            atmStrikeMap.put(entry.getKey(),entry.getValue());
                        });
                        atmStrikesStraddle.entrySet().stream().filter(atmStrikeStraddle -> atmStrikeStraddle.getKey().contains(String.valueOf(atmStrike))).forEach(atmBankStrikeMap -> {
                            executorService.submit(() -> {
                                System.out.println(atmBankStrikeMap.getKey());
                                OrderParams orderParams = new OrderParams();
                                orderParams.tradingsymbol = atmBankStrikeMap.getKey();
                                orderParams.exchange = "NFO";
                                orderParams.orderType = "MARKET";
                                orderParams.product = "MIS";
                                orderParams.transactionType = "SELL";
                                orderParams.validity = "DAY";
                                LocalDate localDate = LocalDate.now();
                                DayOfWeek dow = localDate.getDayOfWeek();
                                String today= dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                String todayCaps=today.toUpperCase();
                                userList.getUser().stream().filter(
                                        user -> user.getStraddleConfigOld() !=null  && user.getStraddleConfigOld().isEnabled() && user.getStraddleConfigOld().getLotConfig().containsKey(todayCaps)
                                ).forEach( user -> {
                                    String botId="";
                                    TelegramBot telegramBot=user.getTelegramBot();
                                    if(telegramBot !=null){
                                        botId=telegramBot.getGroupId();
                                    }
                                    String botIdFinal=botId;
                                    AtomicInteger qty=new AtomicInteger(1);
                                    user.getStraddleConfigOld().getLotConfig().entrySet().stream().forEach(day->{
                                        String lotValue=day.getKey();
                                        if(lotValue.contains(todayCaps)) {
                                           int value=Integer.parseInt(day.getValue());
                                            qty.getAndSet(value);
                                        }
                                    });
                                    com.zerodhatech.models.Order order = null;
                                    orderParams.quantity = 25 * qty.get();
                                    TradeData tradeData = new TradeData();
                                    tradeData.setStockName(atmBankStrikeMap.getKey());
                                    try {
                                        order = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        tradeData.setEntryOrderId(order.orderId);
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setQty(25 * qty.get());
                                        tradeData.setEntryType("SELL");
                                        tradeData.setStockId(Integer.valueOf(atmBankStrikeMap.getValue()));
                                        sendMessage.sendToTelegram("Straddle option sold for user:"+user.getName()+" strike: " + atmBankStrikeMap.getKey(), telegramToken,botIdFinal);

                                    } catch (KiteException e) {
                                        tradeData.isErrored = true;
                                        System.out.println("Error while placing straddle order: " + e.message);
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken,botIdFinal);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ",Exception:" + e.getMessage(), telegramToken,botIdFinal);

                                        }
                                        //e.printStackTrace();
                                    } catch (IOException e) {
                                        tradeData.isErrored = true;
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage + ",Exception:" + e.getMessage(), telegramToken,botIdFinal);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ",Exception:" + e.getMessage(), telegramToken,botIdFinal);

                                        }

                                    }
                                    user.getStraddleConfigOld().straddleTradeMap.put(atmBankStrikeMap.getKey(), tradeData);
                                });
                            });
                        });

                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            });

        }
        stopWatch.stop();
        log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());
    }


    @Scheduled(cron = "${stradle.sl.scheduler}")
    public void sLMonitorScheduler() {
        // log.info("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getStraddleConfigOld() !=null &&  user.getStraddleConfigOld().isEnabled()).forEach(user -> {
            String botId="";
            TelegramBot telegramBot=user.getTelegramBot();
            if(telegramBot !=null){
                botId=telegramBot.getGroupId();
            }
            String botIdFinal=botId;
            if (user.getStraddleConfigOld().straddleTradeMap != null) {
                user.getStraddleConfigOld().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            // System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                            List<com.zerodhatech.models.Order> orderList = null;
                            try {
                                orderList = user.getKiteConnect().getOrders();
                                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                            } catch (KiteException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            orderList.stream().forEach(order -> {
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    Date date = new Date();
                                    String currentDate = format.format(date);
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(5))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
                                    AtomicDouble triggerPriceAtomic = new AtomicDouble();
                                    if(slPrice.get(trendTradeData.getStockId())==null){
                                        try {
                                            String historicURL = "https://api.kite.trade/instruments/historical/" + trendTradeData.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                            System.out.print(response);
                                            HistoricalData historicalData = new HistoricalData();
                                            JSONObject json = new JSONObject(response);
                                            String status = json.getString("status");

                                            if (!status.equals("error")) {
                                                historicalData.parseResponse(json);
                                                System.out.println();
                                                historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                                                    try {
                                                        Date openDatetime = sdf.parse(historicalData1.timeStamp);
                                                        String openDate = format.format(openDatetime);
                                                        if (sdf.format(openDatetime).equals(openDate + "T09:19:00")) {
                                                            BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                            trendTradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                                            triggerPriceAtomic.addAndGet(triggerPriceTemp.doubleValue());
                                                            slPrice.put(trendTradeData.getStockId(),triggerPriceTemp);
                                                            System.out.println("setting sl price based on 9:19 close :"+trendTradeData.getStockId()+":"+triggerPriceTemp);
                                                        }
                                                    } catch (ParseException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                });
                                            }
                                        }catch (Exception e){
                                            System.out.println(e.getMessage());
                                        }
                                    }else {
                                        triggerPrice=slPrice.get(trendTradeData.getStockId());
                                    }
                                    if(triggerPriceAtomic.get()>0){
                                        triggerPrice= BigDecimal.valueOf(triggerPriceAtomic.get());
                                    }
                                    OrderParams orderParams = new OrderParams();
                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                    orderParams.exchange = "NFO";
                                    orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                    orderParams.orderType = "SL";
                                    orderParams.product = "MIS";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100))).multiply(new BigDecimal(5)).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                    orderParams.triggerPrice = triggerPrice.doubleValue();
                                    trendTradeData.setSlPrice(triggerPrice);
                                    orderParams.price = price.doubleValue();
                                    orderParams.transactionType = "BUY";
                                    orderParams.validity = "DAY";
                                    com.zerodhatech.models.Order orderResponse = null;
                                    try {
                                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        trendTradeData.setSlOrderId(orderResponse.orderId);
                                        trendTradeData.isSlPlaced = true;
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName(), telegramToken,botIdFinal);
                                        log.info("SL order placed for: " + trendTradeData.getStockName() );

                                    } catch (KiteException e) {
                                        log.info("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message, telegramToken,botIdFinal);
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken,botIdFinal);
                                        e.printStackTrace();
                                    }
                                } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;
                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                                    } else if ("COMPLETE".equals(order.status)) {
                                        trendTradeData.isSLHit = true;
                                        trendTradeData.isExited = true;
                                        String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken, botIdFinal);
                                        if (user.getStraddleConfigOld() != null && user.getStraddleConfigOld().reverseEntry != null && user.getStraddleConfigOld().reverseEntry.isEnabled() && !map.getKey().contains("REENTRY")) {
                                            ReverseEntry reverseEntry=user.getStraddleConfigOld().reverseEntry;
                                            if (trendTradeData.getRentryCount() < reverseEntry.getCount()) {
                                                log.info("inside reverse entry sell");
                                                trendTradeData.setRentryCount(1);
                                                        OrderParams orderParams = new OrderParams();
                                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                                        orderParams.exchange = "NFO";
                                                        orderParams.quantity = trendTradeData.getQty();
                                                        orderParams.orderType = "SL";
                                                        orderParams.product = "MIS";
                                                        orderParams.transactionType = "SELL";
                                                        orderParams.validity = "DAY";
                                                        com.zerodhatech.models.Order orderd=null;
                                                        orderParams.triggerPrice = trendTradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                        BigDecimal price = trendTradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).subtract(trendTradeData.getSellPrice().divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                        orderParams.price = price.doubleValue();
                                                        TradeData reverseTrade = new TradeData();
                                                        reverseTrade.setStockName(trendTradeData.getStockName());
                                                        try {
                                                            orderd = user.getKiteConnect().placeOrder(orderParams, "regular");
                                                            reverseTrade.setEntryOrderId(orderd.orderId);
                                                            reverseTrade.isOrderPlaced = true;
                                                            reverseTrade.setQty(trendTradeData.getQty());
                                                            reverseTrade.setEntryType("SELL");
                                                            sendMessage.sendToTelegram("reentry Straddle option order placed for strike: " + reverseTrade.getStockName(),  telegramToken,botIdFinal);
                                                            user.getStraddleConfigOld().straddleTradeMap.put(trendTradeData.getStockName() + "-REENTRY", reverseTrade);

                                                        } catch (KiteException | IOException e) {
                                                            reverseTrade.isErrored = true;
                                                            System.out.println("Error while placing straddle order: " + e.getMessage()+":"+new Gson().toJson(orderParams));
                                                            System.out.println("order reseponse: " +new Gson().toJson(orderd));
                                                            if (order != null) {
                                                                sendMessage.sendToTelegram("Error while placing reentry straddle order: " + reverseTrade.getStockName() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken,botIdFinal);
                                                            } else {
                                                                sendMessage.sendToTelegram("Error while placing reentry straddle order: " + reverseTrade.getStockName(), telegramToken,botIdFinal);

                                                            }
                                                            //e.printStackTrace();
                                                        }
                                                    }


                                            }
                                        }
                                    }

                            });
                        });

            }
        });
    }
    public Map<Integer, BigDecimal> slPrice = new HashMap<>();
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    @Scheduled(cron = "${stradle.sl.immediate}")
    public void sLMonitorSchedulerImmediate() {
        // log.info("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getStraddleConfigOld() !=null &&  user.getStraddleConfigOld().isEnabled()).forEach(user -> {
            String botId="";
            TelegramBot telegramBot=user.getTelegramBot();
            if(telegramBot !=null){
                botId=telegramBot.getGroupId();
            }
            String botIdFinal=botId;
            if (user.getStraddleConfigOld().straddleTradeMap != null) {
                user.getStraddleConfigOld().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            // System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                            List<com.zerodhatech.models.Order> orderList = null;
                            try {
                                orderList = user.getKiteConnect().getOrders();
                                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                            } catch (KiteException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            Date date = new Date();
                            String currentDate = format.format(date);
                            orderList.stream().forEach(order -> {
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(5))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
                                    AtomicDouble triggerPriceAtomic = new AtomicDouble();
                                    if(slPrice.get(trendTradeData.getStockId())==null){
                                        try {
                                            String historicURL = "https://api.kite.trade/instruments/historical/" + trendTradeData.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                            System.out.print(response);
                                            HistoricalData historicalData = new HistoricalData();
                                            JSONObject json = new JSONObject(response);
                                            String status = json.getString("status");

                                            if (!status.equals("error")) {
                                                historicalData.parseResponse(json);
                                                System.out.println();
                                                historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                                                    try {
                                                        Date openDatetime = sdf.parse(historicalData1.timeStamp);
                                                        String openDate = format.format(openDatetime);
                                                        if (sdf.format(openDatetime).equals(openDate + "T09:19:00")) {
                                                            BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                            trendTradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                                            triggerPriceAtomic.addAndGet(triggerPriceTemp.doubleValue());
                                                            slPrice.put(trendTradeData.getStockId(),triggerPriceTemp);
                                                            System.out.println("setting sl price based on 9:19 close :"+trendTradeData.getStockId()+":"+triggerPriceTemp);
                                                        }
                                                    } catch (ParseException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                });
                                            }
                                        }catch (Exception e){
                                            System.out.println(e.getMessage());
                                        }
                                    }else {
                                        triggerPrice=slPrice.get(trendTradeData.getStockId());
                                    }
                                    if(triggerPriceAtomic.get()>0){
                                        triggerPrice= BigDecimal.valueOf(triggerPriceAtomic.get());
                                    }
                                    OrderParams orderParams = new OrderParams();
                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                    orderParams.exchange = "NFO";
                                    orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                    orderParams.orderType = "SL";
                                    orderParams.product = "MIS";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100)).multiply(new BigDecimal(5))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                    orderParams.triggerPrice = triggerPrice.doubleValue();
                                    trendTradeData.setSlPrice(triggerPrice);
                                    orderParams.price = price.doubleValue();
                                    orderParams.transactionType = "BUY";
                                    orderParams.validity = "DAY";
                                    com.zerodhatech.models.Order orderResponse = null;
                                    try {
                                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        trendTradeData.setSlOrderId(orderResponse.orderId);
                                        trendTradeData.isSlPlaced = true;
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName(), telegramToken,botIdFinal);
                                        log.info("SL order placed for: " + trendTradeData.getStockName());

                                    } catch (KiteException e) {
                                        log.info("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message, telegramToken,botIdFinal);
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken,botIdFinal);
                                        e.printStackTrace();
                                    }
                                } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;
                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                                    } else if ("COMPLETE".equals(order.status)) {
                                        trendTradeData.isSLHit = true;
                                        trendTradeData.isExited = true;
                                        LocalDate localDate = LocalDate.now();
                                        DayOfWeek dow = localDate.getDayOfWeek();
                                        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                        String todayCaps = today.toUpperCase();
                                        String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken, botIdFinal);
                                        if (user.getStraddleConfigOld() != null && user.getStraddleConfigOld().reverseEntry != null && user.getStraddleConfigOld().reverseEntry.isEnabled()) {
                                            ReverseEntry reverseEntry=user.getStraddleConfigOld().reverseEntry;
                                            int totalRetry=0;
                                            AtomicInteger retryCount=new AtomicInteger();
                                            reverseEntry.retryCountConfig.forEach((lotValue, value1) -> {
                                                    if (lotValue.contains(todayCaps)) {
                                                        int value = (Integer.parseInt(value1));
                                                        retryCount.getAndSet(value);
                                                    }
                                                });
                                            TradeData parentTradeData=null;
                                            if(trendTradeData.getStockName().contains("REENTRY")){
                                                parentTradeData=user.getStraddleConfigOld().straddleTradeMap.get(trendTradeData.getParentEntry());
                                                totalRetry=parentTradeData.getRentryCount();
                                            }else {
                                                totalRetry=trendTradeData.getRentryCount();
                                            }
                                            if (totalRetry<retryCount.get()) {
                                                log.info("inside reverse entry sell");
                                                //.setRentryCount(1);

                                                OrderParams orderParams = new OrderParams();
                                                orderParams.tradingsymbol = trendTradeData.getStockName();
                                                orderParams.exchange = "NFO";
                                                orderParams.quantity = trendTradeData.getQty();
                                                orderParams.orderType = "SL";
                                                orderParams.product = "MIS";
                                                orderParams.transactionType = "SELL";
                                                orderParams.validity = "DAY";
                                                com.zerodhatech.models.Order orderd=null;
                                                orderParams.triggerPrice = trendTradeData.getSellPrice().doubleValue();
                                                BigDecimal price = trendTradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).subtract(trendTradeData.getSellPrice().divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                orderParams.price = price.doubleValue();
                                                TradeData reverseTrade = new TradeData();
                                                int retryCountN=0;
                                                if(trendTradeData.getStockName().contains("REENTRY") && parentTradeData!=null){
                                                    retryCountN=parentTradeData.getRentryCount()+1;
                                                    parentTradeData.setRentryCount(retryCountN);
                                                    reverseTrade.setParentEntry(parentTradeData.getStockName());
                                                }else if(!trendTradeData.getStockName().contains("REENTRY")){
                                                    retryCountN=1;
                                                    trendTradeData.setRentryCount(retryCountN);
                                                }
                                                reverseTrade.setStockName(trendTradeData.getStockName());
                                                try {
                                                    orderd = user.getKiteConnect().placeOrder(orderParams, "regular");
                                                    reverseTrade.setEntryOrderId(orderd.orderId);
                                                    reverseTrade.isOrderPlaced = true;
                                                    reverseTrade.setQty(trendTradeData.getQty());
                                                    reverseTrade.setEntryType("SELL");
                                                    sendMessage.sendToTelegram("reentry Straddle option order placed for strike: " + reverseTrade.getStockName(),  telegramToken,botIdFinal);
                                                    user.getStraddleConfigOld().straddleTradeMap.put(trendTradeData.getStockName() + "-REENTRY_"+retryCountN, reverseTrade);

                                                } catch (KiteException | IOException e) {
                                                    reverseTrade.isErrored = true;
                                                    System.out.println("Error while placing straddle order: " + e.getMessage()+":"+new Gson().toJson(orderParams));
                                                    System.out.println("order reseponse: " +new Gson().toJson(orderd));
                                                    if (order != null) {
                                                        sendMessage.sendToTelegram("Error while placing reentry straddle order: " + reverseTrade.getStockName() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken,botIdFinal);
                                                    } else {
                                                        sendMessage.sendToTelegram("Error while placing reentry straddle order: " + reverseTrade.getStockName(), telegramToken,botIdFinal);

                                                    }
                                                    //e.printStackTrace();
                                                }
                                            }


                                        }
                                    }
                                }

                            });
                        });

            }
        });
    }
    @Scheduled(cron = "${straddle.exit.position.scheduler}")
    public void exitPositions() throws KiteException, IOException {
        System.out.println("Straddle Exit positions scheduler started");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        userList.getUser().stream().filter(user -> user.getStraddleConfigOld() !=null && user.getStraddleConfigOld().isEnabled()).forEach(user -> {
            String botId="";
            TelegramBot telegramBot=user.getTelegramBot();
            if(telegramBot !=null){
                botId=telegramBot.getGroupId();
            }
            String botIdFinal=botId;
            if (user.getStraddleConfigOld().straddleTradeMap != null) {
                user.getStraddleConfigOld().straddleTradeMap.entrySet().stream().filter(orbTradeDataEntity -> !orbTradeDataEntity.getValue().isExited && !orbTradeDataEntity.getValue().isSLHit).forEach(trendMap -> {
                    executor.submit(() -> {
                        TradeData trendTradeData = trendMap.getValue();
                        if (trendTradeData.getSlOrderId() != null && trendTradeData.getSlOrderId() != "") {
                            try {
                                if (trendTradeData.isSlPlaced &&!trendTradeData.isSLCancelled && !trendTradeData.isSLHit) {
                                    Order order = user.getKiteConnect().cancelOrder(trendTradeData.getSlOrderId(), "regular");
                                    trendMap.getValue().isSLCancelled = true;
                                    String message = MessageFormat.format("System Cancelled SL {0}", trendMap.getValue().getStockName());
                                    System.out.println(message);
                                    sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                                }
                            } catch (KiteException e) {
                                e.printStackTrace();
                                String message = MessageFormat.format("Error while System cancelling SL {0},[1]", trendMap.getValue().getStockName(), e.message);
                                System.out.println(message);
                                sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                            } catch (IOException e) {
                                e.printStackTrace();
                                String message = MessageFormat.format("Error while System cancelling SL {0},{1}", trendMap.getValue().getStockName(), e.getMessage());
                                System.out.println(message);
                                sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                            }
                        }
                        if(!trendTradeData.isSlPlaced && !trendTradeData.isExited && trendTradeData.isOrderPlaced){
                            try {
                                user.getKiteConnect().cancelOrder(trendTradeData.getEntryOrderId(), "regular");
                                String message = MessageFormat.format("System Cancelled Re-Entry Order {0}", trendTradeData.getStockName());
                                System.out.println(message);
                                sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                            } catch (KiteException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                    });

                });

                List<Position> positions = null;
                try {
                    positions = user.getKiteConnect().getPositions().get("net");
                    System.out.println(positions);
                } catch (KiteException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                positions.stream().filter(position -> "MIS".equals(position.product) && (position.netQuantity != 0)).forEach(position -> {
                    //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                    OrderParams orderParams = new OrderParams();
                    orderParams.tradingsymbol = position.tradingSymbol;
                    orderParams.exchange = "NFO";
                    orderParams.quantity = Math.abs(position.netQuantity);
                    orderParams.orderType = "MARKET";
                    orderParams.product = "MIS";
                    //orderParams.price=price.doubleValue();
                    if (position.netQuantity > 0) {
                        orderParams.transactionType = "SELL";
                    } else {
                        orderParams.transactionType = "BUY";
                    }
                    orderParams.validity = "DAY";
                    com.zerodhatech.models.Order orderResponse = null;
                    try {
                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                        System.out.println(new Gson().toJson(orderResponse));
                        if (user.getStraddleConfigOld().straddleTradeMap.get(position.tradingSymbol) != null) {
                            user.getStraddleConfigOld().straddleTradeMap.get(position.tradingSymbol).isExited = true;
                        }
                        String message = MessageFormat.format("Closed Position {0}", orderParams.tradingsymbol);
                        System.out.println(message);
                        sendMessage.sendToTelegram(message, telegramToken,botIdFinal);
                    } catch (KiteException e) {
                        System.out.println("Error while placing straddle order: " + e.message);
                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken,botIdFinal);
                        e.printStackTrace();
                    } catch (IOException e) {
                        System.out.println("Error while placing straddle order: " + e.getMessage());
                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken,botIdFinal);
                        e.printStackTrace();
                    }
                    //  }
                });

            }});
    }

    @Scheduled(cron = "${straddle.monitor.position.scheduler}")
    public void monitorPositions() throws KiteException, IOException {
        userList.getUser().stream().filter(user ->user.getStraddleConfigOld() !=null && user.getStraddleConfigOld().isEnabled()).forEach(user -> {
            try {
                List<Position> positions = null;
                String botId = "";
                TelegramBot telegramBot = user.getTelegramBot();
                if (telegramBot != null) {
                    botId = telegramBot.getGroupId();
                }
                String botIdFinal = botId;
                try {
                    positions = user.getKiteConnect().getPositions().get("net");
                } catch (KiteException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                positions.stream().filter(position -> position.netQuantity == 0 && "MIS".equals(position.product) && user.getStraddleConfigOld() != null && user.getStraddleConfigOld().straddleTradeMap != null && user.getStraddleConfigOld().straddleTradeMap.get(position.tradingSymbol) != null && !user.getStraddleConfig().straddleTradeMap.get(position.tradingSymbol).isExited).forEach(position -> {
                    if (user.getStraddleConfigOld() != null) {
                        TradeData tradeData = user.getStraddleConfigOld().straddleTradeMap.get(position.tradingSymbol);
                        List<com.zerodhatech.models.Order> orderList = null;
                        try {
                            orderList = user.getKiteConnect().getOrders();
                            //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                        } catch (KiteException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        orderList.stream().forEach(order -> {

                            if (("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId()) && tradeData.isSlPlaced && !tradeData.isSLCancelled && !tradeData.isExited && !tradeData.isSLHit) {
                                tradeData.isExited = true;
                                try {
                                    Order orderC = user.getKiteConnect().cancelOrder(tradeData.getSlOrderId(), "regular");
                                } catch (KiteException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                tradeData.isSLCancelled = true;
                                String message = MessageFormat.format("System Cancelled SL {0}", tradeData.getStockName());
                                log.info(message);
                                sendMessage.sendToTelegram(message, telegramToken);
                            }
                        });
                    }
                });
            }catch (Exception e){
                log.info("error while executing monitorsl:"+user.getName()+":"+e.getMessage());
            }
        });
    }

}
