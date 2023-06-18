package com.sakthi.trade;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.TradeValidity;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.repo.TradeStrategyRepo;
import com.sakthi.trade.repo.UserSubscriptionRepo;
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
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class TradeEngine {
    public static final Logger LOGGER = LoggerFactory.getLogger(TradeEngine.class.getName());
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    @Autowired
    UserSubscriptionRepo userSubscriptionRepo;
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
    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;
    @Autowired
    TradeSedaQueue tradeSedaQueue;

    public static boolean isTimeGreaterThan(LocalTime time, LocalTime compareTo) {
        return time.isAfter(compareTo) || time.equals(compareTo);
    }

    public static void main(String[] args) {
        LocalTime currentTime = LocalTime.now();
        LocalTime cncSLTime = LocalTime.of(21, 27);
        System.out.println(isTimeGreaterThan(currentTime, cncSLTime));
    }

    public String getAlgoName() {
        return "TRADE_ENGINE";
    }

    @Scheduled(cron = "${tradeEngine.load.strategy}")
    public void loadStrategy() {
        //  strategyMap = new ConcurrentHashMap<>();
        Date date = new Date();
        strategyMap=new HashMap<>();
        List<TradeStrategy> tradeStrategyList = tradeStrategyRepo.getActiveActiveStrategy();
        // System.out.println(new Gson().toJson(tradeStrategyList));
        tradeStrategyList.forEach(strategy -> {
            List<UserSubscription> userSubscriptionList=userSubscriptionRepo.getUserSubs(strategy.getTradeStrategyKey());
            if(userSubscriptionList!=null && !userSubscriptionList.isEmpty()){
                System.out.println(gson.toJson(userSubscriptionList));
                UserSubscriptions userSubscriptions=new UserSubscriptions();
                userSubscriptions.setUserSubscriptionList(userSubscriptionList);
                strategy.setUserSubscriptions(userSubscriptions);
            }
            String index = strategy.getIndex();
            AtomicInteger lotA = new AtomicInteger(0);
            config.forEach(lot -> {
                // System.out.println(lot);
                String[] lotSplit = lot.split("-");
                if (lotSplit[0].equals(index)) {
                    lotA.getAndSet(Integer.parseInt(lotSplit[1]));
                }

            });
            strategy.setLotSize(strategy.getLotSize() * lotA.get());
            if (strategy.getTradeDays().contains(dayFormat.format(date).toUpperCase()) || Objects.equals(strategy.getTradeDays(), "All")) {
                if (strategy.isRangeBreak()) {
                    //TODO: this is outdated
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
        strategyMap.entrySet().forEach(stringMapEntry -> {
            Map<String, List<TradeStrategy>> stringTradeStrategyMap = stringMapEntry.getValue();
            stringTradeStrategyMap.entrySet().forEach(tradeStrategyMap -> {
                List<TradeStrategy> list = tradeStrategyMap.getValue();
                list.forEach(strategy -> {
                    tradeSedaQueue.sendTelemgramSeda(stringMapEntry.getKey() + ":" + tradeStrategyMap.getKey() + ":" + strategy.getUserId() + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getLotSize());
                });
            });
        });
        System.out.println("strategy config:"+new Gson().toJson(strategyMap));
    }

    @Scheduled(cron = "${tradeEngine.load.open.data}")
    public void loadNrmlPositions() {
        Iterable<OpenTradeDataEntity> openTradeDataEntities1 = openTradeDataRepo.findAll();
        List<OpenTradeDataEntity> openList = new ArrayList<>();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
                    openList.add(openTradeDataEntity);
                }
            }
        });
        if (openList.size() > 0 && openTrade.size() == 0) {
            openList.forEach(openTradeDataEntity -> {
                //  if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    List<Position> positions = brokerWorker.getRateLimitedPositions(user);

                    positions.stream().filter(position -> "NRML".equals(position.product) && openTradeDataEntity.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).findFirst().ifPresent(position -> {
                        int positionQty = Math.abs(position.netQuantity);
                        if (positionQty != openTradeDataEntity.getQty()) {
                            //   openTradeDataEntity.setQty(positionQty);
                            tradeSedaQueue.sendTelemgramSeda("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty." + ":" + getAlgoName(),user.telegramBot.getGroupId());
                            LOGGER.info("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.",user.telegramBot.getGroupId());

                        }
                        openTradeDataEntity.isSlPlaced = false;
                        openTradeDataEntity.setSlOrderId(null);

                        TradeData tradeData = tradeDataMapper.mapTradeDataEntityToTradeData(openTradeDataEntity);
                        List<TradeData> tradeDataList = openTrade.get(user.getName());
                        if (tradeDataList == null) {
                            tradeDataList = new ArrayList<>();
                        }
                        tradeDataList.add(tradeData);
                        openTrade.put(user.getName(), tradeDataList);
                        tradeSedaQueue.sendTelemgramSeda("Open Position: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName(),user.telegramBot.getGroupId());
                    });
                } else {
                    String openStr = gson.toJson(openTradeDataEntity);
                    OpenTradeDataBackupEntity openTradeDataBackupEntity = gson.fromJson(openStr, OpenTradeDataBackupEntity.class);
                    openTradeDataBackupRepo.save(openTradeDataBackupEntity);
                    openTradeDataRepo.deleteById(openTradeDataEntity.getDataKey());
                }
                // }
            });
        }
        System.out.println(gson.toJson(openTrade));
    }

    @Scheduled(cron = "${tradeEngine.execute.strategy}")
    public void executeStrategy() {
        Date date = new Date();
        //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = dateFormat.format(date);
        //     String currentDateStr="2023-06-16";

        //tradeSedaQueue.sendTelemgramSeda("started trade engine execution");
        Calendar candleCalenderMin = Calendar.getInstance();
        Calendar calendarCurrentMin = Calendar.getInstance();
        candleCalenderMin.add(Calendar.MINUTE, -1);
        Date currentMinDate = calendarCurrentMin.getTime();
        Date candleCurrentMinDate = candleCalenderMin.getTime();
        String candleHourMinStr = hourMinFormat.format(candleCurrentMinDate);

        //   String candleHourMinStr="09:34";
        // System.out.println(candleHourMinStr);
        String currentHourMinStr = hourMinFormat.format(currentMinDate);
         //   String currentHourMinStr="09:35";
        // System.out.println(currentHourMinStr);

        executorThread.submit(() -> {
            //ORB range break code starts
            rangeStrategyMap.forEach((index, strategyList) -> {
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
                        Optional.of(optionalHistoricalLatestData).ifPresent(lastHistoricalDataOp -> {

                            HistoricalData lastHistoricalData = lastHistoricalDataOp.get();
                            try {
                                orbRangeBreak(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr + ":00", lastHistoricalData);
                            } catch (Exception e) {
                                LOGGER.error("error" + e.getMessage());
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
                        Optional.of(optionalHistoricalLatestData).ifPresent(lastHistoricalDataOp -> {
                            HistoricalData lastHistoricalData = lastHistoricalDataOp.get();
                            //      LOGGER.info("last candle time and close: " + lastHistoricalData.timeStamp + ":" + lastHistoricalData.close);
                            List<TradeStrategy> strategies = timeStrategyListMap.get(currentHourMinStr);

                            if (strategies.size() > 0) {
                                LOGGER.info("trade engine: " + gson.toJson(strategies));
                                strategies.forEach(strategy -> {
                                    // executorThreadStrategy.submit(() -> {
                                    LOGGER.info("strategy name:" + strategy.getTradeStrategyKey());
                                    Map<Double, Map<String, StrikeData>> rangeStrikes = new HashMap<>();

                                    if (strategy.getEntryTime().equals(currentHourMinStr)) {
                                        rangeStrikes = strikeSelection(currentDateStr, strategy, lastHistoricalData.close, candleHourMinStr + ":00");
                                    }


                                    LOGGER.info(strategy.getTradeStrategyKey() + ":" + rangeStrikes);
                                    rangeStrikes.forEach((indexStrikePrice, strikeDataEntry) -> {
                                        strikeDataEntry.entrySet().stream().forEach(strikeDataEntry1 -> {
                                            StrikeData strikeData = strikeDataEntry1.getValue();
                                            OrderParams orderParams = new OrderParams();
                                            AtomicDouble triggerPriceTemp = new AtomicDouble();
                                            double strikePrice = indexStrikePrice.doubleValue();
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
                                                orderParams.orderType = "MARKET";
                                            }
                                            LOGGER.info(strategy.getTradeStrategyKey() + "placing order for:" + strikeData.getZerodhaSymbol());

                                            orderParams.tradingsymbol = strikeData.getZerodhaSymbol();
                                            orderParams.exchange = "NFO";
                                            if ("MIS".equals(strategy.getTradeValidity())) {
                                                orderParams.product = "MIS";
                                            } else {
                                                orderParams.product = "NRML";
                                            }
                                            orderParams.transactionType = strategy.getOrderType();
                                            orderParams.validity = "DAY";
                                            LocalDate localDate = LocalDate.now();
                                            DayOfWeek dow = localDate.getDayOfWeek();
                                            String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                            //TODO get all users subsribed to this.
                                          //  String[] userIdAr = strategy.getUserId().split(",");
                                          //  Arrays.stream(userIdAr).forEach(userId -> {
                                            strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach( userSubscription -> {
                                                userList.getUser().stream().filter(
                                                        user -> user.getName().equals(userSubscription.getUserId())
                                                ).forEach(user -> {
                                                    TradeData tradeData = new TradeData();
                                                    if ("SELL".equals(strategy.getOrderType())) {
                                                        tradeData.setSellPrice(new BigDecimal(triggerPriceTemp.get()));
                                                    } else {
                                                        tradeData.setBuyPrice(new BigDecimal(triggerPriceTemp.get()));
                                                    }
                                                    BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                                    Order order = null;
                                                    int lot = strategy.getLotSize() * userSubscription.getLotSize();
                                                    orderParams.quantity = lot;

                                                    String dataKey = UUID.randomUUID().toString();
                                                    tradeData.setDataKey(dataKey);

                                                    tradeData.setStockName(strikeData.getZerodhaSymbol());
                                                    try {
                                                        //TODO set sl price, entry price, exit date
                                                        tradeData.setQty(lot);
                                                        tradeData.setEntryType(strategy.getOrderType());
                                                        tradeData.setUserId(user.getName());
                                                        tradeData.setTradeDate(currentDateStr);
                                                        tradeData.setStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                                        tradeData.setTradeStrategy(strategy);
                                                        order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                        if (order != null) tradeData.setEntryOrderId(order.orderId);
                                                        //  tradeData.isOrderPlaced = true;

                                                        mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                                        List<TradeData> tradeDataList = openTrade.get(user.getName());
                                                        if (tradeDataList == null) {
                                                            tradeDataList = new ArrayList<>();
                                                        }
                                                        tradeDataList.add(tradeData);
                                                        openTrade.put(user.getName(), tradeDataList);
                                                        LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                                        tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: "
                                                                + strikeData.getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                    } catch (Exception e) {
                                                        List<TradeData> tradeDataList = openTrade.get(user.getName());
                                                        if (tradeDataList == null) {
                                                            tradeDataList = new ArrayList<>();
                                                        }
                                                        tradeDataList.add(tradeData);
                                                        openTrade.put(user.getName(), tradeDataList);
                                                        tradeData.isErrored = true;
                                                        LOGGER.info("Error while placing straddle order: " + e);
                                                        e.printStackTrace();
                                                        tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());

                                                    } catch (KiteException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                });
                                            });
                                        });

                                    });
                                      });
                              //  });
                            }

                        });
                    }
                });
            });
        });
        slCode(currentDateStr, currentHourMinStr, candleHourMinStr);
        exitCode(currentDateStr, currentHourMinStr);
    }

    public void exitCode(String currentDateStr, String currentHourMinStr) {
        exitThread.submit(() -> {
            openTrade.entrySet().stream().forEach(userTradeData -> {
                String userId = userTradeData.getKey();
                List<TradeData> tradeDataList = userTradeData.getValue();
                User user = userList.getUser().stream().filter(
                        user1 -> user1.getName().equals(userId)
                ).findFirst().get();
                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                try {
                    List<Order> orders = brokerWorker.getOrders(user);
                    List<Position> positions = brokerWorker.getPositions(user);
                    tradeDataList.forEach(tradeData -> {
                        TradeStrategy strategy = tradeData.getTradeStrategy();
                        try {
                            if ((tradeData.getTradeDate().equals(currentDateStr) && strategy != null && strategy.getTradeValidity().equals("MIS")) ||
                                    (dateFormat.parse(tradeData.getTradeDate()).before(dateFormat.parse(currentDateStr)) && strategy != null && strategy.getTradeValidity().equals("BTST"))) {
                                if (currentHourMinStr.equals(strategy.getExitTime())) {
                                    orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                        try {
                                            Order order = brokerWorker.cancelOrder(orderr.orderId, user);
                                            tradeSedaQueue.sendTelemgramSeda("sl buy qty modified for nrml:" + tradeData.getUserId() + ": new sl qty:" + tradeData.getQty() + ":" + getAlgoName(),user.telegramBot.getGroupId());
                                        } catch (Exception e) {
                                            LOGGER.info(e.getMessage());
                                        } catch (KiteException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                    positions.stream().filter(position -> (tradeData.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)
                                            && (("MIS".equals(position.product) && strategy.getTradeValidity().equals("MIS")) || ("NRML".equals(position.product) && strategy.getTradeValidity().equals("BTST"))))).forEach(position -> {
                                        OrderParams orderParams = new OrderParams();
                                        orderParams.tradingsymbol = position.tradingSymbol;
                                        orderParams.exchange = "NFO";
                                        orderParams.quantity = tradeData.getQty();
                                        orderParams.orderType = "MARKET";
                                        if ("MIS".equals(strategy.getTradeValidity())) {
                                            orderParams.product = "MIS";
                                        } else {
                                            orderParams.product = "NRML";
                                        }
                                        if (position.netQuantity > 0 && "BUY".equals(tradeData.getEntryType())) {
                                            orderParams.transactionType = "SELL";
                                        } else {
                                            orderParams.transactionType = "BUY";
                                        }
                                        orderParams.validity = "DAY";
                                        com.zerodhatech.models.Order orderResponse = null;
                                        try {
                                            orderResponse = brokerWorker.placeOrder(orderParams, user, tradeData);
                                            LOGGER.info(new Gson().toJson(orderResponse));
                                            String message = MessageFormat.format("Closed Intraday Buy Position {0}", orderParams.tradingsymbol + ":" + strategy.getTradeStrategyKey());
                                            LOGGER.info(message);
                                            tradeData.isExited = true;
                                            mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                            tradeSedaQueue.sendTelemgramSeda(message + ":" + tradeData.getUserId() + ":" + getAlgoName(),user.telegramBot.getGroupId());

                                        } catch (Exception e) {
                                            LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                            tradeSedaQueue.sendTelemgramSeda("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(),user.telegramBot.getGroupId());
                                            e.printStackTrace();
                                        } catch (KiteException e) {
                                            throw new RuntimeException(e);
                                        }

                                    });
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

    public void slCode(String currentDateStr, String currentHourMinStr, String candleHourMinStr) {
        sLMonitor.submit(() -> {
            //  Type type = new TypeToken<List<TradeData>>(){}.getType();
            // String data="[{\"stockName\":\"NIFTY2331617500PE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"959a60d1-cb97-4006-850e-50653f62008a\",\"buyPrice\":168,\"buyTradedPrice\":168,\"stockId\":14236418,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":true,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000131603\",\"slOrderId\":\"230310000161496\",\"slPrice\":138,\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"modifyDate\":\"2023-03-10\",\"tradeStrategy\":{\"tradeStrategyKey\":\"NFBCD15117btst\",\"index\":\"NF\",\"entryTime\":\"09:17\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:23\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":true,\"simpleMomentumType\":\"POINTS_UP\",\"simpleMomentumValue\":10.00,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":1,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09:17 points 10 sl 30 CNC\"}},{\"stockName\":\"NIFTY2331617350CE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"f11cade6-bea7-4a66-873f-58dfc63f568f\",\"buyPrice\":165,\"stockId\":14234626,\"userId\":\"LTK728\",\"isOrderPlaced\":false,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000131648\",\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"NFBCD15117btst\",\"index\":\"NF\",\"entryTime\":\"09:17\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:23\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":true,\"simpleMomentumType\":\"POINTS_UP\",\"simpleMomentumValue\":10.00,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":1,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09:17 points 10 sl 30 CNC\"}},{\"stockName\":\"NIFTY2331617500PE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"68f5c02b-9913-418b-a5d0-255201e5cf06\",\"sellPrice\":17372.95000000000072759576141834259033203125,\"stockId\":14236418,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":true,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"NORF23\",\"index\":\"NF\",\"entryTime\":\"09:23\",\"tradeValidity\":\"MIS\",\"exitTime\":\"15:10\",\"intradayExitTime\":\"15:10\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":true,\"strategyEnabled\":true,\"rangeBreakTime\":\"09:23\",\"rangeStartTime\":\"09:16\",\"rangeBreakSide\":\"High,Low\",\"rangeBreakInstrument\":\"INDEX\",\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":0,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09-16 to 09:23 ORB sl 30\",\"rangeLow\":0,\"rangeHigh\":17413.9000000000014551915228366851806640625}},{\"stockName\":\"BANKNIFTY2331640700CE\",\"tradeDate\":\"2023-03-10\",\"qty\":25,\"dataKey\":\"22c3f32c-503c-494c-aff4-55fbe5343bfa\",\"sellPrice\":222.2,\"sellTradedPrice\":222.2,\"stockId\":14149378,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000542149\",\"entryType\":\"SELL\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"BNFABOST135\",\"index\":\"BNF\",\"entryTime\":\"09:35\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:34\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"OTM2\",\"orderType\":\"SELL\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"MARKET\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":0,\"lotSize\":25,\"positionalLotSize\":0,\"slType\":\"PERCENT\",\"slValue\":50.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"bnf stbt sell 9:35 CNC sl 50 OTM2\"}},{\"stockName\":\"BANKNIFTY2331640300PE\",\"tradeDate\":\"2023-03-10\",\"qty\":25,\"dataKey\":\"5b0da0b2-5240-461f-8b72-a02b2867a969\",\"sellPrice\":252.5,\"sellTradedPrice\":252.5,\"stockId\":14147586,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000542164\",\"entryType\":\"SELL\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"BNFABOST135\",\"index\":\"BNF\",\"entryTime\":\"09:35\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:34\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"OTM2\",\"orderType\":\"SELL\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"MARKET\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":0,\"lotSize\":25,\"positionalLotSize\":0,\"slType\":\"PERCENT\",\"slValue\":50.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"bnf stbt sell 9:35 CNC sl 50 OTM2\"}}]";
            //List<TradeData> prodList = gson.fromJson(data, type);
            //  openTrade.put("LTK728",prodList);
            openTrade.entrySet().stream().forEach(userTradeData -> {
                String userId = userTradeData.getKey();
                //   LOGGER.info("Insde SL:"+userId);

                List<TradeData> tradeData = userTradeData.getValue();
                User user = userList.getUser().stream().filter(
                        user1 -> user1.getName().equals(userId)
                ).findFirst().get();

                BrokerWorker brokerWorker = workerFactory.getWorker(user);

                try {
                    List<Order> orderList = brokerWorker.getOrders(user);
                    //     LOGGER.info("OrderList:"+gson.toJson(orderList));
                    //  LOGGER.info("TradeList:"+gson.toJson(tradeData));
                    tradeData.stream().filter(order -> order.getEntryOrderId() != null && !order.isExited).forEach(trendTradeData -> {
                        TradeStrategy strategy = trendTradeData.getTradeStrategy();
                        orderList.forEach(order -> {
                            if (!trendTradeData.isErrored && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced) {
                                    if ("CANCELLED".equals(order.status)) {
                                        trendTradeData.isSLCancelled = true;

                                        String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":"  + strategy.getTradeStrategyKey());
                                        LOGGER.info(message);
                                        try {
                                            tradeSedaQueue.sendTelemgramSeda(message,user.telegramBot.getGroupId());
                                        } catch (Exception e) {
                                            LOGGER.info("error:" + e);
                                        }
                                    }
                                }
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && !trendTradeData.isOrderPlaced) {
                                    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                        trendTradeData.isOrderPlaced = true;
                                        try {
                                            LOGGER.info("order executed" + trendTradeData.getStockName() + ":" + trendTradeData.getEntryOrderId());
                                            if ("BUY".equals(trendTradeData.getEntryType())) {
                                                if (trendTradeData.getBuyPrice() == null) {
                                                    trendTradeData.setBuyPrice(new BigDecimal(order.averagePrice));
                                                }
                                                trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                            } else {
                                                if (trendTradeData.getSellPrice() == null) {
                                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                                }
                                                trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                            }
                                            //BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(50)).setScale(0, RoundingMode.UP);
                                            String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":"  + strategy.getTradeStrategyKey());
                                            LOGGER.info(message);
                                            tradeSedaQueue.sendTelemgramSeda(message,user.telegramBot.getGroupId());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                   
                                }
                                if (trendTradeData.isOrderPlaced && trendTradeData.getEntryOrderId().equals(order.orderId)) {
                                    if (!trendTradeData.isSlPlaced) {
                                        //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                        try {
                                            // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
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
                                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                } catch (Exception e) {
                                                    LOGGER.info("error:" + e);
                                                }
                                            } catch (Exception e) {
                                                // tradeData.isErrored = true;
                                                LOGGER.info("error while placing sl order: " + e.getMessage() + ":" + user.getName());
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                //e.printStackTrace();
                                            } catch (KiteException e) {
                                                throw new RuntimeException(e);
                                            }

                                        } catch (Exception e) {
                                            LOGGER.info("error while placing sl:" + e.getMessage() + trendTradeData.getEntryOrderId() + ":" + trendTradeData.getStockName());
                                        }
                                    }/*else {
                                        //TODO: add time check
                                        tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + getAlgoName()+":"+strategy.getTradeStrategyKey());
                                    }*/

                                }
                                if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && order.orderId.equals(trendTradeData.getSlOrderId()) && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                    trendTradeData.isSLHit = true;
                                    trendTradeData.isExited = true;
                                    trendTradeData.setExitOrderId(order.orderId);
                                    trendTradeData.setSellPrice(trendTradeData.getSlPrice());
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                    BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, RoundingMode.UP);
                                    String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage.toString(), trendTradeData.getStockName() + ":" + user.getName() + ":"  + strategy.getTradeStrategyKey() + ":" + strategy.getTradeStrategyKey());
                                    LOGGER.info(message);
                                    tradeSedaQueue.sendTelemgramSeda(message,user.telegramBot.getGroupId());
                                    mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                    if (strategy.isReentry()) {
                                        long tradeCount = userTradeData.getValue().stream().filter(tradeDataTemp ->
                                                trendTradeData.getStockName().equals(tradeDataTemp.getStockName())
                                                        && tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey()) && tradeDataTemp.isSLHit).count();
                                        if (tradeCount < strategy.getReentryCount().intValue() + 1) {
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = trendTradeData.getStockName();
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = trendTradeData.getQty();
                                            orderParams.orderType = "SL-M";
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
                                                trendTradeData.isSlPlaced = true;
                                                trendTradeData.setSlOrderId(orderd.orderId);
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                try {
                                                    LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                } catch (Exception e) {
                                                    LOGGER.info("error:" + e);
                                                }
                                            } catch (Exception e) {
                                                // tradeData.isErrored = true;
                                                LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                //e.printStackTrace();
                                            } catch (KiteException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                    if (strategy.isTrailToCost()) {
                                        userTradeData.getValue().stream().filter(tradeDataTemp -> tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey())
                                                && !tradeDataTemp.isSLHit).forEach(tradeDataMod -> {
                                            try {
                                                Order order1=brokerWorker.getOrder(user,tradeDataMod.getSlOrderId());
                                                OrderParams orderParams=new OrderParams();
                                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                                    orderParams.triggerPrice=trendTradeData.getBuyPrice().doubleValue();
                                                    orderParams.price=trendTradeData.getBuyPrice().doubleValue();
                                                }else {
                                                    orderParams.triggerPrice=trendTradeData.getSellPrice().doubleValue();
                                                    orderParams.price=trendTradeData.getSellPrice().doubleValue();
                                                }
                                                brokerWorker.modifyOrder(order1.orderId,orderParams,user,trendTradeData);
                                                tradeSedaQueue.sendTelemgramSeda("executed trail sl " + trendTradeData.getStockName() + ":" + user.getName()  + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                            } catch (IOException e) {
                                                tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName()  + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                throw new RuntimeException(e);
                                            } catch (KiteException e) {
                                                tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                                throw new RuntimeException(e);
                                            }
                                        });
                                    }
                                }

                            } else if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                                String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":"  + strategy.getTradeStrategyKey());
                                LOGGER.info(message);
                                trendTradeData.isErrored = true;
                                tradeSedaQueue.sendTelemgramSeda(message,user.telegramBot.getGroupId());
                            }


                        });
                        try {
                            LocalTime currentTime = LocalTime.now();
                            LocalTime cncSLTime = LocalTime.of(9, 16);
                            if (dateFormat.parse(trendTradeData.getTradeDate()).before(dateFormat.parse(currentDateStr)) && strategy != null && strategy.getTradeValidity().equals("BTST")
                                    && isTimeGreaterThan(currentTime, cncSLTime)) {
                                if (!trendTradeData.isSlPlaced && trendTradeData.isOrderPlaced) {
                                    //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                    try {
                                        // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                        double strikePrice = 0;
                                        try {
                                            String historicURL1 = "https://api.kite.trade/instruments/historical/" + trendTradeData.getStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                                            String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL1), String.valueOf(trendTradeData.getStockId()), currentHourMinStr);
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
                                        } catch (Exception e) {
                                            LOGGER.error("error while getting current price of script:" + trendTradeData.getStockName());
                                        }
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
                                        if ("BUY".equals(trendTradeData.getEntryType())) {
                                            if ("POINTS".equals(slType)) {
                                                triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(slValue)).setScale(0, RoundingMode.HALF_UP);
                                            } else {
                                                BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, trendTradeData.getBuyPrice());
                                                triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(slPoints)).setScale(0, RoundingMode.HALF_UP);
                                            }
                                            price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                            orderParams.transactionType = "SELL";
                                            LOGGER.info("buy price:" + trendTradeData.getBuyPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                            if (strikePrice > 0 && strikePrice < triggerPriceTemp.doubleValue()) {
                                                orderParams.orderType = "MARKET";
                                            }
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
                                            if (strikePrice > 0 && triggerPriceTemp.doubleValue() < strikePrice) {
                                                orderParams.orderType = "MARKET";
                                            }
                                        }
                                        orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                        orderParams.price = price.doubleValue();
                                        trendTradeData.setSlPrice(triggerPriceTemp);
                                        try {
                                            LOGGER.info("input:" + gson.toJson(orderParams));
                                            orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                            trendTradeData.isSlPlaced = true;
                                            trendTradeData.setSlOrderId(orderd.orderId);
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                            try {
                                                LOGGER.info("placing sl for option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL");
                                                tradeSedaQueue.sendTelemgramSeda("option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL" + ":"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                            } catch (Exception e) {
                                                LOGGER.info("error:" + e);
                                            }
                                        } catch (Exception e) {
                                            // tradeData.isErrored = true;
                                            LOGGER.info("error while placing sl option order: " + e.getMessage() + ":" + user.getName());
                                            tradeSedaQueue.sendTelemgramSeda("option order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status:" + e.getMessage() + ": algo:"  + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                            //e.printStackTrace();
                                        } catch (KiteException e) {
                                            throw new RuntimeException(e);
                                        }

                                    } catch (Exception e) {
                                        LOGGER.info("error while placing sl:" + e.getMessage() + trendTradeData.getEntryOrderId() + ":" + trendTradeData.getStockName());
                                    }
                                    //  }

                                }
                            }
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } catch (KiteException e) {
                    throw new RuntimeException(e);
                }
            });

        });
    }

    public void orbRangeBreak(TradeStrategy strategy, HistoricalData historicalData, String currentDateStr, String currentHourMinStr, String candleHourMinStr, HistoricalData lastHistoricData) throws Exception {

        Map<String, Double> orbHighLow = new HashMap<>();
        if ((strategy.getRangeBreakTime()).equals(currentHourMinStr)) {
            orbHighLow(historicalData.dataArrayList, orbHighLow, strategy, currentDateStr);

        }
        try {
            if (dateTimeFormat.parse(currentDateStr + " " + currentHourMinStr).after(dateTimeFormat.parse(currentDateStr + " " + strategy.getRangeBreakTime()))) {

                if (strategy.getRangeLow().doubleValue() > 0) {
                    if (lastHistoricData.close < strategy.getRangeLow().doubleValue()) {
                        Map<String, StrikeData> rangeSelected;
                        rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDateStr, strategy.getStrikePriceRangeHigh().intValue(), strategy.getStrikePriceRangeLow().intValue(), candleHourMinStr, strategy.getIndex());
                        Map.Entry<String, StrikeData> finalSelected;
                        if ("SELL".equals(strategy.getOrderType())) {
                            finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("CE")).findFirst().get();
                        } else {
                            finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                        }
                        TradeData tradeData = new TradeData();

                        //    tradeData.setSellTime(candleDateTimeFormat.format(lastHistoricData.timeStamp));
                        // tradeData.setSellPrice(new BigDecimal(lastHistoricData.close));
                        tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                        OrderParams orderParams = new OrderParams();
                        orderParams.tradingsymbol = finalSelected.getValue().getZerodhaSymbol();
                        orderParams.exchange = "NFO";
                        if ("MIS".equals(strategy.getTradeValidity())) {
                            orderParams.product = "MIS";
                        } else {
                            orderParams.product = "NRML";
                        }
                        orderParams.transactionType = strategy.getOrderType();
                        orderParams.validity = "DAY";
                        orderParams.orderType = "MARKET";
                        LocalDate localDate = LocalDate.now();
                        DayOfWeek dow = localDate.getDayOfWeek();
                        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                        //TODO get all users subsribed to this.
                        String[] userIdAr = strategy.getUserId().split(",");
                        strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach( userSubscription -> {
                            userList.getUser().stream().filter(
                                    user -> user.getName().equals(userSubscription.getUserId())
                            ).forEach(user -> {
                                BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                Order order = null;
                                int lot = strategy.getLotSize() * userSubscription.getLotSize();
                                orderParams.quantity = lot;
                                String dataKey = UUID.randomUUID().toString();
                                tradeData.setDataKey(dataKey);

                                tradeData.setStockName(finalSelected.getValue().getZerodhaSymbol());
                                try {
                                    //TODO set sl price, entry price, exit date
                                    tradeData.setQty(lot);
                                    tradeData.setEntryType(strategy.getOrderType());
                                    tradeData.setUserId(user.getName());
                                    tradeData.setTradeDate(currentDateStr);
                                    tradeData.setStockId(Integer.valueOf(finalSelected.getValue().getZerodhaId()));
                                    tradeData.setTradeStrategy(strategy);
                                    order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                    if (order != null) tradeData.setEntryOrderId(order.orderId);
                                    //  tradeData.isOrderPlaced = true;

                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                    List<TradeData> tradeDataList = openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    openTrade.put(user.getName(), tradeDataList);
                                    LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                    tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: " + finalSelected.getValue().getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                } catch (Exception e) {
                                    List<TradeData> tradeDataList = openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    openTrade.put(user.getName(), tradeDataList);
                                    tradeData.isErrored = true;
                                    LOGGER.info("Error while placing straddle order: " + e);
                                    e.printStackTrace();
                                    tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + finalSelected.getValue().getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(),user.telegramBot.getGroupId());

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
                        rangeSelected = mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDateStr, strategy.getStrikePriceRangeHigh().intValue(), strategy.getStrikePriceRangeLow().intValue(), candleHourMinStr, strategy.getIndex());
                        Map.Entry<String, StrikeData> finalSelected;
                        if ("SELL".equals(strategy.getOrderType())) {
                            finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                        } else {
                            finalSelected = rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("CE")).findFirst().get();
                        }
                        TradeData tradeData = new TradeData();
                        tradeData.setQty(strategy.getLotSize());
                        //  tradeData.setSellTime(candleDateTimeFormat.format(lastHistoricData.timeStamp));
                        //  tradeData.setSellPrice(new BigDecimal(lastHistoricData.close));
                        tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                        tradeData.setTradeStrategy(strategy);
                        OrderParams orderParams = new OrderParams();
                        orderParams.tradingsymbol = finalSelected.getValue().getZerodhaSymbol();
                        orderParams.exchange = "NFO";
                        orderParams.orderType = "MARKET";
                        if ("MIS".equals(strategy.getTradeValidity())) {
                            orderParams.product = "MIS";
                        } else {
                            orderParams.product = "NRML";
                        }
                        orderParams.transactionType = strategy.getOrderType();
                        orderParams.validity = "DAY";
                        LocalDate localDate = LocalDate.now();
                        DayOfWeek dow = localDate.getDayOfWeek();
                        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                        //TODO get all users subsribed to this.
                        strategy.getUserSubscriptions().getUserSubscriptionList().stream().forEach( userSubscription -> {
                            userList.getUser().stream().filter(
                                    user -> user.getName().equals(userSubscription.getUserId())
                            ).forEach(user -> {
                                int lot = strategy.getLotSize() * Integer.valueOf(userSubscription.getLotSize());
                                orderParams.quantity = lot;
                                BrokerWorker brokerWorker = workerFactory.getWorker(user);

                                Order order = null;

                                String dataKey = UUID.randomUUID().toString();
                                tradeData.setDataKey(dataKey);

                                tradeData.setStockName(finalSelected.getValue().getZerodhaSymbol());
                                try {
                                    //TODO set sl price, entry price, exit date
                                    tradeData.setQty(lot);
                                    tradeData.setEntryType(strategy.getOrderType());
                                    tradeData.setUserId(user.getName());
                                    tradeData.setTradeDate(currentDateStr);
                                    tradeData.setStockId(Integer.valueOf(finalSelected.getValue().getZerodhaId()));
                                    order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                    if (order != null) tradeData.setEntryOrderId(order.orderId);
                                    //  tradeData.isOrderPlaced = true;

                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                    List<TradeData> tradeDataList = openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    openTrade.put(user.getName(), tradeDataList);
                                    LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                    tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: " + finalSelected.getValue().getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(),user.telegramBot.getGroupId());
                                } catch (Exception e) {
                                    List<TradeData> tradeDataList = openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    openTrade.put(user.getName(), tradeDataList);
                                    tradeData.isErrored = true;
                                    LOGGER.info("Error while placing straddle order: " + e);
                                    e.printStackTrace();
                                    tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + finalSelected.getValue().getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(),user.telegramBot.getGroupId());

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
            throw new Exception(e);
        }


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
        LOGGER.info(strategy.getTradeStrategyKey() + ":Low:" + String.valueOf(low) + ":high:" + String.valueOf(high));
        strategy.setRangeLow(new BigDecimal(low));
        strategy.setRangeHigh(new BigDecimal(high));
    }

    public Map<Double, Map<String, StrikeData>> strikeSelection(String currentDate, TradeStrategy strategy, double close, String checkTime) {
        String strikeSelectionType = strategy.getStrikeSelectionType();
        // String checkTime = strategy.getCandleCheckTime();
        String index = strategy.getIndex();
        Map<String, Map<String, StrikeData>> strikeMasterMap = new HashMap<>();
        if (zerodhaTransactionService.expDate.equals(currentDate) && strategy.getTradeValidity().equals(TradeValidity.BTST.getValidity())) {
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
        }
        if (strikeSelectionType.contains(StrikeSelectionType.OTM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale = strikeSelectionType.substring(3);
            // String sub=otmStrikeVale.substring(3);
            int otmValue = Integer.valueOf(otmStrikeVale) * 100;
            int ceValue = atmStrike + otmValue;
            int peValue = atmStrike - otmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapCe = strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(), strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapPe = strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(), strikeDataMapPe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(peValue)), stringMapPE);
            }
            return stringMapMap;
        }
        if (strikeSelectionType.contains(StrikeSelectionType.ITM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale = strikeSelectionType.substring(3);
            // String sub=otmStrikeVale.substring(3);
            int itmValue = Integer.valueOf(otmStrikeVale) * 100;
            int ceValue = atmStrike - itmValue;
            int peValue = atmStrike + itmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapCe = strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(), strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapPe = strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(), strikeDataMapPe.getValue());
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
            rangeSelected = mathUtils.getPriceRangeSortedWithLowRange(currentDate, high, low, checkTime, index, strategy);
            rangeSelected.entrySet().stream().forEach(atmNiftyStrikeMap -> {
                String historicPriceURL = "https://api.kite.trade/instruments/historical/" + atmNiftyStrikeMap.getValue().getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
                String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicPriceURL), atmNiftyStrikeMap.getValue().getZerodhaId(), checkTime);
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
