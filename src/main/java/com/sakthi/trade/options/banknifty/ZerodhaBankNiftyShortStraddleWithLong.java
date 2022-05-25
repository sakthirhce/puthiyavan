package com.sakthi.trade.options.banknifty;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
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
public class ZerodhaBankNiftyShortStraddleWithLong {

    @Autowired
    CommonUtil commonUtil;


    @Value("${telegram.straddle.bot.token}")
    String telegramToken;

    // AtomicInteger doubleTopCount = new AtomicInteger(0);

    @Value("${telegram.orb.bot.token}")
    String telegramTokenGroup;
    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;

    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    public Map<String, String> atmStrikeMap = new HashMap<>();
    public Map<Integer, BigDecimal> slPrice = new HashMap<>();
    @Autowired
    UserList userList;

    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;

    public String getAlgoName() {
        return "STRADDLE_LONG";
    }

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @Scheduled(cron = "${banknifty.nrml.straddle.schedule}")
    public void zerodhaBankNifty() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = new Date();
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
            historicalData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T09:30:00")) {
                        System.out.println(historicalData1.close);
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        System.out.println("Bank Nifty:" + atmStrike);
                        final Map<String, String> atmStrikesStraddle;
                        if (zerodhaTransactionService.expDate.equals(currentDate)) {
                            atmStrikesStraddle = zerodhaTransactionService.bankNiftyNextWeeklyOptions.get(String.valueOf(atmStrike));
                        } else {
                            atmStrikesStraddle = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                        }
                        atmStrikesStraddle.forEach((key, value) -> {
                            System.out.println(key + ":" + value);
                            atmStrikeMap.put(key, value);
                        });
                        atmStrikesStraddle.entrySet().stream().filter(atmStrikeStraddle -> atmStrikeStraddle.getKey().contains(String.valueOf(atmStrike))).forEach(atmBankStrikeMap -> {
                            executorService.submit(() -> {
                                System.out.println(atmBankStrikeMap.getKey());
                                OrderParams orderParams = new OrderParams();
                                orderParams.tradingsymbol = atmBankStrikeMap.getKey();
                                orderParams.exchange = "NFO";
                                orderParams.orderType = "MARKET";
                                orderParams.product = "NRML";
                                orderParams.transactionType = "SELL";
                                orderParams.validity = "DAY";
                                LocalDate localDate = LocalDate.now();
                                DayOfWeek dow = localDate.getDayOfWeek();
                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                String todayCaps = today.toUpperCase();
                                userList.getUser().stream().filter(
                                        user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled() && user.getStraddleConfig().getLotConfig().containsKey(todayCaps)
                                ).forEach(user -> {
                                    AtomicInteger qty = new AtomicInteger(1);
                                    user.getStraddleConfig().getLotConfig().entrySet().forEach(day -> {
                                        String lotValue = day.getKey();
                                        if (lotValue.contains(todayCaps)) {
                                            int value = Integer.parseInt(day.getValue());
                                            qty.getAndSet(value);
                                        }
                                    });
                                    Order order = null;
                                    orderParams.quantity = 25 * qty.get();
                                    TradeData tradeData = new TradeData();
                                    String dataKey = UUID.randomUUID().toString();
                                    tradeData.setDataKey(dataKey);
                                    tradeData.setStockName(atmBankStrikeMap.getKey());
                                    try {
                                        order = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        tradeData.setEntryOrderId(order.orderId);
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setQty(25 * qty.get());
                                        tradeData.setEntryType("SELL");
                                        tradeData.setUserId(user.getName());
                                        tradeData.setStockId(Integer.valueOf(atmBankStrikeMap.getValue()));
                                        mapTradeDataToSaveOpenTradeDataEntity(tradeData);
                                        sendMessage.sendToTelegram("Straddle option sold for user:" + user.getName() + " strike: " + atmBankStrikeMap.getKey(), telegramTokenGroup, "-713214125");
                                    } catch (KiteException e) {
                                        tradeData.isErrored = true;
                                        System.out.println("Error while placing straddle order: " + e.message);

                                        sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ":" + user.getName() + ",Exception:" + e.getMessage(), telegramTokenGroup, "-713214125");

                                    } catch (IOException e) {
                                        tradeData.isErrored = true;
                                        System.out.println("Error while placing straddle order: " + e.getMessage());
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ",Exception:" + e.getMessage(), telegramTokenGroup, "-713214125");
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ":" + user.getName() + ",Exception:" + e.getMessage(), telegramTokenGroup, "-713214125");
                                        }
                                    }
                                    user.getStraddleConfig().straddleTradeMap.put(atmBankStrikeMap.getKey(), tradeData);
                                });
                            });
                        });

                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            });

        } else {
            sendMessage.sendToTelegram("Error while calling history api for straddle", telegramTokenGroup, "-713214125");
        }
        stopWatch.stop();
        System.out.println("process completed in ms:" + stopWatch.getTotalTimeMillis());
    }

    List<OpenTradeDataEntity> openTradeDataEntities = new ArrayList<>();

    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;
    Gson gson = new Gson();

    @Scheduled(cron = "${straddle.nrml.sl.immediate.scheduler}")
    public void sLMonitorSchedulerImmediate() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
try{
            if (user.getStraddleConfig().straddleTradeMap != null) {
                BigDecimal slPercent=user.getStraddleConfig().getSl();
                user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            // System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                            List<Order> orderList = null;
                            try {
                                orderList = user.getKiteConnect().getOrders();
                                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                            } catch (KiteException | IOException e) {
                                e.printStackTrace();
                            }
                            orderList.forEach(order -> {
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && "SELL".equals(order.transactionType)) {
                                    if (!trendTradeData.isSlPlaced && "COMPLETE".equals(order.status)) {
                                        Date date = new Date();
                                        String currentDate = format.format(date);
                                        BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(5))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
                                        AtomicDouble triggerPriceAtomic = new AtomicDouble();
                                        if (slPrice.get(trendTradeData.getStockId()) == null) {
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
                                                            if (sdf.format(openDatetime).equals(openDate + "T09:34:00")) {
                                                                //BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                                BigDecimal triggerPriceTemp=MathUtils.percentageValueOfAmount(slPercent,new BigDecimal(historicalData1.close));
                                                                trendTradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                                                triggerPriceAtomic.addAndGet(triggerPriceTemp.doubleValue());
                                                                slPrice.put(trendTradeData.getStockId(), triggerPriceTemp);
                                                                System.out.println("setting sl price based on 9:34 close :" + trendTradeData.getStockId() + ":" + triggerPriceTemp);
                                                            }
                                                        } catch (ParseException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    });
                                                }
                                            } catch (Exception e) {
                                                System.out.println(e.getMessage());
                                            }
                                        } else {
                                            triggerPrice = slPrice.get(trendTradeData.getStockId());
                                        }
                                        if (triggerPriceAtomic.get() > 0) {
                                            triggerPrice = BigDecimal.valueOf(triggerPriceAtomic.get());
                                        }
                                        trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                        trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                        trendTradeData.setSlPercentage(new BigDecimal("20"));
                                        OrderParams orderParams = new OrderParams();
                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                        orderParams.exchange = "NFO";
                                        LocalDate localDate = LocalDate.now();
                                        DayOfWeek dow = localDate.getDayOfWeek();
                                        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                        String todayCaps = today.toUpperCase();
                                        AtomicInteger qty = new AtomicInteger(0);
                                        if (user.getStraddleConfig().getBuyConfig() != null && user.getStraddleConfig().getBuyConfig().isEnabled()) {
                                            user.getStraddleConfig().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                                if (lotValue.contains(todayCaps)) {
                                                    int value = (Integer.parseInt(value1) * 25);
                                                    qty.getAndSet(value);
                                                }
                                            });
                                            qty.getAndAdd(trendTradeData.getQty());
                                        }
                                        orderParams.quantity = Integer.parseInt(order.filledQuantity) + qty.get();
                                        orderParams.orderType = "SL";
                                        orderParams.product = "NRML";
                                        BigDecimal price = ((triggerPrice.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                        orderParams.triggerPrice = triggerPrice.doubleValue();
                                        trendTradeData.setSlPrice(triggerPrice);
                                        orderParams.price = price.doubleValue();
                                        orderParams.transactionType = "BUY";
                                        orderParams.validity = "DAY";
                                        Order orderResponse;
                                        try {
                                            orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                            trendTradeData.setSlOrderId(orderResponse.orderId);
                                            trendTradeData.setSlPrice(new BigDecimal(orderParams.triggerPrice));
                                            trendTradeData.isSlPlaced = true;
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                            BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                            sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName() + ":" + user.getName() + ": sell slipage" + slipage.toString(), telegramTokenGroup, "-713214125");
                                            System.out.println("Placed SL order for: " + trendTradeData.getStockName() + ":" + user.getName() + ":" + trendTradeData.getSlOrderId());

                                        } catch (KiteException e) {
                                            System.out.println("Error while placing straddle order: " + e.message);
                                            sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ":" + user.getName() + ":" + " error message:" + e.message + ":" + user.getName(), telegramTokenGroup, "-713214125");
                                            e.printStackTrace();
                                        } catch (Exception e) {
                                            System.out.println("Error while placing straddle order: " + e.getMessage());
                                            sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ":" + user.getName() + ":" + ": error message:" + e.getMessage() + ":" + user.getName(), telegramTokenGroup, "-713214125");
                                            e.printStackTrace();
                                        }
                                    } else if ("REJECTED".equals(order.status)) {
                                        String message = MessageFormat.format("Entry order placement rejected for {0}", map.getKey() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage);
                                        System.out.println(message);
                                        trendTradeData.isErrored = true;
                                        sendMessage.sendToTelegram(message, telegramTokenGroup, "-713214125");
                                    }
                                }
                            });
                        });
            }}catch (Exception e){
            e.printStackTrace();
            }
        });
    }

    @Scheduled(cron = "${straddle.nrml.sl.scheduler}")
    public void sLMonitorScheduler() {
        //  System.out.println("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {

            if (user.getStraddleConfig().straddleTradeMap != null) {
                BigDecimal slPercent=user.getStraddleConfig().getSl();
                user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            // System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                            List<Position> positions = null;
                            List<Order> orderList = null;
                            try {
                                orderList = user.getKiteConnect().getOrders();
                                positions = user.getKiteConnect().getPositions().get("net");
                                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                            } catch (KiteException | IOException e) {
                                e.printStackTrace();
                            }
                            final List<Position> positionsFinall = positions;
                            orderList.forEach(order -> {
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    Date date = new Date();
                                    String currentDate = format.format(date);
                                    BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(5))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
                                    AtomicDouble triggerPriceAtomic = new AtomicDouble();
                                    if (slPrice.get(trendTradeData.getStockId()) == null) {
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
                                                        if (sdf.format(openDatetime).equals(openDate + "T09:34:00")) {
                                                           // BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                            BigDecimal triggerPriceTemp=MathUtils.percentageValueOfAmount(slPercent,new BigDecimal(historicalData1.close));
                                                            trendTradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                                            triggerPriceAtomic.addAndGet(triggerPriceTemp.doubleValue());
                                                            slPrice.put(trendTradeData.getStockId(), triggerPriceTemp);
                                                            System.out.println("setting sl price based on 9:34 close :" + trendTradeData.getStockId() + ":" + triggerPriceTemp);
                                                        }
                                                    } catch (ParseException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                });
                                            }
                                        } catch (Exception e) {
                                            System.out.println(e.getMessage());
                                        }
                                    } else {
                                        triggerPrice = slPrice.get(trendTradeData.getStockId());
                                    }
                                    if (triggerPriceAtomic.get() > 0) {
                                        triggerPrice = BigDecimal.valueOf(triggerPriceAtomic.get());
                                    }
                                    trendTradeData.setSlPercentage(slPercent);
                                    OrderParams orderParams = new OrderParams();
                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                    orderParams.exchange = "NFO";
                                    LocalDate localDate = LocalDate.now();
                                    DayOfWeek dow = localDate.getDayOfWeek();
                                    String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                    String todayCaps = today.toUpperCase();
                                    AtomicInteger qty = new AtomicInteger(0);
                                    if (user.getStraddleConfig().getBuyConfig() != null && user.getStraddleConfig().getBuyConfig().isEnabled()) {
                                        user.getStraddleConfig().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                            if (lotValue.contains(todayCaps)) {
                                                int value = (Integer.parseInt(value1) * 25);
                                                qty.getAndSet(value);
                                            }
                                        });
                                        qty.getAndAdd(trendTradeData.getQty());
                                    }
                                    orderParams.quantity = Integer.parseInt(order.filledQuantity) + qty.get();
                                    orderParams.orderType = "SL";
                                    orderParams.product = "NRML";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100)).multiply(new BigDecimal(5))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                    orderParams.triggerPrice = triggerPrice.doubleValue();
                                    trendTradeData.setSlPrice(triggerPrice);
                                    orderParams.price = price.doubleValue();
                                    orderParams.transactionType = "BUY";
                                    orderParams.validity = "DAY";
                                    Order orderResponse;
                                    try {
                                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        trendTradeData.setSlOrderId(orderResponse.orderId);
                                        trendTradeData.setSlPrice(new BigDecimal(orderParams.triggerPrice));
                                        trendTradeData.isSlPlaced = true;
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName() + ":" + user.getName(), telegramTokenGroup, "-713214125");
                                        System.out.println("SL order placed for: " + trendTradeData.getStockName());

                                    } catch (KiteException e) {
                                        System.out.println("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ":" + user.getName() + ":" + " error message:" + e.message + ":" + user.getName(), telegramTokenGroup, "-713214125");
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        System.out.println("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ":" + user.getName() + ":" + ": error message:" + e.getMessage() + ":" + user.getName(), telegramTokenGroup, "-713214125");
                                        e.printStackTrace();
                                    }
                                } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced && !trendTradeData.isErrored && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;
                                        if ("SELL".equals(trendTradeData.getEntryType())) {
                                            String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName());
                                            System.out.println(message);
                                            sendMessage.sendToTelegram(message, telegramTokenGroup, "-713214125");

                                        } else {
                                            String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName());
                                            System.out.println(message);
                                            sendMessage.sendToTelegram(message, telegramTokenGroup, telegramToken);
                                        }

                                    }
                                    /*if (order.orderId.equals(trendTradeData.getSlOrderId())){
                                        if(("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status))  && !trendTradeData.isExited){
                                            //
                                            positionsFinall.stream().filter(position -> trendTradeData.getStockName().equals(position.tradingSymbol) && !trendTradeData.isExited && position.netQuantity == 0).findFirst().ifPresent(
                                                    position -> {
                                                        System.out.println("inside open sl cancellation method");
                                                        try {
                                                            user.getKiteConnect().cancelOrder(order.orderId, "regular");
                                                        } catch (KiteException | IOException e) {
                                                            System.out.println(e.getMessage());
                                                        }
                                                        trendTradeData.isExited = true;
                                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                                    }
                                            );
                                        }
                                    }*/
                                    if ("COMPLETE".equals(order.status)) {
                                        if ("BUY".equals(order.transactionType)) {
                                            trendTradeData.isSLHit = true;
                                            trendTradeData.isExited = true;
                                            trendTradeData.setExitOrderId(order.orderId);
                                            trendTradeData.setBuyPrice(trendTradeData.getSlPrice());
                                            trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                            try {
                                                BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                                String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName() + ":" + user.getName() + ": sl buy slipage:" + slipage.toString());
                                                System.out.println(message);
                                                sendMessage.sendToTelegram(message, telegramTokenGroup, "-713214125");
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);

                                            // if (doubleTopCount.get() == 0) {
                                            System.out.println("inside options stop sell");
                                            //doubleTopCount.getAndAdd(1);
                                            if (user.getStraddleConfig().buyConfig != null && user.getStraddleConfig().buyConfig.isEnabled()) {
                                            //    BigDecimal triggerPrice = trendTradeData.getSlPrice().subtract(trendTradeData.getSlPrice().divide(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                BigDecimal triggerPriceTemp=MathUtils.percentageValueOfAmount(slPercent,trendTradeData.getSlPrice());
                                                System.out.println("inside options stop sell filter" + trendTradeData.getStockName());
                                                LocalDate localDate = LocalDate.now();
                                                DayOfWeek dow = localDate.getDayOfWeek();
                                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                                String todayCaps = today.toUpperCase();
                                                AtomicInteger qty = new AtomicInteger(0);
                                                if (user.getStraddleConfig().getBuyConfig() != null && user.getStraddleConfig().getBuyConfig().isEnabled()) {
                                                    user.getStraddleConfig().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                                        if (lotValue.contains(todayCaps)) {
                                                            int value = (Integer.parseInt(value1) * 25);
                                                            qty.getAndSet(value);
                                                        }
                                                    });
                                                    qty.getAndAdd(trendTradeData.getQty());
                                                }
                                                if (qty.get() > 0 && user.getStraddleConfig().getBuyConfig() != null && user.getStraddleConfig().getBuyConfig().isEnabled()) {
                                                    OrderParams orderParams = new OrderParams();
                                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                                    orderParams.exchange = "NFO";
                                                    orderParams.quantity = qty.get();
                                                    orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                                    BigDecimal price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams.price = price.doubleValue();
                                                    orderParams.orderType = "SL";
                                                    orderParams.product = "NRML";
                                                    orderParams.transactionType = "SELL";
                                                    orderParams.validity = "DAY";
                                                    com.zerodhatech.models.Order orderd;
                                                    TradeData doubleToptradeData = new TradeData();
                                                    doubleToptradeData.setStockName(trendTradeData.getStockName());
                                                    String dataKey = UUID.randomUUID().toString();
                                                    doubleToptradeData.setDataKey(dataKey);
                                                    try {
                                                        orderd = user.getKiteConnect().placeOrder(orderParams, "regular");
                                                        doubleToptradeData.setEntryOrderId(trendTradeData.getSlOrderId());
                                                        doubleToptradeData.isOrderPlaced = true;
                                                        doubleToptradeData.isSlPlaced = true;
                                                        doubleToptradeData.setBuyPrice(trendTradeData.getSlPrice());
                                                        doubleToptradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                                        doubleToptradeData.setStockId(trendTradeData.getStockId());
                                                        doubleToptradeData.setSlPrice(triggerPriceTemp);
                                                        doubleToptradeData.setQty(trendTradeData.getQty());
                                                        doubleToptradeData.setSlOrderId(orderd.orderId);
                                                        doubleToptradeData.setUserId(user.getName());
                                                        doubleToptradeData.setSlPercentage(slPercent);
                                                        doubleToptradeData.setEntryType("BUY");
                                                        mapTradeDataToSaveOpenTradeDataEntity(doubleToptradeData);
                                                        sendMessage.sendToTelegram("Straddle option bought for strike: " + doubleToptradeData.getStockName() + ":" + user.getName() + " and placed SL", telegramToken);
                                                        user.getStraddleConfig().straddleTradeMap.put(trendTradeData.getStockName() + "-BUY", doubleToptradeData);

                                                    } catch (KiteException | IOException e) {
                                                        // tradeData.isErrored = true;
                                                        System.out.println("Error while placing straddle order: " + e.getMessage() + ":" + user.getName());
                                                        sendMessage.sendToTelegram("Error while placing Double top straddle order: " + doubleToptradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                                                        //e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }
                                        if ("SELL".equals(order.transactionType) && map.getKey().contains("BUY") && trendTradeData.getSlOrderId().equals(order.orderId) && !trendTradeData.isExited) {
                                            trendTradeData.isSLHit = true;
                                            trendTradeData.isExited = true;
                                            trendTradeData.setExitOrderId(order.orderId);
                                            trendTradeData.setSellPrice(trendTradeData.getSlPrice());
                                            trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                            BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                            String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage.toString(), map.getKey() + ":" + user.getName());
                                            System.out.println(message);
                                            sendMessage.sendToTelegram(message, telegramToken);
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        }

                                    } else if ("REJECTED".equals(order.status)) {
                                        String message = MessageFormat.format("SL order placement rejected for {0}", map.getKey() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage);
                                        System.out.println(message);
                                        trendTradeData.isErrored = true;
                                        sendMessage.sendToTelegram(message, telegramTokenGroup, "-713214125");
                                    }
                                }

                            });
                        });

            }
        });
    }

    public void pLAndSlippageCalculation() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orderList = user.getKiteConnect().getOrders();
                if (user.getStraddleConfig().straddleTradeMap != null) {
                    user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isExited)
                            .forEach(openTradeData -> {

                                TradeData tradeData = openTradeData.getValue();
                                orderList.stream().filter(order -> tradeData.getSlOrderId().equals(order.orderId)).findFirst().ifPresent(order -> {
                                    tradeData.setSlExecutedPrice(new BigDecimal(order.averagePrice));
                                    BigDecimal slSlipage;
                                    if ("SELL".equals(tradeData.getEntryType())) {
                                        slSlipage = tradeData.getSlPrice().subtract(tradeData.getSlExecutedPrice());
                                    } else {
                                        slSlipage = tradeData.getSlExecutedPrice().subtract(tradeData.getSlPrice());
                                    }
                                    tradeData.setSlSlipage(slSlipage);
                                });
                                HistoricalData historicalData = new HistoricalData();
                                String status;
                                Date date = new Date();
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                                String currentDate = format.format(date);
                                String historicURL = "https://api.kite.trade/instruments/historical/" + tradeData.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                System.out.print(tradeData.getStockName() + " history api response:" + response);

                                JSONObject json = new JSONObject(response);
                                status = json.getString("status");
                                if (!status.equals("error")) {
                                    historicalData.parseResponse(json);
                                }
                                if (historicalData.dataArrayList.size() > 0) {
                                    HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);

                                    if (!status.equals("error") && lastMin.close < tradeData.getSlPrice().doubleValue()) {
                                        System.out.println(tradeData.getUserId() + ":" + tradeData.getStockName() + ":inside buy entry type  sl condition, close is buy than sl price:" + lastMin.close + ":" + tradeData.getSlPrice().doubleValue());
                                        tradeData.setSlPrice(new BigDecimal(lastMin.close));
                                    }
                                }
                            });
                }
            } catch (KiteException | IOException e) {
                e.printStackTrace();
            }
        });


    }

    @Scheduled(cron = "${banknifty.nrml.nextday.position.load.schedule}")
    public void loadNrmlPositions() {
        Iterable<OpenTradeDataEntity> openTradeDataEntities1 = openTradeDataRepo.findAll();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored) {
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    List<Position> positions = null;
                    try {
                        positions = user.getKiteConnect().getPositions().get("net");
                    } catch (KiteException | IOException e) {
                        System.out.println("error wile calling position :" + openTradeDataEntity.getUserId());
                    }
                    positions.stream().filter(position -> "NRML".equals(position.product) && openTradeDataEntity.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).findFirst().ifPresent(position -> {
                        int positionQty = Math.abs(position.netQuantity);
                        if (positionQty != openTradeDataEntity.getQty()) {
                            //   openTradeDataEntity.setQty(positionQty);
                            if ("SELL".equals(openTradeDataEntity.getEntryType())) {
                                System.out.println("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.");
                                sendMessage.sendToTelegram("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.", telegramTokenGroup, "-713214125");

                            } else {
                                System.out.println("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.");
                                sendMessage.sendToTelegram("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ",over riding position qty as trade qty.", telegramToken);
                            }
                        }
                        openTradeDataEntity.isSlPlaced = false;
                        openTradeDataEntity.setSlOrderId(null);
                        openTradeDataEntities.add(openTradeDataEntity);
                        if ("SELL".equals(openTradeDataEntity.getEntryType())) {
                            sendMessage.sendToTelegram("Open Position: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");

                        } else {
                            sendMessage.sendToTelegram("Open Position: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramToken);
                        }

                    });
                } else {
                    String openStr = gson.toJson(openTradeDataEntity);
                    OpenTradeDataBackupEntity openTradeDataBackupEntity = gson.fromJson(openStr, OpenTradeDataBackupEntity.class);
                    openTradeDataBackupRepo.save(openTradeDataBackupEntity);
                    openTradeDataRepo.deleteById(openTradeDataEntity.getDataKey());
                }
            }
        });
    }

    @Scheduled(cron = "${banknifty.nrml.nextday.exit.price.schedule}")
    public void exitPriceNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orders = user.getKiteConnect().getOrders();
                List<Position> positions = user.getKiteConnect().getPositions().get("net");
                System.out.println(positions);
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited && user.getName().equals(openTradeDataEntity.getUserId())).forEach(openTradeDataEntity -> {
                    orders.stream().filter(order -> "COMPLETE".equals(order.status) && order.orderId.equals(openTradeDataEntity.getExitOrderId())).findFirst().ifPresent(orderr -> {
                        try {
                            Date date = new Date();
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                            String currentDate = format.format(date);
                            try {


                                String historicURL = "https://api.kite.trade/instruments/historical/" + openTradeDataEntity.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                System.out.print(openTradeDataEntity.getStockName() + " history api response:" + response);
                                HistoricalData historicalData = new HistoricalData();
                                JSONObject json = new JSONObject(response);
                                String status = json.getString("status");
                                if (!status.equals("error")) {
                                    historicalData.parseResponse(json);
                                }
                                if (historicalData.dataArrayList.size() > 0) {
                                    historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                                        try {
                                            Date openDatetime = sdf.parse(historicalData1.timeStamp);
                                            String openDate = format.format(openDatetime);
                                            if (sdf.format(openDatetime).equals(openDate + "T09:33:00")) {
                                                BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                if ("SELL".equals(orderr.transactionType)) {
                                                    openTradeDataEntity.setSellPrice(new BigDecimal(historicalData1.close));
                                                } else {
                                                    openTradeDataEntity.setBuyPrice(new BigDecimal(orderr.averagePrice));
                                                }
                                                System.out.println("setting  9:34 exit :" + openTradeDataEntity.getStockId() + ":" + historicalData1.close);
                                            }
                                        } catch (ParseException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                }

                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                            openTradeDataEntity.isExited = true;
                            if ("SELL".equals(orderr.transactionType)) {
                                openTradeDataEntity.setSellTradedPrice(new BigDecimal(orderr.averagePrice));
                            } else {
                                openTradeDataEntity.setBuyTradedPrice(new BigDecimal(orderr.averagePrice));
                            }
                            saveTradeData(openTradeDataEntity);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    });
                });
            } catch (Exception | KiteException e) {
                System.out.println(e.getMessage());
            }
        });
    }

    @Scheduled(cron = "${banknifty.intraday.buy.exit.schedule}")
    public void buyIntradayExit() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orders = user.getKiteConnect().getOrders();
                List<Position> positions = user.getKiteConnect().getPositions().get("net");
                System.out.println(positions);
                if (user.getStraddleConfig().straddleTradeMap != null) {
                    user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null && !map.getValue().isExited).forEach(straddleTradeMap -> {
                        TradeData tradeData = straddleTradeMap.getValue();
                        if (user.getName().equals(straddleTradeMap.getValue().getUserId())) {
                            if (user.getStraddleConfig().buyConfig != null && user.getStraddleConfig().buyConfig.isEnabled()) {
                                LocalDate localDate = LocalDate.now();
                                DayOfWeek dow = localDate.getDayOfWeek();
                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                String todayCaps = today.toUpperCase();
                                AtomicInteger qty = new AtomicInteger(0);
                                user.getStraddleConfig().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                    if (lotValue.contains(todayCaps)) {
                                        int value = (Integer.parseInt(value1) * 25);
                                        qty.getAndSet(value);
                                        orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                            try {
                                                // if(straddleTradeMap.getKey().contains("BUY")) {
                                                OrderParams orderParams = new OrderParams();
                                                orderParams.quantity = Integer.parseInt(orderr.quantity) - qty.get();
                                                tradeData.setQty(orderParams.quantity);
                                                orderParams.tradingsymbol = tradeData.getStockName();
                                                orderParams.exchange = "NFO";
                                                orderParams.triggerPrice = tradeData.getSlPrice().doubleValue();
                                                BigDecimal price;
                                                if (straddleTradeMap.getKey().contains("BUY")) {
                                                    price = tradeData.getSlPrice().subtract(tradeData.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams.transactionType = "SELL";
                                                } else {
                                                    price = tradeData.getSlPrice().add(tradeData.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams.transactionType = "BUY";
                                                }
                                                orderParams.validity = "DAY";
                                                orderParams.price = price.doubleValue();
                                                orderParams.orderType = "SL";
                                                orderParams.product = "NRML";
                                                Order order = user.getKiteConnect().modifyOrder(tradeData.getSlOrderId(), orderParams, "regular");
                                                sendMessage.sendToTelegram("sl buy qty modified for nrml:" + tradeData.getUserId() + ": new sl qty:" + tradeData.getQty(), telegramToken);
                                                //}
                                            } catch (KiteException | IOException e) {
                                                System.out.println(e.getMessage());
                                            }
                                        });
                                        positions.stream().filter(position -> "NRML".equals(position.product) && straddleTradeMap.getKey().contains("BUY") && tradeData.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).forEach(position -> {
                                            //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = position.tradingSymbol;
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = qty.get();
                                            orderParams.orderType = "MARKET";
                                            orderParams.product = "NRML";
                                            //orderParams.price=price.doubleValue();
                                            if (position.netQuantity > 0) {
                                                orderParams.transactionType = "SELL";
                                                orderParams.validity = "DAY";
                                                com.zerodhatech.models.Order orderResponse = null;
                                                try {
                                                    orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                                    System.out.println(new Gson().toJson(orderResponse));
                                                    //  openTradeDataEntity.isExited=true;
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData);
                                                    String message = MessageFormat.format("Closed Intraday Buy Position {0}", orderParams.tradingsymbol);
                                                    System.out.println(message);

                                                    sendMessage.sendToTelegram(message + ":" + tradeData.getUserId(), telegramToken);

                                                } catch (KiteException e) {
                                                    System.out.println("Error while exiting straddle order: " + e.message);
                                                    sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken);
                                                    e.printStackTrace();
                                                } catch (IOException e) {
                                                    System.out.println("Error while exiting straddle order: " + e.getMessage());

                                                    sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken);
                                                    e.printStackTrace();
                                                }
                                            }
                                            //  }
                                        });
                                    }
                                });


                            }

                        }
                    });
                }

            } catch (Exception | KiteException e) {
                System.out.println(e.getMessage());
            }
        });
    }

    @Scheduled(cron = "${banknifty.nrml.nextday.sl.place.schedule}")
    public void placeSLNrmlPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            try {
                OrderParams orderParams = new OrderParams();
                orderParams.tradingsymbol = openTradeDataEntity.getStockName();
                orderParams.exchange = "NFO";
                orderParams.quantity = openTradeDataEntity.getQty();
                orderParams.triggerPrice = openTradeDataEntity.getSlPrice().doubleValue();
                Date date = new Date();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                String currentDate = format.format(date);
                orderParams.orderType = "SL";
                orderParams.product = "NRML";
                HistoricalData historicalData = new HistoricalData();
                String status = "error";
                try {
                    String historicURL = "https://api.kite.trade/instruments/historical/" + openTradeDataEntity.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+11:15:00";
                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                    System.out.print(openTradeDataEntity.getStockName() + " history api response:" + response);

                    JSONObject json = new JSONObject(response);
                    status = json.getString("status");
                    if (!status.equals("error")) {
                        historicalData.parseResponse(json);
                    }
                } catch (Exception e) {
                    System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage());
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage(), telegramTokenGroup, "-713214125");
                    } else {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage(), telegramToken);
                    }
                }

                if (openTradeDataEntity.getEntryType().equals("BUY")) {
                    orderParams.transactionType = "SELL";
                    if (historicalData.dataArrayList.size() > 0) {
                        HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);

                        if (!status.equals("error") && lastMin.close < openTradeDataEntity.getSlPrice().doubleValue()) {
                            System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":inside buy entry type  sl condition, close is buy than sl price:" + lastMin.close + ":" + openTradeDataEntity.getSlPrice().doubleValue());
                            //  openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                            orderParams.orderType = "MARKET";
                        }
                    }
                    BigDecimal price = openTradeDataEntity.getSlPrice().subtract(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                    orderParams.price = price.doubleValue();

                } else {
                    orderParams.transactionType = "BUY";
                    if (historicalData.dataArrayList.size() > 0) {
                        HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                        if (!status.equals("error") && lastMin.close > openTradeDataEntity.getSlPrice().doubleValue()) {
                            System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":inside sell entry type sl condition, close is greater than sl price:" + lastMin.close + ":" + openTradeDataEntity.getSlPrice().doubleValue());
                            // openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                            orderParams.orderType = "MARKET";
                        }
                    }
                    BigDecimal price = openTradeDataEntity.getSlPrice().add(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                    orderParams.price = price.doubleValue();
                    System.out.println(price.doubleValue());

                }

                orderParams.validity = "DAY";
                com.zerodhatech.models.Order orderd;
                try {
                    System.out.println("inside order placement");
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    System.out.println("order params" + new Gson().toJson(orderParams));
                    orderd = user.getKiteConnect().placeOrder(orderParams, "regular");
                    System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":order response:" + new Gson().toJson(orderd));
                    openTradeDataEntity.isSlPlaced = true;
                    openTradeDataEntity.setSlOrderId(orderd.orderId);
                    openTradeDataRepo.save(openTradeDataEntity);
                    if (orderParams.transactionType.equals("SELL")) {
                        sendMessage.sendToTelegram("placed sl order for:" + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram("placed sl order for:" + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                    }
                } catch (Exception e) {

                    System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage());
                    if (orderParams.transactionType.equals("SELL")) {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage(), telegramTokenGroup, "-713214125");
                    }
                } catch (KiteException e) {
                    System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage());
                    if (orderParams.transactionType.equals("SELL")) {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage(), telegramTokenGroup, "-713214125");
                    }
                    // throw new RuntimeException(e);
                }
            } catch (Exception e) {
                System.out.println(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while placing 9:16 sl order" + e.getMessage());
            }
        });


    }


    @Scheduled(cron = "${banknifty.nrml.nextday.exit.schedule}")
    public void exitNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orders = user.getKiteConnect().getOrders();
                List<Position> positions = user.getKiteConnect().getPositions().get("net");
                System.out.println(positions);
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited && user.getName().equals(openTradeDataEntity.getUserId())).forEach(openTradeDataEntity -> {
                            orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getSlOrderId())).forEach(orderr -> {
                                try {
                                    Order order = user.getKiteConnect().cancelOrder(orderr.orderId, "regular");
                                } catch (KiteException | IOException e) {
                                    System.out.println(e.getMessage());
                                }
                            });
                            positions.stream().filter(position -> "NRML".equals(position.product) && openTradeDataEntity.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).forEach(position -> {
                                //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                                OrderParams orderParams = new OrderParams();
                                orderParams.tradingsymbol = position.tradingSymbol;
                                orderParams.exchange = "NFO";
                                orderParams.quantity = openTradeDataEntity.getQty();
                                orderParams.orderType = "MARKET";
                                orderParams.product = "NRML";
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
                                    //  openTradeDataEntity.isExited=true;
                                    openTradeDataEntity.setExitOrderId(orderResponse.orderId);
                                    saveTradeData(openTradeDataEntity);
                                    String message = MessageFormat.format("Closed Position {0}", orderParams.tradingsymbol);
                                    System.out.println(message);
                                    if (orderParams.transactionType.equals("SELL")) {
                                        sendMessage.sendToTelegram(message + ":" + openTradeDataEntity.getUserId(), telegramToken);
                                    } else {
                                        sendMessage.sendToTelegram(message + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                                    }
                                } catch (KiteException e) {
                                    System.out.println("Error while exiting straddle order: " + e.message);
                                    if (orderParams.transactionType.equals("SELL")) {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken);
                                    } else {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramTokenGroup, "-713214125");
                                    }
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    System.out.println("Error while exiting straddle order: " + e.getMessage());
                                    if (orderParams.transactionType.equals("SELL")) {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken);
                                    } else {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramTokenGroup, "-713214125");
                                    }
                                    e.printStackTrace();
                                }
                                //  }
                            });

                        }
                );
            } catch (KiteException | IOException e) {
                System.out.println(e.getMessage());
            }


        });
    }

    @Scheduled(cron = "${banknifty.nrml.nextday.sl.monitor.schedule}")
    public void sLNrmlMonitorPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
            List<Order> orderList = null;
            List<Position> positions = null;
            try {
                orderList = user.getKiteConnect().getOrders();
                positions = user.getKiteConnect().getPositions().get("net");
                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
            } catch (KiteException | IOException e) {
                e.printStackTrace();
            }

            Optional<Order> sLOrder = orderList.stream().filter(order1 -> openTradeDataEntity.getSlOrderId().equals(order1.orderId)).findFirst();
            if (sLOrder.isPresent()) {
                Order order = sLOrder.get();
                if ("COMPLETE".equals(order.status) && !openTradeDataEntity.isExited) {
                    openTradeDataEntity.isExited = true;
                    openTradeDataEntity.isSLHit = true;
                    if ("SELL".equals(order.transactionType)) {
                        openTradeDataEntity.setSellPrice(openTradeDataEntity.getSlPrice());
                        openTradeDataEntity.setSellTradedPrice(new BigDecimal(order.averagePrice));
                    } else {
                        openTradeDataEntity.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                        openTradeDataEntity.setBuyTradedPrice(openTradeDataEntity.getSlPrice());
                    }
                    saveTradeData(openTradeDataEntity);
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                    } else {
                        sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramToken);
                    }
                }
                if ("CANCELLED".equals(order.status) && !openTradeDataEntity.isSlCancelled) {
                    openTradeDataEntity.isSlCancelled = true;
                    saveTradeData(openTradeDataEntity);
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                    } else {
                        sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramToken);
                    }
                }
                if ("REJECTED".equals(order.status) && !openTradeDataEntity.isErrored) {
                    openTradeDataEntity.isErrored = true;
                    saveTradeData(openTradeDataEntity);
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram("SL order placement rejected for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                    } else {
                        sendMessage.sendToTelegram("SL order placement rejected for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramToken);
                    }
                }
            }
        });
    }

    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData) {
        try {
            OpenTradeDataEntity openTradeDataEntity = new OpenTradeDataEntity();
            openTradeDataEntity.setDataKey(tradeData.getDataKey());
            openTradeDataEntity.setAlgoName(this.getAlgoName());
            openTradeDataEntity.setStockName(tradeData.getStockName());
            openTradeDataEntity.setEntryType(tradeData.getEntryType());
            openTradeDataEntity.setUserId(tradeData.getUserId());
            openTradeDataEntity.isOrderPlaced = tradeData.isOrderPlaced;
            openTradeDataEntity.isSlPlaced = tradeData.isSlPlaced();
            openTradeDataEntity.isExited = tradeData.isExited();
            openTradeDataEntity.isErrored = tradeData.isErrored;
            openTradeDataEntity.isSLHit = tradeData.isSLHit;
            openTradeDataEntity.setBuyTradedPrice(tradeData.getBuyTradedPrice());
            openTradeDataEntity.setSellTradedPrice(tradeData.getSellTradedPrice());
            openTradeDataEntity.setExitOrderId(tradeData.getExitOrderId());
            openTradeDataEntity.setBuyPrice(tradeData.getBuyPrice());
            openTradeDataEntity.setSellPrice(tradeData.getSellPrice());
            openTradeDataEntity.setSlPrice(tradeData.getSlPrice());
            openTradeDataEntity.setQty(tradeData.getQty());
            openTradeDataEntity.setSlPercentage(tradeData.getSlPercentage());
            openTradeDataEntity.setEntryOrderId(tradeData.getEntryOrderId());
            openTradeDataEntity.setSlOrderId(tradeData.getSlOrderId());
            openTradeDataEntity.setStockId(tradeData.getStockId());
            saveTradeData(openTradeDataEntity);
            System.out.println("sucessfully saved trade data");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    public void saveTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            openTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

}
