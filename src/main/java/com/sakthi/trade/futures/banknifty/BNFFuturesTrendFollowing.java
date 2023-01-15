package com.sakthi.trade.futures.banknifty;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Component
@Slf4j
public class BNFFuturesTrendFollowing {
    public static final Logger LOGGER = Logger.getLogger(BNFFuturesTrendFollowing.class.getName());
    public Map<Integer, BigDecimal> slPrice = new HashMap<>();
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;
    @Autowired
    TransactionService transactionService;
    @Autowired
    TelegramMessenger sendMessage;
    Gson gson = new Gson();
    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    UserList userList;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    List<OpenTradeDataEntity> openTradeDataEntities = new ArrayList<>();

    public String getAlgoName() {
        return "BNF_FUTURE_930";
    }

    @Scheduled(cron = "${banknifty.futures.schedule}")
    public void bnfFutures() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = new Date();
        String currentDate = format.format(date);
        Map<String, String> currentFuturesMap = zerodhaTransactionService.currentFutures.get("BANKNIFTY");
        Map.Entry<String, String> entry = currentFuturesMap.entrySet().iterator().next();
        String futuresName = entry.getKey();
        String futuresValue = entry.getValue();
        String historicURL = "https://api.kite.trade/instruments/historical/" + futuresValue + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:40:00";
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.print(response);
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            HistoricalData lastHistoricalDataElement = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
            System.out.println(lastHistoricalDataElement.close);
            historicalData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T09:29:00")) {
                        BigDecimal halfPercent = MathUtils.percentageValueOfAmount(new BigDecimal(".5"), new BigDecimal(historicalData1.close));
                        BigDecimal longTrigger = new BigDecimal(historicalData1.close).add(halfPercent).setScale(0, RoundingMode.HALF_UP);
                        BigDecimal shortTrigger = new BigDecimal(historicalData1.close).subtract(halfPercent).setScale(0, RoundingMode.HALF_UP);
                        BigDecimal slTrigger = new BigDecimal(historicalData1.close).setScale(0, RoundingMode.HALF_UP);
                        LocalDate localDate = LocalDate.now();
                        DayOfWeek dow = localDate.getDayOfWeek();
                        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                        String todayCaps = today.toUpperCase();
                        if (longTrigger.doubleValue() < lastHistoricalDataElement.close) {
                            sendMessage.sendToTelegram("Bank Nifty Futures Long Trigger already met trigger price:" + longTrigger.doubleValue() + ": current close:" + lastHistoricalDataElement.close + " hence not placing order", telegramToken);
                        } else {
                            sendMessage.sendToTelegram("Bank Nifty Futures Long Trigger price:" + longTrigger.doubleValue(), telegramToken);
                            userList.getUser().stream().filter(
                                    user -> user.getBnfFutures() != null && user.getBnfFutures().isNrmlEnabled() && user.getBnfFutures().getLotConfig().containsKey(todayCaps)
                            ).forEach(user -> {
                                AtomicInteger qty = new AtomicInteger(1);
                                user.getBnfFutures().getLotConfig().entrySet().forEach(day -> {
                                    String lotValue = day.getKey();
                                    if (lotValue.contains(todayCaps)) {
                                        int value = Integer.parseInt(day.getValue());
                                        qty.getAndSet(value);
                                    }
                                });
                                TradeData trendTradeData = new TradeData();
                                OrderParams buyOrderParams = new OrderParams();
                                buyOrderParams.tradingsymbol = futuresName;
                                trendTradeData.setStockName(futuresName);
                                buyOrderParams.exchange = "NFO";
                                buyOrderParams.quantity = 25;
                                buyOrderParams.orderType = "SL";
                                buyOrderParams.product = "NRML";
                                BigDecimal upPrice = ((halfPercent.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).add(longTrigger)).setScale(0, RoundingMode.HALF_UP);
                                buyOrderParams.triggerPrice = longTrigger.doubleValue();
                                trendTradeData.setSlPrice(new BigDecimal(lastHistoricalDataElement.close));
                                buyOrderParams.price = upPrice.doubleValue();
                                buyOrderParams.transactionType = "BUY";
                                buyOrderParams.validity = "DAY";
                                Order orderResponse;
                                try {
                                    orderResponse = user.getKiteConnect().placeOrder(buyOrderParams, "regular");
                                    trendTradeData.setEntryOrderId(orderResponse.orderId);
                                    trendTradeData.setSlPrice(slTrigger);
                                    trendTradeData.setQty(buyOrderParams.quantity);
                                    trendTradeData.setUserId(user.getName());
                                    trendTradeData.setBuyPrice(longTrigger);
                                    trendTradeData.setEntryType("BUY");
                                    trendTradeData.isOrderPlaced = true;
                                    trendTradeData.setStockId(Integer.parseInt(futuresValue));
                                    // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                    sendMessage.sendToTelegram("Placed BUY order for: " + trendTradeData.getStockName() + ":" + futuresName + "-BUY" + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                } catch (Exception e) {
                                    sendMessage.sendToTelegram("error while placing condition order: " + trendTradeData.getStockName() + ":" + futuresName + "-BUY" + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                } catch (KiteException e) {
                                    sendMessage.sendToTelegram("error while placing condition order: " + trendTradeData.getStockName() + ":" + futuresName + "-BUY" + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                }
                                user.getBnfFutures().straddleTradeMap.put(futuresName + "-BUY", trendTradeData);
                            });


                        }
                        if (shortTrigger.doubleValue() > lastHistoricalDataElement.close) {
                            sendMessage.sendToTelegram("Bank Nifty Futures short Trigger already met trigger price:" + shortTrigger.doubleValue() + ": current close:" + lastHistoricalDataElement.close + " hence not placing order", telegramToken);
                        } else {
                            sendMessage.sendToTelegram("Bank Nifty Futures short Trigger price:" + shortTrigger.doubleValue(), telegramToken);
                            userList.getUser().stream().filter(
                                    user -> user.getBnfFutures() != null && user.getBnfFutures().isNrmlEnabled() && user.getBnfFutures().getLotConfig().containsKey(todayCaps)
                            ).forEach(user -> {
                                AtomicInteger qty = new AtomicInteger(1);
                                user.getBnfFutures().getLotConfig().entrySet().forEach(day -> {
                                    String lotValue = day.getKey();
                                    if (lotValue.contains(todayCaps)) {
                                        int value = Integer.parseInt(day.getValue());
                                        qty.getAndSet(value);
                                    }
                                });
                                TradeData trendTradeData = new TradeData();
                                OrderParams sellOrderParams = new OrderParams();
                                sellOrderParams.tradingsymbol = futuresName;
                                trendTradeData.setStockName(futuresName);
                                sellOrderParams.exchange = "NFO";
                                sellOrderParams.quantity = 25 * qty.get();
                                sellOrderParams.orderType = "SL";
                                sellOrderParams.product = "NRML";
                                BigDecimal downPrice = shortTrigger.subtract((halfPercent.divide(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).multiply(new BigDecimal(5)))).setScale(0, RoundingMode.HALF_UP);
                                sellOrderParams.triggerPrice = shortTrigger.doubleValue();
                                trendTradeData.setSlPrice(new BigDecimal(lastHistoricalDataElement.close));
                                sellOrderParams.price = downPrice.doubleValue();
                                sellOrderParams.transactionType = "SELL";
                                sellOrderParams.validity = "DAY";
                                Order orderResponse1;
                                try {
                                    orderResponse1 = user.getKiteConnect().placeOrder(sellOrderParams, "regular");
                                    trendTradeData.setEntryOrderId(orderResponse1.orderId);
                                    trendTradeData.setSlPrice(slTrigger);
                                    trendTradeData.setUserId(user.getName());
                                    trendTradeData.setQty(sellOrderParams.quantity);
                                    trendTradeData.setEntryType("SELL");
                                    trendTradeData.setSellPrice(longTrigger);
                                    trendTradeData.isOrderPlaced = true;
                                    trendTradeData.setStockId(Integer.parseInt(futuresValue));
                                    // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                    sendMessage.sendToTelegram("Placed Condition Sell order for: " + trendTradeData.getStockName() + ":" + futuresName + "-SELL" + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                } catch (Exception | KiteException e) {
                                    sendMessage.sendToTelegram("error while placing condition order: " + trendTradeData.getStockName() + ":" + futuresName + "-SELL" + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                }
                                user.getBnfFutures().straddleTradeMap.put(futuresName + "-SELL", trendTradeData);
                            });
                        }
                    }
                } catch (Exception e) {
                    LOGGER.info("exception while executing futures algo ");
                }
            });
        }
    }

    @Scheduled(cron = "${banknifty.futures.sl.monitor}")
    public void sLMonitorScheduler() {
        // LOGGER.info("short straddle SLMonitor scheduler started");

        userList.getUser().forEach(user -> {
            if (user.getBnfFutures() != null && user.getBnfFutures().isNrmlEnabled()) {
                if (user.getBnfFutures().straddleTradeMap != null) {
                    user.getBnfFutures().straddleTradeMap
                            .forEach((key, value) -> {
                                if (value.isOrderPlaced && value.getEntryOrderId() != null) {
                                    TradeData trendTradeData = value;
                                    // LOGGER.info(" trade data:"+new Gson().toJson(trendTradeData));
                                    List<com.zerodhatech.models.Order> orderList = null;
                                    try {
                                        orderList = user.getKiteConnect().getOrders();
                                        //   LOGGER.info("get trade response:"+new Gson().toJson(orderList));
                                    } catch (KiteException | IOException e) {
                                        e.printStackTrace();
                                    }
                                   // log.info("debug log:"+new Gson().toJson(trendTradeData));
                                    orderList.forEach(order -> {
                                  //      log.info("debug order log:"+new Gson().toJson(order));
                                        try {
                                        if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status)) {
                                            //   trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));


                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = trendTradeData.getStockName();
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                            orderParams.orderType = "SL";
                                            orderParams.product = "NRML";
                                            orderParams.triggerPrice = trendTradeData.getSlPrice().setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            BigDecimal divide = trendTradeData.getSlPrice().setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP);
                                            BigDecimal slipage=new BigDecimal(0);
                                            if ("BUY".equals(order.transactionType)) {
                                                orderParams.transactionType = "SELL";
                                                trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                                slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                                BigDecimal price = (trendTradeData.getSlPrice().setScale(0, RoundingMode.HALF_UP)).subtract(divide).setScale(0, RoundingMode.HALF_UP);
                                                orderParams.price = price.doubleValue();
                                            } else if ("SELL".equals(order.transactionType)) {
                                                orderParams.transactionType = "BUY";
                                                trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                                slipage = (trendTradeData.getSellPrice().subtract(trendTradeData.getSellTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                                BigDecimal price = (trendTradeData.getSlPrice().setScale(0, RoundingMode.HALF_UP)).add(divide).setScale(0, RoundingMode.HALF_UP);
                                                orderParams.price = price.doubleValue();
                                            }

                                            orderParams.validity = "DAY";
                                            com.zerodhatech.models.Order orderResponse = null;
                                            try {
                                                orderResponse = user.getKiteConnect().placeOrder(orderParams, "regular");
                                                trendTradeData.setSlOrderId(orderResponse.orderId);
                                                trendTradeData.setUserId(user.getName());
                                                trendTradeData.isSlPlaced = true;
                                                String dataKey = UUID.randomUUID().toString();
                                                trendTradeData.setDataKey(dataKey);
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,true);
                                                sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName() +":" + trendTradeData.getEntryType() + ":" + user.getName() + ":" + getAlgoName()+": slipage:"+slipage.doubleValue(), telegramToken);
                                                LOGGER.info("SL order placed for: " + trendTradeData.getStockName() + ":" + trendTradeData.getUserId());

                                            } catch (KiteException e) {
                                                LOGGER.info("Error while placing straddle order: " + e.message);
                                                sendMessage.sendToTelegram("Error while placing SL order: " + trendTradeData.getStockName() + " error message:" + e.message + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                                e.printStackTrace();
                                            } catch (IOException e) {
                                                LOGGER.info("Error while placing straddle order: " + e.getMessage());
                                                sendMessage.sendToTelegram("Error while placing SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage() + ":" + user.getName() + ":" + getAlgoName(), telegramToken);
                                                e.printStackTrace();
                                            }
                                        } else if ( trendTradeData.isSlPlaced && trendTradeData.getSlOrderId().equals(order.orderId)&& !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                            if ("CANCELLED".equals(order.status)) {
                                                trendTradeData.isSLCancelled = true;
                                                String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                                LOGGER.info(message);
                                                sendMessage.sendToTelegram(message, telegramToken);
                                            } else if ("COMPLETE".equals(order.status)) {
                                                trendTradeData.isSLHit = true;
                                                trendTradeData.isExited = true;
                                                BigDecimal slipage=new BigDecimal(0);
                                                if ("BUY".equals(order.transactionType)) {
                                                    trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                                    slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                                } else if ("SELL".equals(order.transactionType)) {
                                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                                    slipage = (trendTradeData.getSellPrice().subtract(trendTradeData.getSellTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                                                }
                                                String message = MessageFormat.format("SL Hit for {0}" + ":" + user.getName() + ":" + getAlgoName() +":"+ trendTradeData.getEntryType() + ":" , trendTradeData.getStockName()+": slipage:"+slipage.doubleValue());
                                                LOGGER.info(message);
                                                sendMessage.sendToTelegram(message, telegramToken);
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,false);
                                            }
                                        }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    });
                                }
                            });

                }
            }
        });
    }

    @Scheduled(cron = "${banknifty.futures.cancel.order}")
    public void cancelEntry() {
        userList.getUser().forEach(user -> {
            if (user.getBnfFutures() != null && user.getBnfFutures().isNrmlEnabled()) {
                if (user.getBnfFutures().straddleTradeMap != null) {
                    user.getBnfFutures().straddleTradeMap.entrySet().forEach(trendMap -> {
                        TradeData trendTradeData = trendMap.getValue();
                        if (trendTradeData.isOrderPlaced() && trendTradeData.getEntryOrderId() != "") {
                            try {
                                if (!trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isSLHit) {
                                    Order order = user.getKiteConnect().cancelOrder(trendTradeData.getEntryOrderId(), "regular");
                                    trendMap.getValue().isSLCancelled = true;
                                    String message = MessageFormat.format("System Cancelled Entry {0}" + ":" + user.getName() + ":" + getAlgoName(), trendMap.getValue().getStockName()+":"+ trendTradeData.getEntryType());
                                            LOGGER.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                    // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData);
                                }
                            } catch (KiteException e) {
                                e.printStackTrace();
                                String message = MessageFormat.format("Error while System cancelling SL {0},{1}", trendMap.getValue().getStockName()+":"+ trendTradeData.getEntryType(), e.message);
                                LOGGER.info(message);
                                sendMessage.sendToTelegram(message + ":" + getAlgoName(), telegramToken);
                            } catch (IOException e) {
                                e.printStackTrace();
                                String message = MessageFormat.format("Error while System cancelling SL {0},{1}", trendMap.getValue().getStockName()+":"+ trendTradeData.getEntryType(), e.getMessage());
                                LOGGER.info(message);
                                sendMessage.sendToTelegram(message + ":" + getAlgoName(), telegramToken);
                            }
                        }
                    });
                }
            }
        });
    }
    public List<OpenTradeDataEntity> openTradeDataEntities1 = new ArrayList<>();
    @Scheduled(cron = "${banknifty.futures.nextday.load.data}")
    public void loadNrmlPositions() {
        openTradeDataEntities1 = openTradeDataRepo.findAll();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored) {
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    List<Position> positions = null;
                    try {
                        positions = user.getKiteConnect().getPositions().get("net");
                    } catch (KiteException | IOException e) {
                        LOGGER.info("error wile calling position :" + openTradeDataEntity.getUserId());
                    }
                    positions.stream().filter(position -> "NRML".equals(position.product) && openTradeDataEntity.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).findFirst().ifPresent(position -> {
                        int positionQty = Math.abs(position.netQuantity);
                        if (positionQty != openTradeDataEntity.getQty()) {
                            //   openTradeDataEntity.setQty(positionQty);

                            LOGGER.info("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.");
                            sendMessage.sendToTelegram("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ",over riding position qty as trade qty." + ":" + getAlgoName(), telegramToken);

                        }
                        openTradeDataEntity.isSlPlaced = false;
                        openTradeDataEntity.setSlOrderId(null);
                        openTradeDataEntities.add(openTradeDataEntity);

                        sendMessage.sendToTelegram("Open Position: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName()+":"+openTradeDataEntity.getEntryType(), telegramToken);


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

    @Scheduled(cron = "${banknifty.futures.nextday.sl.place}")
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
                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage());

                    sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage() + ":" + getAlgoName(), telegramToken);

                }

                if (openTradeDataEntity.getEntryType().equals("BUY")) {
                    orderParams.transactionType = "SELL";
                    if (historicalData.dataArrayList.size() > 0) {
                        HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);

                        if (!status.equals("error") && lastMin.close < openTradeDataEntity.getSlPrice().doubleValue()) {
                            LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":inside buy entry type  sl condition, close is buy than sl price:" + lastMin.close + ":" + openTradeDataEntity.getSlPrice().doubleValue());
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
                            LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":inside sell entry type sl condition, close is greater than sl price:" + lastMin.close + ":" + openTradeDataEntity.getSlPrice().doubleValue());
                            // openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                            orderParams.orderType = "MARKET";
                        }
                    }
                    BigDecimal price = openTradeDataEntity.getSlPrice().add(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                    orderParams.price = price.doubleValue();
                    // LOGGER.info(price.doubleValue());

                }

                orderParams.validity = "DAY";
                com.zerodhatech.models.Order orderd;
                try {
                    LOGGER.info("inside order placement");
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    LOGGER.info("order params" + new Gson().toJson(orderParams));
                    orderd = user.getKiteConnect().placeOrder(orderParams, "regular");
                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":order response:" + new Gson().toJson(orderd));
                    openTradeDataEntity.isSlPlaced = true;
                    openTradeDataEntity.setSlOrderId(orderd.orderId);
                    openTradeDataRepo.save(openTradeDataEntity);

                    sendMessage.sendToTelegram("placed sl order for:" + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName(), telegramToken);
                } catch (Exception e) {

                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage());

                    sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage() + ":" + getAlgoName(), telegramToken);

                } catch (KiteException e) {
                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage());

                    sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage() + ":" + getAlgoName(), telegramToken);

                    // throw new RuntimeException(e);
                }
            } catch (Exception e) {
                LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while placing 9:16 sl order" + e.getMessage());
            }
        });


    }

    @Scheduled(cron = "${banknifty.futures.nextday.exit.price}")
    public void exitPriceNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orders = user.getKiteConnect().getOrders();
                List<Position> positions = user.getKiteConnect().getPositions().get("net");
                LOGGER.info(new Gson().toJson(positions));
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited && user.getName().equals(openTradeDataEntity.getUserId())).forEach(openTradeDataEntity -> {
                    orders.stream().filter(order -> "COMPLETE".equals(order.status) && openTradeDataEntity.getExitOrderId().equals(order.orderId)).findFirst().ifPresent(orderr -> {
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
                                            if (sdf.format(openDatetime).equals(openDate + "T09:28:00")) {
                                                BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                if ("SELL".equals(orderr.transactionType)) {
                                                    openTradeDataEntity.setSellPrice(new BigDecimal(historicalData1.close));
                                                } else {
                                                    openTradeDataEntity.setBuyPrice(new BigDecimal(orderr.averagePrice));
                                                }
                                                LOGGER.info("setting  9:29 exit :" + openTradeDataEntity.getStockId() + ":" + historicalData1.close);
                                            }
                                        } catch (ParseException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                }

                            } catch (Exception e) {
                                LOGGER.info(e.getMessage());
                            }
                            openTradeDataEntity.isExited = true;
                            if ("SELL".equals(orderr.transactionType)) {
                                openTradeDataEntity.setSellTradedPrice(new BigDecimal(orderr.averagePrice));
                            } else {
                                openTradeDataEntity.setBuyTradedPrice(new BigDecimal(orderr.averagePrice));
                            }
                            saveTradeData(openTradeDataEntity);
                        } catch (Exception e) {
                            LOGGER.info(e.getMessage());
                        }
                    });
                });
            } catch (Exception | KiteException e) {
                LOGGER.info(e.getMessage());
            }
        });
    }
    @Scheduled(cron = "${banknifty.futures.nextday.exit.schedule}")
    public void exitNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getStraddleConfig() != null && user.getStraddleConfig().isNrmlEnabled()).forEach(user -> {
            try {
                List<Order> orders = user.getKiteConnect().getOrders();
                List<Position> positions = user.getKiteConnect().getPositions().get("net");
                LOGGER.info(new Gson().toJson(positions));
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited && user.getName().equals(openTradeDataEntity.getUserId())).forEach(openTradeDataEntity -> {
                            orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getSlOrderId())).forEach(orderr -> {
                                try {
                                    Order order = user.getKiteConnect().cancelOrder(orderr.orderId, "regular");
                                } catch (KiteException | IOException e) {
                                    LOGGER.info(e.getMessage());
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
                                    LOGGER.info(new Gson().toJson(orderResponse));
                                      openTradeDataEntity.isExited=true;
                                    openTradeDataEntity.setExitOrderId(orderResponse.orderId);
                                    saveTradeData(openTradeDataEntity);
                                    String message = MessageFormat.format("Closed Position {0}", orderParams.tradingsymbol);
                                    LOGGER.info(message);

                                    sendMessage.sendToTelegram(message + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName(), telegramToken);

                                } catch (KiteException e) {
                                    LOGGER.info("Error while exiting straddle order: " + e.message);

                                    sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(), telegramToken);

                                    e.printStackTrace();
                                } catch (IOException e) {
                                    LOGGER.info("Error while exiting straddle order: " + e.getMessage());

                                    sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(), telegramToken);

                                    e.printStackTrace();
                                }
                                //  }
                            });

                        }
                );
            } catch (KiteException | IOException e) {
                LOGGER.info(e.getMessage());
            }


        });
    }


    @Scheduled(cron = "${banknifty.futures.nextday.sl.monitor}")
    public void sLNrmlMonitorPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
            List<Order> orderList = null;
            List<Position> positions = null;
            try {
                orderList = user.getKiteConnect().getOrders();
                positions = user.getKiteConnect().getPositions().get("net");
                //   LOGGER.info("get trade response:"+new Gson().toJson(orderList));
            } catch (KiteException | IOException e) {
                e.printStackTrace();
            }

            Optional<Order> sLOrder = orderList.stream().filter(order1 -> openTradeDataEntity.getSlOrderId().equals(order1.orderId)).findFirst();
            if (sLOrder.isPresent()) {
                Order order = sLOrder.get();
                if ("COMPLETE".equals(order.status) && !openTradeDataEntity.isExited) {
                    openTradeDataEntity.isExited = true;
                    openTradeDataEntity.isSLHit = true;
                    BigDecimal slipage;
                    if ("SELL".equals(order.transactionType)) {
                        openTradeDataEntity.setSellPrice(openTradeDataEntity.getSlPrice());
                        openTradeDataEntity.setSellTradedPrice(new BigDecimal(order.averagePrice));
                         slipage = (openTradeDataEntity.getBuyPrice().subtract(openTradeDataEntity.getBuyTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);
                    } else {
                        openTradeDataEntity.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                        openTradeDataEntity.setBuyPrice(openTradeDataEntity.getSlPrice());
                         slipage = (openTradeDataEntity.getBuyPrice().subtract(openTradeDataEntity.getBuyTradedPrice())).multiply(new BigDecimal(25)).setScale(0, BigDecimal.ROUND_UP);

                    }
                    saveTradeData(openTradeDataEntity);
                    sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName()+":"+slipage.doubleValue()+":"+openTradeDataEntity.getEntryType(), telegramToken);

                }
                if ("CANCELLED".equals(order.status) && !openTradeDataEntity.isSlCancelled) {
                    openTradeDataEntity.isSlCancelled = true;
                    saveTradeData(openTradeDataEntity);

                    sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName(), telegramToken);

                }
                if ("REJECTED".equals(order.status) && !openTradeDataEntity.isErrored) {
                    openTradeDataEntity.isErrored = true;
                    saveTradeData(openTradeDataEntity);

                    sendMessage.sendToTelegram("SL order placement rejected for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName(), telegramToken);

                }
            }
        });
    }
    @Autowired
    TradeDataMapper tradeDataMapper;
    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData,boolean orderPlaced) {
        try {/*
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
            Date date = new Date();
            if(orderPlaced) {
                String tradeDate = format.format(date);
                openTradeDataEntity.setTradeDate(tradeDate);
                tradeData.setTradeDate(tradeDate);
            }else{
                openTradeDataEntity.setTradeDate(tradeData.getTradeDate());
            }
            saveTradeData(openTradeDataEntity);*/
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData,orderPlaced,this.getAlgoName());
            //LOGGER.info("sucessfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }

    public void saveTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            Date date = new Date();
            String tradeDate = format.format(date);
            openTradeDataEntity.setModifyDate(tradeDate);
            openTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }

}
