package com.sakthi.trade;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.TradeValidity;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.repo.TradeStrategyRepo;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.Expiry;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class TradeEngine {
    public static final Logger LOGGER =  LoggerFactory.getLogger(TradeEngine.class.getName());
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    CommonUtil commonUtil;
    @Autowired
    TransactionService transactionService;
    Map<String, Map<String, List<TradeStrategy>>> strategyMap = new ConcurrentHashMap<>();
   Map<String, List<TradeStrategy>> rangeStrategyMap = new ConcurrentHashMap<>();
    Map<String, List<TradeData>> openTrade = new ConcurrentHashMap<>(); // stock_id: List of trades
    ExecutorService executorThread = java.util.concurrent.Executors.newFixedThreadPool(2);
    ExecutorService executorThreadIndex = java.util.concurrent.Executors.newFixedThreadPool(2);
    ExecutorService executorThreadRange = java.util.concurrent.Executors.newFixedThreadPool(2);
    ExecutorService executorRangeThreadSt = java.util.concurrent.Executors.newFixedThreadPool(3);
    ExecutorService sLMonitor = java.util.concurrent.Executors.newFixedThreadPool(1);
    ExecutorService exitThread = java.util.concurrent.Executors.newFixedThreadPool(1);
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    SimpleDateFormat hourMinFormat = new SimpleDateFormat("HH:mm");
    SimpleDateFormat dayFormat = new SimpleDateFormat("E");
    @Autowired
    TradeDataMapper tradeDataMapper;
    @Autowired
    UserList userList;
    @Autowired
    BrokerWorkerFactory workerFactory;
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;
    @Value("${lotSize.config}")
    List<String> config;
    Gson gson = new Gson();
    @Autowired
    TelegramMessenger sendMessage;
    @Autowired
    MathUtils mathUtils;

    public String getAlgoName() {
        return "TRADE_ENGINE";
    }
    @Scheduled(cron="${tradeEngine.load.strategy}")
    public void loadStrategy() {
      //  strategyMap = new ConcurrentHashMap<>();
        Date date = new Date();
        List<TradeStrategy> tradeStrategyList = tradeStrategyRepo.getActiveUsersActiveStrategy();
       // System.out.println(new Gson().toJson(tradeStrategyList));
        tradeStrategyList.forEach(strategy -> {
            String index = strategy.getIndex();
            AtomicInteger lotA = new AtomicInteger(0);
            config.forEach(lot -> {
                System.out.println(lot);
                String[] lotSplit = lot.split("-");
                if (lotSplit[0].equals(index)) {
                    lotA.getAndSet(Integer.parseInt(lotSplit[1]));
                }

            });
            strategy.setLotSize(strategy.getLotSize() * lotA.get());
            if (strategy.getTradeDays().contains(dayFormat.format(date).toUpperCase())|| Objects.equals(strategy.getTradeDays(), "All")) {
                if (strategy.isRangeBreak()) {
                    List<TradeStrategy> indexTimeMap = rangeStrategyMap.get(index);
                        if (rangeStrategyMap.get(index) != null && indexTimeMap.size() > 0) {
                            indexTimeMap.add(strategy);
                        } else {
                            List<TradeStrategy> tradeStrategies = new ArrayList<>();
                            tradeStrategies.add(strategy);
                            rangeStrategyMap.put(strategy.getIndex(), tradeStrategies);
                        }

                } else {

                    if (strategyMap.get(index) != null) {
                        strategy.setLotSize(strategy.getLotSize() * lotA.get());
                        Map<String, List<TradeStrategy>> indexTimeMap = strategyMap.get(index);
                        List<TradeStrategy> strategies = indexTimeMap.get(strategy.getEntryTime());
                        if (indexTimeMap.containsKey(strategy.getEntryTime()) && strategies.size() > 0) {
                            strategies.add(strategy);
                        } else {
                            List<TradeStrategy> tradeStrategies = new ArrayList<>();
                            tradeStrategies.add(strategy);
                            indexTimeMap.put(strategy.getEntryTime(), tradeStrategies);
                            strategyMap.put(strategy.getIndex(), indexTimeMap);
                        }

                    } else {
                        Map<String, List<TradeStrategy>> indexTimeMap = new HashMap<>();
                        List<TradeStrategy> tradeStrategies = new ArrayList<>();
                        tradeStrategies.add(strategy);
                        indexTimeMap.put(strategy.getEntryTime(), tradeStrategies);
                        strategyMap.put(strategy.getIndex(), indexTimeMap);
                    }
                }
            }
        });
        System.out.println(new Gson().toJson(strategyMap));
    }

    @Autowired
    TradeSedaQueue tradeSedaQueue;

    @Scheduled(cron="${tradeEngine.execute.strategy}")
    public void executeStrategy() {
        Date date = new Date();
  //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = dateFormat.format(date);
       // String currentDateStr="2023-02-24";

       // tradeSedaQueue.sendTelemgramSeda("started trade engine execution");
        Calendar candleCalenderMin = Calendar.getInstance();
        Calendar calendarCurrentMin = Calendar.getInstance();
        candleCalenderMin.add(Calendar.MINUTE, -1);
        Date currentMinDate = calendarCurrentMin.getTime();
        Date candleCurrentMinDate = candleCalenderMin.getTime();
        String candleHourMinStr=hourMinFormat.format(candleCurrentMinDate);

     //  String candleHourMinStr="09:23";
       // System.out.println(candleHourMinStr);
        String currentHourMinStr=hourMinFormat.format(currentMinDate);

       // String currentHourMinStr="09:24";
       // System.out.println(currentHourMinStr);
        //LOGGER.info("trade engine: " + currentDateStr+":"+currentHourMinStr);
        executorThread.submit(() -> {
            //ORB range break code starts
            rangeStrategyMap.forEach((index,strategyList)->{
                strategyList.forEach(strategy -> {
                    String stockId = null;
                    if ("BNF".equals(index)) {
                        stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                    } else if ("NF".equals(index)) {
                        stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                    } else if ("FN".equals(index)) {
                        stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                    }
                    String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,currentHourMinStr);
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
                        Optional.of(optionalHistoricalLatestData).ifPresent(lastHistoricalDataOp -> {

                        HistoricalData lastHistoricalData = lastHistoricalDataOp.get();
                        try {
                            orbRangeBreak(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr + ":00", lastHistoricalData);
                        } catch (Exception e) {
                            LOGGER.error("error"+e.getMessage());
                            throw new RuntimeException(e);

                        }
                    });
                    }

                });
            });
            //ORB range break code End
            strategyMap.forEach((index, timeStrategyListMap) -> {
                executorThreadIndex.submit(() -> {
                String stockId = null;
                if ("BNF".equals(index)) {
                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                } else if ("NF".equals(index)) {
                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                } else if ("FN".equals(index)) {
                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                }
                String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,currentHourMinStr);
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
                    Optional.of(optionalHistoricalLatestData).ifPresent(lastHistoricalDataOp -> {
                        HistoricalData lastHistoricalData = lastHistoricalDataOp.get();
                  //      LOGGER.info("last candle time and close: " + lastHistoricalData.timeStamp + ":" + lastHistoricalData.close);
                        List<TradeStrategy> strategies = timeStrategyListMap.get(currentHourMinStr);

                        if (strategies.size() > 0) {
                            strategies.forEach(strategy -> {
                               // executorThreadStrategy.submit(() -> {
                                    LOGGER.info("strategy name:" + strategy.getAliasName());
                                    Map<Double, Map<String, StrikeData>> rangeStrikes = new HashMap<>();

                                        if (strategy.getEntryTime().equals(currentHourMinStr)) {
                                            rangeStrikes = strikeSelection(currentDateStr, strategy, lastHistoricalData.close, candleHourMinStr + ":00");
                                        }


                                    LOGGER.info(strategy.getAliasName()+":"+rangeStrikes);
                                    rangeStrikes.forEach((strikePrice, strikeDataEntry) -> {
                                        strikeDataEntry.entrySet().stream().forEach(strikeDataEntry1 -> {
                                            StrikeData strikeData = strikeDataEntry1.getValue();
                                            OrderParams orderParams = new OrderParams();

                                            if (strategy.isSimpleMomentum()) {
                                                orderParams.orderType = "SL";
                                                BigDecimal triggerPriceTemp=new BigDecimal(0);
                                                if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_UP.getType())) {
                                                     triggerPriceTemp = (MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), new BigDecimal(strikePrice)).add(new BigDecimal(strikePrice))).setScale(0, RoundingMode.HALF_UP);
                                                }
                                                if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_DOWN.getType())) {
                                                     triggerPriceTemp = (new BigDecimal(strikePrice)).subtract(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), new BigDecimal(strikePrice))).setScale(0, RoundingMode.HALF_UP);
                                                }
                                                if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_UP.getType())) {
                                                     triggerPriceTemp = new BigDecimal(strikePrice).add(strategy.getSimpleMomentumValue()).setScale(0, RoundingMode.HALF_UP);
                                                }
                                                if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_DOWN.getType())) {
                                                     triggerPriceTemp = new BigDecimal(strikePrice).subtract(strategy.getSimpleMomentumValue()).setScale(0, RoundingMode.HALF_UP);

                                                }
                                                System.out.println(triggerPriceTemp);
                                                orderParams.triggerPrice=triggerPriceTemp.doubleValue();
                                                BigDecimal price =triggerPriceTemp.add(triggerPriceTemp.setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                orderParams.price=price.doubleValue();
                                            }
                                            LOGGER.info(strategy.getAliasName()+"placing order for:"+strikeData.getZerodhaSymbol());

                                            orderParams.tradingsymbol = strikeData.getZerodhaSymbol();
                                            orderParams.exchange = "NFO";
                                            orderParams.product = "NRML";
                                            orderParams.transactionType = strategy.getOrderType();
                                            orderParams.validity = "DAY";
                                            LocalDate localDate = LocalDate.now();
                                            DayOfWeek dow = localDate.getDayOfWeek();
                                            String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                            //TODO get all users subsribed to this.
                                            userList.getUser().stream().filter(
                                                    user -> user.getName().equals(strategy.getUserId())
                                            ).forEach(user -> {
                                                BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                                Order order = null;
                                                orderParams.quantity = strategy.getLotSize();
                                                TradeData tradeData = new TradeData();
                                                String dataKey = UUID.randomUUID().toString();
                                                tradeData.setDataKey(dataKey);
                                                tradeData.setStockName(strikeData.getZerodhaSymbol());
                                                try {
                                                    //TODO set sl price, entry price, exit date
                                                       order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                    if (order != null) tradeData.setEntryOrderId(order.orderId);
                                                  //  tradeData.isOrderPlaced = true;
                                                    tradeData.setQty(strategy.getLotSize());
                                                    tradeData.setEntryType(strategy.getOrderType());
                                                    tradeData.setUserId(user.getName());
                                                    tradeData.setStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                                    List<TradeData> tradeDataList = openTrade.get(user.getName());
                                                    if (tradeDataList == null) {
                                                        tradeDataList = new ArrayList<>();
                                                    }
                                                    tradeDataList.add(tradeData);
                                                    openTrade.put(user.getName(), tradeDataList);
                                                    LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                                    sendMessage.sendToTelegram("Options traded for user:" + user.getName() + " strike: " + strikeData.getZerodhaSymbol() + ":" + strategy.getAliasName(), telegramToken);
                                                } catch (Exception e) {
                                                    tradeData.isErrored = true;
                                                    LOGGER.info("Error while placing straddle order: " + e);
                                                    e.printStackTrace();
                                                    sendMessage.sendToTelegram("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(), telegramToken);

                                                } catch (KiteException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });

                                        });

                                    });
                              //  });
                            });
                        }

                    });
                }
            });
        });
        });
        sLMonitor.submit(() -> {
            openTrade.entrySet().stream().forEach(userTradeData -> {
                String userId=userTradeData.getKey();
                List<TradeData> tradeData = userTradeData.getValue();
                User user = userList.getUser().stream().filter(
                        user1 -> user1.getClientId().equals(userId)
                ).findFirst().get();

                BrokerWorker brokerWorker = workerFactory.getWorker(user);

                try {
                    List<Order> orderList = brokerWorker.getOrders(user);

                    tradeData.stream().filter(order -> order.getEntryOrderId() != null && !order.isExited).forEach(trendTradeData -> {
                        orderList.forEach(order -> {
                            if (!trendTradeData.isErrored && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;

                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                        LOGGER.info(message);
                                        try {
                                            sendMessage.sendToTelegram(message, telegramToken);
                                        } catch (Exception e) {
                                            LOGGER.info("error:" + e);
                                        }
                                    }
                                }
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && !trendTradeData.isOrderPlaced ) {
                                    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                        trendTradeData.isOrderPlaced=true;
                                        try {
                                            LOGGER.info("order executed" + trendTradeData.getStockName() + ":" + trendTradeData.getEntryOrderId());
                                            //BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(50)).setScale(0, RoundingMode.UP);
                                            String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                            LOGGER.info(message);
                                            sendMessage.sendToTelegram(message, telegramToken);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                if (!trendTradeData.isSlPlaced && trendTradeData.isOrderPlaced ) {
                                //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                            try {
                                                // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                                OrderParams orderParams = new OrderParams();
                                                orderParams.tradingsymbol = trendTradeData.getStockName();
                                                orderParams.exchange = "NFO";
                                                orderParams.quantity = trendTradeData.getQty();
                                                orderParams.orderType = "SL";
                                                orderParams.product = "NRML";
                                                orderParams.validity = "DAY";
                                                com.zerodhatech.models.Order orderd = null;
                                                if ("BUY".equals(order.transactionType)) {
                                                    BigDecimal triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                    BigDecimal price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                                    orderParams.price = price.doubleValue();
                                                    orderParams.transactionType = "SELL";
                                                    trendTradeData.setSlPrice(triggerPriceTemp);
                                                    LOGGER.info("buy price:" + trendTradeData.getBuyPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                                }else{
                                                    BigDecimal triggerPriceTemp = (trendTradeData.getBuyPrice().add(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                    BigDecimal price = triggerPriceTemp.add(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                    orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                                    orderParams.price = price.doubleValue();
                                                    orderParams.transactionType = "BUY";
                                                    trendTradeData.setSlPrice(triggerPriceTemp);
                                                    LOGGER.info("sell price:" + trendTradeData.getSellPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                                }

                                                try {
                                                    LOGGER.info("input:" + gson.toJson(orderParams));
                                                    orderd =brokerWorker.placeOrder(orderParams,user,trendTradeData);
                                                    trendTradeData.isSlPlaced = true;
                                                    trendTradeData.setSlOrderId(orderd.orderId);
                                                    mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                                    try {
                                                        LOGGER.info("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL");
                                                        sendMessage.sendToTelegram("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL" + ":" + getAlgoName(), telegramToken);
                                                    } catch (Exception e) {
                                                        LOGGER.info("error:" + e);
                                                    }
                                                } catch (Exception e) {
                                                    // tradeData.isErrored = true;
                                                    LOGGER.info("Nifty option order: " + e.getMessage() + ":" + user.getName());
                                                    sendMessage.sendToTelegram("Nifty option order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ":" + getAlgoName(), telegramToken);
                                                    //e.printStackTrace();
                                                } catch (KiteException e) {
                                                    throw new RuntimeException(e);
                                                }

                                            } catch (Exception e) {
                                                LOGGER.info("error while placing sl:" + e.getMessage() + trendTradeData.getEntryOrderId() + ":" + trendTradeData.getStockName());
                                            }
                                      //  }

                                }
                                if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && "SELL".equals(order.transactionType) && order.orderId.equals(trendTradeData.getSlOrderId()) && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                    trendTradeData.isSLHit = true;
                                    trendTradeData.isExited = true;
                                    trendTradeData.setExitOrderId(order.orderId);
                                    trendTradeData.setSellPrice(trendTradeData.getSlPrice());
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, RoundingMode.UP);
                                    String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage.toString(), trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                    LOGGER.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                         mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                }

                            } else if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                                String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + getAlgoName());
                                LOGGER.info(message);
                                trendTradeData.isErrored = true;
                                sendMessage.sendToTelegram(message, telegramToken);
                            }


                        });
                    });
                }catch (Exception | KiteException e){
                    e.printStackTrace();
                }
            });

        });

        exitThread.submit(() -> {
            openTrade.entrySet().stream().forEach(userTradeData -> {
                String userId=userTradeData.getKey();
                List<TradeData> tradeDataList = userTradeData.getValue();
                User user = userList.getUser().stream().filter(
                        user1 -> user1.getClientId().equals(userId)
                ).findFirst().get();
                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                try {
                    List<Order> orders = brokerWorker.getOrders(user);
                    List<Position> positions = brokerWorker.getPositions(user);
                    tradeDataList.stream().forEach(trendTradeData -> {
                        TradeStrategy strategy = trendTradeData.getTradeStrategy();
                        try {
                            if ((trendTradeData.getTradeDate().equals(currentDateStr) && strategy != null && strategy.getTradeValidity().equals("MIS")) || (dateFormat.parse(trendTradeData.getTradeDate()).before(dateFormat.parse(currentDateStr)) && strategy != null && strategy.getTradeValidity().equals("CNC"))) {
                                if (currentHourMinStr.equals(strategy.getExitTime())) {

                                    LOGGER.info(new Gson().toJson(positions));
                                    if (user.getNiftyBuy935().straddleTradeMap != null) {
                                        user.getNiftyBuy935().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null && !map.getValue().isExited).forEach(straddleTradeMap -> {
                                            TradeData tradeData = straddleTradeMap.getValue();
                                            LOGGER.info("nifty 15:10 exit started 1");
                                            if (user.getName().equals(straddleTradeMap.getValue().getUserId())) {
                                                if (user.getNiftyBuy935().getLotConfig() != null && user.getNiftyBuy935().buyConfig.isEnabled()) {
                                                    LOGGER.info("nifty 15:10 exit started 2");
                                                    LocalDate localDate = LocalDate.now();
                                                    DayOfWeek dow = localDate.getDayOfWeek();
                                                    String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                                    String todayCaps = today.toUpperCase();
                                                    AtomicInteger qty = new AtomicInteger(0);
                                                    user.getNiftyBuy935().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                                        if (lotValue.contains(todayCaps)) {
                                                            int value = (Integer.parseInt(value1) * 50);
                                                            qty.getAndSet(value);
                                                            orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                                                try {
                                                                    OrderParams orderParams = new OrderParams();
                                                                    LOGGER.info("nifty 15:10 exit started 4");
                                                                    orderParams.quantity = Integer.parseInt(orderr.quantity) - qty.get();
                                                                    tradeData.setQty(orderParams.quantity);
                                                                    orderParams.tradingsymbol = tradeData.getStockName();
                                                                    orderParams.exchange = "NFO";
                                                                    orderParams.orderType = orderr.orderType;
                                                                    orderParams.validity = "DAY";
                                                                    orderParams.price = Double.parseDouble(orderr.price);
                                                                    orderParams.triggerPrice = Double.parseDouble(orderr.triggerPrice);
                                                                    orderParams.orderType = "SL";
                                                                    orderParams.product = "NRML";
                                                                    System.out.println("input for bnf sl mod: " + new Gson().toJson(orderParams));
                                                                    //  Order order = user.getKiteConnect().modifyOrder(tradeData.getSlOrderId(), orderParams, "regular");
                                                                    sendMessage.sendToTelegram("sl buy qty modified for nrml:" + tradeData.getUserId() + ": new sl qty:" + tradeData.getQty() + ":" + getAlgoName(), telegramToken);
                                                                } catch (Exception e) {
                                                                    LOGGER.info(e.getMessage());
                                                                }
                                                            });
                                                            positions.stream().filter(position -> ("NRML".equals(position.product) || "MARGIN".equals(position.product)) && tradeData.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).forEach(position -> {
                                                                //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                                                                OrderParams orderParams = new OrderParams();
                                                                orderParams.tradingsymbol = position.tradingSymbol;
                                                                orderParams.exchange = "NFO";
                                                                orderParams.quantity = qty.get();
                                                                orderParams.orderType = "MARKET";
                                                                orderParams.product = "NRML";
                                                                //orderParams.price=price.doubleValue();
                                                                if (position.netQuantity > 0) {
                                                                    orderParams.transactionType = "SELL";
                                                                    orderParams.validity = "DAY";
                                                                    com.zerodhatech.models.Order orderResponse = null;
                                                                    try {
                                                                           orderResponse = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                                        LOGGER.info(new Gson().toJson(orderResponse));
                                                                        tradeData.setQty(qty.get());
                                                                        //mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                                        String message = MessageFormat.format("Closed Intraday Buy Position {0}", orderParams.tradingsymbol);
                                                                        LOGGER.info(message);
                                                                        TradeData tradeDataBuy = gson.fromJson(gson.toJson(tradeData), TradeData.class);
                                                                        String dataKey = UUID.randomUUID().toString();
                                                                        tradeDataBuy.setDataKey(dataKey);
                                                                        tradeDataBuy.isExited = true;
                                                                        tradeDataBuy.setExitOrderId(orderResponse.orderId);
                                                                        //  mapTradeDataToSaveOpenTradeDataEntity(tradeDataBuy, true);
                                                                        user.getNiftyBuy935().straddleTradeMap.put(tradeDataBuy.getStockName() + "-BUY-INTRADAY", tradeDataBuy);
                                                                        sendMessage.sendToTelegram(message + ":" + tradeData.getUserId() + ":" + getAlgoName(), telegramToken);

                                                                    } catch (Exception e) {
                                                                        LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(), telegramToken);
                                                                        e.printStackTrace();
                                                                    } catch (KiteException e) {
                                                                        throw new RuntimeException(e);
                                                                    }
                                                                }
                                                                //  }
                                                            });
                                                        }
                                                    });


                                                }

                                            }
                                        });
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (IOException | KiteException e) {
                    throw new RuntimeException(e);
                }
            });

        });
    }

    public void  orbRangeBreak(TradeStrategy strategy,HistoricalData historicalData,String currentDateStr,String currentHourMinStr,String candleHourMinStr,HistoricalData lastHistoricData) throws Exception {

        Map<String, Double> orbHighLow = new HashMap<>();
        if((strategy.getRangeBreakTime()).equals(currentHourMinStr)) {
            orbHighLow(historicalData.dataArrayList, orbHighLow, strategy, currentDateStr);

        }
        try {
            if(dateTimeFormat.parse(currentDateStr+" "+currentHourMinStr).after(dateTimeFormat.parse(currentDateStr+" "+strategy.getRangeBreakTime()))) {

                if (strategy.getRangeLow().doubleValue() > 0) {
                        if (lastHistoricData.close < strategy.getRangeLow().doubleValue()) {
                            Map<String, StrikeData> rangeSelected;
                            rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDateStr, strategy.getStrikePriceRangeHigh().intValue(), strategy.getStrikePriceRangeLow().intValue(), candleHourMinStr, strategy.getIndex());
                            Map.Entry<String, StrikeData> finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                            TradeData tradeData = new TradeData();
                            tradeData.setEntryType("BUY");
                            tradeData.isOrderPlaced = true;
                            tradeData.setQty(strategy.getLotSize());
                        //    tradeData.setSellTime(candleDateTimeFormat.format(lastHistoricData.timeStamp));
                            tradeData.setSellPrice(new BigDecimal(lastHistoricData.close));
                            tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                            List<TradeData> tradeDataList = openTrade.get(finalSelected.getValue().getZerodhaId());
                            if (tradeDataList != null && tradeDataList.size() > 0) {
                                tradeDataList.add(tradeData);
                            } else {
                                tradeDataList = new ArrayList<>();
                                tradeDataList.add(tradeData);
                            }
                            openTrade.put(finalSelected.getValue().getZerodhaId(), tradeDataList);
                            String message = "TradeEngine:"+strategy.getAliasName()+":"+currentHourMinStr+"option orb range low broke, strike selected :" + finalSelected.getValue().getZerodhaSymbol();
                            sendMessage.sendToTelegram(message, telegramToken);
                            strategy.setRangeLow(new BigDecimal(0));
                        }

                }
                if (strategy.getRangeHigh().doubleValue() > 0) {
                    if (lastHistoricData.close > strategy.getRangeHigh().doubleValue()) {
                        Map<String, StrikeData> rangeSelected;
                        rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDateStr, strategy.getStrikePriceRangeHigh().intValue(), strategy.getStrikePriceRangeLow().intValue(), candleHourMinStr, strategy.getIndex());
                        Map.Entry<String, StrikeData> finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("CE")).findFirst().get();
                        TradeData tradeData = new TradeData();
                        tradeData.setEntryType("BUY");
                        tradeData.isOrderPlaced = true;
                        tradeData.setQty(strategy.getLotSize());
                      //  tradeData.setSellTime(candleDateTimeFormat.format(lastHistoricData.timeStamp));
                        tradeData.setSellPrice(new BigDecimal(lastHistoricData.close));
                        tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                        List<TradeData> tradeDataList = openTrade.get(finalSelected.getValue().getZerodhaId());
                        if (tradeDataList != null && tradeDataList.size() > 0) {
                            tradeDataList.add(tradeData);
                        } else {
                            tradeDataList = new ArrayList<>();
                            tradeDataList.add(tradeData);
                        }
                        openTrade.put(strategy.getUserId(), tradeDataList);
                        String message = "TradeEngine:"+strategy.getAliasName()+":"+currentHourMinStr+"option orb range low broke, strike selected :" + finalSelected.getValue().getZerodhaSymbol();
                        sendMessage.sendToTelegram(message, telegramToken);
                        strategy.setRangeHigh(new BigDecimal(0));
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception(e);
        }


    }
    //@Scheduled(cron="${tradeEngine.execute.strategy}")
    public void  executeStrategyTest() {
        Date date = new Date();
        MDC.put("run_time",candleDateTimeFormat.format(date));
     //   String currentDateStr = dateFormat.format(date);
         String currentDateStr="2023-02-10";
        LOGGER.info("trade engine: " + currentDateStr);
        executorThread.submit(() -> {
            strategyMap.entrySet().stream().forEach(indexEntry -> {
                String index = indexEntry.getKey();
                Map<String, List<TradeStrategy>> timeStrategyListMap = indexEntry.getValue();
                String stockId = null;
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

                String status = json.getString("status");
                if (!status.equals("error")) {
                    historicalData.parseResponse(json);
                    HistoricalData lastHistoricalData = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                    try {
                        Date openDatetime = candleDateTimeFormat.parse(lastHistoricalData.timeStamp);
                        String openDate = dateFormat.format(openDatetime);
                    //    String currentHourMin=hourMinFormat.format(date);
                           String currentHourMin="09:23";
                        List<TradeStrategy> strategies = timeStrategyListMap.get(currentHourMin + ":00");
                        if (strategies.size() > 0) {
                            strategies.stream().forEach(strategy -> {
                                System.out.println(strategy.getAliasName());
                                Map<Double, Map<String, StrikeData>> rangeStrikes = new HashMap<>();
                                if (strategy.isRangeBreak()) {
                                    Map<String, Double> orbHighLow = new HashMap<>();
                                    if(strategy.getRangeBreakTime().equals(currentHourMin + ":00")) {
                                        orbHighLow(historicalData.dataArrayList, orbHighLow, strategy, currentDateStr);
                                    }
                                    try {
                                        if(dateTimeFormat.parse(openDate+" "+currentHourMin).after(dateTimeFormat.parse(openDate+" "+hourMinFormat.format(strategy.getRangeBreakTime())))) {
                                            if (strategy.getRangeLow().doubleValue() > 0) {
                                             //   if (hourMinFormat.parse(strategy.getRangeBreakTime()).after(hourMinFormat.parse(currentHourMin + ":00"))) {
                                                    if (lastHistoricalData.close < strategy.getRangeLow().doubleValue()) {
                                                        Calendar calendar = Calendar.getInstance();
                                                        SimpleDateFormat sdf1 = new SimpleDateFormat("HH:mm");
                                                        calendar.add(Calendar.MINUTE, -1);
                                                      //  Date currentMinDate = calendar.getTime();
                                                      //  String currentMin = sdf1.format(currentMinDate);
                                                        Map<String, StrikeData> rangeSelected;
                                                        rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(openDate, strategy.getStrikePriceRangeHigh().intValue(), strategy.getStrikePriceRangeLow().intValue(), currentHourMin + ":00", strategy.getIndex());
                                                        Map.Entry<String, StrikeData> finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                                                        TradeData tradeData = new TradeData();
                                                        tradeData.setEntryType("BUY");
                                                        tradeData.isOrderPlaced = true;
                                                        tradeData.setQty(strategy.getLotSize());
                                                        tradeData.setSellTime(candleDateTimeFormat.format(lastHistoricalData.timeStamp));
                                                        tradeData.setSellPrice(new BigDecimal(lastHistoricalData.close));
                                                        tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                                                        List<TradeData> tradeDataList = openTrade.get(finalSelected.getValue().getZerodhaId());
                                                        if (tradeDataList != null && tradeDataList.size() > 0) {
                                                            tradeDataList.add(tradeData);
                                                        } else {
                                                            tradeDataList = new ArrayList<>();
                                                            tradeDataList.add(tradeData);
                                                        }
                                                        openTrade.put(finalSelected.getValue().getZerodhaId(), tradeDataList);
                                                        String message = "TradeEngine:option orb range low broke, strike selected :" + finalSelected.getValue().getZerodhaSymbol();
                                                        sendMessage.sendToTelegram(message, telegramToken);
                                                        strategy.setRangeLow(new BigDecimal(0));
                                                    }
                                              //  }

                                            }
                                            if (strategy.getRangeHigh().doubleValue() > 0) {
                                                if (lastHistoricalData.close > strategy.getRangeHigh().doubleValue()) {
                                                    Calendar calendar = Calendar.getInstance();
                                                    SimpleDateFormat sdf1 = new SimpleDateFormat("HH:mm");
                                                    calendar.add(Calendar.MINUTE, -1);
                                                    Date currentMinDate = calendar.getTime();
                                                    String currentMin = sdf1.format(currentMinDate);
                                                    Map<String, StrikeData> rangeSelected;
                                                    rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(openDate, strategy.getStrikePriceRangeHigh().intValue(), strategy.getStrikePriceRangeLow().intValue(), currentMin + ":00", strategy.getIndex());
                                                    Map.Entry<String, StrikeData> finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                                                    TradeData tradeData = new TradeData();
                                                    tradeData.setEntryType("BUY");
                                                    tradeData.isOrderPlaced = true;
                                                    tradeData.setQty(strategy.getLotSize());
                                                    tradeData.setSellTime(candleDateTimeFormat.format(lastHistoricalData.timeStamp));
                                                    tradeData.setSellPrice(new BigDecimal(lastHistoricalData.close));
                                                    tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                                                    List<TradeData> tradeDataList = openTrade.get(finalSelected.getValue().getZerodhaId());
                                                    if (tradeDataList != null && tradeDataList.size() > 0) {
                                                        tradeDataList.add(tradeData);
                                                    } else {
                                                        tradeDataList = new ArrayList<>();
                                                        tradeDataList.add(tradeData);
                                                    }
                                                    openTrade.put(finalSelected.getValue().getZerodhaId(), tradeDataList);
                                                    String message = "TradeEngine:option orb range low broke, strike selected :" + finalSelected.getValue().getZerodhaSymbol();
                                                    sendMessage.sendToTelegram(message, telegramToken);
                                                    strategy.setRangeHigh(new BigDecimal(0));
                                                }
                                            }
                                        }
                                    } catch (ParseException e) {
                                        throw new RuntimeException(e);
                                    }


                                } else {
                                    if(strategy.getEntryTime().equals(currentHourMin + ":00")) {
                                        rangeStrikes = strikeSelection(currentDateStr, strategy, lastHistoricalData.close,currentDateStr);
                                    }

                                }
                                System.out.println(rangeStrikes);
                                rangeStrikes.entrySet().stream().forEach(rangeStrike -> {
                                    Double strikePrice = rangeStrike.getKey();
                                    Map<String, StrikeData> strikeDataEntry = rangeStrike.getValue();
                                    StrikeData strikeData = strikeDataEntry.entrySet().stream().findFirst().get().getValue();
                                    if (strategy.isSimpleMomentum()) {
                                        if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_UP.getType())) {
                                            BigDecimal triggerPriceTemp = (MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), new BigDecimal(strikePrice)).add(new BigDecimal(strikePrice))).setScale(0, RoundingMode.HALF_UP);
                                            System.out.println(triggerPriceTemp);
                                        }
                                        if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_DOWN.getType())) {
                                            BigDecimal triggerPriceTemp = (new BigDecimal(strikePrice)).subtract(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), new BigDecimal(strikePrice))).setScale(0, RoundingMode.HALF_UP);
                                            System.out.println(triggerPriceTemp);
                                        } else if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_UP.getType())) {
                                            BigDecimal triggerPriceTemp = new BigDecimal(strikePrice).add(strategy.getSimpleMomentumValue()).setScale(0, RoundingMode.HALF_UP);
                                            System.out.println(triggerPriceTemp);
                                        } else if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_DOWN.getType())) {
                                            BigDecimal triggerPriceTemp = new BigDecimal(strikePrice).subtract(strategy.getSimpleMomentumValue()).setScale(0, RoundingMode.HALF_UP);
                                            System.out.println(triggerPriceTemp);

                                        }
                                    }
                                    LOGGER.info(strikeData.getZerodhaSymbol());
                                    OrderParams orderParams = new OrderParams();
                                    orderParams.tradingsymbol = strikeData.getZerodhaSymbol();
                                    orderParams.exchange = "NFO";
                                    orderParams.orderType = "MARKET";
                                    orderParams.product = "NRML";
                                    orderParams.transactionType = strategy.getOrderType();
                                    orderParams.validity = "DAY";
                                    LocalDate localDate = LocalDate.now();
                                    DayOfWeek dow = localDate.getDayOfWeek();
                                    String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                    //TODO get all users subsribed to this.
                                    userList.getUser().stream().filter(
                                            user -> user.getClientId().equals(strategy.getUserId())
                                    ).forEach(user -> {
                                        BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                        Order order = null;
                                        orderParams.quantity = strategy.getLotSize();
                                        TradeData tradeData = new TradeData();
                                        String dataKey = UUID.randomUUID().toString();
                                        tradeData.setDataKey(dataKey);
                                        tradeData.setStockName(strikeData.getZerodhaSymbol());
                                        try {
                                            //   order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                            tradeData.setEntryOrderId(order.orderId);
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setQty(strategy.getLotSize());
                                            tradeData.setEntryType(strategy.getOrderType());
                                            tradeData.setUserId(user.getName());
                                            tradeData.setStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                            //  mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                            List<TradeData> tradeDataList = openTrade.get(strikeData.getZerodhaId());
                                            tradeDataList.add(tradeData);
                                            openTrade.put(strikeData.getZerodhaId(), tradeDataList);
                                            LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                            sendMessage.sendToTelegram("Options traded for user:" + user.getName() + " strike: " + strikeData.getZerodhaSymbol() + ":" + strategy.getAliasName(), telegramToken);
                                        } catch (Exception e) {
                                            tradeData.isErrored = true;
                                            LOGGER.info("Error while placing straddle order: " + e);

                                            sendMessage.sendToTelegram("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(), telegramToken);

                                        }
                                    });

                                });


                            });
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
        sLMonitor.submit(() -> {
            openTrade.entrySet().stream().forEach(openTradeEntry -> {
                List<TradeData> tradeData = openTradeEntry.getValue();
                tradeData.stream().filter(order -> order.isOrderPlaced && order.getEntryOrderId() != null && !order.isExited).forEach(trendTradeData -> {
                    User user = userList.getUser().stream().filter(
                            user1 -> user1.getClientId().equals(trendTradeData.getUserId())
                    ).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);

                    List<Order> orderList = null;
                    try {
                        orderList = brokerWorker.getOrders(user);
                    } catch (KiteException | IOException e) {
                        e.printStackTrace();
                    }
                    orderList.forEach(order -> {
                        if (!trendTradeData.isErrored && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                            if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced) {
                                if ("CANCELLED".equals(order.status)) {
                                    trendTradeData.isSLCancelled = true;

                                    String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                    LOGGER.info(message);
                                    try {
                                        sendMessage.sendToTelegram(message, telegramToken);
                                    } catch (Exception e) {
                                        LOGGER.info("error:" + e);
                                    }


                                }
                            }
                            if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced) {
                                if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {

                                    if ("BUY".equals(order.transactionType)) {
                                        LOGGER.info("buy completed" + trendTradeData.trueDataSymbol + ":" + trendTradeData.getEntryOrderId());
                                        try {
                                            //   LOGGER.info("buy completed");

                                            try {
                                                //BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(50)).setScale(0, RoundingMode.UP);
                                                String message = MessageFormat.format("Option Buy Triggered for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                                LOGGER.info(message);
                                                sendMessage.sendToTelegram(message, telegramToken);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                            BigDecimal triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                            LOGGER.info("buy price:" + trendTradeData.getBuyPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = trendTradeData.getStockName();
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = trendTradeData.getQty();
                                            orderParams.triggerPrice = triggerPriceTemp.doubleValue();

                                            BigDecimal price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                            orderParams.price = price.doubleValue();
                                            orderParams.orderType = "SL";
                                            orderParams.product = "NRML";
                                            orderParams.transactionType = "SELL";
                                            orderParams.validity = "DAY";
                                            com.zerodhatech.models.Order orderd = null;

                                            try {
                                                LOGGER.info("input:" + gson.toJson(orderParams));
                                                //  orderd =brokerWorker.placeOrder(orderParams,user,trendTradeData);
                                                trendTradeData.isSlPlaced = true;
                                                trendTradeData.setSlPrice(triggerPriceTemp);
                                                trendTradeData.setSlOrderId(orderd.orderId);
                                                //      mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                                try {
                                                    LOGGER.info("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL");
                                                    sendMessage.sendToTelegram("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL" + ":" + getAlgoName(), telegramToken);
                                                } catch (Exception e) {
                                                    LOGGER.info("error:" + e);
                                                }
                                            } catch (Exception e) {
                                                // tradeData.isErrored = true;
                                                LOGGER.info("Nifty option order: " + e.getMessage() + ":" + user.getName());
                                                sendMessage.sendToTelegram("Nifty option order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ":" + getAlgoName(), telegramToken);
                                                //e.printStackTrace();
                                            }

                                        } catch (Exception e) {
                                            LOGGER.info("error while placing sl:" + e.getMessage() + trendTradeData.getEntryOrderId() + ":" + trendTradeData.getStockName());
                                        }
                                    }
                                }
                            }
                            if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && "SELL".equals(order.transactionType) && order.orderId.equals(trendTradeData.getSlOrderId()) && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                trendTradeData.isSLHit = true;
                                trendTradeData.isExited = true;
                                trendTradeData.setExitOrderId(order.orderId);
                                trendTradeData.setSellPrice(trendTradeData.getSlPrice());
                                trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, RoundingMode.UP);
                                String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage.toString(), trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                LOGGER.info(message);
                                sendMessage.sendToTelegram(message, telegramToken);
                                //     mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                            }

                        } else if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                            String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + getAlgoName());
                            LOGGER.info(message);
                            trendTradeData.isErrored = true;
                            sendMessage.sendToTelegram(message, telegramToken);
                        }


                    });
                });
            });
        });

        exitThread.submit(() -> {
            openTrade.entrySet().stream().forEach(openTradeEntry -> {
                List<TradeData> tradeData1 = openTradeEntry.getValue();
                tradeData1.stream().filter(order -> order.isOrderPlaced && order.getEntryOrderId() != null && !order.isExited).forEach(trendTradeData -> {
                    User user = userList.getUser().stream().filter(
                            user1 -> user1.getClientId().equals(trendTradeData.getUserId())
                    ).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    try {
                        List<Order> orders = brokerWorker.getOrders(user);
                        List<Position> positions = brokerWorker.getPositions(user);
                        LOGGER.info(new Gson().toJson(positions));
                        if (user.getNiftyBuy935().straddleTradeMap != null) {
                            user.getNiftyBuy935().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null && !map.getValue().isExited).forEach(straddleTradeMap -> {
                                TradeData tradeData = straddleTradeMap.getValue();
                                LOGGER.info("nifty 15:10 exit started 1");
                                if (user.getName().equals(straddleTradeMap.getValue().getUserId())) {
                                    if (user.getNiftyBuy935().getLotConfig() != null && user.getNiftyBuy935().buyConfig.isEnabled()) {
                                        LOGGER.info("nifty 15:10 exit started 2");
                                        LocalDate localDate = LocalDate.now();
                                        DayOfWeek dow = localDate.getDayOfWeek();
                                        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                        String todayCaps = today.toUpperCase();
                                        AtomicInteger qty = new AtomicInteger(0);
                                        user.getNiftyBuy935().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                            if (lotValue.contains(todayCaps)) {
                                                int value = (Integer.parseInt(value1) * 50);
                                                qty.getAndSet(value);
                                                LOGGER.info("nifty 15:10 exit started 3");
                                                orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                                    try {
                                                        OrderParams orderParams = new OrderParams();
                                                        LOGGER.info("nifty 15:10 exit started 4");
                                                        orderParams.quantity = Integer.parseInt(orderr.quantity) - qty.get();
                                                        tradeData.setQty(orderParams.quantity);
                                                        orderParams.tradingsymbol = tradeData.getStockName();
                                                        orderParams.exchange = "NFO";
                                                        orderParams.orderType = orderr.orderType;
                                                        orderParams.validity = "DAY";
                                                        orderParams.price = Double.parseDouble(orderr.price);
                                                        orderParams.triggerPrice = Double.parseDouble(orderr.triggerPrice);
                                                        orderParams.orderType = "SL";
                                                        orderParams.product = "NRML";
                                                        System.out.println("input for bnf sl mod: " + new Gson().toJson(orderParams));
                                                        //  Order order = user.getKiteConnect().modifyOrder(tradeData.getSlOrderId(), orderParams, "regular");
                                                        sendMessage.sendToTelegram("sl buy qty modified for nrml:" + tradeData.getUserId() + ": new sl qty:" + tradeData.getQty() + ":" + getAlgoName(), telegramToken);
                                                    } catch (Exception e) {
                                                        LOGGER.info(e.getMessage());
                                                    }
                                                });
                                                positions.stream().filter(position -> ("NRML".equals(position.product) || "MARGIN".equals(position.product)) && tradeData.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).forEach(position -> {
                                                    //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                                                    OrderParams orderParams = new OrderParams();
                                                    orderParams.tradingsymbol = position.tradingSymbol;
                                                    orderParams.exchange = "NFO";
                                                    orderParams.quantity = qty.get();
                                                    orderParams.orderType = "MARKET";
                                                    orderParams.product = "NRML";
                                                    //orderParams.price=price.doubleValue();
                                                    if (position.netQuantity > 0) {
                                                        orderParams.transactionType = "SELL";
                                                        orderParams.validity = "DAY";
                                                        com.zerodhatech.models.Order orderResponse = null;
                                                        try {
                                                            //   orderResponse = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                            LOGGER.info(new Gson().toJson(orderResponse));
                                                            tradeData.setQty(qty.get());
                                                            //mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                            String message = MessageFormat.format("Closed Intraday Buy Position {0}", orderParams.tradingsymbol);
                                                            LOGGER.info(message);
                                                            TradeData tradeDataBuy = gson.fromJson(gson.toJson(tradeData), TradeData.class);
                                                            String dataKey = UUID.randomUUID().toString();
                                                            tradeDataBuy.setDataKey(dataKey);
                                                            tradeDataBuy.isExited = true;
                                                            tradeDataBuy.setExitOrderId(orderResponse.orderId);
                                                            //  mapTradeDataToSaveOpenTradeDataEntity(tradeDataBuy, true);
                                                            user.getNiftyBuy935().straddleTradeMap.put(tradeDataBuy.getStockName() + "-BUY-INTRADAY", tradeDataBuy);
                                                            sendMessage.sendToTelegram(message + ":" + tradeData.getUserId() + ":" + getAlgoName(), telegramToken);

                                                        } catch (Exception e) {
                                                            LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                                            sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(), telegramToken);
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                    //  }
                                                });
                                            }
                                        });


                                    }

                                }
                            });
                        }
                    } catch (Exception | KiteException e) {
                        e.printStackTrace();
                    }
                });
            });
        });
    }

    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData, boolean orderPlaced) {
        try {
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData, orderPlaced, this.getAlgoName());
            LOGGER.info("sucessfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }

    public void saveTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            Date date = new Date();
            String tradeDate = dateFormat.format(date);
            openTradeDataEntity.setModifyDate(tradeDate);
            openTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }

    public void orbHighLow(List<HistoricalData> orb, Map<String, Double> orbHighLow, TradeStrategy strategy, String currentDate) {
        double low = Double.MAX_VALUE;
        double high = Double.MIN_VALUE;
        String rangeStart = strategy.getRangeStartTime();
        String rangeEnd = strategy.getRangeBreakTime();
        for (HistoricalData candle : orb) {
            try {
                if ((candleDateTimeFormat.parse(candle.timeStamp).after(candleDateTimeFormat.parse(currentDate + "T" +rangeStart+":00")) || candleDateTimeFormat.parse(candle.timeStamp).equals(candleDateTimeFormat.parse(currentDate + "T" +rangeStart+":00"))) &&
                        (candleDateTimeFormat.parse(candle.timeStamp).before(candleDateTimeFormat.parse(currentDate + "T" + rangeEnd+":00"))|| candleDateTimeFormat.parse(candle.timeStamp).equals(candleDateTimeFormat.parse(currentDate + "T" + rangeEnd+":00")))) {
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
        LOGGER.info(strategy.getAliasName()+":Low:"+String.valueOf(low)+":high:"+String.valueOf(high));
        strategy.setRangeLow(new BigDecimal(low));
        strategy.setRangeHigh(new BigDecimal(high));
    }

    public Map<Double, Map<String, StrikeData>> strikeSelection(String currentDate, TradeStrategy strategy, double close,String checkTime) {
        String strikeSelectionType = strategy.getStrikeSelectionType();
       // String checkTime = strategy.getCandleCheckTime();
        String index = strategy.getIndex();
        Map<String, Map<String, StrikeData>> strikeMasterMap = new HashMap<>();
        if (zerodhaTransactionService.expDate.equals(currentDate) && strategy.getTradeValidity().equals(TradeValidity.POSITIONAL.getValidity())) {
            if ("BNF".equals(index)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_NEXT.expiryName);
            } else if ("NF".equals(index)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_NEXT.expiryName);
            } else if ("FN".equals(index)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_NEXT.expiryName);
            }
        } else {
            if ("BNF".equals(index)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_CURRENT.expiryName);
            } else if ("NF".equals(index)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_CURRENT.expiryName);
            } else if ("FN".equals(index)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.FN_CURRENT.expiryName);
            }
        }
        int atmStrike = commonUtil.findATM((int) close);
        if (strikeSelectionType.equals(StrikeSelectionType.ATM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            Map<String, StrikeData> strikeDataMap1 = strikeMasterMap.get(String.valueOf(atmStrike));
            stringMapMap.put(close, strikeDataMap1);
            return stringMapMap;
        }if (strikeSelectionType.contains(StrikeSelectionType.OTM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale=strikeSelectionType.substring(3);
           // String sub=otmStrikeVale.substring(3);
            int otmValue=Integer.valueOf(otmStrikeVale)*100;
            int ceValue=atmStrike+otmValue;
            int peValue=atmStrike-otmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()){
                Map.Entry<String, StrikeData> strikeDataMapCe =strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(),strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()){
                Map.Entry<String, StrikeData> strikeDataMapPe =strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(),strikeDataMapPe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(peValue)), stringMapPE);
            }
            return stringMapMap;
        }
        if (strikeSelectionType.equals(StrikeSelectionType.CLOSE_PREMUIM.getType())) {
            int closePremium = strategy.getStrikeClosestPremium().intValue();
            Map<Double, Map<String, StrikeData>> strikes = mathUtils.getPriceCloseToPremium(currentDate, closePremium, checkTime, index);
            return strikes;
        }
        if (strikeSelectionType.equals(StrikeSelectionType.PRICE_RANGE.getType())) {
            int high = strategy.getStrikePriceRangeHigh().intValue();
            int low = strategy.getStrikePriceRangeLow().intValue();
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            Map<String, StrikeData> rangeSelected;
            rangeSelected = mathUtils.getPriceRangeSortedWithLowRange(currentDate, high, low, checkTime, index);
            rangeSelected.entrySet().stream().forEach(atmNiftyStrikeMap -> {
                String historicPriceURL = "https://api.kite.trade/instruments/historical/" + atmNiftyStrikeMap.getValue().getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
                String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicPriceURL),atmNiftyStrikeMap.getValue().getZerodhaId(),checkTime);
                HistoricalData historicalPriceData = new HistoricalData();
                JSONObject priceJson = new JSONObject(priceResponse);
                String responseStatus = priceJson.getString("status");

                if (!responseStatus.equals("error")) {
                    historicalPriceData.parseResponse(priceJson);
                    historicalPriceData.dataArrayList.forEach(historicalDataPrice -> {

                        Date priceDatetime = null;
                        try {
                            priceDatetime = candleDateTimeFormat.parse(historicalDataPrice.timeStamp);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                        String priceDate = dateFormat.format(priceDatetime);
                        if (candleDateTimeFormat.format(priceDatetime).equals(priceDate + "T" + checkTime)) {
                            Map<String, StrikeData> strikeDataMap = new HashMap<>();
                            strikeDataMap.put(atmNiftyStrikeMap.getKey(), atmNiftyStrikeMap.getValue());
                            stringMapMap.put(historicalDataPrice.close, strikeDataMap);
                        }
                    });
                }
            });
            return stringMapMap;
        }
        return null;
    }
}
