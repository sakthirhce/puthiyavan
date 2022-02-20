package com.sakthi.trade.options.banknifty;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.entity.StockDayDataEntity;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.repo.StockDayDataRepository;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
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

    @Value("${telegram.orb.bot.token}")
    String telegramTokenGroup;
    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;
    private java.util.concurrent.Executors Executors;

    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    public Map<String, String> atmStrikeMap = new HashMap<>();


    @Autowired
    UserList userList;

    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;

    public String getAlgoName() {
        return "STRADDLE_LONG";
    }

    @Scheduled(cron = "${banknifty.nrml.straddle.schedule}")
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
                    if (sdf.format(openDatetime).equals(openDate + "T09:30:00")) {
                        System.out.println(historicalData1.close);
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        log.info("Bank Nifty:" + atmStrike);
                        //check usuage of bankniftyFlyingLot
                        //   int qty = 25 * (Integer.valueOf(bankniftyLot));
                   /* if("Mon".equals(weekDay.format(date))){
                        qty= qty+(Integer.valueOf(bankniftyFlyingLot)*25);
                    }*/
                        final Map<String, String> atmStrikesStraddle;
                        if (zerodhaTransactionService.expDate.equals(currentDate)) {
                            atmStrikesStraddle = zerodhaTransactionService.bankNiftyNextWeeklyOptions.get(String.valueOf(atmStrike));
                        } else {
                            atmStrikesStraddle = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                        }
                        atmStrikesStraddle.entrySet().forEach(entry -> {
                            System.out.println(entry.getKey() + ":" + entry.getValue());
                            atmStrikeMap.put(entry.getKey(), entry.getValue());
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
                                        user -> user.getStraddleConfig().isNrmlEnabled() && user.getStraddleConfig().getLotConfig().containsKey(todayCaps)
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
                                    tradeData.setEntryType("BUY");
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
                                        sendMessage.sendToTelegram("Straddle option sold for user:" + user.getName() + " strike: " + atmBankStrikeMap.getKey(), telegramToken);
                                    } catch (KiteException e) {
                                        tradeData.isErrored = true;
                                        System.out.println("Error while placing straddle order: " + e.message);
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey()+":"+user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey()+":"+user.getName() + ",Exception:" + e.getMessage(), telegramToken);
                                        }
                                        //e.printStackTrace();
                                    } catch (IOException e) {
                                        tradeData.isErrored = true;
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey()+":"+user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ",Exception:" + e.getMessage(), telegramToken);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing straddle order: " + atmBankStrikeMap.getKey()+":"+user.getName() + ",Exception:" + e.getMessage(), telegramToken);
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

        }
        stopWatch.stop();
        log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());
    }

    List<OpenTradeDataEntity> openTradeDataEntities = new ArrayList<>();

    @Scheduled(cron = "${banknifty.nrml.nextday.position.load.schedule}")
    public void loadNrmlPositions() {
        Iterable<OpenTradeDataEntity> openTradeDataEntities1 = openTradeDataRepo.findAll();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            if (!openTradeDataEntity.isExited) {
                openTradeDataEntity.isSlPlaced = false;
                openTradeDataEntity.setSlOrderId(null);
                openTradeDataEntities.add(openTradeDataEntity);
            }
        });
    }

    @Scheduled(cron = "${banknifty.nrml.nextday.sl.place.schedule}")
    public void placeSLNrmlPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
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
                System.out.print(response);

                JSONObject json = new JSONObject(response);
                status = json.getString("status");
                if (!status.equals("error")) {
                    historicalData.parseResponse(json);
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
            if (openTradeDataEntity.getEntryType().equals("BUY")) {
                orderParams.transactionType = "SELL";
                if (!status.equals("error") && lastMin.close < openTradeDataEntity.getSlPrice().doubleValue()) {
                    openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                }
                BigDecimal price = openTradeDataEntity.getSlPrice().subtract(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                orderParams.price = price.doubleValue();

            } else {
                orderParams.transactionType = "BUY";
                if (!status.equals("error") && lastMin.close > openTradeDataEntity.getSlPrice().doubleValue()) {
                    openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                }
                BigDecimal price = openTradeDataEntity.getSlPrice().add(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                orderParams.price = price.doubleValue();


            }

            orderParams.validity = "DAY";
            com.zerodhatech.models.Order orderd;
            try {
                User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                orderd = user.getKiteConnect().placeOrder(orderParams, "regular");
                openTradeDataEntity.isSlPlaced = true;
                openTradeDataEntity.setSlOrderId(orderd.orderId);
                openTradeDataRepo.save(openTradeDataEntity);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } catch (KiteException e) {
                System.out.println(e.getMessage());
                throw new RuntimeException(e);
            }
        });

    }

    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;

    @Scheduled(cron = "${banknifty.nrml.nextday.exit.schedule}")
    public void exitSLNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orders = user.getKiteConnect().getOrders();
                List<Position> positions = user.getKiteConnect().getPositions().get("net");
                System.out.println(positions);
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
                            orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getSlOrderId())).forEach(orderr -> {
                                try {
                                    Order order = user.getKiteConnect().cancelOrder(orderr.orderId, "regular");
                                } catch (KiteException e) {
                                    System.out.println(e.getMessage());
                                } catch (IOException e) {
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

                                    String message = MessageFormat.format("Closed Position {0}", orderParams.tradingsymbol);
                                    System.out.println(message);
                                    sendMessage.sendToTelegram(message+":"+user.getName(), telegramToken);
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

                        }
                );
            } catch (KiteException e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }


        });
        Gson gson = new Gson();
        openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            try {
                openTradeDataEntity.isExited = true;
                String openStr = gson.toJson(openTradeDataEntity);
                OpenTradeDataBackupEntity openTradeDataBackupEntity = gson.fromJson(openStr, OpenTradeDataBackupEntity.class);
                openTradeDataBackupRepo.save(openTradeDataBackupEntity);

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });
        openTradeDataRepo.deleteAll();

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
            openTradeDataEntity.setBuyPrice(tradeData.getBuyPrice());
            openTradeDataEntity.setSellPrice(tradeData.getSellPrice());
            openTradeDataEntity.setSlPrice(tradeData.getSlPrice());
            openTradeDataEntity.setQty(openTradeDataEntity.getQty());
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

    @Scheduled(cron = "${straddle.nrml.sl.immediate.scheduler}")
    public void sLMonitorSchedulerImmediate() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {

            if (user.getStraddleConfig().straddleTradeMap != null) {
                user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
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
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(5))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);

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
                                        user.getStraddleConfig().getBuyConfig().getLotConfig().entrySet().stream().forEach(day -> {
                                            String lotValue = day.getKey();
                                            if (lotValue.contains(todayCaps)) {
                                                int value = Integer.parseInt(day.getValue());
                                                qty.getAndSet(value);
                                            }
                                        });
                                    }
                                    orderParams.quantity = Integer.valueOf(order.filledQuantity) * 2 + (25 * qty.get());
                                    orderParams.orderType = "SL";
                                    orderParams.product = "NRML";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                    orderParams.triggerPrice = triggerPrice.doubleValue();
                                    trendTradeData.setSlPrice(triggerPrice);
                                    orderParams.price = price.doubleValue();
                                    orderParams.transactionType = "BUY";
                                    orderParams.validity = "DAY";
                                    Order orderResponse = null;
                                    try {
                                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        trendTradeData.setSlOrderId(orderResponse.orderId);
                                        trendTradeData.setSlPrice(new BigDecimal(orderParams.triggerPrice));
                                        trendTradeData.isSlPlaced = true;
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName()+":"+user.getName(), telegramTokenGroup,"-713214125");
                                        log.info("SL order placed for: " + trendTradeData.getStockName());

                                    } catch (KiteException e) {
                                        log.info("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message+":"+user.getName(), telegramTokenGroup,"-713214125");
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage()+":"+user.getName(), telegramTokenGroup,"-713214125");
                                        e.printStackTrace();
                                    }
                                }
                            });
                        });
            }
        });
    }

    @Scheduled(cron = "${straddle.nrml.sl.scheduler}")
    public void sLMonitorScheduler() {
        // log.info("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {

            if (user.getStraddleConfig().straddleTradeMap != null) {
                user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
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
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                    BigDecimal triggerPrice = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(5))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);

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
                                        user.getStraddleConfig().getBuyConfig().getLotConfig().entrySet().stream().forEach(day -> {
                                            String lotValue = day.getKey();
                                            if (lotValue.contains(todayCaps)) {
                                                int value = Integer.parseInt(day.getValue());
                                                qty.getAndSet(value);
                                            }
                                        });
                                    }
                                    orderParams.quantity = Integer.valueOf(order.filledQuantity) * 2 + (25 * qty.get());
                                    orderParams.orderType = "SL";
                                    orderParams.product = "NRML";
                                    BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(100))).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);
                                    orderParams.triggerPrice = triggerPrice.doubleValue();
                                    trendTradeData.setSlPrice(triggerPrice);
                                    orderParams.price = price.doubleValue();
                                    orderParams.transactionType = "BUY";
                                    orderParams.validity = "DAY";
                                    Order orderResponse = null;
                                    try {
                                        orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                        trendTradeData.setSlOrderId(orderResponse.orderId);
                                        trendTradeData.setSlPrice(new BigDecimal(orderParams.triggerPrice));
                                        trendTradeData.isSlPlaced = true;
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName()+":"+user.getName(), telegramTokenGroup,"-713214125");
                                        log.info("SL order placed for: " + trendTradeData.getStockName());

                                    } catch (KiteException e) {
                                        log.info("Error while placing straddle order: " + e.message);
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message+":"+user.getName(), telegramTokenGroup,"-713214125");
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        log.info("Error while placing straddle order: " + e.getMessage());
                                        sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage()+":"+user.getName(), telegramTokenGroup,"-713214125");
                                        e.printStackTrace();
                                    }
                                } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && "BUY".equals(order.transactionType) && trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;
                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName()+":"+user.getName());
                                        log.info(message);
                                        sendMessage.sendToTelegram(message, telegramTokenGroup,"-713214125");
                                    } else if ("COMPLETE".equals(order.status)) {
                                        if ("BUY".equals(order.transactionType)) {
                                            trendTradeData.isSLHit = true;
                                            trendTradeData.isExited = true;
                                            String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName()+":"+user.getName());
                                            log.info(message);
                                            sendMessage.sendToTelegram(message, telegramTokenGroup,"-713214125");
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);

                                            // if (doubleTopCount.get() == 0) {
                                            log.info("inside options stop sell");
                                            doubleTopCount.getAndAdd(1);
                                            if (user.getStraddleConfig().buyConfig.isEnabled()) {
                                                BigDecimal triggerPrice = trendTradeData.getSlPrice().subtract(trendTradeData.getSlPrice().divide(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                log.info("inside options stop sell filter" + trendTradeData.getStockName());
                                                LocalDate localDate = LocalDate.now();
                                                DayOfWeek dow = localDate.getDayOfWeek();
                                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                                String todayCaps = today.toUpperCase();
                                                AtomicInteger qty = new AtomicInteger(0);
                                                if (user.getStraddleConfig().getBuyConfig() != null && user.getStraddleConfig().getBuyConfig().isEnabled()) {
                                                    user.getStraddleConfig().getBuyConfig().getLotConfig().entrySet().stream().forEach(day -> {
                                                        String lotValue = day.getKey();
                                                        if (lotValue.contains(todayCaps)) {
                                                            int value = Integer.parseInt(day.getValue());
                                                            qty.getAndSet(value);
                                                        }
                                                    });
                                                }
                                                if (qty.get() > 0) {
                                                    OrderParams orderParams = new OrderParams();
                                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                                    orderParams.exchange = "NFO";
                                                    orderParams.quantity = 25 * qty.get() + trendTradeData.getQty();
                                                    orderParams.triggerPrice = triggerPrice.doubleValue();
                                                    BigDecimal price = triggerPrice.subtract(triggerPrice.divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams.price = price.doubleValue();
                                                    orderParams.orderType = "SL";
                                                    orderParams.product = "NRML";
                                                    orderParams.transactionType = "SELL";
                                                    orderParams.validity = "DAY";
                                                    com.zerodhatech.models.Order orderd = null;
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
                                                        doubleToptradeData.setSlPrice(triggerPrice);
                                                        doubleToptradeData.setQty(25 * qty.get());
                                                        doubleToptradeData.setSlOrderId(orderd.orderId);
                                                        doubleToptradeData.setUserId(user.getName());
                                                        doubleToptradeData.setSlPercentage(new BigDecimal("20"));
                                                        doubleToptradeData.setEntryType("BUY");
                                                        mapTradeDataToSaveOpenTradeDataEntity(doubleToptradeData);
                                                        sendMessage.sendToTelegram("Straddle option bought for strike: " + doubleToptradeData.getStockName()+":"+user.getName(), telegramToken);
                                                        user.getStraddleConfig().straddleTradeMap.put(trendTradeData.getStockName() + "-BUY", doubleToptradeData);

                                                    } catch (KiteException | IOException e) {
                                                        // tradeData.isErrored = true;
                                                        log.info("Error while placing straddle order: " + e.getMessage() +":"+user.getName());
                                                        if (order != null) {
                                                            sendMessage.sendToTelegram("Error while placing Double top straddle order: " + doubleToptradeData.getStockName()+":"+user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                                                        } else {
                                                            sendMessage.sendToTelegram("Error while placing Double top straddle order: " + doubleToptradeData.getStockName()+":"+user.getName(), telegramToken);

                                                        }
                                                        //e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }
                                        if ("SELL".equals(order.transactionType) && map.getKey().contains("BUY")) {
                                            trendTradeData.isSLHit = true;
                                            trendTradeData.isExited = true;
                                            String message = MessageFormat.format("SL Hit for {0}", map.getKey()+":"+user.getName());
                                            log.info(message);
                                            sendMessage.sendToTelegram(message, telegramTokenGroup,"-713214125");
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                        }

                                    }


                                }

                            });
                        });

            }
        });
    }

    @Scheduled(cron = "${banknifty.nrml.nextday.sl.monitor.schedule}")
    public void sLNrmlMonitorPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
            List<Order> orderList = null;
            List<Position> positions=null;
            try {
                orderList = user.getKiteConnect().getOrders();
                positions = user.getKiteConnect().getPositions().get("net");
                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
            } catch (KiteException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            positions.stream().filter(position->  openTradeDataEntity.getStockName().equals(position.tradingSymbol) && position.netQuantity==0).findFirst().ifPresent(
                    position -> {
                        openTradeDataEntity.isExited=true;
                        saveTradeData(openTradeDataEntity);
                    }
            );
            Optional<Order> sLOrder= orderList.stream().filter(order1->openTradeDataEntity.getSlOrderId().equals(order1.orderId)).findFirst();
               if(sLOrder.isPresent()){
                   Order order=sLOrder.get();
                    if ("COMPLETE".equals(order.status)) {
                        openTradeDataEntity.isExited = true;
                        saveTradeData(openTradeDataEntity);
                        sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() +":"+openTradeDataEntity.getUserId(), telegramTokenGroup,"-713214125");
                    }
                    if ("CANCELLED".equals(order.status)) {
                       sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() +":"+openTradeDataEntity.getUserId() , telegramTokenGroup,"-713214125");
                    }
                    if(("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getSlOrderId()) &&  openTradeDataEntity.isExited )
                   try {
                       Order order1 = user.getKiteConnect().cancelOrder(order.orderId, "regular");
                   } catch (KiteException e) {
                       System.out.println(e.getMessage());
                   } catch (IOException e) {
                       System.out.println(e.getMessage());
                   }
                }
            });
    }

    @Scheduled(cron = "${banknifty.nrml.sl.monitor.schedule}")
    public void sLMonitorPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            if (user.getStraddleConfig().straddleTradeMap != null) {
                user.getStraddleConfig().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                        .forEach(openTradeData -> {
                            List<Order> orderList = null;
                            List<Position> positions = null;
                            try {
                                orderList = user.getKiteConnect().getOrders();
                                positions = user.getKiteConnect().getPositions().get("net");
                                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                            } catch (KiteException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            TradeData openTradeDataEntity=openTradeData.getValue();
                            positions.stream().filter(position -> openTradeDataEntity.getStockName().equals(position.tradingSymbol) && position.netQuantity == 0).findFirst().ifPresent(
                                    position -> {
                                        openTradeDataEntity.isExited = true;
                                       mapTradeDataToSaveOpenTradeDataEntity(openTradeDataEntity);
                                    }
                            );
                            Optional<Order> sLOrder = orderList.stream().filter(order1 -> openTradeDataEntity.getSlOrderId().equals(order1.orderId)).findFirst();
                            if (sLOrder.isPresent()) {
                                Order order = sLOrder.get();
                                if ("COMPLETE".equals(order.status)) {
                                    openTradeDataEntity.isExited = true;
                                    mapTradeDataToSaveOpenTradeDataEntity(openTradeDataEntity);
                                    sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                                }
                                if ("CANCELLED".equals(order.status)) {
                                    sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId(), telegramTokenGroup, "-713214125");
                                }
                                if (("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getSlOrderId()) && openTradeDataEntity.isExited)
                                    try {
                                        Order order1 = user.getKiteConnect().cancelOrder(order.orderId, "regular");
                                    } catch (KiteException e) {
                                        System.out.println(e.getMessage());
                                    } catch (IOException e) {
                                        System.out.println(e.getMessage());
                                    }
                            }
                        });
            }
        });
    }
}
