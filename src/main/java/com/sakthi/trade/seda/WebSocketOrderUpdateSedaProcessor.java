package com.sakthi.trade.seda;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    ZerodhaTransactionService zerodhaTransactionService;

    @Autowired
    TransactionService transactionService;

    @Autowired
    MathUtils mathUtils;
    @Autowired
    TradeEngine tradeEngine;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat exchangeDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    SimpleDateFormat hourMinFormat = new SimpleDateFormat("HH:mm");
    @Autowired
    TradeDataMapper tradeDataMapper;

    @Value("${websocket.userId}")
    String websocketUserId;

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
            if(userId.equals(websocketUserId)) {
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
                                            tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                        } catch (Exception e) {
                                            tradeSedaQueue.sendTelemgramSeda("error while processing", "error");
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

                                }/*
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
                                            if("SS".equals(strategy.getIndex())){
                                                orderParams.exchange = "BFO";
                                            }
                                            if(strategy.getTradeStrategyKey().length()<=20) {
                                                orderParams.tag = strategy.getTradeStrategyKey();
                                            }
                                            orderParams.quantity = trendTradeData.getQty();
                                            orderParams.orderType = "SL";
                                            if ("MIS".equals(strategy.getTradeValidity())) {
                                                orderParams.product = "MIS";
                                            } else {
                                                orderParams.product = "NRML";
                                            }
                                            orderParams.product = order.product;
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

                                    }}*/
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
                                        tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                        if (strategy.isReentry() && "SELL".equals(trendTradeData.getEntryType())) {
                                            long tradeCount = userTradeData.getValue().stream().filter(tradeDataTemp ->
                                                    trendTradeData.getStockName().equals(tradeDataTemp.getStockName())
                                                            && tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey()) && tradeDataTemp.isSLHit).count();
                                            if (tradeCount < strategy.getReentryCount().intValue() + 1) {

                                                OrderParams orderParams = new OrderParams();
                                                if (strategy.getTradeStrategyKey().length() <= 20) {
                                                    orderParams.tag = strategy.getTradeStrategyKey();
                                                }
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
                                                if("SS".equals(strategy.getIndex())){
                                                    orderParams.exchange = "BFO";
                                                }
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
                                                        tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                    } catch (Exception e) {
                                                        tradeSedaQueue.sendTelemgramSeda("Error while placing order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "error");
                                                    }
                                                } catch (Exception e) {
                                                    LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                                    tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "error");
                                                } catch (KiteException e) {
                                                    tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "error");
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                        }
                                        if (strategy.isReentry() && "BUY".equals(trendTradeData.getEntryType())) {
                                            try {
                                                String optionStrikeType = trendTradeData.getStockName().substring(trendTradeData.getStockName().length() - 2);
                                                LOGGER.info("optionStrikeType: "+optionStrikeType+":"+trendTradeData.getStockName());
                                                long tradeCount = userTradeData.getValue().stream().filter(tradeDataTemp ->
                                                        optionStrikeType.equals(tradeDataTemp.getStockName().substring(tradeDataTemp.getStockName().length() - 2))
                                                                && tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey()) && tradeDataTemp.isSLHit).count();
                                                LOGGER.info("tradeCount: "+tradeCount);
                                                if (tradeCount < strategy.getReentryCount().intValue() + 1) {
                                                    LOGGER.info("re-entry condition satisfied");
                                                    String stockId = "";
                                                    String index = strategy.getIndex();
                                                    if ("BNF".equals(index)) {
                                                        stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                                                    } else if ("NF".equals(index)) {
                                                        stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                                                    } else if ("FN".equals(index)) {
                                                        stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                                                    }
                                                    String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                                                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                                    HistoricalData historicalData = new HistoricalData();
                                                    JSONObject json = new JSONObject(response);
                                                     LOGGER.info(historicURL+":"+response);
                                                    String status = json.getString("status");
                                                    if (!status.equals("error")) {
                                                        historicalData.parseResponse(json);
                                                        HistoricalData optionalHistoricalLatestData = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                                                        Date lastCandleTimestamp = candleDateTimeFormat.parse(optionalHistoricalLatestData.timeStamp);
                                                        String currentHourMinStr = hourMinFormat.format(lastCandleTimestamp);
                                                        System.out.println("last candle:"+gson.toJson(lastCandleTimestamp));
                                                        System.out.println("currentHourMinStr:"+currentHourMinStr);
                                                        Map<String, Map<String, StrikeData>> rangeStrikes = new HashMap<>();
                                                        rangeStrikes = mathUtils.strikeSelection(currentDateStr, strategy, optionalHistoricalLatestData.close, currentHourMinStr+":00",optionStrikeType);
                                                        rangeStrikes.forEach((indexStrikePrice, strikeDataEntry) -> {
                                                            strikeDataEntry.entrySet().stream().forEach(strikeDataEntry1 -> {
                                                                StrikeData strikeData = strikeDataEntry1.getValue();
                                                                OrderParams orderParams = new OrderParams();
                                                                AtomicDouble triggerPriceTemp = new AtomicDouble();
                                                                String[] prices= indexStrikePrice.split("-");
                                                                double strikePrice = Double.parseDouble(prices[0]);
                                                                if (strategy.isSimpleMomentum()) {
                                                                    if (strategy.getStrikeSelectionType().equals(StrikeSelectionType.ATM.getType())) {
                                                                        String historicURL1 = "https://api.kite.trade/instruments/historical/" + strikeData.getZerodhaId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                                                                        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL1), strikeData.getZerodhaId(), currentHourMinStr);
                                                                        HistoricalData historicalStrikeData = new HistoricalData();
                                                                        JSONObject strikeJson = new JSONObject(priceResponse);
                                                                        Optional<HistoricalData> historicalStrikeDataLastData = Optional.empty();
                                                                        String strikeStatus = strikeJson.getString("status");
                                                                        if (!strikeStatus.equals("error")) {
                                                                            historicalStrikeData.parseResponse(strikeJson);

                                                                            historicalStrikeDataLastData = historicalStrikeData.dataArrayList.stream().filter(candle -> {
                                                                                try {
                                                                                    Date candleDateTime = candleDateTimeFormat.parse(candle.timeStamp);
                                                                                    return hourMinFormat.format(candleDateTime).equals(currentHourMinStr);
                                                                                } catch (ParseException e) {
                                                                                    throw new RuntimeException(e);
                                                                                }
                                                                            }).findFirst();
                                                                            strikePrice = historicalStrikeDataLastData.get().close;
                                                                        }
                                                                    }
                                                                    orderParams.orderType = "SL";

                                                                    if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_UP.getType())) {
                                                                        triggerPriceTemp.getAndSet((MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), new BigDecimal(strikePrice)).add(new BigDecimal(strikePrice))).setScale(0, RoundingMode.HALF_UP).doubleValue());
                                                                    }
                                                                    if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_DOWN.getType())) {
                                                                        triggerPriceTemp.getAndSet((new BigDecimal(strikePrice)).subtract(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), new BigDecimal(strikePrice))).setScale(0, RoundingMode.HALF_UP).doubleValue());
                                                                    }
                                                                    if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_UP.getType())) {
                                                                        triggerPriceTemp.getAndSet(new BigDecimal(strikePrice).add(strategy.getSimpleMomentumValue()).setScale(0, RoundingMode.HALF_UP).doubleValue());
                                                                    }
                                                                    if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_DOWN.getType())) {
                                                                        triggerPriceTemp.getAndSet(new BigDecimal(strikePrice).subtract(strategy.getSimpleMomentumValue())
                                                                                .setScale(0, RoundingMode.HALF_UP).doubleValue());

                                                                    }
                                                                    System.out.println(triggerPriceTemp);
                                                                    orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                                                    BigDecimal price = new BigDecimal(triggerPriceTemp.get()).add(new BigDecimal(triggerPriceTemp.get()).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                                    orderParams.price = price.doubleValue();

                                                                } else {
                                                                    //TODO: add call to api to get price
                                                                    orderParams.orderType = "MARKET";
                                                                }
                                                                LOGGER.info(strategy.getTradeStrategyKey() + "placing order for:" + strikeData.getZerodhaSymbol());

                                                                orderParams.tradingsymbol = strikeData.getZerodhaSymbol();
                                                                orderParams.exchange = "NFO";
                                                                if("SS".equals(strategy.getIndex())){
                                                                    orderParams.exchange = "BFO";
                                                                }
                                                                if ("MIS".equals(strategy.getTradeValidity())) {
                                                                    orderParams.product = "MIS";
                                                                } else {
                                                                    orderParams.product = "NRML";
                                                                }
                                                                orderParams.transactionType = strategy.getOrderType();
                                                                orderParams.validity = "DAY";
                                                                if (strategy.getTradeStrategyKey().length() <= 20) {
                                                                    orderParams.tag = strategy.getTradeStrategyKey();
                                                                }
                                                                LocalDate localDate = LocalDate.now();
                                                                DayOfWeek dow = localDate.getDayOfWeek();
                                                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);

                                                                strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach(userSubscription -> {
                                                                    userList.getUser().stream().filter(
                                                                            user1 -> user1.getName().equals(userSubscription.getUserId())
                                                                    ).forEach(user1 -> {
                                                                        TradeData tradeDataRetry = new TradeData();
                                                                        if ("SELL".equals(strategy.getOrderType())) {
                                                                            tradeDataRetry.setSellPrice(new BigDecimal(triggerPriceTemp.get()));
                                                                        } else {
                                                                            tradeDataRetry.setBuyPrice(new BigDecimal(triggerPriceTemp.get()));
                                                                        }
                                                                        BrokerWorker brokerWorker1 = workerFactory.getWorker(user1);

                                                                        Order order1 = null;
                                                                        int lot = strategy.getLotSize() * userSubscription.getLotSize();
                                                                        orderParams.quantity = lot;

                                                                        String dataKey = UUID.randomUUID().toString();
                                                                        tradeDataRetry.setDataKey(dataKey);

                                                                        tradeDataRetry.setStockName(strikeData.getZerodhaSymbol());
                                                                        try {
                                                                            //TODO set sl price, entry price, exit date
                                                                            tradeDataRetry.setQty(lot);
                                                                            tradeDataRetry.setEntryType(strategy.getOrderType());
                                                                            tradeDataRetry.setUserId(user1.getName());
                                                                            tradeDataRetry.setTradeDate(currentDateStr);
                                                                            tradeDataRetry.setStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                                                            try {
                                                                                tradeEngine.addStriketoWebsocket(Long.parseLong(strikeData.getZerodhaId()));
                                                                            } catch (Exception e) {
                                                                                e.printStackTrace();
                                                                            }
                                                                            tradeDataRetry.setStrikeId(strikeData.getDhanId());
                                                                            tradeDataRetry.setTradeStrategy(strategy);
                                                                            order1 = brokerWorker1.placeOrder(orderParams, user1, tradeDataRetry);
                                                                            if (order1 != null) {
                                                                                tradeDataRetry.setEntryOrderId(order1.orderId);
                                                                                //  tradeData.isOrderPlaced = true;
                                                                            }

                                                                            mapTradeDataToSaveOpenTradeDataEntity(tradeDataRetry, true);
                                                                            List<TradeData> tradeDataList = tradeEngine.openTrade.get(user1.getName());
                                                                            if (tradeDataList == null) {
                                                                                tradeDataList = new ArrayList<>();
                                                                            }
                                                                            tradeDataList.add(tradeDataRetry);
                                                                            tradeEngine.openTrade.put(user.getName(), tradeDataList);
                                                                            LOGGER.info("trade data" + new Gson().toJson(tradeDataRetry));
                                                                            tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: "
                                                                                    + strikeData.getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                                                        } catch (Exception e) {
                                                                            List<TradeData> tradeDataList = tradeEngine.openTrade.get(user.getName());
                                                                            if (tradeDataList == null) {
                                                                                tradeDataList = new ArrayList<>();
                                                                            }
                                                                            tradeDataList.add(tradeDataRetry);
                                                                            tradeEngine.openTrade.put(user.getName(), tradeDataList);
                                                                            tradeDataRetry.isErrored = true;
                                                                            LOGGER.info("Error while placing straddle order: " + e);
                                                                            e.printStackTrace();
                                                                            tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());

                                                                        } catch (KiteException e) {
                                                                            throw new RuntimeException(e);
                                                                        }
                                                                    });
                                                                });
                                                            });

                                                        });
                                                    }
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                LOGGER.info("error while placing buy rentry order: " + e.getMessage() + ":" + user.getName());
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing buy reentry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                            }
                                        }
                                            if (strategy.isTrailToCost()) {
                                            userTradeData.getValue().stream().filter(tradeDataTemp -> tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey())
                                                    && !tradeDataTemp.isSLHit && !tradeDataTemp.isExited).forEach(tradeDataMod -> {
                                                try {
                                                    //Order order1 = brokerWorker.getOrder(user, tradeDataMod.getSlOrderId());
                                                    OrderParams orderParams = new OrderParams();
                                                    if ("BUY".equals(trendTradeData.getEntryType())) {
                                                        orderParams.triggerPrice = tradeDataMod.getBuyPrice().setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                                                        double price = tradeDataMod.getBuyPrice().subtract(tradeDataMod.getBuyPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                        orderParams.price = price;
                                                        tradeDataMod.setSlPrice(tradeDataMod.getBuyPrice());
                                                    } else {
                                                        orderParams.triggerPrice = tradeDataMod.getSellPrice().setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                                                        double price = tradeDataMod.getSellPrice().add(tradeDataMod.getSellPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                        orderParams.price = price;
                                                        tradeDataMod.setSlPrice(tradeDataMod.getSellPrice());
                                                    }
                                                    if(tradeDataMod.getTradeStrategy().isWebsocketSlEnabled()) {
                                                        tradeEngine.openTrade.entrySet().stream().forEach(userTradeData1 -> {
                                                            List<TradeData> tradeDataList = userTradeData1.getValue();
                                                            tradeDataList.stream().filter(tradeDataTemp1 -> tradeDataTemp1.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey())
                                                                    && !tradeDataTemp1.isSLHit && !tradeDataTemp1.isExited && tradeDataTemp1.getStockId() == tradeDataMod.getStockId()).forEach(tradeData1 -> {
                                                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                                                    tradeData1.setSlPrice(tradeData1.getBuyPrice());
                                                                } else {
                                                                    tradeData1.setSlPrice(tradeData1.getSellPrice());
                                                                }
                                                                tradeSedaQueue.sendTelemgramSeda("set trail sl for websocket" + tradeData1.getStockName() + ":" + tradeData1.getUserId() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                mapTradeDataToSaveOpenTradeDataEntity(tradeData1, false);
                                                            });
                                                        });
                                                    }else {
                                                        brokerWorker.modifyOrder(tradeDataMod.getSlOrderId(), orderParams, user, tradeDataMod);
                                                        mapTradeDataToSaveOpenTradeDataEntity(tradeDataMod, false);
                                                        tradeSedaQueue.sendTelemgramSeda("executed trail sl " + trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(),"exp-trade");
                                                    }
                                                    } catch (Exception e) {
                                                    tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "error");
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                        }
                                    }

                                 if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                                    String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + strategy.getTradeStrategyKey());
                                    LOGGER.info(message);
                                    trendTradeData.isErrored = true;
                                    tradeSedaQueue.sendTelemgramSeda(message, "error");
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
