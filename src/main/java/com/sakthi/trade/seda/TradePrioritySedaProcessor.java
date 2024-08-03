package com.sakthi.trade.seda;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.service.TradeHelperService;
import com.sakthi.trade.service.TradingStrategyAndTradeData;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TradePrioritySedaProcessor implements Processor {
    Gson gson = new Gson();

    @Autowired
    BrokerWorkerFactory workerFactory;
    @Autowired
    UserList userList;
    @Autowired
    TradingStrategyAndTradeData tradingStrategyAndTradeData;
    public static final Logger LOGGER = LoggerFactory.getLogger(TradePrioritySedaProcessor.class.getName());
    @Autowired
    public TransactionService transactionService;
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Autowired
    TradeHelperService tradeHelperService;
    public TradePrioritySedaProcessor(){
    }

    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            Date date = new Date();
            String currentDateStr = tradingStrategyAndTradeData.dateFormat.format(date);
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
                                        && order.isOrderPlaced && order.getTradeStrategy().isTarget() && order.getTradeStrategy().isFreakBuy() &&
                                        !order.isExited).
                                forEach(trendTradeData -> {
                                    Optional<Order> orderOptional=finalOrders.stream()
                                            .filter(zorder -> (zorder.exchangeOrderId.equals(trendTradeData.getEntryOrderId()) && "COMPLETE".equals(zorder.status))).findFirst();

                                    if(orderOptional.isPresent()) {
                                        Order order=orderOptional.get();
                                        TradeStrategy strategy = trendTradeData.getTradeStrategy();

                                        trendTradeData.setBuyPrice(new BigDecimal(order.averagePrice));
                                        trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                        try{
                                        trendTradeData.setBuyTime(tradingStrategyAndTradeData.exchangeDateTimeFormat.format(order.exchangeTimestamp));
                                        }catch (Exception e){
                                            LOGGER.error("error while setting buy time for freak trade,{}",e.getMessage());
                                            e.printStackTrace();
                                        }
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
                                        Order orderd;
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
                                                LOGGER.info("target order: buy price:{} target price: {}", trendTradeData.getBuyPrice().doubleValue(), price.doubleValue());
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
                                                LOGGER.info("target order: sell price:{} target price: {}", trendTradeData.getSellPrice().doubleValue(), price.doubleValue());
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
                                        if (!trendTradeData.isSlPriceCalculated && !trendTradeData.isErrored & !trendTradeData.getTradeStrategy().isHedge() && !trendTradeData.isNoSl()) {
                                            //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                            LocalTime localTime=LocalTime.now();
                                            String exitTime=localTime.plusHours(2).format(tradingStrategyAndTradeData.dateFormatHHmm);
                                            strategy.setExitTime(exitTime);
                                            try {
                                                // tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                                OrderParams orderParams1 = new OrderParams();
                                                orderParams1.tradingsymbol = trendTradeData.getStockName();
                                                orderParams1.exchange = "NFO";
                                                orderParams1.quantity = trendTradeData.getQty();
                                                orderParams1.orderType = "SL";
                                                if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                                    orderParams1.exchange = "BFO";
                                                }
                                                orderParams1.product = order.product;
                                                orderParams1.validity = "DAY";
                                                Order orderda = null;
                                                BigDecimal price1;
                                                BigDecimal triggerPriceTemp;
                                                String slType = strategy.getSlType();
                                                BigDecimal slValue = strategy.getSlValue();
                                                if ("BUY".equals(order.transactionType)) {
                                                    if ("POINTS".equals(slType)) {
                                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getBuyPrice().subtract(slValue));
                                                    } else {
                                                        BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, trendTradeData.getBuyPrice());
                                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getBuyPrice().subtract(slPoints));
                                                    }
                                                    try {
                                                        BigDecimal webtriggerPriceTr = getTempSLPrice(trendTradeData, strategy);
                                                        BigDecimal webtriggerPrice = new BigDecimal(0);
                                                        if (webtriggerPriceTr != null && webtriggerPriceTr.doubleValue() > 0) {
                                                            webtriggerPrice = webtriggerPriceTr.subtract(webtriggerPriceTr.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                        }
                                                        trendTradeData.setTempSlPrice(webtriggerPriceTr);
                                                        if (webtriggerPrice != null && trendTradeData.getBuyPrice() != null) {
                                                            LOGGER.info("buy web price:{} sl price: {}", trendTradeData.getBuyPrice().doubleValue(), webtriggerPrice.doubleValue());
                                                        }
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    price1 = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams1.transactionType = "SELL";
                                                    if (trendTradeData.getBuyPrice() != null) {
                                                        LOGGER.info("buy price:{} sl price: {}", trendTradeData.getBuyPrice().doubleValue(), triggerPriceTemp.doubleValue());
                                                    }
                                                } else {
                                                    if ("POINTS".equals(slType)) {
                                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice().add(slValue));
                                                    } else {
                                                        BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, trendTradeData.getSellPrice());
                                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice().add(slPoints));
                                                    }
                                                    try {
                                                        BigDecimal triggerPriceT = getSLPrice(currentDateStr, String.valueOf(trendTradeData.getZerodhaStockId()), trendTradeData);
                                                        if (triggerPriceT != null && triggerPriceT.doubleValue() > 0) {
                                                            triggerPriceTemp = getSLPrice(currentDateStr, String.valueOf(trendTradeData.getZerodhaStockId()), trendTradeData);
                                                        }
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    try {
                                                        BigDecimal webtriggerPriceTr = getTempSLPrice(trendTradeData, strategy);
                                                        BigDecimal webtriggerPrice = new BigDecimal(0);
                                                        if (webtriggerPriceTr != null && webtriggerPriceTr.doubleValue() > 0) {
                                                            webtriggerPrice = webtriggerPriceTr.add(webtriggerPriceTr.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                        }
                                                        trendTradeData.setTempSlPrice(webtriggerPriceTr);
                                                        if (webtriggerPrice != null && trendTradeData.getSellPrice() != null) {
                                                            LOGGER.info("sell web price:{} sl price: {}", trendTradeData.getSellPrice().doubleValue(), webtriggerPrice.doubleValue());
                                                        }
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    price1 = triggerPriceTemp.add(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams1.transactionType = "BUY";
                                                    if (trendTradeData.getSellPrice() != null) {
                                                        LOGGER.info("sell price:{} sl price: {}", trendTradeData.getSellPrice().doubleValue(), triggerPriceTemp.doubleValue());
                                                    }
                                                }
                                                orderParams1.triggerPrice = trendTradeData.getTempSlPrice().doubleValue();
                                                BigDecimal webtriggerPrice = trendTradeData.getTempSlPrice();
                                                if ("BUY".equals(order.transactionType)) {
                                                    if (trendTradeData.getTempSlPrice() != null && trendTradeData.getTempSlPrice().doubleValue() > 0) {
                                                        webtriggerPrice = trendTradeData.getTempSlPrice().subtract(trendTradeData.getTempSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                    }
                                                } else {
                                                    if (trendTradeData.getTempSlPrice() != null && trendTradeData.getTempSlPrice().doubleValue() > 0) {
                                                        webtriggerPrice = trendTradeData.getTempSlPrice().add(trendTradeData.getTempSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                    }
                                                }
                                                orderParams1.price = price1.doubleValue();
                                                orderParams1.triggerPrice = triggerPriceTemp.doubleValue();
                                                trendTradeData.setSlPrice(triggerPriceTemp);
                                                trendTradeData.setTradeStrategy(strategy);
                                                System.out.println("trade sl data"+ gson.toJson(trendTradeData));
                                                try {
                                                    LOGGER.info("input:" + gson.toJson(orderParams1));

                                                    if(!strategy.isWebsocketSlEnabled() && !strategy.isHedge() && !strategy.isNoSl()) {
                                                        LOGGER.info("websocket not enabled for:{}", gson.toJson(orderParams1));
                                                        try {
                                                            orderParams1.price=MathUtils.roundToNearestFivePaiseUP(price).doubleValue();
                                                            trendTradeData.setInitialSLPrice(price);
                                                            orderda = brokerWorker.placeOrder(orderParams1, user, trendTradeData);
                                                            trendTradeData.isSlPlaced = true;
                                                            trendTradeData.setSlOrderId(orderda.orderId);
                                                            trendTradeData.isSlPriceCalculated = true;
                                                        } catch (IOException | KiteException e) {
                                                            e.printStackTrace();
                                                            throw e;
                                                        }
                                                    }else {
                                                        trendTradeData.isSlPriceCalculated = true;
                                                    }
                                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                    try {
                                                        LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL");
                                                        if(trendTradeData.getSellPrice()!=null) {
                                                            tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey() + ":sell price: " + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice()).doubleValue() + " sl price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
                                                        }
                                                        if(trendTradeData.getBuyPrice()!=null) {
                                                            tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey() + ":sell price: " + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getBuyPrice()).doubleValue() + " sl price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
                                                        }
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                        LOGGER.info("error:" + e);
                                                    }
                                                } catch (Exception e) {
                                                    trendTradeData.isErrored = true;
                                                    e.printStackTrace();
                                                    LOGGER.info("error while placing sl order: {}:{}", e.getMessage(), user.getName());
                                                    tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                } catch (KiteException e) {
                                                    e.printStackTrace();
                                                    if(!strategy.isWebsocketSlEnabled()) {
                                                        trendTradeData.isErrored = true;
                                                        LOGGER.info("KITE error while placing sl order: {}:{}", e.getMessage(), user.getName());
                                                        tradeSedaQueue.sendTelemgramSeda("KITE Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                        String errorMessage=e.message;
                                                        Pattern pattern = Pattern.compile("Trigger price for stoploss buy orders cannot be above the upper circuit price");
                                                        Matcher matcher = pattern.matcher(errorMessage);
                                                        if (matcher.find() && e.code == 400) {
                                                            Pattern numberPattern = Pattern.compile("\\d+(\\.\\d+)?");
                                                            Matcher numberMatcher = numberPattern.matcher(errorMessage);
                                                            while (numberMatcher.find()) {
                                                                String number = numberMatcher.group();
                                                                double newTriggerPrice = Double.parseDouble(number);
                                                                orderParams1.triggerPrice = newTriggerPrice;
                                                                BigDecimal newSLPrice = new BigDecimal(newTriggerPrice).add(new BigDecimal(newTriggerPrice).divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                                orderParams1.price = newSLPrice.doubleValue();
                                                                LOGGER.info("Zerodha error. Recalculated price for stock {}:{}{}:{}", trendTradeData.getStockName(), user.getName(), newTriggerPrice, orderParams.price);
                                                                tradeSedaQueue.sendTelemgramSeda("Zerodha price error while placing sl. Recalculated price for stock " + trendTradeData.getStockName() + ":" + user.getName() + newTriggerPrice+":"+orderParams.price, "exp-trade");

                                                                try {
                                                                    LOGGER.info("input:{}", gson.toJson(orderParams1));
                                                                    orderd = brokerWorker.placeOrder(orderParams1, user, trendTradeData);
                                                                    trendTradeData.isSlPlaced = true;
                                                                    trendTradeData.setSlOrderId(orderd.orderId);
                                                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                                    try {
                                                                        LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL");
                                                                        tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                    } catch (Exception e1) {
                                                                        e1.printStackTrace();
                                                                        LOGGER.info("error while sending message error:{}", e1.getMessage());
                                                                    }
                                                                }catch (Exception e2) {
                                                                    e2.printStackTrace();
                                                                    trendTradeData.isErrored = true;
                                                                    LOGGER.info("error while placing sl-retry order: {}:{}", e.getMessage(), user.getName());
                                                                    tradeSedaQueue.sendTelemgramSeda("Error while placing sl-retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                                } catch (KiteException e3) {
                                                                    e3.printStackTrace();
                                                                    trendTradeData.isErrored = true;
                                                                    LOGGER.info("KITE error-retry while placing sl order: {}:{}", e.getMessage(), user.getName());
                                                                    tradeSedaQueue.sendTelemgramSeda("KITE error-retry while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                LOGGER.info("error while placing sl:{}{}:{}", e.getMessage(), trendTradeData.getEntryOrderId(), trendTradeData.getStockName());
                                            }
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

    public BigDecimal getTempSLPrice(TradeData tradeData, TradeStrategy strategy) {
        BigDecimal triggerPriceTemp = new BigDecimal(0);
        try {
            if ("BUY".equals(strategy.getOrderType())) {
                if ("POINTS".equals(strategy.getTempSlType())) {
                    triggerPriceTemp = (MathUtils.roundToNearestFivePaiseUP(tradeData.getBuyPrice().subtract(strategy.getTempSlValue())));
                } else {
                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(strategy.getTempSlValue(), tradeData.getBuyPrice());
                    triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(tradeData.getBuyPrice().subtract(slPoints));
                }
            } else {
                if ("POINTS".equals(strategy.getTempSlType())) {
                    triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(tradeData.getSellPrice().add(strategy.getTempSlValue()));
                } else {
                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(strategy.getTempSlValue(), tradeData.getSellPrice());
                    triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(tradeData.getSellPrice().add(slPoints));
                }
            }
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
        LOGGER.info("price:{} sl price: {}", tradeData.getSellPrice(), triggerPriceTemp.doubleValue());
        return triggerPriceTemp;
    }
    public BigDecimal getSLPrice(String currentDate, String stockId, TradeData tradeData) {
        AtomicDouble triggerPriceAtomic = new AtomicDouble();
        try {
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            // System.out.print(response);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            String status = json.getString("status");

            if (!status.equals("error")) {
                try {
                    historicalData.parseResponse(json);

                    TradeStrategy tradeStrategy = tradeData.getTradeStrategy();
                    Optional<HistoricalData> optionalHistoricalLatestData = historicalData.dataArrayList.stream().filter(candle -> {
                        try {
                            Date candleDateTime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(candle.timeStamp);
                            return tradingStrategyAndTradeData.hourMinFormat.format(candleDateTime).equals(tradeStrategy.getEntryTime());
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }).findFirst();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                    LocalTime entryTime = LocalTime.parse(tradeStrategy.getEntryTime(), formatter);
                    LocalTime entryPreviousTime = entryTime.minusMinutes(1);
                    Optional<HistoricalData> optionalHistoricalPreviousCandleData = historicalData.dataArrayList.stream().filter(candle -> {

                        Date candleDateTime = null;
                        try {
                            candleDateTime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(candle.timeStamp);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                        String time = tradingStrategyAndTradeData.hourMinFormat.format(candleDateTime);
                        return time.equals(entryPreviousTime.format(formatter));
                    }).findFirst();

                    String slType = tradeStrategy.getSlType();
                    BigDecimal slValue = tradeStrategy.getSlValue();

                    if (optionalHistoricalPreviousCandleData.isPresent()) {
                        try {
                            HistoricalData historicalData1 = optionalHistoricalPreviousCandleData.get();
                            Date openDatetime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(historicalData1.timeStamp);
                            String openDate = tradingStrategyAndTradeData.dateFormat.format(openDatetime);
                            if (tradingStrategyAndTradeData.candleDateTimeFormat.format(openDatetime).equals(openDate + "T" + tradeStrategy.getEntryTime() + ":00")) {
                                if ("SELL".equals(tradeData.getEntryType())) {
                                    if ("POINTS".equals(slType)) {
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.close).add(slValue)).doubleValue());
                                    } else {
                                        BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, new BigDecimal(historicalData1.close));
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.close).add(slPoints)).doubleValue());
                                    }
                                } else {
                                    if ("POINTS".equals(slType)) {
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(tradeData.getBuyPrice().subtract(slValue)).doubleValue());
                                    } else {
                                        BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, tradeData.getBuyPrice());
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(tradeData.getBuyPrice().subtract(slPoints)).doubleValue());
                                    }
                                }
                                LOGGER.info("setting sl price based on previous:" + historicalData1.timeStamp + " close:" + historicalData1.close + ":" + tradeData.getZerodhaStockId() + ":" + triggerPriceAtomic.get() + ":" + tradeData.getUserId());
                            }
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        } catch (Exception e) {
                            LOGGER.info(e.getMessage());
                        }
                        // });
                    }

                    if (optionalHistoricalLatestData.isPresent()) {
                        try {
                            HistoricalData historicalData1 = optionalHistoricalLatestData.get();
                            Date openDatetime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(historicalData1.timeStamp);
                            String openDate = tradingStrategyAndTradeData.dateFormat.format(openDatetime);
                            if (tradingStrategyAndTradeData.candleDateTimeFormat.format(openDatetime).equals(openDate + "T" + tradeStrategy.getEntryTime() + ":00")) {
                                if ("SELL".equals(tradeData.getEntryType())) {
                                    tradeData.setSellPrice(new BigDecimal(historicalData1.open));
                                    if ("POINTS".equals(slType)) {
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.open).add(slValue)).doubleValue());
                                    } else {
                                        BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, new BigDecimal(historicalData1.open));
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.open).add(slPoints)).doubleValue());
                                    }
                                } else {
                                    //  tradeData.setBuyPrice(new BigDecimal(historicalData1.open));
                                    if ("POINTS".equals(slType)) {
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(tradeData.getBuyPrice().subtract(slValue)).doubleValue());
                                    } else {
                                        BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, tradeData.getBuyPrice());
                                        triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(tradeData.getBuyPrice().subtract(slPoints)).doubleValue());
                                    }
                                }
                                LOGGER.info("setting sl price based on current:" + historicalData1.timeStamp + "  open:" + historicalData1.open + ":" + tradeData.getZerodhaStockId() + ":" + triggerPriceAtomic.get() + ":" + tradeData.getUserId());
                            }
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        } catch (Exception e) {
                            LOGGER.info(e.getMessage());
                        }
                        // });
                    }
                } catch (Exception e) {
                    LOGGER.info(e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
        return new BigDecimal(triggerPriceAtomic.get());
    }
}
