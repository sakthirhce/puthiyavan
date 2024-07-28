package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;
import com.sakthi.trade.cache.GlobalTick;
import com.sakthi.trade.cache.GlobalTickCache;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.service.TradingStrategyAndTradeData;
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
import java.util.stream.Collectors;

@Component
public class WebSocketTicksSedaProcessor implements Processor {
    Gson gson = new Gson();

    @Autowired
    public TradingStrategyAndTradeData tradingStrategyAndTradeData;
    @Value("${data.export}")
    public boolean dataExport;
    @Value("${websocket.freak.sl.type}")
    public String freakSlType;
    @Autowired
    public BrokerWorkerFactory workerFactory;
    public static final Logger LOGGER = LoggerFactory.getLogger(WebSocketTicksSedaProcessor.class.getName());
    @Autowired
    public UserList userList;
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
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat sstimeFormat = new SimpleDateFormat("ss");
    @Autowired
    ExpiryDayDetails expiryDayDetails;
    Map<Long,Long> alertCount=new HashMap<>();

    @Autowired
    public GlobalTickCache globalTickCache;

    @Autowired
    CommonUtil commonUtil;
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

    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            tickMessageProcessing(message);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void tickMessageProcessing(String message){
            try{
            ArrayList<Tick> ticks = gson.fromJson(message, listType);
            LocalTime currentTime = LocalTime.now();
            LocalDateTime currentDateTime = LocalDateTime.now();
            if (!ticks.isEmpty()) {
                exportTicksToFile(currentTime,dataExport,ticks,csvWriter,tradingStrategyAndTradeData.candleDateTimeFormat, tradingStrategyAndTradeData.openTrade);

                tradingStrategyAndTradeData.openTrade.forEach((userId, tradeDataList) -> {
                    User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    tradeDataList.forEach(tradeData -> {
                        List<Tick> ticklist = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == tradeData.getZerodhaStockId()).collect(Collectors.toList());
                     //   LOGGER.info("tick size:{},{}",tradeData.getZerodhaStockId(), ticklist.size());
                        Optional<Tick> tickOp = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == tradeData.getZerodhaStockId()).findFirst();
                        if (tickOp.isPresent()) {
                            Tick tick = tickOp.get();
                            double lastTradedPrice = tick.getLastTradedPrice();
                            TradeStrategy strategy = tradeData.getTradeStrategy();
                            try {
                                processRangeBuy(tradeData, lastTradedPrice, strategy, brokerWorker, user, tradeSedaQueue);
                            } catch (Exception e) {
                                LOGGER.error("error while placing range order:{}", e.getMessage());
                            }

                            if (tradeData.getEntryOrderId() != null && tradeData.isOrderPlaced && !tradeData.isExited && !tradeData.isSLCancelled) {
                                //Injection Freak move alert:Start
                                try {
                                    freakLastTickAlertData(tick, lastTradedPrice, tradeData, strategy, brokerWorker, currentDateTime, user);
                                    freakMoveInLast30Sec(tick, lastTradedPrice, tradeData, globalTickCache, alertCount, tradeSedaQueue);
                                }catch (Exception e){
                                    LOGGER.info("error while calculating freak points");
                                    e.printStackTrace();
                                }
                                //Injection Freak move alert:End

                                try {
                                    if (!tradeData.isWebsocketSlModified() && tradeData.isSlPriceCalculated && !tradeData.isSlPlaced) {
                                        BigDecimal slPrice = tradeData.getSlPrice();
                                        if (lastTradedPrice > slPrice.doubleValue() && "SELL".equals(tradeData.getEntryType())) {
                                            LOGGER.info("last traded price above sl:{}:{}", tradeData.getStockName(), lastTradedPrice);
                                            placeDynamicSellSL(tradeData, lastTradedPrice, strategy, brokerWorker, user, dateTimeFormatter, currentDateTime);
                                        }

                                        if (lastTradedPrice < slPrice.doubleValue() && "BUY".equals(tradeData.getEntryType()) && !strategy.isNoSl()) {
                                            placeDynamicBuySL(tradeData, lastTradedPrice, strategy, brokerWorker, user, dateTimeFormatter, currentDateTime);
                                        }

                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    LOGGER.error("Error while processing websocket dynamic SL: {}", e.getMessage());
                                    tradeSedaQueue.sendTelemgramSeda("Error while processing websocket dynamic SL" +
                                            " order:" + user.getName() + ",Exception:" + e.getMessage() + ":" + tradeData.getTradeStrategy().getTradeStrategyKey(), "exp-trade");
                                }
                            }
                            if (strategy.isTrailEnabled()) {
                                try {
                                    executeTrailingStopLoss(user, tradeData, tick, strategy, brokerWorker, tradeSedaQueue, decimalFormat);
                                } catch (Exception e) {
                                    LOGGER.error("An error occurred while executing trailing stop loss: {}", e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }
                    });

                });
                executor.submit(() -> {
                    calculateExpiryATM(currentTime, ticks, expiryDayDetails, commonUtil, sstimeFormat);
                });

            }
        } catch (Exception e) {
            try {
                LOGGER.error("error while processing tick:{},{}", message,e.getMessage());
                e.printStackTrace();
            }catch (Exception e1){
                LOGGER.error("error while processing getting tick from exchang:{}", e1.getMessage());
                e1.printStackTrace();
            }
        }
    }
    public void processRangeBuy(TradeData tradeData, double lastTradedPrice, TradeStrategy strategy, BrokerWorker brokerWorker, User user, TradeSedaQueue tradeSedaQueue) {
        if (tradeData.getEntryOrderId() == null && !tradeData.isOrderPlaced) {
            if (tradeData.range && "BUY".equals(tradeData.getEntryType())) {
                if (tradeData.getRangeHigh() != null && tradeData.getRangeHigh().doubleValue() < lastTradedPrice) {
                    LOGGER.info("option price higher than range:{},last traded price: {},high:{} ", tradeData.getStockName(), lastTradedPrice, tradeData.getRangeHigh().doubleValue());
                    OrderParams orderParams = new OrderParams();

                    orderParams.exchange = "NFO";
                    if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
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
                    strategy.getUserSubscriptions().getUserSubscriptionList().stream()
                            .filter(userSubscription -> user.getName().equals(userSubscription.getUserId()))
                            .forEach(subscriptionOptional -> {
                                LOGGER.info("range order zerodha input:" + gson.toJson(orderParams));
                                //if (subscriptionOptional.isPresent()) {
                              //  UserSubscription subscription = subscriptionOptional.get();
                                Order order;
                                int lot = strategy.getLotSize() * subscriptionOptional.getLotSize();
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
                                //  }
                            });
                }
            }
        }
    }
    public void placeDynamicSellSL(TradeData tradeData, double lastTradedPrice, TradeStrategy strategy, BrokerWorker brokerWorker, User user,
                                   DateTimeFormatter dateTimeFormatter, LocalDateTime currentDateTime) {
        try {
        if ("SELL".equals(tradeData.getEntryType())) {
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
            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                orderParams.exchange = "BFO";
            }
            orderParams.validity = "DAY";
            orderParams.transactionType = "BUY";
            orderParams.orderType = freakSlType;
            orderParams.price = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
            if (!tradeData.isWebsocketSlModified()) {
                Order order = brokerWorker.placeOrder(orderParams, user, tradeData);
               // mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                tradeData.isSlPlaced = true;
                tradeData.setSlOrderId(order.orderId);
                tradeData.setWebsocketSlModified(true);
                tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
            }
        }}catch (Exception | KiteException e) {
            e.printStackTrace();
        }}
    public void placeDynamicBuySL(TradeData tradeData, double lastTradedPrice, TradeStrategy strategy, BrokerWorker brokerWorker, User user, DateTimeFormatter dateTimeFormatter, LocalDateTime currentDateTime) {
        try {
            if ("BUY".equals(tradeData.getEntryType())) {
                LOGGER.info("last traded price below sl:{}:{}", tradeData.getStockName(), lastTradedPrice);
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
                if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                    orderParams.exchange = "BFO";
                }
                orderParams.validity = "DAY";
                orderParams.transactionType = "SELL";
                orderParams.orderType = "LIMIT";
                orderParams.price = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                if (!tradeData.isWebsocketSlModified()) {
                    Order order = brokerWorker.placeOrder(orderParams, user, tradeData);

                    tradeData.isSlPlaced = true;
                    tradeData.setSlOrderId(order.orderId);
                    tradeData.setWebsocketSlModified(true);
                    tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                  //  mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                }
            }}catch (Exception | KiteException e) {
            e.printStackTrace();
        }}
    public void calculateExpiryATM(LocalTime currentTime, List<Tick> ticks, ExpiryDayDetails expiryDayDetails, CommonUtil commonUtil, SimpleDateFormat sstimeFormat) {
        try {
            LocalTime tickStartTime = LocalTime.of(9, 14);
            LocalTime tickEndTime = LocalTime.of(15, 31);
            if (currentTime.isAfter(tickStartTime) && currentTime.isBefore(tickEndTime)) {
                Optional<Tick> tickOp = ticks.stream().filter(tickTemp -> !expiryDayDetails.indexIdList.isEmpty()
                        && expiryDayDetails.indexIdList.containsKey(tickTemp.getInstrumentToken())).findFirst();
                if (tickOp.isPresent()) {
                    Tick indexTickData = tickOp.get();
                    String indexConfig = expiryDayDetails.indexIdList.get(indexTickData.getInstrumentToken());
                    long currentATM = commonUtil.findATMTick((int) indexTickData.getLastTradedPrice(), indexConfig);
                    try {
                        expiryDayDetails.currentATMList.put(indexTickData.getInstrumentToken(), currentATM);
                        String ssTime = sstimeFormat.format(indexTickData.getTickTimestamp());
                        if ("00".equals(ssTime)) {
                           // LOGGER.info("atm:{}:{}", expiryDayDetails.expiryCurrentAtmValue, indexTickData.getLastTradedPrice());
                        }
                    } catch (Exception e) {
                        LOGGER.error("error while calculating expiry ATM:{}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("error while calculating expiry ATM:{}", e.getMessage());
        }
    }
    public void exportTicksToFile(LocalTime currentTime, boolean dataExport, List<Tick> ticks, CSVWriter csvWriter, SimpleDateFormat candleDateTimeFormat, Map<String, List<TradeData>> openTrade) {
        try {
            LocalTime tickStartTime = LocalTime.of(9, 14);
            LocalTime tickEndTime = LocalTime.of(15, 31);
            if (currentTime.isAfter(tickStartTime) && currentTime.isBefore(tickEndTime)) {

                    ticks.forEach(tick -> { if (dataExport) {
                        String[] dataHeader = {String.valueOf(tick.getInstrumentToken()), candleDateTimeFormat.format(tick.getTickTimestamp()), String.valueOf(tick.getLastTradedPrice())};
                        csvWriter.writeNext(dataHeader);
                        try {
                            csvWriter.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                        try {
                            Optional<Map.Entry<String, List<TradeData>>> optionalTrade = openTrade.entrySet().stream()
                                    .filter(entry -> entry.getValue().stream()
                                            .anyMatch(tradeData -> tick.getInstrumentToken() == tradeData.getZerodhaStockId())).findFirst();

                            if (optionalTrade.isPresent()) {
                                globalTickCache.setHistoricData((int) tick.getInstrumentToken(), tick.getLastTradedPrice());
                            }
                        }catch (Exception e){
                            LOGGER.error("error while setting tick price in global tick cache:{}",tick.getInstrumentToken());
                            e.printStackTrace();
                        }
                    });


            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void executeTrailingStopLoss(User user, TradeData tradeData, Tick tick, TradeStrategy strategy, BrokerWorker brokerWorker, TradeSedaQueue tradeSedaQueue, DecimalFormat decimalFormat) {
        try {
            if (tradeData.isOrderPlaced && !tradeData.isExited && !tradeData.isSlPlaced) {
               // System.out.printf("stock:" + tradeData.getStockName() + ":" + tick.getLastTradedPrice() + ":" + tradeData.getTradeStrategy().getTradeStrategyKey());
                double entryPrice;
                if ("BUY".equals(tradeData.getEntryType())) {
                    entryPrice = tradeData.getBuyPrice().doubleValue();
                } else {
                    entryPrice = tradeData.getSellPrice().doubleValue();
                }
                if (tradeData.getInitialSLPrice() != null) {
                    double trailingStopLoss = updatedTrailPrice(tradeData.getInitialSLPrice().doubleValue(), entryPrice, tick.getLastTradedPrice(), strategy.getTrailPointMove().intValue(), strategy.getTrailSlMoves().intValue(), tradeData.getEntryType());

                    if(tradeData.getSlOrderId()!=null) {
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
                    }
                }
            }
        } catch (IOException e) {
            tradeSedaQueue.sendTelemgramSeda("error while modifying trail sl Option " + tradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
            throw new RuntimeException(e);
        } catch (KiteException e) {
            tradeSedaQueue.sendTelemgramSeda("error while modifying trail sl Option " + tradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
            throw new RuntimeException(e);
        }
    }

    public void freakLastTickAlertData(Tick tick, double lastTradedPrice, TradeData tradeData, TradeStrategy strategy,BrokerWorker brokerWorker,LocalDateTime currentDateTime,User user) {
        try {
            GlobalTick globalTick = globalTickCache.getHistoricData((int) tick.getInstrumentToken());
        //    LOGGER.info("stock_id "+tradeData.getZerodhaStockId()+" last traded price:"+lastTradedPrice+" cache: "+gson.toJson(globalTick));
            if (globalTick != null) {
                List<Double> tickData = globalTick.getHistoricalDataMap();
                if (tickData != null && !tickData.isEmpty()) {
                    double previousTick = tickData.get(tickData.size() - 2); // size-1 means current tick
                    double diff = lastTradedPrice - previousTick;
                    if (diff > 0 && lastTradedPrice > 20) {
                        double percentageDiffBWPreviousAndRecentTick = 1.1 * previousTick;
                        double tenPercentOfPreviousTick = 0.20 * previousTick;
                      //  String tickMessageq = "last traded price is more than 10% higher than previous tick. Difference: " + String.format("%,.2f", diff) + ". last trade price: " + lastTradedPrice + ". ";
                        //LOGGER.info(tickMessageq);
                        if (lastTradedPrice > percentageDiffBWPreviousAndRecentTick) {
                            String tickMessage = "last traded price is more than 10% higher than previous tick. Difference: " + String.format("%,.2f", diff) + ". last trade price: " + lastTradedPrice + ". ";
                            LOGGER.info(tickMessage);
                            if(strategy.isFreakBuy()){
                                placeFreakBuy(tradeData,strategy,brokerWorker,user);
                            }
                            tradeSedaQueue.sendTelemgramSeda(tickMessage, "algo");
                            if ( tradeData.getSlPrice()!=null && (lastTradedPrice + tenPercentOfPreviousTick) >= tradeData.getSlPrice().doubleValue()) {
                                tickMessage ="Another 10% to touch SL price. SLPrice:" + String.format("%,.2f", tradeData.getSlPrice());
                                LOGGER.info(tickMessage);
                                tradeSedaQueue.sendTelemgramSeda(tickMessage, "algo");
                                String freakSlMessage="placing sl based on freak tick:"+ tradeData.getStockName()+": last traded price"+lastTradedPrice+": user: "+tradeData.getUserId();
                                if(strategy.isFreakSlPlace() && !tradeData.isSlPlaced && !tradeData.isWebsocketSlModified() && tradeData.isSlPriceCalculated ) {
                                    LOGGER.info(freakSlMessage);
                                    tradeSedaQueue.sendTelemgramSeda(freakSlMessage, "algo");
                                    placeDynamicSellSL(tradeData, lastTradedPrice, strategy, brokerWorker, user, dateTimeFormatter, currentDateTime);
                                }
                                }

                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred while processing the freak tick data", e);
            e.printStackTrace();
        }
    }

    private void placeFreakBuy(TradeData tradeData, TradeStrategy strategy, BrokerWorker brokerWorker,User user) {

        if (!tradeData.isOrderPlaced){
            try {
                if ("BUY".equals(tradeData.getEntryType())) {
                    LOGGER.info("freak move detected, placing buy:{}", tradeData.getStockName());
                    OrderParams orderParams = new OrderParams();
                    if (strategy.getTradeStrategyKey().length() <= 20) {
                        orderParams.tag = strategy.getTradeStrategyKey();
                    }
                    orderParams.tradingsymbol = tradeData.getStockName();
                    orderParams.exchange = "NFO";
                    orderParams.quantity = tradeData.getQty();
                    if ("MIS".equals(strategy.getTradeValidity())) {
                        orderParams.product = "MIS";
                    } else {
                        orderParams.product = "NRML";
                    }
                    if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                        orderParams.exchange = "BFO";
                    }
                    orderParams.validity = "DAY";
                    orderParams.transactionType = "BUY";
                    orderParams.orderType = "MARKET";
                   // orderParams.price = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
              //      if (!tradeData.isWebsocketSlModified()) {
                        Order order = brokerWorker.placeOrder(orderParams, user, tradeData);
                        tradeData.isOrderPlaced = true;
                        tradeData.setEntryOrderId(order.orderId);
                        mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                        try {
                            postTradePrioritySedaQueue(tradeData);
                        }catch (Exception e){
                            e.printStackTrace();
                            LOGGER.error("error while posting to trade priority seda queue: {}", gson.toJson(tradeData));
                        }
                        LOGGER.info("freak trade data: {}", gson.toJson(tradeData));
                        tradeSedaQueue.sendTelemgramSeda("Freak Options traded for user:" + user.getName() + " strike: "
                                + tradeData.getStockName() + ":" + strategy.getTradeStrategyKey(), "exp-trade");

                  //  }
                }}catch (Exception | KiteException e) {
                e.printStackTrace();
                LOGGER.info("KITE Error while placing freak buy order: {}", e.getMessage());
            }
        }
    }

    public void freakMoveInLast30Sec(Tick tick, double lastTradedPrice, TradeData tradeData, GlobalTickCache globalTickCache, Map<Long, Long> alertCount, TradeSedaQueue tradeSedaQueue) {
        try {
            // Set the historic data for the instrument
            // Retrieve the historic data
            GlobalTick globalTick = globalTickCache.getHistoricData((int) tick.getInstrumentToken());
            if (globalTick != null) {
                List<Double> tickData = globalTick.getHistoricalDataMap();

                // Check if tickData is not empty and has more than 30 entries
                if (tickData != null && !tickData.isEmpty() && tickData.size() > 30) {
                    double lastTick = tickData.get(tickData.size() - 1);
                    double percentCheck = getPercentConfig(lastTick);

                    // Get the last 30 ticks
                    List<Double> subTickData = tickData.subList(Math.max(0, tickData.size() - 30), tickData.size());
                    double low = Collections.min(subTickData);
                    double high = Collections.max(subTickData);
                    double diff = high - low;
                    if (tradeData.getEntryType().equals("SELL")) {
                        if (tradeData.getSellPrice() != null) {
                            double percentageDiff = (diff / tradeData.getSellPrice().doubleValue()) * 100;
                            long key = tick.getInstrumentToken();

                            // Update alert count
                            alertCount.put(key, alertCount.getOrDefault(key, 0L) + 1);

                            // Check if the conditions are met to log and send an alert
                            if (percentageDiff > percentCheck && diff > 0 && alertCount.get(key) < 3) {
                                LOGGER.info("stock:{}:high:{}:low:{}:diff:{}:percentageDiff:{}",
                                        tradeData.getStockName(), high, low, diff, percentageDiff);
                                tradeSedaQueue.sendTelemgramSeda("stock:" + tradeData.getStockName() +
                                        " high:" + high + " low:" + low + " diff:" + diff +
                                        " percentageDiff:" + percentageDiff, "error");
                            }
                        } else {
                            LOGGER.warn("tradeData or sellPrice is null for stock: {}", tradeData.getStockName());
                        }
                    }
                } else {
                //    LOGGER.warn("tickData is null, empty, or does not have more than 30 entries for instrument: {}", tick.getInstrumentToken());
                }
            } else {
                LOGGER.warn("GlobalTick is null for instrument: {}", tick.getInstrumentToken());
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred while processing the tick 30 sec data");
            e.printStackTrace();
        }
    }

    private double getPercentConfig(double lastTick) {
        if (lastTick > 50) {
            return 10;
        } else if (lastTick > 40) {
            return 15;
        } else if (lastTick >= 20 && lastTick < 30) {
            return 20;
        } else if (lastTick >= 10 && lastTick < 20) {
            return 40;
        } else {
            return 60;
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

    public void postTradePrioritySedaQueue(TradeData tradeData) {
        tradeSedaQueue.sendTradePrioritySeda(gson.toJson(tradeData));
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
