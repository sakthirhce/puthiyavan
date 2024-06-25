package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.cache.GlobalTick;
import com.sakthi.trade.cache.GlobalTickCache;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.entity.UserSubscription;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ExpiryDayDetails;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Tick;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WebSocketTicksSedaProcessor implements Processor {
    Gson gson = new Gson();
    @Autowired
    TradeEngine tradeEngine;
    @Value("${data.export}")
    boolean dataExport;
    @Autowired
    BrokerWorkerFactory workerFactory;
    public static final Logger LOGGER = LoggerFactory.getLogger(WebSocketTicksSedaProcessor.class.getName());
    @Autowired
    UserList userList;
    @Autowired
    TradeDataMapper tradeDataMapper;
    @Autowired
    TradeSedaQueue tradeSedaQueue;
    DecimalFormat decimalFormat = new DecimalFormat("0.00");
    Type listType = new TypeToken<List<Tick>>() {
    }.getType();
    CSVWriter csvWriter;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    ExecutorService executor = Executors.newFixedThreadPool(10);
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat sstimeFormat = new SimpleDateFormat("ss");
    @Autowired
    ExpiryDayDetails expiryDayDetails;

    public WebSocketTicksSedaProcessor() {
        try {
            Date date = new Date();
            csvWriter = new CSVWriter(new FileWriter("/home/ubuntu/tick_" + dateFormat.format(date) + ".csv", true));
            String[] dataHeader = {"instrument_token", "tick_time", "last_price"};
            csvWriter.writeNext(dataHeader);
            csvWriter.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Autowired
    GlobalTickCache globalTickCache;

    @Autowired
    CommonUtil commonUtil;

    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            ArrayList<Tick> ticks = gson.fromJson(message, listType);
            LocalTime currentTime = LocalTime.now();
            LocalDateTime currentDateTime = LocalDateTime.now();
            if (!ticks.isEmpty()) {
                try {
                    LocalTime tickStartTime = LocalTime.of(9, 14);
                    LocalTime tickEndTime = LocalTime.of(15, 31);
                    if (currentTime.isAfter(tickStartTime) && currentTime.isBefore(tickEndTime)) {
                        if (dataExport) {
                            ticks.forEach(tick -> {
                                String[] dataHeader = {String.valueOf(tick.getInstrumentToken()), candleDateTimeFormat.format(tick.getTickTimestamp()), String.valueOf(tick.getLastTradedPrice())};
                                csvWriter.writeNext(dataHeader);
                            });
                            csvWriter.flush();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                tradeEngine.openTrade.forEach((userId, tradeDataList) -> {
                    User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    tradeDataList.forEach(tradeData -> {
                        Optional<Tick> tickOp = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == tradeData.getStockId()).findFirst();
                        if (tickOp.isPresent()) {
                            Tick tick = tickOp.get();
                            double lastTradedPrice = tick.getLastTradedPrice();
                            globalTickCache.setHistoricData(tradeData.getStockId(), lastTradedPrice);
                            TradeStrategy strategy = tradeData.getTradeStrategy();
                            try {
                                if (tradeData.getEntryOrderId() == null && !tradeData.isOrderPlaced) {
                                    if (tradeData.range && "BUY".equals(tradeData.getEntryType())) {

                                        if (tradeData.getRangeHigh() != null && tradeData.getRangeHigh().doubleValue() < lastTradedPrice) {
                                            LOGGER.info("option price higher than range:{},last traded price: {},high:{} ", tradeData.getStockName(), lastTradedPrice, tradeData.getRangeHigh().doubleValue());
                                            OrderParams orderParams = new OrderParams();

                                            orderParams.exchange = "NFO";
                                            if ("SS".equals(strategy.getIndex())) {
                                                orderParams.exchange = "BFO";
                                            }
                                            if ("MIS".equals(strategy.getTradeValidity())) {
                                                orderParams.product = "MIS";
                                            } else {
                                                orderParams.product = "NRML";
                                            }
                                            orderParams.tradingsymbol = tradeData.getStockName();
                                            orderParams.transactionType = strategy.getOrderType();
                                            orderParams.validity = "DAY";
                                            orderParams.orderType = "MARKET";
                                            Optional<UserSubscription> subscriptionOptional = strategy.getUserSubscriptions().getUserSubscriptionList().stream()
                                                    .filter(userSubscription -> userSubscription.getUserId().equals(strategy.getUserId())).findFirst();
                                            if (subscriptionOptional.isPresent()) {
                                                UserSubscription subscription = subscriptionOptional.get();
                                                Order order;
                                                int lot = strategy.getLotSize() * subscription.getLotSize();
                                                orderParams.quantity = lot;
                                                tradeData.setQty(lot);
                                                try {
                                                    order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                    tradeData.setEntryOrderId(order.orderId);

                                                } catch (IOException | KiteException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                tradeData.setEntryOrderId(order.orderId);
                                                tradeData.isOrderPlaced = true;
                                                mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                                tradeSedaQueue.sendTelemgramSeda("Range Options traded for user:" + user.getName() + " strike: " + tradeData.getStockName() + ":" + strategy.getTradeStrategyKey(), "exp-trade");

                                            }
                                        }

                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("error while placing range order:{}", e.getMessage());
                            }

                            if (tradeData.getEntryOrderId() != null && tradeData.isOrderPlaced && !tradeData.isExited && !tradeData.isSLCancelled) {

                                try {
                                    GlobalTick globalTick = globalTickCache.getHistoricData((int) tick.getInstrumentToken());
                                    if (globalTick != null) {
                                        List<Double> tickData = globalTick.getHistoricalDataMap();
                                        if (!tickData.isEmpty()) {
                                            double lastTick = tickData.get(tickData.size() - 1);
                                            double diff = lastTradedPrice - lastTick;
                                            double percentCheck = 15;
                                            if (lastTick > 20 && lastTick < 30) {
                                                percentCheck = 20;
                                            }
                                            if (lastTick < 20) {
                                                percentCheck = 40;
                                            }
                                            if (lastTick < 10) {
                                                percentCheck = 80;
                                            }
                                            if (lastTick > 50 ) {
                                                percentCheck = 10;
                                            }
                                            if (diff > 0 && lastTradedPrice > 20) {
                                                double percentageDiffOfLastTradePrice = 0.1 * lastTradedPrice;
                                                double percentageDiffOfSoldPrice = 0.1 * tradeData.getSellPrice().doubleValue();
                                                if ((percentageDiffOfSoldPrice <= diff || percentageDiffOfLastTradePrice <= diff)) {
                                                    LOGGER.info("last traded price difference is more than 10%,{}", String.format("%,.2f", diff));
                                                    tradeSedaQueue.sendTelemgramSeda("last traded price difference is more than 10%. stock: " + tradeData.getStockName() + " last trade price: " + lastTradedPrice + "diff: " + String.format("%,.2f", diff), "algo");
                                                    double percentToSoldPrice = (diff / tradeData.getSellPrice().doubleValue()) * 100;
                                                    if (percentToSoldPrice <= 20) {
                                                        LOGGER.info("The difference is {}% to touch the sold price.", String.format("%,.2f", percentToSoldPrice));
                                                        tradeSedaQueue.sendTelemgramSeda("The difference is less than " + String.format("%,.2f", percentToSoldPrice) + "% to touch the sold price. stock:" + tradeData.getStockName() + " last trade price: " + lastTradedPrice + " diff: " + String.format("%,.2f", diff) + " sell price: " + String.format("%,.2f", tradeData.getSellPrice().doubleValue()), "algo");
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                try {
                                    GlobalTick globalTick = globalTickCache.getHistoricData((int) tick.getInstrumentToken());
                                    if (globalTick != null) {
                                        List<Double> tickData = globalTick.getHistoricalDataMap();
                                        List<Double> tickData30Sec = globalTick.getHistoricalDataMap();
                                        if (!tickData.isEmpty() && tickData30Sec.size() > 30) {
                                            double lastTick = tickData.get(tickData.size() - 1);
                                            double percentCheck = 15;
                                            if (lastTick > 20 && lastTick < 30) {
                                                percentCheck = 20;
                                            }
                                            if (lastTick < 20) {
                                                percentCheck = 40;
                                            }
                                            if (lastTick < 10) {
                                                percentCheck = 60;
                                            }
                                            if (lastTick > 50) {
                                                percentCheck = 10;
                                            }
                                            if (lastTick > 20) {
                                                List<Double> subTickData = tickData.subList(tickData.size() - 30, tickData.size() - 1);
                                                double low = Double.MAX_VALUE;
                                                double high = Double.MIN_VALUE;
                                                for (int i = 1; i <= subTickData.size(); i++) {
                                                    double currentData = subTickData.get(i);
                                                    if (currentData > high) {
                                                        high = currentData;
                                                    }
                                                    if (currentData < low) {
                                                        low = currentData;
                                                    }
                                                }
                                                double diff = high - low;
                                                double percentageDiff = (diff / tradeData.getSellPrice().doubleValue()) * 100;
                                                if (percentageDiff > percentCheck && diff > 0) {
                                                    LOGGER.info("stock:{}:high:{}:low:{}:diff:{}:percentageDiff:{}", tradeData.getStockName(), high, low, diff, percentageDiff);
                                                    tradeSedaQueue.sendTelemgramSeda("stock:" + tradeData.getStockName() + " high:" + high + " low:" + low + " diff:" + diff + " percentageDiff:" + percentageDiff, "algo");
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                try {
                                    if (!tradeData.isWebsocketSlModified() && tradeData.isSlPriceCalculated && !tradeData.isSlPlaced) {
                                        if ("SELL".equals(tradeData.getEntryType())) {
                                            BigDecimal testPrice = tradeData.getSlPrice();
                                            if (lastTradedPrice > testPrice.doubleValue()) {
                                                LOGGER.info("last traded price above sl:{}:{}", tradeData.getStockName(), lastTradedPrice);
                                                // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                                OrderParams orderParams = new OrderParams();
                                                if (strategy.getTradeStrategyKey().length() <= 20) {
                                                    orderParams.tag = strategy.getTradeStrategyKey();
                                                }
                                                orderParams.tradingsymbol = tradeData.getStockName();
                                                orderParams.exchange = "NFO";
                                                orderParams.quantity = tradeData.getQty();
                                                orderParams.orderType = "SL";
                                                if ("MIS".equals(strategy.getTradeValidity())) {
                                                    orderParams.product = "MIS";
                                                } else {
                                                    orderParams.product = "NRML";
                                                }
                                                if ("SS".equals(strategy.getIndex())) {
                                                    orderParams.exchange = "BFO";
                                                }
                                                orderParams.validity = "DAY";
                                                orderParams.transactionType = "BUY";
                                                orderParams.orderType = "LIMIT";
                                                orderParams.price = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                                                if (!tradeData.isWebsocketSlModified()) {
                                                    Order order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                    // postOrderModifyDataToSeda(tradeData, orderParams, null,"placeOrder");
                                                    tradeData.isSlPlaced = true;
                                                    tradeData.setSlOrderId(order.orderId);
                                                    tradeData.setWebsocketSlModified(true);
                                                    tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                }
                                            }
                                        } else {
                                            BigDecimal testPrice = tradeData.getSlPrice();
                                            if (lastTradedPrice < testPrice.doubleValue()) {
                                                LOGGER.info("last traded price below sl:{}:{}", tradeData.getStockName(), lastTradedPrice);
                                                Order order1;
                                                try {
                                                    order1 = brokerWorker.getOrder(user, tradeData.getSlOrderId());
                                                } catch (IOException | KiteException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                OrderParams orderParams = new OrderParams();
                                                orderParams.triggerPrice = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                                                orderParams.price = tradeData.getSlPrice().subtract(tradeData.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                orderParams.orderType = "LIMIT";
                                                orderParams.price = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                                                if (!tradeData.isWebsocketSlModified()) {
                                                    postOrderModifyDataToSeda(tradeData, orderParams, order1.orderId);
                                                    tradeData.setWebsocketSlModified(true);
                                                    tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                }
                                            }
                                        }

                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    tradeSedaQueue.sendTelemgramSeda("Error while processing websocket dynamic SL" +
                                            " order:" + user.getName() + ",Exception:" + e.getMessage() + ":" + tradeData.getTradeStrategy().getTradeStrategyKey(), "exp-trade");
                                } catch (KiteException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            if (strategy.isTrailEnabled()) {
                                if (tradeData.isOrderPlaced && !tradeData.isExited && !tradeData.isSlPlaced) {
                                    System.out.printf("stock:" + tradeData.getStockName() + ":" + tick.getLastTradedPrice() + ":" + tradeData.getTradeStrategy().getTradeStrategyKey());
                                    double entryPrice;
                                    if ("BUY".equals(tradeData.getEntryType())) {
                                        entryPrice = tradeData.getBuyPrice().doubleValue();
                                    } else {
                                        entryPrice = tradeData.getSellPrice().doubleValue();
                                    }
                                    double trailingStopLoss =
                                            updatedTrailPrice(tradeData.getInitialSLPrice().doubleValue(), entryPrice, tick.getLastTradedPrice(), strategy.getTrailPointMove().intValue(), strategy.getTrailSlMoves().intValue(), tradeData.getEntryType());
                                    try {
                                        Order order1 = brokerWorker.getOrder(user, tradeData.getSlOrderId());
                                        OrderParams orderParams = new OrderParams();
                                        if ("BUY".equals(tradeData.getEntryType())) {
                                            if (Double.parseDouble(order1.triggerPrice) < trailingStopLoss) {
                                                orderParams.price = new BigDecimal(trailingStopLoss).subtract(new BigDecimal(trailingStopLoss).divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            } else {
                                                trailingStopLoss = 0;
                                            }
                                        } else {

                                            if (Double.parseDouble(order1.triggerPrice) > trailingStopLoss) {
                                                orderParams.price = new BigDecimal(trailingStopLoss).add(new BigDecimal(trailingStopLoss).divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            } else {
                                                trailingStopLoss = 0;
                                            }
                                        }
                                        if (trailingStopLoss > 0) {
                                            orderParams.triggerPrice = Double.valueOf(decimalFormat.format(trailingStopLoss));
                                            tradeData.setSlPrice(new BigDecimal(trailingStopLoss).setScale(0, RoundingMode.HALF_UP));
                                            brokerWorker.modifyOrder(order1.orderId, orderParams, user, tradeData);
                                            tradeSedaQueue.sendTelemgramSeda("executed trail sl " + tradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                        }
                                    } catch (IOException e) {
                                        tradeSedaQueue.sendTelemgramSeda("error while modifying trail sl Option " + tradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                        throw new RuntimeException(e);
                                    } catch (KiteException e) {
                                        tradeSedaQueue.sendTelemgramSeda("error while modifying trail sl Option " + tradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        }
                    });

                });
                executor.submit(() -> {
                    try {
                        LocalTime tickStartTime = LocalTime.of(9, 14);
                        LocalTime tickEndTime = LocalTime.of(15, 31);
                        if (currentTime.isAfter(tickStartTime) && currentTime.isBefore(tickEndTime)) {
                            //  ticks.stream().forEach(tick -> {
                            Optional<Tick> tickOp = ticks.stream().filter(tickTemp -> expiryDayDetails.expiryStockId != null
                                    && tickTemp.getInstrumentToken() == Long.parseLong(expiryDayDetails.expiryStockId)).findFirst();
                            if (tickOp.isPresent()) {
                                Tick indexTickData = tickOp.get();
                                expiryDayDetails.expiryCurrentAtmValue = commonUtil.findATMTick((int) indexTickData.getLastTradedPrice(), expiryDayDetails.expIndexName);
                                try {
                                    String ssTime = sstimeFormat.format(indexTickData.getTickTimestamp());
                                    if ("00".equals(ssTime)) {
                                        LOGGER.info("atm:{}:{}", expiryDayDetails.expiryCurrentAtmValue, indexTickData.getLastTradedPrice());
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("error while calculating expiry ATM:{}", e.getMessage());
                                }

                            }
                            //    });
                        }

                    } catch (Exception e) {
                        LOGGER.error("error while calculating expiry ATM:{}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData, boolean orderPlaced) {
        try {
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData, orderPlaced, "TRADE_ENGINE");
            LOGGER.info("sucessfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }

    public void postOrderModifyDataToSeda(TradeData tradeData, OrderParams orderParams, String orderId) {
        User userB = userList.getUser().stream().filter(user -> tradeData.getUserId().equals(user.getName())).findFirst().get();
        OrderSedaData orderSedaData = new OrderSedaData();
        orderSedaData.setOrderParams(orderParams);
        orderSedaData.setUser(userB);
        orderSedaData.setOrderModificationType("modify");
        orderSedaData.setOrderId(orderId);
        tradeSedaQueue.sendOrderPlaceSeda(orderSedaData);
        // });
    }

    public double updatedTrailPrice(double initialSL, double entryPrice, double newPrice, int pointMove, int increaseSL, String entryType) {
        // Calculate the trailing stop-loss based on the price movement
        double trailingStopLoss = 0;
        int localPriceMove = (int) (newPrice - entryPrice);
        System.out.println("price move diff:" + localPriceMove + ":pointMove:" + pointMove + ":initialSL:" + initialSL + ":increaseSL:" + increaseSL + ":entryPrice:" + entryPrice + ":newPrice:" + newPrice);
        if (localPriceMove >= pointMove) {
            // For every this.pointMove, increase stop-loss by this.increaseSL points
            for (int i = increaseSL; i <= localPriceMove; i = i + increaseSL) {
                double trailingStopLossNew;
                if ("BUY".equals(entryType)) {
                    trailingStopLossNew = initialSL + i;
                } else {
                    trailingStopLossNew = initialSL - i;
                }
                if (newPrice > trailingStopLossNew && "BUY".equals(entryType)) {
                    trailingStopLoss = trailingStopLossNew;
                } else if (newPrice < trailingStopLossNew && "SELL".equals(entryType)) {
                    trailingStopLoss = trailingStopLossNew;
                }
            }
            System.out.printf("trailingStopLoss:" + trailingStopLoss);
        }
        return trailingStopLoss;
    }
}
