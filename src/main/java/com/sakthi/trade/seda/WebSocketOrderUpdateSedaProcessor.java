package com.sakthi.trade.seda;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;


@Component
public class WebSocketOrderUpdateSedaProcessor implements Processor {
    Gson gson = new Gson();
    public static final Logger LOGGER = LoggerFactory.getLogger(WebSocketOrderUpdateSedaProcessor.class.getName());
    @Autowired
    UserList userList;
    @Autowired
    BrokerWorkerFactory workerFactory;
    @Autowired
    TradeSedaQueue tradeSedaQueue;
    @Autowired
    TradeEngine tradeEngine;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat exchangeDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    @Autowired
    TradeDataMapper tradeDataMapper;

    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData, boolean orderPlaced) {
        try {
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData, orderPlaced, "TRADE_ENGINE");
            LOGGER.info("successfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            Order order = gson.fromJson(message, Order.class);
            orderCheck(order);
            System.out.println("processor order update " + order.orderId + ":" + order.status + ":" + order.tradingSymbol + ":" + order.orderType + ":" + order.product + ":" + order.quantity);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void orderCheck(Order order){
        Date date = new Date();
        Calendar candleCalenderMin = Calendar.getInstance();
        candleCalenderMin.add(Calendar.MINUTE, -1);
        String currentDateStr = dateFormat.format(date);
        tradeEngine.openTrade.entrySet().stream().forEach(userTradeData -> {
            String userId = userTradeData.getKey();
            if(userId.equals("LTK728")) {
                List<TradeData> tradeData = userTradeData.getValue();
                User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(userId)).findFirst().get();
                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                try {
                    tradeData.stream().filter(order1 -> order1.getEntryOrderId() != null && !order1.isExited).forEach(trendTradeData -> {
                        TradeStrategy strategy = trendTradeData.getTradeStrategy();
                       // if (strategy.getTradeStrategyKey().equals("NORF24target")) {
                            if (!trendTradeData.isErrored && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;

                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey());
                                        LOGGER.info(message);
                                        try {
                                            tradeSedaQueue.sendTelemgramSeda(message, user.telegramBot.getGroupId());
                                        } catch (Exception e) {
                                            LOGGER.info("error:" + e);
                                        }
                                    }
                                }
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced) {
                                    if (!trendTradeData.isOrderPlaced) {
                                        if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                            trendTradeData.isOrderPlaced = true;
                                            try {
                                                LOGGER.info("order executed" + trendTradeData.getStockName() + ":" + trendTradeData.getEntryOrderId());
                                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                                    if (trendTradeData.getBuyPrice() == null) {
                                                        trendTradeData.setBuyPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                    }
                                                    trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                } else {
                                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                }
                                                String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + trendTradeData.getSellPrice() + ":" + trendTradeData.getBuyPrice());
                                                LOGGER.info(message);
                                                tradeSedaQueue.sendTelemgramSeda(message, user.telegramBot.getGroupId());
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    } else {
                                        if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                            try {
                                                LOGGER.info("order executed" + trendTradeData.getStockName() + ":" + trendTradeData.getEntryOrderId());
                                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                                    if (trendTradeData.getBuyPrice() == null) {
                                                        trendTradeData.setBuyPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                    }
                                                    trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                    trendTradeData.setBuyTime(exchangeDateTimeFormat.format(order.exchangeUpdateTimestamp));
                                                } else {

                                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice).setScale(2, RoundingMode.HALF_EVEN));
                                                    trendTradeData.setSellTime(exchangeDateTimeFormat.format(order.exchangeUpdateTimestamp));
                                                }
                                                String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + trendTradeData.getSellPrice() + ":" + trendTradeData.getBuyPrice());
                                                LOGGER.info(message);
                                                tradeSedaQueue.sendTelemgramSeda(message, user.telegramBot.getGroupId());
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }

                                }
                                if (trendTradeData.isOrderPlaced && trendTradeData.getEntryOrderId().equals(order.orderId)) {
                                    if (!trendTradeData.isSlPlaced) {
                                        try {
                                            Date orderExeutedDate = order.exchangeUpdateTimestamp;
                                            Date currentDate = new Date();
                                            long difference_In_Time = currentDate.getTime() - orderExeutedDate.getTime();
                                            long difference_In_Minutes = (difference_In_Time / (1000 * 60)) % 60;
                                            if (difference_In_Minutes > 2) {
                                                String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ": but SL not placed, Please Check");
                                                tradeSedaQueue.sendTelemgramSeda(message, user.telegramBot.getGroupId());
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    if (!trendTradeData.isSlPlaced && !trendTradeData.isErrored) {
                                        try {
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = trendTradeData.getStockName();
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = trendTradeData.getQty();
                                            orderParams.orderType = "SL";
                                            if ("MIS".equals(strategy.getTradeValidity())) {
                                                orderParams.product = "MIS";
                                            } else {
                                                orderParams.product = "NRML";
                                            }
                                            orderParams.validity = "DAY";
                                            com.zerodhatech.models.Order orderd = null;
                                            BigDecimal price;
                                            BigDecimal triggerPriceTemp;
                                            String slType = strategy.getSlType();
                                            BigDecimal slValue = strategy.getSlValue();
                                            if ("BUY".equals(order.transactionType)) {
                                                if ("POINTS".equals(slType)) {
                                                    triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(slValue)).setScale(0, RoundingMode.HALF_UP);
                                                } else {
                                                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, trendTradeData.getBuyPrice());
                                                    triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(slPoints)).setScale(0, RoundingMode.HALF_UP);
                                                }
                                                price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                orderParams.transactionType = "SELL";
                                                LOGGER.info("buy price:" + trendTradeData.getBuyPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                            } else {
                                                if ("POINTS".equals(slType)) {
                                                    triggerPriceTemp = (trendTradeData.getSellPrice().add(slValue)).setScale(0, RoundingMode.HALF_UP);
                                                } else {
                                                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, trendTradeData.getSellPrice());
                                                    triggerPriceTemp = (trendTradeData.getSellPrice().add(slPoints)).setScale(0, RoundingMode.HALF_UP);
                                                }
                                                price = triggerPriceTemp.add(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                orderParams.transactionType = "BUY";
                                                LOGGER.info("sell price:" + trendTradeData.getSellPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                            }
                                            orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                            trendTradeData.setInitialSLPrice(triggerPriceTemp);
                                            orderParams.price = price.doubleValue();
                                            trendTradeData.setSlPrice(triggerPriceTemp);
                                            try {
                                                LOGGER.info("input:" + gson.toJson(orderParams));
                                                orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                trendTradeData.isSlPlaced = true;
                                                trendTradeData.setSlOrderId(orderd.orderId);
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                try {
                                                    LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL");
                                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                } catch (Exception e) {
                                                    LOGGER.info("error:" + e);
                                                }
                                            } catch (Exception e) {
                                                trendTradeData.isErrored = true;
                                                LOGGER.info("error while placing sl order: " + e.getMessage() + ":" + user.getName());
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                            } catch (KiteException e) {
                                                throw new RuntimeException(e);
                                            }

                                        } catch (Exception e) {
                                            LOGGER.info("error while placing sl:" + e.getMessage() + trendTradeData.getEntryOrderId() + ":" + trendTradeData.getStockName());
                                        }

                                    }
                                    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && order.orderId.equals(trendTradeData.getSlOrderId()) && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                        trendTradeData.isSLHit = true;
                                        trendTradeData.isExited = true;
                                        trendTradeData.setExitOrderId(order.orderId);
                                        if ("BUY".equals(trendTradeData.getEntryType())) {
                                            trendTradeData.setSellPrice(trendTradeData.getSlPrice());
                                            trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                        } else {
                                            trendTradeData.setBuyPrice(trendTradeData.getSlPrice());
                                            trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                        }
                                        BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, RoundingMode.UP);
                                        String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage.toString(), trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getTradeStrategyKey());
                                        LOGGER.info(message);
                                        tradeSedaQueue.sendTelemgramSeda(message, user.telegramBot.getGroupId());
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                        if (strategy.isReentry() && "SELL".equals(trendTradeData.getEntryType())) {
                                            long tradeCount = userTradeData.getValue().stream().filter(tradeDataTemp ->
                                                    trendTradeData.getStockName().equals(tradeDataTemp.getStockName())
                                                            && tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey()) && tradeDataTemp.isSLHit).count();
                                            if (tradeCount < strategy.getReentryCount().intValue() + 1) {
                                                OrderParams orderParams = new OrderParams();
                                                TradeData reentryTradeData = new TradeData();
                                                reentryTradeData.setStockName(trendTradeData.getStockName());
                                                String dataKey = UUID.randomUUID().toString();
                                                reentryTradeData.setDataKey(dataKey);
                                                //TODO set sl price, entry price, exit date
                                                reentryTradeData.setQty(trendTradeData.getQty());
                                                reentryTradeData.setEntryType(strategy.getOrderType());
                                                reentryTradeData.setUserId(user.getName());
                                                reentryTradeData.setTradeDate(currentDateStr);
                                                reentryTradeData.setStockId(trendTradeData.getStockId());
                                                reentryTradeData.setStrikeId(trendTradeData.getStrikeId());
                                                reentryTradeData.setTradeStrategy(strategy);
                                                orderParams.tradingsymbol = trendTradeData.getStockName();
                                                orderParams.exchange = "NFO";
                                                orderParams.quantity = trendTradeData.getQty();
                                                orderParams.orderType = "SL";
                                                orderParams.triggerPrice = trendTradeData.getSellPrice().doubleValue();
                                                orderParams.price = trendTradeData.getSellPrice().subtract(trendTradeData.getSellPrice().divide(new BigDecimal(100))
                                                        .multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                if ("MIS".equals(strategy.getTradeValidity())) {
                                                    orderParams.product = "MIS";
                                                } else {
                                                    orderParams.product = "NRML";
                                                }
                                                orderParams.transactionType = strategy.getOrderType();
                                                orderParams.validity = "DAY";
                                                com.zerodhatech.models.Order orderd = null;
                                                try {
                                                    LOGGER.info("input:" + gson.toJson(orderParams));
                                                    orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                    reentryTradeData.setEntryOrderId(orderd.orderId);
                                                    List<TradeData> tradeDataList = tradeEngine.openTrade.get(user.getName());
                                                    tradeDataList.add(reentryTradeData);
                                                    tradeEngine.openTrade.put(user.getName(), tradeDataList);
                                                    mapTradeDataToSaveOpenTradeDataEntity(reentryTradeData, false);

                                                    try {
                                                        LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                                        tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                    } catch (Exception e) {
                                                        LOGGER.info("error:" + e);
                                                    }
                                                } catch (Exception e) {
                                                    LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                                    tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                } catch (KiteException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                        }
                                        if (strategy.isReentry() && "BUY".equals(trendTradeData.getEntryType())) {
                                            OrderParams orderParams = new OrderParams();
                                            TradeData reentryTradeData = new TradeData();
                                            reentryTradeData.setStockName(trendTradeData.getStockName());
                                            String dataKey = UUID.randomUUID().toString();
                                            reentryTradeData.setDataKey(dataKey);
                                            //TODO set sl price, entry price, exit date
                                            reentryTradeData.setQty(trendTradeData.getQty());
                                            reentryTradeData.setEntryType(strategy.getOrderType());
                                            reentryTradeData.setUserId(user.getName());
                                            reentryTradeData.setTradeDate(currentDateStr);
                                            reentryTradeData.setStockId(trendTradeData.getStockId());
                                            reentryTradeData.setStrikeId(trendTradeData.getStrikeId());
                                            reentryTradeData.setTradeStrategy(strategy);
                                            orderParams.tradingsymbol = trendTradeData.getStockName();
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = trendTradeData.getQty();
                                            orderParams.orderType = "SL";
                                          //  orderParams.triggerPrice = trendTradeData.get().doubleValue();
                                            double triggerPriceTemp = 0;
                                            if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_UP.getType())) {
                                                triggerPriceTemp=(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), trendTradeData.getSlPrice()).add(trendTradeData.getSlPrice())).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            }
                                            if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_DOWN.getType())) {
                                                triggerPriceTemp=((trendTradeData.getSlPrice()).subtract(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), trendTradeData.getSlPrice())).setScale(0, RoundingMode.HALF_UP).doubleValue());
                                            }
                                            if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_UP.getType())) {
                                                triggerPriceTemp=(trendTradeData.getSlPrice().add(strategy.getSimpleMomentumValue())).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            }
                                            if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_DOWN.getType())) {
                                                triggerPriceTemp=(trendTradeData.getSlPrice().subtract(strategy.getSimpleMomentumValue()))
                                                        .setScale(0, RoundingMode.HALF_UP).doubleValue();

                                            }
                                            System.out.println(triggerPriceTemp);
                                            orderParams.triggerPrice = triggerPriceTemp;
                                            orderParams.price = BigDecimal.valueOf(triggerPriceTemp).add(BigDecimal.valueOf(triggerPriceTemp).divide(new BigDecimal(100))
                                                    .multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            if ("MIS".equals(strategy.getTradeValidity())) {
                                                orderParams.product = "MIS";
                                            } else {
                                                orderParams.product = "NRML";
                                            }
                                            orderParams.transactionType = strategy.getOrderType();
                                            orderParams.validity = "DAY";
                                            com.zerodhatech.models.Order orderd = null;
                                            try {
                                                LOGGER.info("input:" + gson.toJson(orderParams));
                                                orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                reentryTradeData.setEntryOrderId(orderd.orderId);
                                                List<TradeData> tradeDataList = tradeEngine.openTrade.get(user.getName());
                                                tradeDataList.add(reentryTradeData);
                                                tradeEngine.openTrade.put(user.getName(), tradeDataList);
                                                mapTradeDataToSaveOpenTradeDataEntity(reentryTradeData, false);

                                                try {
                                                    LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                } catch (Exception e) {
                                                    LOGGER.info("error:" + e);
                                                }
                                            } catch (Exception e) {
                                                LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                            } catch (KiteException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                        if (strategy.isTrailToCost()) {
                                            userTradeData.getValue().stream().filter(tradeDataTemp -> tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey())
                                                    && !tradeDataTemp.isSLHit).forEach(tradeDataMod -> {
                                                try {
                                                    Order order1 = brokerWorker.getOrder(user, tradeDataMod.getSlOrderId());
                                                    OrderParams orderParams = new OrderParams();
                                                    if ("BUY".equals(trendTradeData.getEntryType())) {
                                                        orderParams.triggerPrice = tradeDataMod.getBuyPrice().setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                                                        double price = tradeDataMod.getBuyPrice().subtract(tradeDataMod.getBuyPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                        orderParams.price = price;
                                                    } else {
                                                        orderParams.triggerPrice = tradeDataMod.getSellPrice().setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                                                        double price = tradeDataMod.getSellPrice().add(tradeDataMod.getSellPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                        orderParams.price = price;
                                                    }
                                                    brokerWorker.modifyOrder(order1.orderId, orderParams, user, tradeDataMod);
                                                    tradeSedaQueue.sendTelemgramSeda("executed trail sl " + trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                } catch (IOException e) {
                                                    tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                    throw new RuntimeException(e);
                                                } catch (KiteException e) {
                                                    tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                        }
                                    }

                                } else if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                                    String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + strategy.getTradeStrategyKey());
                                    LOGGER.info(message);
                                    trendTradeData.isErrored = true;
                                    tradeSedaQueue.sendTelemgramSeda(message, user.telegramBot.getGroupId());
                                }
                            }
                       // }

                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }
}
