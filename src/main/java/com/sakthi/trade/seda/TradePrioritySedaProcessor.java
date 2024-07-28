package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.repo.PositionPLDataRepo;
import com.sakthi.trade.service.TradingStrategyAndTradeData;
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;

@Component
public class TradePrioritySedaProcessor implements Processor {
    Gson gson = new Gson();

    @Autowired
    BrokerWorkerFactory workerFactory;
    @Autowired
    UserList userList;
    @Autowired
    TradingStrategyAndTradeData tradingStrategyAndTradeData;
    public static final Logger LOGGER = LoggerFactory.getLogger(WebSocketTicksSedaProcessor.class.getName());
    public TradePrioritySedaProcessor(){
    }

    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            TradeData tradeData = gson.fromJson(message, TradeData.class);
            tradingStrategyAndTradeData.openTrade.forEach((userId, tradeDataList) -> {
                User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                List<Order> orders = List.of();
                try {
                    orders = brokerWorker.getOrders(user);
                } catch (IOException | KiteException e) {
                    LOGGER.error("error1 while getting orders to place target:{}", e.getMessage());
                    e.printStackTrace();
                }
                if (userId.equals(user.getName())) {
                    try {
                        List<Order> finalOrders = orders;
                        tradeDataList.stream().filter(order -> order.getEntryOrderId() != null
                                        && order.getEntryOrderId().equals(tradeData.getEntryOrderId()) && !order.isTargetOrderPlaced()
                                        && order.isOrderPlaced && order.getTradeStrategy().isTarget() &&
                                        !order.isExited).
                                forEach(trendTradeData -> {
                                    Optional<Order> orderOptional=finalOrders.stream()
                                            .filter(order -> ("COMPLETE".equals(order.status))).findFirst();
                                    if(orderOptional.isPresent()) {
                                        TradeStrategy strategy = trendTradeData.getTradeStrategy();
                                        String targetType = strategy.getTargetType();
                                        BigDecimal targetValue = strategy.getTargetValue();
                                        OrderParams orderParams = new OrderParams();
                                        if (strategy.getTradeStrategyKey().length() <= 20) {
                                            orderParams.tag = strategy.getTradeStrategyKey();
                                        }
                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                        orderParams.exchange = "NFO";
                                        orderParams.quantity = trendTradeData.getQty();
                                        orderParams.orderType = "LIMIT";
                                        if ("MIS".equals(strategy.getTradeValidity())) {
                                            orderParams.product = "MIS";
                                        } else {
                                            orderParams.product = "NRML";
                                        }
                                        if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                            orderParams.exchange = "BFO";
                                        }
                                        if ("MIS".equals(strategy.getTradeValidity())
                                                && "BUY".equals(strategy.getOrderType()) && !strategy.isHedge()) {
                                            orderParams.product = "NRML";
                                        }
                                        orderParams.validity = "DAY";
                                        Order orderd = null;
                                        BigDecimal price;
                                        if ("BUY".equals(trendTradeData.getEntryType())) {
                                            if ("POINTS".equals(targetType)) {
                                                price = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getBuyPrice().add(targetValue));
                                            } else {
                                                BigDecimal slPoints = MathUtils.percentageValueOfAmount(targetValue, trendTradeData.getBuyPrice());
                                                price = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getBuyPrice().add(slPoints));
                                            }
                                            orderParams.transactionType = "SELL";
                                            if (trendTradeData.getBuyPrice() != null) {
                                                LOGGER.info("buy price:{} target price: {}", trendTradeData.getBuyPrice().doubleValue(), price.doubleValue());
                                            }
                                        } else {
                                            if ("POINTS".equals(targetType)) {
                                                price = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice().subtract(targetValue));
                                            } else {
                                                BigDecimal slPoints = MathUtils.percentageValueOfAmount(targetValue, trendTradeData.getSellPrice());
                                                price = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice().subtract(slPoints));
                                            }
                                            orderParams.transactionType = "BUY";
                                            if (trendTradeData.getSellPrice() != null) {
                                                LOGGER.info("sell price:{} target price: {}", trendTradeData.getSellPrice().doubleValue(), price.doubleValue());
                                            }
                                        }
                                        orderParams.price = price.doubleValue();
                                        System.out.println("trade target data" + gson.toJson(trendTradeData));
                                        try {
                                            LOGGER.info("input:" + gson.toJson(orderParams));

                                            if (!trendTradeData.isTargetOrderPlaced()) {
                                                LOGGER.info("websocket not enabled for:{}", gson.toJson(orderParams));
                                                try {
                                                    orderParams.price = MathUtils.roundToNearestFivePaiseUP(price).doubleValue();
                                                    orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                    trendTradeData.setTargetOrderPlaced(true);
                                                    trendTradeData.setTargetOrderId(orderd.orderId);
                                                } catch (IOException | KiteException e) {
                                                    LOGGER.error("error while placing target kite order:{}", e.getMessage());
                                                    e.printStackTrace();
                                                    //  throw e;
                                                }
                                            }
                                        } catch (Exception e) {
                                            LOGGER.error("error1 while placing target kite order:{}", e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        LOGGER.error("error2 while placing target kite order:{}", e.getMessage());
                        e.printStackTrace();
                    }
                }
            });

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
