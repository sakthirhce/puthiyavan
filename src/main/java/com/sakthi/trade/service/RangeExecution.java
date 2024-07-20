package com.sakthi.trade.service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.*;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.seda.TradeSedaQueue;
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
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RangeExecution {
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    public static final Logger LOGGER = LoggerFactory.getLogger(RangeExecution.class.getName());
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    Gson gson = new Gson();
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Autowired
    TradeHelperService tradeHelperService;
    @Autowired
    MathUtils mathUtils;
    @Autowired
    BrokerWorkerFactory workerFactory;
    @Autowired
    public UserList userList;
    @Autowired
    ZerodhaWebsocket zerodhaWebsocket;
    SimpleDateFormat hourMinFormat = new SimpleDateFormat("HH:mm");
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    @Autowired
    public TransactionService transactionService;

    @Autowired
    TradingStrategyAndTradeData tradingStrategyAndTradeData;

    public void processStrategy(String index, List<TradeStrategy> strategyList, String currentDateStr, String currentHourMinStr, String candleHourMinStr) {
        strategyList.forEach(strategy -> {
            try {
                String stockId = getStockId(index);
                if (stockId == null) {
                    return;
                }
                String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:30:00";
                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), stockId, currentHourMinStr);
                HistoricalData historicalData = new HistoricalData();
                JSONObject json = new JSONObject(response);
                String status = json.getString("status");
                if (!status.equals("error")) {
                    historicalData.parseResponse(json);

                    Optional<HistoricalData> optionalHistoricalLatestData = historicalData.dataArrayList.stream().filter(candle -> {
                        try {
                            Date candleDateTime = candleDateTimeFormat.parse(candle.timeStamp);
                            return hourMinFormat.format(candleDateTime).equals(candleHourMinStr);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }).findFirst();
                    if (optionalHistoricalLatestData.isPresent()) {

                        HistoricalData lastHistoricalData = optionalHistoricalLatestData.get();
                        try {
                            if ("INDEX".equals(strategy.getRangeBreakInstrument())) {
                                if ((strategy.getRangeBreakTime()).equals(currentHourMinStr)|| (dateTimeFormat.parse(currentDateStr + " " + currentHourMinStr).after(dateTimeFormat.parse(currentDateStr + " " + strategy.getRangeBreakTime())))) {
                                    rangeBreak(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr + ":00", lastHistoricalData);
                                }
                            } else {
                                if(currentHourMinStr.equals(strategy.getRangeStartTime())) {
                                    rangeBreakOptions(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr, lastHistoricalData, index);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("error1 while executing range:{}", e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }

                }
            } catch (Exception e) {
                LOGGER.error("error2 while executing range:{}", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void rangeBreakOptions(TradeStrategy strategy, HistoricalData historicalData, String currentDateStr, String currentHourMinStr, String candleHourMinStr, HistoricalData lastHistoricalData, String index) {
        try {
            if (currentHourMinStr.equals(strategy.getRangeStartTime())) {
                Map<String, Map<String, StrikeData>> rangeStrikes = new HashMap<>();
                rangeStrikes = mathUtils.strikeSelection(currentDateStr, strategy, lastHistoricalData.close, candleHourMinStr + ":00");
                LOGGER.info("range strikes {}:{}", strategy.getTradeStrategyKey(), rangeStrikes);
                rangeStrikes.forEach((indexStrikePrice, strikeDataEntry) -> {

                    strikeDataEntry.forEach((key, strikeData) -> {
                        LOGGER.info("range option strikes {}:{}", strategy.getTradeStrategyKey(), strikeData.getZerodhaSymbol());
                        //OrderParams orderParams = new OrderParams();
                        AtomicDouble triggerPriceTemp = new AtomicDouble();
                        String[] prices = indexStrikePrice.split("-");
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
                                            return hourMinFormat.format(candleDateTime).equals(candleHourMinStr);
                                        } catch (ParseException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }).findFirst();
                                    strikePrice = historicalStrikeDataLastData.get().close;
                                }
                            }

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

                            BigDecimal price = BigDecimal.valueOf(triggerPriceTemp.get()).add(BigDecimal.valueOf(triggerPriceTemp.get()).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);

                        }
                        LOGGER.info(strategy.getTradeStrategyKey() + "placing order for:" + strikeData.getZerodhaSymbol());

                        LocalDate localDate = LocalDate.now();
                        DayOfWeek dow = localDate.getDayOfWeek();

                        strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach(userSubscription -> {
                            userList.getUser().stream().filter(
                                    user -> user.getName().equals(userSubscription.getUserId())
                            ).forEach(user -> {
                                TradeData tradeData = new TradeData();
                                if ("SELL".equals(strategy.getOrderType())) {
                                    tradeData.setSellPrice(BigDecimal.valueOf(triggerPriceTemp.get()));
                                } else {
                                    tradeData.setBuyPrice(BigDecimal.valueOf(triggerPriceTemp.get()));
                                }
                                BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                Order order;
                                int lot = strategy.getLotSize() * userSubscription.getLotSize();

                                String dataKey = UUID.randomUUID().toString();
                                tradeData.setDataKey(dataKey);

                                tradeData.setStockName(strikeData.getZerodhaSymbol());
                                try {
                                    //TODO set sl price, entry price, exit date
                                    tradeData.setQty(lot);
                                    tradeData.setEntryType(strategy.getOrderType());
                                    tradeData.setUserId(user.getName());
                                    tradeData.setTradeDate(currentDateStr);
                                    tradeData.setZerodhaStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                    try {
                                        zerodhaWebsocket.addTradeStriketoWebsocket(Long.parseLong(strikeData.getZerodhaId()), tradeData.getStockName(), index);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    tradeData.setStrikeId(strikeData.getDhanId());
                                    tradeData.setTradeStrategy(strategy);
                                    tradeData.range = true;
                                    // order = brokerWorker.placeOrder(orderParams, user, tradeData);
                           /* if (order != null) {
                                tradeData.setEntryOrderId(order.orderId);
                                //  tradeData.isOrderPlaced = true;
                            }*/
                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                    List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    LOGGER.info("trade data during range start time:"+gson.toJson(tradeData));
                                    tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                    //LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                    tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: "
                                            + strikeData.getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                } catch (Exception e) {
                                    List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                    tradeData.isErrored = true;
                                    LOGGER.info("Error while placing straddle order: " + e);
                                    e.printStackTrace();
                                    tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");

                                } catch (KiteException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        });
                    });


                });
            }
        } catch (Exception e) {
            LOGGER.error("error while finding strike at range start: {},{}", e.getMessage(), strategy.getTradeStrategyKey());
        }
    }

    public void orbHighLow(List<HistoricalData> orb, Map<String, Double> orbHighLow, TradeStrategy strategy, String currentDate) {
        double low = Double.MAX_VALUE;
        double high = Double.MIN_VALUE;
        String rangeStart = strategy.getRangeStartTime();
        String rangeEnd = strategy.getRangeBreakTime();
        for (HistoricalData candle : orb) {
            try {
                if ((candleDateTimeFormat.parse(candle.timeStamp).after(candleDateTimeFormat.parse(currentDate + "T" + rangeStart + ":00")) || candleDateTimeFormat.parse(candle.timeStamp).equals(candleDateTimeFormat.parse(currentDate + "T" + rangeStart + ":00"))) &&
                        (candleDateTimeFormat.parse(candle.timeStamp).before(candleDateTimeFormat.parse(currentDate + "T" + rangeEnd + ":00")) || candleDateTimeFormat.parse(candle.timeStamp).equals(candleDateTimeFormat.parse(currentDate + "T" + rangeEnd + ":00")))) {
                    if (candle.high > high) {
                        high = candle.high;
                    }

                    if (candle.low < low) {
                        low = candle.low;
                    }
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
        orbHighLow.put("LOW", low);
        orbHighLow.put("HIGH", high);
        String message = strategy.getTradeStrategyKey() + ":Low:" + low + ":high:" + high;
        LOGGER.info(message);
        User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
        tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
        strategy.setRangeLow(new BigDecimal(low));
        strategy.setRangeHigh(new BigDecimal(high));
    }

    public void orbHighLowOptions(List<HistoricalData> orb, Map<String, Double> orbHighLow, TradeStrategy strategy, TradeData tradeData, String currentDate) {
        double low = Double.MAX_VALUE;
        double high = Double.MIN_VALUE;
        String rangeStart = strategy.getRangeStartTime();
        String rangeEnd = strategy.getRangeBreakTime();
        for (HistoricalData candle : orb) {
            try {
                if ((candleDateTimeFormat.parse(candle.timeStamp).after(candleDateTimeFormat.parse(currentDate + "T" + rangeStart + ":00")) || candleDateTimeFormat.parse(candle.timeStamp).equals(candleDateTimeFormat.parse(currentDate + "T" + rangeStart + ":00"))) &&
                        (candleDateTimeFormat.parse(candle.timeStamp).before(candleDateTimeFormat.parse(currentDate + "T" + rangeEnd + ":00")) || candleDateTimeFormat.parse(candle.timeStamp).equals(candleDateTimeFormat.parse(currentDate + "T" + rangeEnd + ":00")))) {
                    if (candle.high > high) {
                        high = candle.high;
                    }

                    if (candle.low < low) {
                        low = candle.low;
                    }
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
        orbHighLow.put("LOW", low);
        orbHighLow.put("HIGH", high);
        String message = strategy.getTradeStrategyKey() + ":Low:" + low + ":high:" + high;
        LOGGER.info(message);
        User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
        tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
        strategy.setRangeLow(new BigDecimal(low));
        strategy.setRangeHigh(new BigDecimal(high));
        tradeData.setRangeLow(new BigDecimal(low));
        tradeData.setRangeHigh(new BigDecimal(high));
        LOGGER.info("trade data after setting high low:"+gson.toJson(tradeData));
    }
    public void rangeBreak(TradeStrategy strategy, HistoricalData historicalData, String currentDateStr, String currentHourMinStr, String candleHourMinStr, HistoricalData lastHistoricData) throws Exception {
        Map<String, Double> orbHighLow = new HashMap<>();
        // if ("ORB".equals(strategy.getRangeType())) {
        try {
            if ((strategy.getRangeBreakTime()).equals(currentHourMinStr)) {
                orbHighLow(historicalData.dataArrayList, orbHighLow, strategy, currentDateStr);

            }
            try {
                if (dateTimeFormat.parse(currentDateStr + " " + currentHourMinStr).after(dateTimeFormat.parse(currentDateStr + " " + strategy.getRangeBreakTime()))) {
                    // String message1 = "TradeEngine:" + strategy.getTradeStrategyKey() + ":" + currentHourMinStr + ":range low"+strategy.getRangeLow()+":range high"+strategy.getRangeHigh();
                    //  log.info(dateTimeFormat.format(new Date())+":"+message1);
                    if (strategy.getRangeLow().doubleValue() > 0) {
                        if (lastHistoricData.close < strategy.getRangeLow().doubleValue()) {
                            Map<String, StrikeData> rangeSelected;
                            rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDateStr, strategy.getStrikePriceRangeHigh().doubleValue(), strategy.getStrikePriceRangeLow().doubleValue(), candleHourMinStr, strategy.getIndex(), strategy.getOrderType(), "PE");
                            Map.Entry<String, StrikeData> finalSelected;
                            System.out.println(gson.toJson(rangeSelected));
                            OrderParams orderParams = new OrderParams();
                            if (strategy.getTradeStrategyKey().length() <= 20) {
                                orderParams.tag = strategy.getTradeStrategyKey();
                            }
                            orderParams.exchange = "NFO";
                            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                orderParams.exchange = "BFO";
                            }
                            if ("MIS".equals(strategy.getTradeValidity())) {
                                orderParams.product = "MIS";
                            } else {
                                orderParams.product = "NRML";
                            }
                            if ("SELL".equals(strategy.getOrderType())) {
                                finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("CE")).findFirst().get();
                                orderParams.product = "NRML";
                            } else {
                                finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                            }
                            orderParams.tradingsymbol = finalSelected.getValue().getZerodhaSymbol();
                            System.out.println(gson.toJson(finalSelected));
                            orderParams.transactionType = strategy.getOrderType();
                            orderParams.validity = "DAY";
                            orderParams.orderType = "MARKET";
                            strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach(userSubscription -> {
                                userList.getUser().stream().filter(
                                        user -> user.getName().equals(userSubscription.getUserId())
                                ).forEach(user -> {
                                    System.out.println("inside user");
                                    BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                    Order order = null;
                                    int lot = strategy.getLotSize() * Integer.valueOf(userSubscription.getLotSize());
                                    orderParams.quantity = lot;

                                    TradeData tradeData = new TradeData();
                                    tradeData.setQty(strategy.getLotSize());
                                    //TODO: add logic to set limit price
                                    tradeData.setZerodhaStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                                    tradeData.setTradeStrategy(strategy);
                                    String dataKey = UUID.randomUUID().toString();
                                    tradeData.setDataKey(dataKey);

                                    tradeData.setStockName(finalSelected.getValue().getZerodhaSymbol());
                                    try {
                                        //TODO set sl price, entry price, exit date
                                        tradeData.setQty(lot);
                                        tradeData.setEntryType(strategy.getOrderType());
                                        tradeData.setUserId(user.getName());
                                        tradeData.setTradeDate(currentDateStr);
                                        tradeData.setZerodhaStockId(Integer.valueOf(finalSelected.getValue().getZerodhaId()));
                                        tradeData.setTradeStrategy(strategy);
                                        order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                        if (order != null) tradeData.setEntryOrderId(order.orderId);
                                        //  tradeData.isOrderPlaced = true;

                                        tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                        List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                        if (tradeDataList == null) {
                                            tradeDataList = new ArrayList<>();
                                        }
                                        tradeDataList.add(tradeData);
                                        tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                        LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                        tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: " + finalSelected.getValue().getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                    } catch (Exception e) {
                                        List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                        if (tradeDataList == null) {
                                            tradeDataList = new ArrayList<>();
                                        }
                                        tradeDataList.add(tradeData);
                                        tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                        tradeData.isErrored = true;
                                        LOGGER.info("Error while placing straddle order: " + e);
                                        e.printStackTrace();
                                        tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + finalSelected.getValue().getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() , "exp-trade");

                                    } catch (KiteException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            });
                            String message = "TradeEngine:" + strategy.getTradeStrategyKey() + ":" + currentHourMinStr + "option orb range low broke, strike selected :" + finalSelected.getValue().getZerodhaSymbol();
                            tradeSedaQueue.sendTelemgramSeda(message);
                            strategy.setRangeLow(new BigDecimal(0));
                        }

                    }
                    if (strategy.getRangeHigh().doubleValue() > 0) {
                        if (lastHistoricData.close > strategy.getRangeHigh().doubleValue()) {
                            Map<String, StrikeData> rangeSelected;
                            rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDateStr, strategy.getStrikePriceRangeHigh().doubleValue(), strategy.getStrikePriceRangeLow().doubleValue(), candleHourMinStr, strategy.getIndex(), strategy.getOrderType(), "CE");
                            Map.Entry<String, StrikeData> finalSelected;
                            OrderParams orderParams = new OrderParams();
                            orderParams.exchange = "NFO";
                            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                orderParams.exchange = "BFO";
                            }
                            orderParams.orderType = "MARKET";
                            if ("MIS".equals(strategy.getTradeValidity())) {
                                orderParams.product = "MIS";
                            } else {
                                orderParams.product = "NRML";
                            }
                            if ("SELL".equals(strategy.getOrderType())) {
                                finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                                orderParams.product = "NRML";
                            } else {
                                finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("CE")).findFirst().get();
                            }
                            orderParams.tradingsymbol = finalSelected.getValue().getZerodhaSymbol();
                            if (strategy.getTradeStrategyKey().length() <= 20) {
                                orderParams.tag = strategy.getTradeStrategyKey();
                            }
                            orderParams.transactionType = strategy.getOrderType();
                            orderParams.validity = "DAY";
                            LocalDate localDate = LocalDate.now();
                            DayOfWeek dow = localDate.getDayOfWeek();
                            String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                            //TODO get all users subsribed to this.
                            strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach(userSubscription -> {
                                userList.getUser().stream().filter(
                                        user -> user.getName().equals(userSubscription.getUserId())
                                ).forEach(user -> {
                                    int lot = strategy.getLotSize() * Integer.valueOf(userSubscription.getLotSize());
                                    orderParams.quantity = lot;
                                    BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                    Order order = null;

                                    String dataKey = UUID.randomUUID().toString();
                                    TradeData tradeData = new TradeData();
                                    tradeData.setQty(strategy.getLotSize());
                                    tradeData.setZerodhaStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                                    tradeData.setTradeStrategy(strategy);
                                    tradeData.setDataKey(dataKey);

                                    tradeData.setStockName(finalSelected.getValue().getZerodhaSymbol());
                                    try {
                                        //TODO set sl price, entry price, exit date
                                        tradeData.setQty(lot);
                                        tradeData.setEntryType(strategy.getOrderType());
                                        tradeData.setUserId(user.getName());
                                        tradeData.setTradeDate(currentDateStr);
                                        tradeData.setZerodhaStockId(Integer.valueOf(finalSelected.getValue().getZerodhaId()));
                                        order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                        if (order != null) tradeData.setEntryOrderId(order.orderId);
                                        //  tradeData.isOrderPlaced = true;

                                        tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                        List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                        if (tradeDataList == null) {
                                            tradeDataList = new ArrayList<>();
                                        }
                                        tradeDataList.add(tradeData);
                                        tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                        LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                        tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: " + finalSelected.getValue().getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                    } catch (Exception e) {
                                        List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                        if (tradeDataList == null) {
                                            tradeDataList = new ArrayList<>();
                                        }
                                        tradeDataList.add(tradeData);
                                        tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                        tradeData.isErrored = true;
                                        LOGGER.info("Error while placing straddle order: " + e);
                                        e.printStackTrace();
                                        tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + finalSelected.getValue().getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage(), "exp-trade");

                                    } catch (KiteException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            });
                            String message = "TradeEngine:" + strategy.getTradeStrategyKey() + ":" + currentHourMinStr + "option orb range high broke, strike selected :" + finalSelected.getValue().getZerodhaSymbol();
                            tradeSedaQueue.sendTelemgramSeda(message);
                            strategy.setRangeHigh(new BigDecimal(0));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
     /*   }
        else if ("BBS".equals(strategy.getRangeType())) {
            LOGGER.info("inside BBS");
            List<HistoricalDataExtended> historicalDataLatest = DataResampler.resampleOHLCData(historicalData.dataArrayList, strategy.getRangeCandleInterval());
            List<HistoricalDataExtended> historicalDataList = new ArrayList<>();
            historicalDataList.addAll(dataArrayList3MHistory);
            historicalDataList.addAll(historicalDataLatest);
            LinkedList<Double> closingPrices = new LinkedList<>();
            int i = 0;
            while (i<historicalDataList.size()) {
                HistoricalDataExtended historicalDataExtended = historicalDataList.get(i);
                closingPrices.add(historicalDataExtended.getClose());
                if (closingPrices.size() > strategy.getBbsWindow()) {
                    closingPrices.removeFirst();
                }
                if (i >= strategy.getBbsWindow() - 1 && historicalDataExtended.sma == 0) {
                    double sma = MathUtils.calculateSMA(closingPrices);
                    double standardDeviation = MathUtils.calculateStandardDeviation(closingPrices, sma);
                    double lowerBand = sma - strategy.getMultiplier() * standardDeviation;
                    double upperBand = sma + strategy.getMultiplier() * standardDeviation;
                    double bandwidth = upperBand - lowerBand;
                    // Store the calculated values back in the candle
                    historicalDataExtended.setSma(sma);
                    historicalDataExtended.setBolingerLowerBand(lowerBand);
                    historicalDataExtended.setBolingerUpperBand(upperBand);
                    historicalDataExtended.setBolingerBandwith(bandwidth);
                }
                i++;
            }
            System.out.println("historicalDataList:"+new Gson().toJson(historicalDataList));
            System.out.println("currentHourMinStr:"+new Gson().toJson(currentHourMinStr));
            HistoricalDataExtended historicalDataExtended = historicalDataList.get(historicalDataList.size() - 1);
            double bandwidth = historicalDataExtended.getBolingerBandwith();
            double sma = historicalDataExtended.sma;
            if (bandwidth < strategy.getSz() && !strategy.isSPositionTaken()) {
                LOGGER.info("squeeze detected and position not taken, bandwith"+bandwidth);
                if (historicalDataExtended.close > sma || historicalDataExtended.close < sma) {
                    String optionStrikeType;
                    //TODO: entry
                    if(historicalDataExtended.close > sma){
                        LOGGER.info("price closed above sma");
                        optionStrikeType="CE";
                    }else {
                        LOGGER.info("price closed below sma");
                        optionStrikeType="PE";
                    }
                    strategy.setSPositionTaken(true);
                    try {
                        String zerodhaStockId = "";
                        String index = strategy.getIndex();
                        if ("BNF".equals(index)) {
                            zerodhaStockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                        } else if ("NF".equals(index)) {
                            zerodhaStockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                        } else if ("FN".equals(index)) {
                            zerodhaStockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                        }
                        String historicURL = "https://api.kite.trade/instruments/historical/" + zerodhaStockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                        HistoricalData historicalDataBBS = new HistoricalData();
                        JSONObject json = new JSONObject(response);
                        //     log.info(currentHourMinStr+":"+historicURL+":"+response);
                        String status = json.getString("status");
                        if (!status.equals("error")) {
                            historicalDataBBS.parseResponse(json);
                            HistoricalData optionalHistoricalLatestData = historicalDataBBS.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                            Date lastCandleTimestamp = candleDateTimeFormat.parse(optionalHistoricalLatestData.timeStamp);
                            Map<Double, Map<String, StrikeData>> rangeStrikes = new HashMap<>();
                            rangeStrikes = mathUtils.strikeSelection(currentDateStr, strategy, optionalHistoricalLatestData.close, currentHourMinStr,optionStrikeType);
                            LOGGER.info("range:"+new Gson().toJson(rangeStrikes));
                            rangeStrikes.forEach((indexStrikePrice, strikeDataEntry) -> {
                                strikeDataEntry.entrySet().stream().forEach(strikeDataEntry1 -> {
                                    StrikeData strikeData = strikeDataEntry1.getValue();
                                    OrderParams orderParams = new OrderParams();
                                    orderParams.orderType = "MARKET";
                                    LOGGER.info(strategy.getTradeStrategyKey() + "placing bbs squeeze order for:" + strikeData.getZerodhaSymbol());

                                    orderParams.tradingsymbol = strikeData.getZerodhaSymbol();
                                    orderParams.exchange = "NFO";
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
                                                tradeDataRetry.isOrderPlaced=true;
                                                tradeDataRetry.setZerodhaStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                                try {
                                                    addStriketoWebsocket(Long.parseLong(strikeData.getZerodhaId()));
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                tradeDataRetry.setStrikeId(strikeData.getDhanId());
                                                tradeDataRetry.setTradeStrategy(strategy);
                                              //  order1 = brokerWorker1.placeOrder(orderParams, user1, tradeDataRetry);
                                                if (order1 != null) {
                                                    tradeDataRetry.setEntryOrderId(order1.orderId);
                                                    //  tradeData.isOrderPlaced = true;
                                                }

                                                mapTradeDataToSaveOpenTradeDataEntity(tradeDataRetry, true);
                                                List<TradeData> tradeDataList = openTrade.get(user1.getName());
                                                if (tradeDataList == null) {
                                                    tradeDataList = new ArrayList<>();
                                                }
                                                tradeDataList.add(tradeDataRetry);
                                                //openTrade.put(user1.getName(), tradeDataList);
                                                LOGGER.info("trade data" + new Gson().toJson(tradeDataRetry));
                                                tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user1.getName() + " strike: "
                                                        + strikeData.getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), user1.telegramBot.getGroupId());
                                            } catch (Exception e) {
                                                List<TradeData> tradeDataList = openTrade.get(user1.getName());
                                                if (tradeDataList == null) {
                                                    tradeDataList = new ArrayList<>();
                                                }
                                                tradeDataList.add(tradeDataRetry);
                                                openTrade.put(user1.getName(), tradeDataList);
                                                tradeDataRetry.isErrored = true;
                                                LOGGER.info("Error while placing straddle order: " + e);
                                                e.printStackTrace();
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user1.getName() + ",Exception:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), user1.telegramBot.getGroupId());

                                            }
                                        });
                                    });
                                });

                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        //  LOGGER.info("error while placing buy rentry order: " + e.getMessage() + ":" + user1.getName());
//tradeSedaQueue.sendTelemgramSeda("Error while placing buy reentry order: " + re.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(),"exp-trade");
                    }
                }
            }*/

        //TODO: check position, add condition to check CE and sma>close otherwise it will close position immediately
          /*  if (strategy.isSPositionTaken()) {
                LOGGER.info("position taken");
                if (historicalDataExtended.close > sma || historicalDataExtended.close < sma) {
                    boolean upside=historicalDataExtended.close < sma;
                    boolean downside=historicalDataExtended.close > sma;
                    LOGGER.info("price closed above "+upside+" below :"+downside);
                    openTrade.entrySet().stream().forEach( openTradeList-> {
                        openTradeList.getValue().forEach(openTradeBBSData->{
                            if("BBS".equals(openTradeBBSData.getTradeStrategy().getRangeType()) && !openTradeBBSData.isExited){
                                openTradeBBSData.isExited=true;
                                OrderParams orderParams = new OrderParams();
                              //  orderParams.tradingsymbol = position.tradingSymbol;
                                orderParams.exchange = "NFO";
                                if (strategy.getTradeStrategyKey().length() <= 20) {
                                    orderParams.tag = strategy.getTradeStrategyKey();
                                }
                                orderParams.quantity = openTradeBBSData.getQty();
                                orderParams.orderType = "MARKET";
                                if ("MIS".equals(strategy.getTradeValidity())) {
                                    orderParams.product = "MIS";
                                } else {
                                    orderParams.product = "NRML";
                                }
                                if ("BUY".equals(openTradeBBSData.getEntryType())) {
                                    orderParams.transactionType = "SELL";
                                } else {
                                    orderParams.transactionType = "BUY";
                                }
                                orderParams.validity = "DAY";
                                Order orderResponse = null;
                                User user = userList.getUser().stream().filter(
                                        user1 -> user1.getName().equals(openTradeList.getKey())
                                ).findFirst().get();
                                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                                try {
                                //    orderResponse = brokerWorker.placeOrder(orderParams, user, openTradeBBSData);
                                    LOGGER.info(new Gson().toJson(orderResponse));
                                    String message = MessageFormat.format("Closed Intraday Position {0}", orderParams.tradingsymbol + ":" + strategy.getTradeStrategyKey());
                                    LOGGER.info(message);
                                    openTradeBBSData.isExited = true;
                                    mapTradeDataToSaveOpenTradeDataEntity(openTradeBBSData, false);
                                    tradeSedaQueue.sendTelemgramSeda(message + ":" + openTradeBBSData.getUserId() + ":" + getAlgoName(),"exp-trade");

                                } catch (Exception e) {
                                    LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                    tradeSedaQueue.sendTelemgramSeda("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + ":" + getAlgoName(),"exp-trade");
                                    e.printStackTrace();
                                }
                            }
                        });
                    });

                }
            }*/


    }

    private String getStockId(String index) {
        switch (index) {
            case "BNF":
                return zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            case "NF":
                return zerodhaTransactionService.niftyIndics.get("NIFTY 50");
            case "FN":
                return zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
            case "MC":
                return zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
            case "SS":
                return zerodhaTransactionService.niftyIndics.get("SENSEX");
            default:
                return null;
        }
    }

// Original loop with method call

}
