package com.sakthi.trade.options.banknifty;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.BuyConfig;
import com.sakthi.trade.zerodha.account.User;
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
public class ZerodhaBankNiftyShortStraddleBuy {

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



    @Autowired
    UserList userList;

    Map<String,Double> price935=new HashMap<>();

    public Map<String,String> atmStrikeMap=new HashMap<>();

    @Scheduled(cron = "${banknifty.buy.historic.straddle.strike}")
    public void zerodhaBankNiftyATMStrike() {
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
                    Date openDatetime = null;
                    try {
                        openDatetime = sdf.parse(historicalData1.timeStamp);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
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
                        HistoricalData historicalData2 = new HistoricalData();
                        final Map<String, String> atmStrikesStraddle = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                        atmStrikesStraddle.entrySet().forEach(entry -> {
                            System.out.println(entry.getKey() + " " + entry.getValue());
                            atmStrikeMap.put(entry.getKey(), entry.getValue());
                            String historicURLStrike = "https://api.kite.trade/instruments/historical/" + entry.getValue() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:10:00";
                            //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
                            String responseStrike = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                            if (!status.equals("error")) {
                                historicalData2.parseResponse(json);
                                if(!price935.containsKey(entry.getKey())) {
                                   HistoricalData lastData= historicalData.dataArrayList.get(historicalData.dataArrayList.size()-1);
                                        try {
                                            Date openDatetime1 = sdf.parse(lastData.timeStamp);
                                            String openDate1 = format.format(openDatetime1);
                                            if (sdf.format(openDatetime1).equals(openDate1 + "T09:34:00")) {
                                                System.out.println(entry.getKey()+" 934 close:" + historicalData2.close);
                                                price935.put(entry.getKey(),historicalData2.close);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                        });
                    }
                 }catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            });
        }
    }

    @Scheduled(cron = "${banknifty.buy.historic.straddle.schedule}")
    public void zerodhaBankNifty() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat weekDay = new SimpleDateFormat("EEE");
        String currentDate = format.format(date);
        atmStrikeMap.entrySet().forEach(entry -> {
            String historicURL = "https://api.kite.trade/instruments/historical/" + entry.getValue() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:10:00";
            //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            System.out.print(response);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            String status = json.getString("status");
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                HistoricalData lastData = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                Double open = price935.get(entry.getKey());
                double percentMove = MathUtils.percentageMove(open, lastData.close);
                LocalDate localDate = LocalDate.now();
                DayOfWeek dow = localDate.getDayOfWeek();
                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                String todayCaps = today.toUpperCase();
                userList.getUser().stream().filter(
                        user -> user.getStraddleConfig().buyConfig.isEnabled() && user.getStraddleConfig().getLotConfig().containsKey(todayCaps)
                ).forEach(user -> {
                    BuyConfig  buyConfig=user.getStraddleConfig().buyConfig;
                    if (percentMove > 25 &&  buyConfig.straddleTradeMap.get(entry.getKey())==null) {
                        System.out.println(entry.getKey());
                        OrderParams orderParams = new OrderParams();
                        orderParams.tradingsymbol = entry.getKey();
                        orderParams.exchange = "NFO";
                        orderParams.orderType = "MARKET";
                        orderParams.product = "NRML";
                        orderParams.transactionType = "BUY";
                        orderParams.validity = "DAY";
                        AtomicInteger qty = new AtomicInteger(1);
                        user.getStraddleConfig().getLotConfig().entrySet().stream().forEach(day -> {
                            String lotValue = day.getKey();
                            if (lotValue.contains(todayCaps)) {
                                int value = Integer.parseInt(day.getValue());
                                qty.getAndSet(value);
                            }
                        });
                        Order order = null;
                        orderParams.quantity = 25 * qty.get();
                        TradeData tradeData = new TradeData();
                        tradeData.setStockName(entry.getKey());
                        try {
                            order = user.getKiteConnect().placeOrder(orderParams, "regular");
                            tradeData.setEntryOrderId(order.orderId);
                            tradeData.isOrderPlaced = true;
                            tradeData.setQty(25 * qty.get());
                            tradeData.setEntryType("BUY");
                            sendMessage.sendToTelegram("Straddle option buy for user:" + user.getName() + " strike: " + entry.getKey(), telegramToken);

                        } catch (KiteException e) {
                            tradeData.isErrored = true;
                            System.out.println("Error while placing straddle buy order: " + e.message);
                            if (order != null) {
                                sendMessage.sendToTelegram("Error while placing straddle buy order: " + entry.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                            } else {
                                sendMessage.sendToTelegram("Error while placing straddle buy order: " + entry.getKey() + ",Exception:" + e.getMessage(), telegramToken);

                            }
                            //e.printStackTrace();
                        } catch (IOException e) {
                            tradeData.isErrored = true;
                            log.info("Error while placing straddle order: " + e.getMessage());
                            if (order != null) {
                                sendMessage.sendToTelegram("Error while placing straddle buy order: " + entry.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage + ",Exception:" + e.getMessage(), telegramToken);
                            } else {
                                sendMessage.sendToTelegram("Error while placing straddle buy order: " + entry.getKey() + ",Exception:" + e.getMessage(), telegramToken);

                            }

                        }
                        buyConfig.straddleTradeMap.put(entry.getKey(), tradeData);
                    }
                });

            }

        });


        stopWatch.stop();
        log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());
    }


    @Scheduled(cron = "${stradle.buy.sl.scheduler}")
    public void sLMonitorScheduler() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig().buyConfig.isEnabled()).forEach(user -> {
            BuyConfig buyConfig=user.getStraddleConfig().buyConfig;
            sLMonitor(buyConfig.straddleTradeMap, user);
            sLMonitor(buyConfig.straddlePreviousDayTradeMap, user);
        });
    }

    public void sLMonitor(Map<String,TradeData> straddleTradeMap, User user)  {
            // log.info("short straddle SLMonitor scheduler started");

                if (straddleTradeMap != null) {
                    straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                            .forEach(map -> {
                                TradeData trendTradeData = map.getValue();
                                // System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                                List<Order> orderList = null;
                                try {
                                    orderList = user.getKiteConnect().getOrders();
                                    //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                                } catch (KiteException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                orderList.stream().forEach(order -> {
                                    if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "BUY".equals(order.transactionType)) {
                                        trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                        trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                        trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                        BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(4))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
                                        OrderParams orderParams = new OrderParams();
                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                        orderParams.exchange = "NFO";
                                        orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                        orderParams.orderType = "SL";
                                        orderParams.product = "NRML";
                                        BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                        orderParams.triggerPrice = triggerPrice.doubleValue();
                                        trendTradeData.setSlPrice(triggerPrice);
                                        orderParams.price = price.doubleValue();
                                        orderParams.transactionType = "SELL";
                                        orderParams.validity = "DAY";
                                        Order orderResponse = null;
                                        try {
                                            orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                            trendTradeData.setSlOrderId(orderResponse.orderId);
                                            trendTradeData.isSlPlaced = true;
                                            sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName(), telegramToken);
                                            log.info("SL order placed for: " + trendTradeData.getStockName());

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
                                            sendMessage.sendToTelegram(message, telegramToken);
                                            if (doubleTopCount.get() == 0) {
                                            }
                                        }
                                    }
                                });
                            });

                }


    }

    @Scheduled(cron = "${straddle.buy.exit.position.scheduler}")
    public void exitPositions() throws KiteException, IOException {
        System.out.println("Straddle Exit positions scheduler started");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        userList.getUser().stream().filter(user -> user.getStraddleConfig().buyConfig.isEnabled()).forEach(user -> {
            BuyConfig buyConfig=user.getStraddleConfig().buyConfig;
            Map<String,TradeData> straddlePreviousDayTradeMap=buyConfig.straddlePreviousDayTradeMap;
            if (straddlePreviousDayTradeMap != null) {
                straddlePreviousDayTradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited && !orbTradeDataEntity.getValue().isSLHit).forEach(trendMap -> {
                    executor.submit(() -> {
                        TradeData trendTradeData = trendMap.getValue();
                        if (trendTradeData.getSlOrderId() != null && trendTradeData.getSlOrderId() != "") {
                            try {
                                if (!trendTradeData.isSLCancelled && !trendTradeData.isSLHit) {
                                    Order order = user.getKiteConnect().cancelOrder(trendTradeData.getSlOrderId(), "regular");
                                    trendMap.getValue().isSLCancelled = true;
                                    String message = MessageFormat.format("System Cancelled SL {0}", trendMap.getValue().getStockName());
                                    System.out.println(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }
                            } catch (KiteException e) {
                                e.printStackTrace();
                                String message = MessageFormat.format("Error while System cancelling SL {0},[1]", trendMap.getValue().getStockName(), e.message);
                                System.out.println(message);
                                sendMessage.sendToTelegram(message, telegramToken);
                            } catch (IOException e) {
                                e.printStackTrace();
                                String message = MessageFormat.format("Error while System cancelling SL {0},{1}", trendMap.getValue().getStockName(), e.getMessage());
                                System.out.println(message);
                                sendMessage.sendToTelegram(message, telegramToken);
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
                positions.stream().filter(position -> "MIS".equals(position.product) && (position.netQuantity > 0 || position.netQuantity < 0)).forEach(position -> {
                    //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                    OrderParams orderParams = new OrderParams();
                    orderParams.tradingsymbol = position.tradingSymbol;
                    orderParams.exchange = "NFO";
                    orderParams.quantity = Math.abs(position.netQuantity);
                    orderParams.orderType = "MARKET";
                    orderParams.product = "NRML";
                    //orderParams.price=price.doubleValue();
                    if (position.netQuantity > 0) {
                        orderParams.transactionType = "SELL";
                    } else {
                        orderParams.transactionType = "BUY";
                    }
                    orderParams.validity = "DAY";
                    Order orderResponse = null;
                    try {
                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                        System.out.println(new Gson().toJson(orderResponse));
                        if (straddlePreviousDayTradeMap.get(position.tradingSymbol) != null) {
                            straddlePreviousDayTradeMap.get(position.tradingSymbol).isExited = true;
                        }
                        String message = MessageFormat.format("Closed Position {0}", orderParams.tradingsymbol);
                        System.out.println(message);
                        sendMessage.sendToTelegram(message, telegramToken);
                    } catch (KiteException e) {
                        System.out.println("Error while placing straddle order: " + e.message);
                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken);
                        e.printStackTrace();
                    } catch (IOException e) {
                        System.out.println("Error while placing straddle order: " + e.getMessage());
                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position), telegramToken);
                        e.printStackTrace();
                    }
                    //  }
                });

            }});
    }

    @Scheduled(cron = "${straddle.buy.monitor.position.scheduler}")
    public void monitorPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig().buyConfig.isEnabled()).forEach(user -> {
            BuyConfig buyConfig=user.getStraddleConfig().buyConfig;
            slStatus(buyConfig.straddleTradeMap,user);
            slStatus(buyConfig.straddlePreviousDayTradeMap,user);
        });
    }

    public void slStatus(Map<String,TradeData> straddleTradeMap, User user)  {

            List<Position> positions = null;
            try {
                positions = user.getKiteConnect().getPositions().get("net");
            } catch (KiteException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            positions.stream().filter(position -> position.netQuantity == 0 && "MIS".equals(position.product) && straddleTradeMap.get(position.tradingSymbol) != null && !straddleTradeMap.get(position.tradingSymbol).isExited).forEach(position -> {
            TradeData tradeData = straddleTradeMap.get(position.tradingSymbol);
            List<Order> orderList = null;
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
                        Order orderC =user.getKiteConnect().cancelOrder(tradeData.getSlOrderId(), "regular");
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

        });

    }

}
