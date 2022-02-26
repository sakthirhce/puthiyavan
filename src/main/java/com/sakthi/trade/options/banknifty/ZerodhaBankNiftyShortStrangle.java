package com.sakthi.trade.options.banknifty;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.entity.StrangleTradeDataEntity;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.repo.StrangleTradeDataRepo;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
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
public class ZerodhaBankNiftyShortStrangle {

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

    @Value("${telegram.orb.bot.token}")
    String telegramTokenGroup;

    @Autowired
    UserList userList;

    @Scheduled(cron = "${banknifty.strangle.schedule}")
    public void zerodhaBankNifty() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat weekDay = new SimpleDateFormat("EEE");
        String currentDate = format.format(date);
        String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        String niftyVix = zerodhaTransactionService.niftyIndics.get("INDIA VIX");
        String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/minute?from=" + currentDate + "+11:25:00&to=" + currentDate + "+15:28:00";
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        String historicVixURL = "https://api.kite.trade/instruments/historical/" + niftyVix + "/minute?from=" + currentDate + "+11:25:00&to=" + currentDate + "+15:28:00";
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
  /*      String historicVixResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicVixURL));
        System.out.print("vix response:"+historicVixResponse);
        HistoricalData historicalDataVix = new HistoricalData();
        JSONObject json = new JSONObject(historicVixResponse);
        String statusVix = json.getString("status");
        if (!statusVix.equals("error")) {
            historicalDataVix.parseResponse(json);
        }
        HistoricalData historicalDataLastVix =historicalDataVix.dataArrayList.get(historicalDataVix.dataArrayList.size()-1);
        System.out.print("last vix:"+historicalDataLastVix.close);*/
        HistoricalData historicalData = new HistoricalData();
        JSONObject json1 = new JSONObject(response);
        String status = json1.getString("status");
        if (!status.equals("error")/* && historicalDataLastVix.close<30*/) {
            historicalData.parseResponse(json1);
            System.out.println();
            historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T12:44:00")) {
                        System.out.println(historicalData1.close);
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        log.info("Bank Nifty:" + atmStrike);
                        //check usuage of bankniftyFlyingLot
                  //      int qty = 25 * (Integer.valueOf(bankniftyLot));
                   /* if("Mon".equals(weekDay.format(date))){
                        qty= qty+(Integer.valueOf(bankniftyFlyingLot)*25);
                    }*/
                       /* int ceOTMStrike = atmStrike+400;
                        int peOTMStrike = atmStrike-400;
                         Map<String, String> strikes=zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(ceOTMStrike));
                        Map<String, String> otmStrikes=new HashMap<>();
                        strikes.entrySet().forEach(entry -> {
                            if(entry.getKey().contains(String.valueOf(ceOTMStrike)) && entry.getKey().contains("CE")){
                                otmStrikes.put(entry.getKey(),entry.getValue());
                                System.out.println(entry.getKey() + " " + entry.getValue());
                            }
                        });*/

                        System.out.println("Bank Nifty:" + atmStrike);
                        final Map<String, String> atmStrikesStraddle;
                     //   if (zerodhaTransactionService.expDate.equals(currentDate)) {
                            atmStrikesStraddle = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                    /*    } else {
                            atmStrikesStraddle=zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(peOTMStrike));
                            atmStrikesStraddle.entrySet().forEach(entry -> {
                            if(entry.getKey().contains(String.valueOf(peOTMStrike)) && entry.getKey().contains("PE")){
                                otmStrikes.put(entry.getKey(),entry.getValue());
                                System.out.println(entry.getKey() + " " + entry.getValue());
                            }

                        });*/
                        atmStrikesStraddle.entrySet().stream().forEach(atmBankStrikeMap -> {
                            executorService.submit(() -> {
                                System.out.println(atmBankStrikeMap.getKey() + " " + atmBankStrikeMap.getValue());
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
                                String today= dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                String todayCaps=today.toUpperCase();
                                userList.getUser().stream().filter(
                                        user ->
                                                user.getStrangleConfig()!=null && user.getStrangleConfig().isEnabled() && user.getStrangleConfig().getDays().contains(todayCaps)
                                ).forEach( user -> {
                                    Order order = null;
                                    orderParams.quantity = 25 * user.getStrangleConfig().getLot();
                                    TradeData tradeData = new TradeData();
                                    tradeData.setStockName(atmBankStrikeMap.getKey());
                                    String dataKey = UUID.randomUUID().toString();
                                    tradeData.setDataKey(dataKey);
                                    try {
                                        order = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        tradeData.setEntryOrderId(order.orderId);
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setQty(25 * user.getStrangleConfig().getLot());
                                        tradeData.setEntryType("SELL");
                                        sendMessage.sendToTelegram("Strangle option sold for user:"+user.getName()+" strike: " + atmBankStrikeMap.getKey(), telegramTokenGroup,"-713214125");
                                        mapTradeDataToSaveOpenTradeDataEntity(tradeData);
                                    } catch (KiteException e) {
                                        tradeData.isErrored = true;
                                        System.out.println("Error while placing Strangle order: " + e.message);
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing Strangle order: " + atmBankStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing Strangle order: " + atmBankStrikeMap.getKey() + ",Exception:" + e.getMessage(), telegramToken);

                                        }
                                        //e.printStackTrace();
                                    } catch (IOException e) {
                                        tradeData.isErrored = true;
                                        System.out.println("Error while placing straddle order: " + e.getMessage());
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage + ",Exception:" + e.getMessage(), telegramToken);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey() + ",Exception:" + e.getMessage(), telegramToken);

                                        }

                                    }
                                    user.getStrangleConfig().getStrangleTradeMap().put(atmBankStrikeMap.getKey(), tradeData);
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
        log.info("strangle process completed in ms:" + stopWatch.getTotalTimeMillis());
    }

    @Scheduled(cron = "${banknifty.strangle.sl.place.immediate}")
    public void sLMonitorSchedulerImmediate() {
        // log.info("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getStrangleConfig().isEnabled()).forEach(user -> {

            if (user.getStrangleConfig().strangleTradeMap != null) {
                user.getStrangleConfig().strangleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
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
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    BigDecimal triggerPrice = (new BigDecimal(order.averagePrice).multiply(new BigDecimal(2))).setScale(0, RoundingMode.HALF_UP);

                                    OrderParams orderParams = new OrderParams();
                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                    orderParams.exchange = "NFO";
                                    orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                    orderParams.orderType = "SL";
                                    orderParams.product = "MIS";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
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
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName(), telegramToken);
                                        log.info("SL order placed for: " + trendTradeData.getStockName());
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                    } catch (KiteException e) {
                                        log.info("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message, telegramToken);
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken);
                                        e.printStackTrace();
                                    }
                                } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;
                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken);
                                    } else if ("COMPLETE".equals(order.status)) {
                                        trendTradeData.isSLHit = true;
                                        trendTradeData.isExited = true;
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken);

                                    }
                                }
                            });
                        });

            }
        });
    }

    @Scheduled(cron = "${banknifty.strangle.sl.place.schedule}")
    public void sLMonitorScheduler() {
        // log.info("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getStrangleConfig().isEnabled()).forEach(user -> {

            if (user.getStrangleConfig().strangleTradeMap != null) {
                user.getStrangleConfig().strangleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
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
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    BigDecimal triggerPrice = (new BigDecimal(order.averagePrice).multiply(new BigDecimal(2))).setScale(0, RoundingMode.HALF_UP);

                                    OrderParams orderParams = new OrderParams();
                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                    orderParams.exchange = "NFO";
                                    orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                    orderParams.orderType = "SL";
                                    orderParams.product = "MIS";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
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
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName(), telegramToken);
                                        log.info("SL order placed for: " + trendTradeData.getStockName());
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                    } catch (KiteException e) {
                                        log.info("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message, telegramToken);
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken);
                                        e.printStackTrace();
                                    }
                                } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;
                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramToken);
                                    } else if ("COMPLETE".equals(order.status)) {
                                        trendTradeData.isSLHit = true;
                                        trendTradeData.isExited = true;
                                        String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName());
                                        log.info(message);
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        sendMessage.sendToTelegram(message, telegramToken);

                                    }
                                }
                            });
                        });

            }
        });
    }


    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData) {
        try {
            StrangleTradeDataEntity openTradeDataEntity = new StrangleTradeDataEntity();
            openTradeDataEntity.setDataKey(tradeData.getDataKey());
            openTradeDataEntity.setAlgoName(this.getAlgoName());
            openTradeDataEntity.setStockName(tradeData.getStockName());
            openTradeDataEntity.setEntryType(tradeData.getEntryType());
            openTradeDataEntity.setUserId(tradeData.getUserId());
            openTradeDataEntity.isOrderPlaced = tradeData.isOrderPlaced;
            openTradeDataEntity.isSlPlaced = tradeData.isSlPlaced();
            openTradeDataEntity.isExited = tradeData.isExited();
            openTradeDataEntity.isErrored = tradeData.isErrored;
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

    @Autowired
    StrangleTradeDataRepo strangleTradeDataRepo;

    public String getAlgoName() {
        return "STRANGLE_SELL";
    }

    public void saveTradeData(StrangleTradeDataEntity openTradeDataEntity) {
        try {
            strangleTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
