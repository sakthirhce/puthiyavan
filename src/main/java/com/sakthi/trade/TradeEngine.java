package com.sakthi.trade;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.*;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.util.ZippingDirectory;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
import com.zerodhatech.ticker.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


@Service
@Slf4j
public class TradeEngine {
    public static final Logger LOGGER = LoggerFactory.getLogger(TradeEngine.class.getName());
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    public Map<String, List<TradeData>> openTrade = Collections.synchronizedMap(new LinkedHashMap<>()); // stock_id: List of trades
    public Map<String, String> lsHoliday = new HashMap<>();
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    @Autowired
    UserSubscriptionRepo userSubscriptionRepo;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    CommonUtil commonUtil;
    @Autowired
    public TransactionService transactionService;
    Map<String, Map<String, List<TradeStrategy>>> strategyMap = new LinkedHashMap<>();
    List<HistoricalDataExtended> dataArrayList3MHistory = new ArrayList<>();
    ArrayList<Long> listOfTokens = new ArrayList<>();
    Map<String, List<TradeStrategy>> rangeStrategyMap = new ConcurrentHashMap<>();
    ExecutorService executorThread = Executors.newFixedThreadPool(2);
    ExecutorService executorThreadIndex = Executors.newFixedThreadPool(2);
    ExecutorService executorThreadRangeOptions = Executors.newFixedThreadPool(2);
    ExecutorService sLMonitor = Executors.newFixedThreadPool(1);
    ExecutorService exitThread = Executors.newFixedThreadPool(1);
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    SimpleDateFormat exchangeDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat hourMinFormat = new SimpleDateFormat("HH:mm");
    SimpleDateFormat dayFormat = new SimpleDateFormat("E");
    KiteTicker tickerProvider;
    @Autowired
    TradeDataMapper tradeDataMapper;
    @Autowired
    public UserList userList;
    @Autowired
    BrokerWorkerFactory workerFactory;
    @Value("${lotSize.config}")
    List<String> config;
    @Value("${websocket.enabled:false}")
    boolean websocketEnabled;
    Gson gson = new Gson();
    @Autowired
    MathUtils mathUtils;
    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Autowired
    IndicatorDataRepo indicatorDataRepo;
    @Value("${filepath.trend}")
    String trendPath;
    boolean isHolidayAlertTriggered = false;
    @Autowired
    ZippingDirectory zippingDirectory;
    @Autowired
    TelegramMessenger telegramClient;
    @Value("${data.export}")
    boolean dataExport;
    @Autowired
    PositionPLDataRepo positionPLDataRepo;

    public static boolean isTimeGreaterThan(LocalTime time, LocalTime compareTo) {
        return time.isAfter(compareTo) || time.equals(compareTo);
    }


    public static boolean isTimeBefore(LocalTime time, LocalTime compareTo) {
        return time.isBefore(compareTo) || time.equals(compareTo);
    }


    public String getAlgoName() {
        return "TRADE_ENGINE";
    }

    @Scheduled(cron = "${tradeEngine.load.strategy}")
    public void loadStrategy() {
        //  strategyMap = new ConcurrentHashMap<>();
        Date date = new Date();
        // if(strategyMap.isEmpty()) {
        strategyMap = new LinkedHashMap<>();
        rangeStrategyMap = new ConcurrentHashMap<>();
        List<TradeStrategy> tradeStrategyList = tradeStrategyRepo.getActiveActiveStrategy();
        // System.out.println(new Gson().toJson(tradeStrategyList));
        tradeStrategyList.forEach(strategy -> {
            List<UserSubscription> userSubscriptionList = userSubscriptionRepo.getUserSubs(strategy.getTradeStrategyKey());
            if (userSubscriptionList != null && !userSubscriptionList.isEmpty()) {
                System.out.println(gson.toJson(userSubscriptionList));
                UserSubscriptions userSubscriptions = new UserSubscriptions();
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
            try {
                if ((strategy.getTradeDays() != null && strategy.getTradeDays().toUpperCase().contains(dayFormat.format(date).toUpperCase())) || Objects.equals(strategy.getTradeDays(), "All")) {
                    if (strategy.isRangeBreak()) {
                        //  System.out.println("range strategy:" + strategy);
                        //TODO: this is outdated
                        List<TradeStrategy> indexTimeMap = rangeStrategyMap.get(index);
                        if (rangeStrategyMap.get(index) != null && !indexTimeMap.isEmpty()) {
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
                            if (indexTimeMap.containsKey(strategy.getEntryTime()) && !strategies.isEmpty()) {
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
            } catch (Exception e) {
                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":error while loading");
            }
        });
        strategyMap.forEach((key, stringTradeStrategyMap) -> stringTradeStrategyMap.forEach((key1, list) -> list.forEach(strategy -> {
            List<String> users = new ArrayList<>();
            strategy.getUserSubscriptions().getUserSubscriptionList().forEach(userSubscription -> users.add(userSubscription.getUserId() + "-" + userSubscription.getLotSize()));
            tradeSedaQueue.sendTelemgramSeda(key + ":" + key1 + ":" + users + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getLotSize());
        })));
        rangeStrategyMap.forEach((key, list) -> {

            //  Map<String, List<TradeStrategy>> stringTradeStrategyMap = stringMapEntry.getValue();
            //  stringTradeStrategyMap.entrySet().forEach(tradeStrategyMap -> {
            //  System.out.println(gson.toJson(list));
            list.forEach(strategy -> {
                List<String> users = new ArrayList<>();
                strategy.getUserSubscriptions().getUserSubscriptionList().forEach(userSubscription -> users.add(userSubscription.getUserId() + "-" + userSubscription.getLotSize()));
                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + strategy.getIndex() + ":" + users + ":" + strategy.getRangeStartTime() + ":" + strategy.getRangeBreakTime() + ":range enabled:" + strategy.isRangeBreak() + ":" + strategy.getLotSize());
            });
            // });
        });
        //   System.out.println("strategy config:" + new Gson().toJson(strategyMap));
        //  }
    }

    // @Scheduled(cron = "${tradeEngine.load.bbs.data}")
    public void loadBBSData() {
        List<IndicatorData> indicatorDataList = indicatorDataRepo.getLast20IndicatorData();
        dataArrayList3MHistory.addAll(mapBBSData(indicatorDataList));
        HistoricalDataExtended historicalDataExtended = dataArrayList3MHistory.get(dataArrayList3MHistory.size() - 1);
        tradeSedaQueue.sendTelemgramSeda("Loaded BBS historical data:" + historicalDataExtended.timeStamp + ": sma: " + historicalDataExtended.sma + ": upper band: " + historicalDataExtended.bolingerUpperBand, "-848547540");
    }

    @Scheduled(cron = "${tradeEngine.load.open.data}")
    public void loadNrmlPositions() {
        Iterable<OpenTradeDataEntity> openTradeDataEntities1 = openTradeDataRepo.findAll();
        // System.out.println(openTradeDataEntities1);
        List<OpenTradeDataEntity> openList = new ArrayList<>();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
                    openList.add(openTradeDataEntity);
                }
            }
        });
        // System.out.println(openList);
        //    if (!openTrade.isEmpty()) {
        openList.forEach(openTradeDataEntity -> {
            System.out.println(gson.toJson(openTradeDataEntity));
            TradeStrategy tradeStrategyList = tradeStrategyRepo.getStrategyByStrategyKey(openTradeDataEntity.tradeStrategyKey);
            System.out.println(gson.toJson(tradeStrategyList));
            if ("BTST".equals(tradeStrategyList.getTradeValidity()) || "CNC".equals(tradeStrategyList.getTradeValidity())) {
                //   if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    List<Position> positions = brokerWorker.getRateLimitedPositions(user);
                    positions.stream().filter(position -> "NRML".equals(position.product)
                            && openTradeDataEntity.getStockName().equals(position.tradingSymbol)
                            && (position.netQuantity != 0)).findFirst().ifPresent(position -> {
                        int positionQty = Math.abs(position.netQuantity);
                        if (positionQty != openTradeDataEntity.getQty()) {
                            //   openTradeDataEntity.setQty(positionQty);
                            tradeSedaQueue.sendTelemgramSeda("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty." + ":" + getAlgoName(), "exp-trade");
                            LOGGER.info("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.", "exp-trade");

                        }
                        openTradeDataEntity.isSlPlaced = false;
                        openTradeDataEntity.isOrderPlaced = true;
                        openTradeDataEntity.setSlOrderId(null);

                        TradeData tradeData = tradeDataMapper.mapTradeDataEntityToTradeData(openTradeDataEntity);
                        List<TradeData> tradeDataList = openTrade.get(user.getName());
                        if (tradeDataList == null) {
                            tradeDataList = new ArrayList<>();
                        }
                        tradeData.setTradeStrategy(tradeStrategyList);
                        tradeDataList.add(tradeData);
                        openTrade.put(user.getName(), tradeDataList);
                        tradeSedaQueue.sendTelemgramSeda("Open Position: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ":" + getAlgoName(), "exp-trade");
                    });
                } else {
                    String openStr = gson.toJson(openTradeDataEntity);
                    OpenTradeDataBackupEntity openTradeDataBackupEntity = gson.fromJson(openStr, OpenTradeDataBackupEntity.class);
                    openTradeDataBackupRepo.save(openTradeDataBackupEntity);
                    openTradeDataRepo.deleteById(openTradeDataEntity.getDataKey());
                }
            }
            //}
        });
        // }
        //  System.out.println(gson.toJson(openTrade));
    }

    @Scheduled(cron = "${tradeEngine.execute.strategy}")
    public void executeStrategy() throws IOException, CsvValidationException {
        Date date = new Date();
        //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = dateFormat.format(date);
        //        String currentDateStr="2023-12-21";
        CSVReader csvHolidayFinReader = new CSVReader(new FileReader(trendPath + "/trading_holiday.csv"));
        String[] lineHoliday;
        while ((lineHoliday = csvHolidayFinReader.readNext()) != null) {
            lsHoliday.put(lineHoliday[0].trim(), lineHoliday[0].trim());
        }
        if (!lsHoliday.containsKey(currentDateStr)) {
            //tradeSedaQueue.sendTelemgramSeda("started trade engine execution");
            Calendar candleCalenderMin = Calendar.getInstance();
            Calendar calendarCurrentMin = Calendar.getInstance();
            candleCalenderMin.add(Calendar.MINUTE, -1);
            Date currentMinDate = calendarCurrentMin.getTime();
            Date candleCurrentMinDate = candleCalenderMin.getTime();
            String candleHourMinStr = hourMinFormat.format(candleCurrentMinDate);
            // String candleHourMinStr="09:19";
            // System.out.println(candleHourMinStr);
            String currentHourMinStr = hourMinFormat.format(currentMinDate);
            // String  currentHourMinStr="09:20";
            // System.out.println(currentHourMinStr);
            //   log.info(currentHourMinStr + ":" + "executing");
            executorThreadRangeOptions.submit(() -> {
                openTrade.forEach((userId, tradeList) -> {
                    tradeList.stream().filter(trade -> trade.range && trade.getEntryOrderId() == null).forEach(tradeData -> {
                        TradeStrategy strategy = tradeData.getTradeStrategy();
                        Map<String, Double> orbHighLow = new HashMap<>();
                        // if ("ORB".equals(strategy.getRangeType())) {
                        try {
                            if ((strategy.getRangeBreakTime()).equals(currentHourMinStr)) {
                                String historicURL = "https://api.kite.trade/instruments/historical/" + tradeData.getStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), String.valueOf(tradeData.getStockId()), currentHourMinStr);
                                // LOGGER.info("Index response: {}:{}", index, response);
                                HistoricalData historicalData = new HistoricalData();
                                JSONObject json = new JSONObject(response);
                                String status = json.getString("status");
                                if (!status.equals("error")) {
                                    historicalData.parseResponse(json);
                                }
                                orbHighLowOptions(historicalData.dataArrayList, orbHighLow, strategy, tradeData, currentDateStr);
                            }
                        } catch (Exception e) {
                            log.error("error while executing range high low for options:{},{}", e.getMessage(), tradeData.getStockName());
                        }
                    });
                });
            });
            executorThread.submit(() -> {
                //ORB range break code starts
                try {
                    rangeStrategyMap.forEach((index, strategyList) -> {
                        strategyList.forEach(strategy -> {
                            try {
                                String stockId = null;
                                if ("BNF".equals(index)) {
                                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                                } else if ("NF".equals(index)) {
                                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                                } else if ("FN".equals(index)) {
                                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                                } else if ("MC".equals(index)) {
                                    stockId = zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
                                } else if ("SS".equals(index)) {
                                    stockId = zerodhaTransactionService.niftyIndics.get("SENSEX");
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
                                            //LOGGER.info("api data name:" + lastHistoricalData.close + " :response:" + response);
                                            if ("INDEX".equals(strategy.getRangeBreakInstrument())) {
                                                rangeBreak(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr + ":00", lastHistoricalData);
                                            } else {
                                                rangeBreakOptions(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr, lastHistoricalData, index);
                                            }
                                        } catch (Exception e) {
                                            LOGGER.error("error:{}", e.getMessage());
                                            // throw new RuntimeException(e);
                                        }
                                    }

                                }
                            } catch (Exception e) {
                                LOGGER.error("error:{}", e.getMessage());
                            }

                        });
                    });
                } catch (Exception e) {
                    log.info("error while executing");
                    e.printStackTrace();
                }
                //ORB range break code End
                strategyMap.forEach((index, timeStrategyListMap) -> {
                    //  log.info(currentHourMinStr + ":" + "executing strategyMap");
                    executorThreadIndex.submit(() -> {
                        String stockId = null;
                        try {

                            if ("BNF".equals(index)) {
                                stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                            } else if ("NF".equals(index)) {
                                stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                            } else if ("FN".equals(index)) {
                                stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                            } else if ("MC".equals(index)) {
                                stockId = zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
                            } else if ("SS".equals(index)) {
                                stockId = zerodhaTransactionService.niftyIndics.get("SENSEX");
                            }
                            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), stockId, currentHourMinStr);
                            // LOGGER.info("Index response: {}:{}", index, response);
                            HistoricalData historicalData = new HistoricalData();
                            JSONObject json = new JSONObject(response);
                            String status = json.getString("status");
                            if (!status.equals("error")) {
                                historicalData.parseResponse(json);
                                Optional<HistoricalData> optionalHistoricalLatestData = historicalData.dataArrayList.stream().filter(candle -> {
                                    try {
                                        //      System.out.println(candle.timeStamp);
                                        Date candleDateTime = candleDateTimeFormat.parse(candle.timeStamp);
                                        return hourMinFormat.format(candleDateTime).equals(candleHourMinStr);
                                    } catch (ParseException e) {
                                        throw new RuntimeException(e);
                                    }
                                }).findFirst();
                                if (optionalHistoricalLatestData.isPresent()) {
                                    Optional.of(optionalHistoricalLatestData).ifPresent(lastHistoricalDataOp -> {
                                        HistoricalData lastHistoricalData = lastHistoricalDataOp.get();
                                        LOGGER.info("last candle time and close: {}:{}", lastHistoricalData.timeStamp, lastHistoricalData.close);
                                        List<TradeStrategy> strategies = timeStrategyListMap.get(currentHourMinStr);
                                        if (strategies != null) {
                                            if (!strategies.isEmpty()) {
                                                // log.info(index+":"+currentHourMinStr+":"+historicURL+":"+response);
                                                LOGGER.info("trade engine: {}", gson.toJson(strategies));
                                                strategies.forEach(strategy -> {
                                                    try {
                                                        // executorThreadStrategy.submit(() -> {
                                                        LOGGER.info("strategy name:{}:{}{}", strategy.getTradeStrategyKey(), currentHourMinStr, strategy.getEntryTime());
                                                        Map<String, Map<String, StrikeData>> rangeStrikes = new HashMap<>();

                                                        if (strategy.getEntryTime().equals(currentHourMinStr)) {
                                                            LOGGER.info("api data name:{} :response:{}", lastHistoricalData.close, response);
                                                            rangeStrikes = mathUtils.strikeSelection(currentDateStr, strategy, lastHistoricalData.close, candleHourMinStr + ":00");
                                                        }


                                                        LOGGER.info("{}:{}", strategy.getTradeStrategyKey(), rangeStrikes);
                                                        rangeStrikes.forEach((indexStrikePrice, strikeDataEntry) -> {
                                                            strikeDataEntry.forEach((key, strikeData) -> {
                                                                OrderParams orderParams = new OrderParams();
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
                                                                    BigDecimal price = BigDecimal.valueOf(triggerPriceTemp.get()).add(BigDecimal.valueOf(triggerPriceTemp.get()).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                                    orderParams.price = price.doubleValue();

                                                                } else {
                                                                    //TODO: add call to api to get price
                                                                    orderParams.orderType = "MARKET";
                                                                }
                                                                LOGGER.info(strategy.getTradeStrategyKey() + "placing order for:" + strikeData.getZerodhaSymbol());

                                                                orderParams.tradingsymbol = strikeData.getZerodhaSymbol();
                                                                orderParams.exchange = "NFO";
                                                                if ("SS".equals(strategy.getIndex())) {
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
                                                                            try {
                                                                                addTradeStriketoWebsocket(Long.parseLong(strikeData.getZerodhaId()), tradeData.getStockName(), index);
                                                                            } catch (Exception e) {
                                                                                e.printStackTrace();
                                                                            }
                                                                            tradeData.setStrikeId(strikeData.getDhanId());
                                                                            tradeData.setTradeStrategy(strategy);
                                                                            order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                                            if (order != null) {
                                                                                tradeData.setEntryOrderId(order.orderId);
                                                                                //  tradeData.isOrderPlaced = true;
                                                                            }
                                                                            mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                                                            List<TradeData> tradeDataList = openTrade.get(user.getName());
                                                                            if (tradeDataList == null) {
                                                                                tradeDataList = new ArrayList<>();
                                                                            }
                                                                            tradeDataList.add(tradeData);
                                                                            openTrade.put(user.getName(), tradeDataList);
                                                                            LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                                                            tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: "
                                                                                    + strikeData.getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
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
                                                                            tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");

                                                                        } catch (KiteException e) {
                                                                            throw new RuntimeException(e);
                                                                        }
                                                                    });
                                                                });
                                                            });

                                                        });
                                                    } catch (Exception e) {
                                                        LOGGER.error("error:{}", e.getMessage());
                                                        tradeSedaQueue.sendTelemgramSeda("Error while executing strategy :" + strategy.getTradeStrategyKey() + ":" + e.getMessage(), "error");
                                                    }
                                                });
                                                //  });
                                            }
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("error:{}", e.getMessage());
                            tradeSedaQueue.sendTelemgramSeda("Error while executing index strategy :" + index + ":" + e.getMessage(), "error");
                        }
                    });

                });
            });
            //slCode(currentDateStr, currentHourMinStr, candleHourMinStr);
            exitCode(currentDateStr, currentHourMinStr);
        } else {
            if (!isHolidayAlertTriggered) {
                tradeSedaQueue.sendTelemgramSeda("Today Trading Holiday. Enjoy your Holiday", "exp-trade");
                isHolidayAlertTriggered = true;
            }
        }
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
                                    tradeData.setStockId(Integer.valueOf(strikeData.getZerodhaId()));
                                    try {
                                        addTradeStriketoWebsocket(Long.parseLong(strikeData.getZerodhaId()), tradeData.getStockName(), index);
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
                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                    List<TradeData> tradeDataList = openTrade.get(user.getName());
                                    if (tradeDataList == null) {
                                        tradeDataList = new ArrayList<>();
                                    }
                                    tradeDataList.add(tradeData);
                                    openTrade.put(user.getName(), tradeDataList);
                                    LOGGER.info("trade data" + new Gson().toJson(tradeData));
                                    tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: "
                                            + strikeData.getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
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
            log.error("error while finding strike at range start: {},{}", e.getMessage(), strategy.getTradeStrategyKey());
        }
    }

    public void exitCode(String currentDateStr, String currentHourMinStr) {
        exitThread.submit(() -> {
            openTrade.forEach((userId, tradeDataList) -> {
                User user = userList.getUser().stream().filter(
                        user1 -> user1.getName().equals(userId)
                ).findFirst().get();
                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                try {
                    List<Order> orders = brokerWorker.getOrders(user);
                    List<Position> positions = brokerWorker.getPositions(user);
                    tradeDataList.forEach(tradeData -> {
                        TradeStrategy strategy = tradeData.getTradeStrategy();
                        if (strategy.isNoExit()) {
                            //log.info("no exit");
                        } else {
                            try {
                                if ((tradeData.getTradeDate().equals(currentDateStr) && (strategy.getTradeValidity().equals("MIS") || strategy.getTradeValidity().equals("BTST-MIS"))) ||
                                        (dateFormat.parse(tradeData.getTradeDate()).before(dateFormat.parse(currentDateStr)) &&
                                                (strategy.getTradeValidity().equals("BTST") || strategy.getTradeValidity().equals("CNC"))) || ("SELL".equals(strategy.getOrderType())
                                        && "MIS".equals(strategy.getTradeValidity()) && strategy.isRangeBreak() && "NF".equals(strategy.getIndex()))) {
                                    if (currentHourMinStr.equals(strategy.getExitTime())) {
                                        if (!tradeData.getTradeStrategy().isNoSl()) {
                                            orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                                try {
                                                    brokerWorker.cancelOrder(orderr.orderId, user);
                                                    tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Triggered sl cancellation:" + tradeData.getUserId() + ":stike:" + tradeData.getStockName(), "exp-trade");
                                                } catch (Exception e) {
                                                    tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                    LOGGER.info(e.getMessage());
                                                } catch (KiteException e) {
                                                    tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                        }
                                        orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getEntryOrderId()) && !tradeData.isOrderPlaced()).forEach(orderr -> {
                                            try {
                                                brokerWorker.cancelOrder(orderr.orderId, user);
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Retry cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "exp-trade");
                                            } catch (Exception e) {
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                LOGGER.info(e.getMessage());
                                            } catch (KiteException e) {
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                throw new RuntimeException(e);
                                            }
                                        });
                                        orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getEntryOrderId())).forEach(orderr -> {
                                            try {
                                                brokerWorker.cancelOrder(orderr.orderId, user);
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Pending order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "exp-trade");
                                            } catch (Exception e) {
                                                LOGGER.info(e.getMessage());
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                            } catch (KiteException e) {
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                throw new RuntimeException(e);
                                            }
                                        });
                                        positions.stream().filter(position -> (tradeData.getStockName().equals(position.tradingSymbol)
                                                && tradeData.isOrderPlaced() && !tradeData.isExited() && (position.netQuantity != 0))).forEach(position -> {
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = position.tradingSymbol;
                                            orderParams.exchange = "NFO";
                                            if ("SS".equals(strategy.getIndex())) {
                                                orderParams.exchange = "BFO";
                                            }
                                            if (strategy.getTradeStrategyKey().length() <= 20) {
                                                orderParams.tag = strategy.getTradeStrategyKey();
                                            }
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
                                            if ("SELL".equals(strategy.getOrderType()) && "MIS".equals(strategy.getTradeValidity()) && strategy.isRangeBreak() && "NF".equals(strategy.getIndex())) {
                                                orderParams.product = "NRML";
                                            }
                                            orderParams.validity = "DAY";
                                            Order orderResponse = null;
                                            try {
                                                orderResponse = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                LOGGER.info(new Gson().toJson(orderResponse));
                                                String message = MessageFormat.format("Placed exit order {0}", orderParams.tradingsymbol + ":" + strategy.getTradeStrategyKey());
                                                LOGGER.info(message);
                                                //  tradeData.isExited = true;
                                                tradeData.setExitOrderId(orderResponse.orderId);
                                                mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                //   tradeSedaQueue.sendTelemgramSeda(message + ":" + tradeData.getUserId() + ":" + getAlgoName(), "error");

                                            } catch (Exception e) {
                                                LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                                tradeSedaQueue.sendTelemgramSeda("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(), "error");
                                                LOGGER.error("error:{}", e.getMessage());
                                            } catch (KiteException e) {
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                throw new RuntimeException(e);
                                            }

                                        });
                                    }
                                }


                            } catch (Exception e) {
                                LOGGER.error("error:{}", e.getMessage());
                            }
                        }
                    });
                } catch (IOException | KiteException e) {
                    LOGGER.error("error:{}", e.getMessage());
                }
            });

        });
    }

    @Scheduled(cron = "${tradeEngine.cnc.sl}")
    public void cncSlCode() {
        Date date = new Date();
        String currentDateStr = dateFormat.format(date);
        Calendar candleCalenderMin = Calendar.getInstance();
        Calendar calendarCurrentMin = Calendar.getInstance();
        candleCalenderMin.add(Calendar.MINUTE, -1);
        Date currentMinDate = calendarCurrentMin.getTime();
        Date candleCurrentMinDate = candleCalenderMin.getTime();
        String candleHourMinStr = hourMinFormat.format(candleCurrentMinDate);
        String currentHourMinStr = hourMinFormat.format(currentMinDate);
        openTrade.forEach((userId, tradeData) -> {

            User user = userList.getUser().stream().filter(
                    user1 -> user1.getName().equals(userId)
            ).findFirst().get();

            BrokerWorker brokerWorker = workerFactory.getWorker(user);

            try {
                tradeData.stream().filter(order -> order.getEntryOrderId() != null && !order.isExited).forEach(trendTradeData -> {
                    LocalTime currentTime = LocalTime.now();
                    LocalTime cncSLTime = LocalTime.of(9, 16);
                    LocalTime cncSLEndTime = LocalTime.of(9, 59);
                    TradeStrategy strategy = trendTradeData.getTradeStrategy();
                    try {
                        if (dateFormat.parse(trendTradeData.getTradeDate()).before(dateFormat.parse(currentDateStr))
                                && strategy != null && (strategy.getTradeValidity().equals("BTST")
                                || strategy.getTradeValidity().equals("CNC"))
                                && (isTimeGreaterThan(currentTime, cncSLTime) || isTimeBefore(currentTime, cncSLEndTime))) {
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
                                        LOGGER.error("error while getting current price of script:{}", trendTradeData.getStockName());
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
                                    Order orderd = null;
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
                                            triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice().add(slValue));
                                        } else {
                                            BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, trendTradeData.getSellPrice());
                                            triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice().add(slPoints));
                                        }
                                        price = triggerPriceTemp.add(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                        orderParams.transactionType = "BUY";
                                        if (trendTradeData.getSellPrice() != null) {
                                            LOGGER.info("sell price:{} sl price: {}", trendTradeData.getSellPrice().doubleValue(), triggerPriceTemp.doubleValue());
                                        }
                                        if (strikePrice > 0 && strikePrice > triggerPriceTemp.doubleValue()) {
                                            orderParams.orderType = "MARKET";
                                        }
                                    }
                                    orderParams.triggerPrice = MathUtils.roundToNearestFivePaiseUP(triggerPriceTemp).doubleValue();
                                    orderParams.price = MathUtils.roundToNearestFivePaiseUP(price).doubleValue();
                                    LOGGER.info("input: {}", gson.toJson(orderParams));
                                    orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                    String message = "CNC SL placed for " + userId + ": stock name:" + trendTradeData.getStockName();
                                    tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                    trendTradeData.isSlPlaced = true;
                                    trendTradeData.setSlOrderId(orderd.orderId);
                                    mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                } catch (Exception e) {
                                    tradeSedaQueue.sendTelemgramSeda("error while placing cnc sl" + trendTradeData.getStockName() + ":" + e.getMessage(), "exp-trade");
                                    log.error("error while placing CNC SL for {}: stock name:{}:{}", userId, trendTradeData.getStockName(), e.getMessage());
                                    e.printStackTrace();
                                } catch (KiteException e) {
                                    tradeSedaQueue.sendTelemgramSeda("kite error while placing cnc sl" + trendTradeData.getStockName() + ":" + e.getMessage(), "exp-trade");
                                    log.error("kite exception while placing CNC SL for " + userId + ": stock name:" + trendTradeData.getStockName() + ":" + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (ParseException e) {
                        tradeSedaQueue.sendTelemgramSeda("parse error while placing cnc sl" + trendTradeData.getStockName() + ":" + e.getMessage(), "exp-trade");
                        log.error("parse exception while placing CNC SL for {}: stock name:{}:{}", userId, trendTradeData.getStockName(), e.getMessage());
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                tradeSedaQueue.sendTelemgramSeda("runtime error while placing cnc sl:" + userId + ":" + e.getMessage(), "exp-trade");
                log.error("runtime exception while placing CNC SL for {}:{}", userId, e.getMessage());
                e.printStackTrace();
            }
        });
}

@Scheduled(cron = "${tradeEngine.sl.immediate}")
public void slImmediate() {
    Date date = new Date();
    //      MDC.put("run_time",candleDateTimeFormat.format(date));
    String currentDateStr = dateFormat.format(date);
    sLMonitor.submit(() -> {
        //  Type type = new TypeToken<List<TradeData>>(){}.getType();
        // String data="[{\"stockName\":\"NIFTY2331617500PE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"959a60d1-cb97-4006-850e-50653f62008a\",\"buyPrice\":168,\"buyTradedPrice\":168,\"stockId\":14236418,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":true,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000131603\",\"slOrderId\":\"230310000161496\",\"slPrice\":138,\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"modifyDate\":\"2023-03-10\",\"tradeStrategy\":{\"tradeStrategyKey\":\"NFBCD15117btst\",\"index\":\"NF\",\"entryTime\":\"09:17\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:23\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":true,\"simpleMomentumType\":\"POINTS_UP\",\"simpleMomentumValue\":10.00,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":1,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09:17 points 10 sl 30 CNC\"}},{\"stockName\":\"NIFTY2331617350CE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"f11cade6-bea7-4a66-873f-58dfc63f568f\",\"buyPrice\":165,\"stockId\":14234626,\"userId\":\"LTK728\",\"isOrderPlaced\":false,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000131648\",\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"NFBCD15117btst\",\"index\":\"NF\",\"entryTime\":\"09:17\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:23\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":true,\"simpleMomentumType\":\"POINTS_UP\",\"simpleMomentumValue\":10.00,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":1,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09:17 points 10 sl 30 CNC\"}},{\"stockName\":\"NIFTY2331617500PE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"68f5c02b-9913-418b-a5d0-255201e5cf06\",\"sellPrice\":17372.95000000000072759576141834259033203125,\"stockId\":14236418,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":true,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"NORF23\",\"index\":\"NF\",\"entryTime\":\"09:23\",\"tradeValidity\":\"MIS\",\"exitTime\":\"15:10\",\"intradayExitTime\":\"15:10\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":true,\"strategyEnabled\":true,\"rangeBreakTime\":\"09:23\",\"rangeStartTime\":\"09:16\",\"rangeBreakSide\":\"High,Low\",\"rangeBreakInstrument\":\"INDEX\",\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":0,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09-16 to 09:23 ORB sl 30\",\"rangeLow\":0,\"rangeHigh\":17413.9000000000014551915228366851806640625}},{\"stockName\":\"BANKNIFTY2331640700CE\",\"tradeDate\":\"2023-03-10\",\"qty\":25,\"dataKey\":\"22c3f32c-503c-494c-aff4-55fbe5343bfa\",\"sellPrice\":222.2,\"sellTradedPrice\":222.2,\"stockId\":14149378,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000542149\",\"entryType\":\"SELL\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"BNFABOST135\",\"index\":\"BNF\",\"entryTime\":\"09:35\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:34\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"OTM2\",\"orderType\":\"SELL\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"MARKET\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":0,\"lotSize\":25,\"positionalLotSize\":0,\"slType\":\"PERCENT\",\"slValue\":50.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"bnf stbt sell 9:35 CNC sl 50 OTM2\"}},{\"stockName\":\"BANKNIFTY2331640300PE\",\"tradeDate\":\"2023-03-10\",\"qty\":25,\"dataKey\":\"5b0da0b2-5240-461f-8b72-a02b2867a969\",\"sellPrice\":252.5,\"sellTradedPrice\":252.5,\"stockId\":14147586,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000542164\",\"entryType\":\"SELL\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"BNFABOST135\",\"index\":\"BNF\",\"entryTime\":\"09:35\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:34\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"OTM2\",\"orderType\":\"SELL\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"MARKET\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":0,\"lotSize\":25,\"positionalLotSize\":0,\"slType\":\"PERCENT\",\"slValue\":50.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"bnf stbt sell 9:35 CNC sl 50 OTM2\"}}]";
        //List<TradeData> prodList = gson.fromJson(data, type);
        //  openTrade.put("LTK728",prodList);
        openTrade.forEach((userId, tradeData) -> {
            //   LOGGER.info("Insde SL:"+userId);

            User user = userList.getUser().stream().filter(
                    user1 -> user1.getName().equals(userId)
            ).findFirst().get();
            //        List<Position> positions=brokerWorker.getPositions(user);
            BrokerWorker brokerWorker = workerFactory.getWorker(user);
            List<Position> positions = brokerWorker.getPositions(user);

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

                                    String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey());
                                    LOGGER.info(message);
                                    try {
                                        tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                    } catch (Exception e) {
                                        LOGGER.info("error:" + e);
                                    }
                                }
                            }
                            if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && !trendTradeData.getTradeStrategy().isNoSl()) {
                                if (!trendTradeData.isOrderPlaced) {
                                    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                        trendTradeData.isOrderPlaced = true;
                                        try {
                                            LOGGER.info("order executed" + trendTradeData.getStockName() + ":" + trendTradeData.getEntryOrderId());
                                            if ("BUY".equals(trendTradeData.getEntryType())) {
                                                if (trendTradeData.getBuyPrice() == null) {
                                                    trendTradeData.setBuyPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                                }
                                                trendTradeData.setBuyTradedPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                            } else {
                                                trendTradeData.setSellPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                                trendTradeData.setSellTradedPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                            }
                                            if (strategy.isHedge()) {
                                                trendTradeData.isExited = true;
                                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                                    trendTradeData.setSellPrice(new BigDecimal(0));
                                                }
                                            }
                                            //BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(50)).setScale(0, RoundingMode.UP);
                                            String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + trendTradeData.getSellPrice() + ":" + trendTradeData.getBuyPrice());
                                            LOGGER.info(message);
                                            tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                        } catch (Exception e) {
                                            LOGGER.error("error:{}", e.getMessage());
                                        }
                                    }
                                } else {
                                    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && !trendTradeData.isSlPriceCalculated) {
                                        try {
                                            if (strategy.isHedge()) {
                                                trendTradeData.isExited = true;
                                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                                    trendTradeData.setSellPrice(new BigDecimal(0));
                                                }
                                            }
                                            LOGGER.info("order executed" + trendTradeData.getStockName() + ":" + trendTradeData.getEntryOrderId());
                                            if ("BUY".equals(trendTradeData.getEntryType())) {
                                                if (trendTradeData.getBuyPrice() == null) {
                                                    trendTradeData.setBuyPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                                }
                                                trendTradeData.setBuyTradedPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                                trendTradeData.setBuyTime(exchangeDateTimeFormat.format(order.exchangeUpdateTimestamp));
                                            } else {

                                                trendTradeData.setSellPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                                trendTradeData.setSellTradedPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
                                                trendTradeData.setSellTime(exchangeDateTimeFormat.format(order.exchangeUpdateTimestamp));
                                            }
                                            //BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(50)).setScale(0, RoundingMode.UP);
                                            String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + trendTradeData.getSellPrice() + ":" + trendTradeData.getBuyPrice());
                                            LOGGER.info(message);
                                            tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                        } catch (Exception e) {
                                            LOGGER.error("error:{}", e.getMessage());
                                        }
                                    }
                                }

                            }
                            if (trendTradeData.isOrderPlaced && trendTradeData.getEntryOrderId().equals(order.orderId)) {
                                if (!trendTradeData.isSlPriceCalculated && !trendTradeData.getTradeStrategy().isHedge()) {
                                    try {
                                        Date orderExeutedDate = order.exchangeUpdateTimestamp;
                                        Date currentDate = new Date();
                                        long difference_In_Time = currentDate.getTime() - orderExeutedDate.getTime();
                                        long difference_In_Minutes = (difference_In_Time / (1000 * 60)) % 60;
                                        if (difference_In_Minutes > 2) {
                                            String message = MessageFormat.format("Order Executed for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ": but SL not calculated, Please Check");
                                            tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (!trendTradeData.isSlPriceCalculated && !trendTradeData.isErrored & !trendTradeData.getTradeStrategy().isHedge()) {
                                    //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                    try {
                                        // mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                        OrderParams orderParams = new OrderParams();
                                        if (strategy.getTradeStrategyKey().length() <= 20) {
                                            orderParams.tag = strategy.getTradeStrategyKey();
                                        }
                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                        orderParams.exchange = "NFO";
                                        orderParams.quantity = trendTradeData.getQty();
                                        orderParams.orderType = "SL";
                                        if ("MIS".equals(strategy.getTradeValidity())) {
                                            orderParams.product = "MIS";
                                        } else {
                                            orderParams.product = "NRML";
                                        }
                                        if ("SS".equals(strategy.getIndex())) {
                                            orderParams.exchange = "BFO";
                                        }
                                        orderParams.product = order.product;
                                        orderParams.validity = "DAY";
                                        Order orderd = null;
                                        BigDecimal price;
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
                                                BigDecimal triggerPriceT = getSLPrice(currentDateStr, String.valueOf(trendTradeData.getStockId()), trendTradeData);
                                                if (triggerPriceT != null && triggerPriceT.doubleValue() > 0) {
                                                    triggerPriceTemp = getSLPrice(currentDateStr, String.valueOf(trendTradeData.getStockId()), trendTradeData);
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
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
                                            price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                            orderParams.transactionType = "SELL";
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
                                                BigDecimal triggerPriceT = getSLPrice(currentDateStr, String.valueOf(trendTradeData.getStockId()), trendTradeData);
                                                if (triggerPriceT != null && triggerPriceT.doubleValue() > 0) {
                                                    triggerPriceTemp = getSLPrice(currentDateStr, String.valueOf(trendTradeData.getStockId()), trendTradeData);
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
                                            price = triggerPriceTemp.add(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                            orderParams.transactionType = "BUY";
                                            if (trendTradeData.getSellPrice() != null) {
                                                LOGGER.info("sell price:{} sl price: {}", trendTradeData.getSellPrice().doubleValue(), triggerPriceTemp.doubleValue());
                                            }
                                        }
                                        orderParams.triggerPrice = trendTradeData.getTempSlPrice().doubleValue();
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
                                        orderParams.price = webtriggerPrice.doubleValue();
                                        trendTradeData.setSlPrice(triggerPriceTemp);
                                        try {
                                            LOGGER.info("input:" + gson.toJson(orderParams));
                                            trendTradeData.isSlPriceCalculated = true;
                                            //  orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                            // trendTradeData.isSlPlaced = true;
                                            // trendTradeData.setSlOrderId(orderd.orderId);
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                            try {
                                                LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL");
                                                tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey() + ":sell price: " + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice()).doubleValue() + " sl price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
                                            } catch (Exception e) {
                                                LOGGER.info("error:" + e);
                                            }
                                        } catch (Exception e) {
                                            trendTradeData.isErrored = true;
                                            LOGGER.info("error while placing sl order: {}:{}", e.getMessage(), user.getName());
                                            tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                        } /*catch (KiteException e) {
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
                                                            orderParams.triggerPrice = newTriggerPrice;
                                                            BigDecimal newSLPrice = new BigDecimal(newTriggerPrice).add(new BigDecimal(newTriggerPrice).divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                            orderParams.price = newSLPrice.doubleValue();
                                                            LOGGER.info("Zerodha error. Recalculated price for stock {}:{}{}:{}", trendTradeData.getStockName(), user.getName(), newTriggerPrice, orderParams.price);
                                                            tradeSedaQueue.sendTelemgramSeda("Zerodha price error while placing sl. Recalculated price for stock " + trendTradeData.getStockName() + ":" + user.getName() + newTriggerPrice+":"+orderParams.price, "exp-trade");

                                                            try {
                                                                LOGGER.info("input:{}", gson.toJson(orderParams));
                                                                orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                                trendTradeData.isSlPlaced = true;
                                                                trendTradeData.setSlOrderId(orderd.orderId);
                                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                                try {
                                                                    LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL");
                                                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                } catch (Exception e1) {
                                                                    LOGGER.info("error while sending message error:{}", e1.getMessage());
                                                                }
                                                            }catch (Exception e2) {
                                                                trendTradeData.isErrored = true;
                                                                LOGGER.info("error while placing sl-retry order: {}:{}", e.getMessage(), user.getName());
                                                                tradeSedaQueue.sendTelemgramSeda("Error while placing sl-retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                            } catch (KiteException e3) {
                                                                trendTradeData.isErrored = true;
                                                                LOGGER.info("KITE error-retry while placing sl order: {}:{}", e.getMessage(), user.getName());
                                                                tradeSedaQueue.sendTelemgramSeda("KITE error-retry while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                            }
                                                        }
                                                    }
                                            }*/

                                    } catch (Exception e) {
                                        LOGGER.info("error while placing sl:{}{}:{}", e.getMessage(), trendTradeData.getEntryOrderId(), trendTradeData.getStockName());
                                    }
                                }/*else {
                                        //TODO: add time check
                                        tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + getAlgoName()+":"+strategy.getTradeStrategyKey());
                                    }*/

                            }
                            if ("OPEN".equals(order.status) && order.orderId.equals(trendTradeData.getSlOrderId()) && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                // List<Position> positions=brokerWorker.getPositions(user);
                                positions.stream().filter(position -> position.tradingSymbol.equals(trendTradeData.getStockName())).findFirst().ifPresent(position -> {
                                    Double lastPrice = position.lastPrice;
                                    String message = MessageFormat.format("Sl open lastPrice:" + lastPrice + "sl price:" + trendTradeData.getSlPrice(), trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getTradeStrategyKey());
                                    LOGGER.info(message);

                                });
                                tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " SL open for long time, please check on priority" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
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
                                String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage, trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getTradeStrategyKey());
                                LOGGER.info(message);
                                tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                if (strategy.isReentry() && "SELL".equals(trendTradeData.getEntryType())) {
                                    long tradeCount = tradeData.stream().filter(tradeDataTemp ->
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
                                        if (strategy.getTradeStrategyKey().length() <= 20) {
                                            orderParams.tag = strategy.getTradeStrategyKey();
                                        }
                                        orderParams.triggerPrice = trendTradeData.getSellPrice().doubleValue();
                                        orderParams.price = trendTradeData.getSellPrice().subtract(trendTradeData.getSellPrice().divide(new BigDecimal(100))
                                                .multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                        if ("MIS".equals(strategy.getTradeValidity())) {
                                            orderParams.product = "MIS";
                                        } else {
                                            orderParams.product = "NRML";
                                        }
                                        if ("SS".equals(strategy.getIndex())) {
                                            orderParams.exchange = "BFO";
                                        }
                                        orderParams.transactionType = strategy.getOrderType();
                                        orderParams.validity = "DAY";
                                        Order orderd = null;
                                        try {
                                            LOGGER.info("input:" + gson.toJson(orderParams));
                                            orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                            reentryTradeData.setEntryOrderId(orderd.orderId);
                                            List<TradeData> tradeDataList = openTrade.get(user.getName());
                                            tradeDataList.add(reentryTradeData);
                                            openTrade.put(user.getName(), tradeDataList);
                                            mapTradeDataToSaveOpenTradeDataEntity(reentryTradeData, false);

                                            try {
                                                LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                                tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                            } catch (Exception e) {
                                                LOGGER.info("error:" + e);
                                            }
                                        } catch (Exception e) {
                                            LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                            tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                        } catch (KiteException e) {
                                            LOGGER.error("error:{}", e.getMessage());
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
                                    if (strategy.getTradeStrategyKey().length() <= 20) {
                                        orderParams.tag = strategy.getTradeStrategyKey();
                                    }
                                    if ("SS".equals(strategy.getIndex())) {
                                        orderParams.exchange = "BFO";
                                    }
                                    orderParams.quantity = trendTradeData.getQty();
                                    orderParams.orderType = "SL";
                                    //  orderParams.triggerPrice = trendTradeData.get().doubleValue();
                                    double triggerPriceTemp = 0;
                                    if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_UP.getType())) {
                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), trendTradeData.getSlPrice()).add(trendTradeData.getSlPrice())).doubleValue();
                                    }
                                    if (strategy.getSimpleMomentumType().equals(ValueType.PERCENT_DOWN.getType())) {
                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP((trendTradeData.getSlPrice()).subtract(MathUtils.percentageValueOfAmount(strategy.getSimpleMomentumValue(), trendTradeData.getSlPrice()))).doubleValue();
                                    }
                                    if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_UP.getType())) {
                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice().add(strategy.getSimpleMomentumValue())).doubleValue();
                                    }
                                    if (strategy.getSimpleMomentumType().equals(ValueType.POINTS_DOWN.getType())) {
                                        triggerPriceTemp = MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice().subtract(strategy.getSimpleMomentumValue())).doubleValue();

                                    }
                                    System.out.println(triggerPriceTemp);
                                    orderParams.triggerPrice = triggerPriceTemp;
                                    orderParams.price = BigDecimal.valueOf(triggerPriceTemp).subtract(BigDecimal.valueOf(triggerPriceTemp).divide(new BigDecimal(100))
                                            .multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                    if ("MIS".equals(strategy.getTradeValidity())) {
                                        orderParams.product = "MIS";
                                    } else {
                                        orderParams.product = "NRML";
                                    }
                                    orderParams.transactionType = strategy.getOrderType();
                                    orderParams.validity = "DAY";
                                    Order orderd = null;
                                    try {
                                        LOGGER.info("input:" + gson.toJson(orderParams));
                                        orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                        reentryTradeData.setEntryOrderId(orderd.orderId);
                                        List<TradeData> tradeDataList = openTrade.get(user.getName());
                                        tradeDataList.add(reentryTradeData);
                                        openTrade.put(user.getName(), tradeDataList);
                                        mapTradeDataToSaveOpenTradeDataEntity(reentryTradeData, false);

                                        try {
                                            LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                            tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                        } catch (Exception e) {
                                            LOGGER.info("error:" + e);
                                        }
                                    } catch (Exception e) {
                                        LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                        tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                    } catch (KiteException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                if (strategy.isTrailToCost()) {
                                    tradeData.stream().filter(tradeDataTemp -> tradeDataTemp.getTradeStrategy().getTradeStrategyKey().equals(strategy.getTradeStrategyKey())
                                            && !tradeDataTemp.isSLHit).forEach(tradeDataMod -> {
                                        try {
                                            // Order order1 = brokerWorker.getOrder(user, tradeDataMod.getSlOrderId());
                                            //   OrderParams orderParams = new OrderParams();
                                            if ("BUY".equals(trendTradeData.getEntryType())) {
                                                trendTradeData.setSlPrice(MathUtils.roundToNearestFivePaiseUP(tradeDataMod.getBuyPrice()));
                                                double price = tradeDataMod.getBuyPrice().subtract(tradeDataMod.getBuyPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(20))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            } else {
                                                trendTradeData.setSlPrice(MathUtils.roundToNearestFivePaiseUP(tradeDataMod.getSellPrice()));
                                                double price = tradeDataMod.getSellPrice().add(tradeDataMod.getSellPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(20))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                            }
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                            //  brokerWorker.modifyOrder(order1.orderId, orderParams, user, tradeDataMod);
                                            tradeSedaQueue.sendTelemgramSeda("executed trail sl " + trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ": trail price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
                                        } catch (Exception e) {
                                            tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                            throw new RuntimeException(e);
                                        }
                                    });
                                }
                            }

                        } else if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                            String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + strategy.getTradeStrategyKey());
                            LOGGER.info(message);
                            trendTradeData.isErrored = true;
                            tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                        }

                        if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && order.orderId.equals(trendTradeData.getExitOrderId())
                                && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                            try {
                                if ("BUY".equals(trendTradeData.getEntryType())) {
                                    trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                } else {
                                    trendTradeData.setBuyPrice(new BigDecimal(order.averagePrice));
                                    trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                }
                                trendTradeData.isExited = true;
                                // BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, RoundingMode.UP);
                                String message = MessageFormat.format("Closed Position {0}", trendTradeData.getStockName() + ":" + strategy.getTradeStrategyKey());
                                LOGGER.info(message);
                                tradeSedaQueue.sendTelemgramSeda(message + ":" + trendTradeData.getUserId() + ":" + getAlgoName(), "exp-trade");
                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                            } catch (Exception e) {
                                LOGGER.error("error while setting exit flag and price: {}", e.getMessage());
                            }
                        }
                    });
                });
            } catch (Exception e) {
                LOGGER.error("error:{}", e.getMessage());
            } catch (KiteException e) {
                LOGGER.error("error:{}", e.getMessage());
            }
        });

    });
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
                        Date candleDateTime = candleDateTimeFormat.parse(candle.timeStamp);
                        return hourMinFormat.format(candleDateTime).equals(tradeStrategy.getEntryTime());
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
                        candleDateTime = candleDateTimeFormat.parse(candle.timeStamp);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                    String time = hourMinFormat.format(candleDateTime);
                    return time.equals(entryPreviousTime.format(formatter));
                }).findFirst();

                String slType = tradeStrategy.getSlType();
                BigDecimal slValue = tradeStrategy.getSlValue();

                if (optionalHistoricalPreviousCandleData.isPresent()) {
                    try {
                        HistoricalData historicalData1 = optionalHistoricalPreviousCandleData.get();
                        Date openDatetime = candleDateTimeFormat.parse(historicalData1.timeStamp);
                        String openDate = dateFormat.format(openDatetime);
                        if (candleDateTimeFormat.format(openDatetime).equals(openDate + "T" + tradeStrategy.getEntryTime() + ":00")) {
                            if ("SELL".equals(tradeData.getEntryType())) {
                                if ("POINTS".equals(slType)) {
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.close).add(slValue)).doubleValue());
                                } else {
                                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, new BigDecimal(historicalData1.close));
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.close).add(slPoints)).doubleValue());
                                }
                            } else {
                                if ("POINTS".equals(slType)) {
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.close).subtract(slValue)).doubleValue());
                                } else {
                                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, new BigDecimal(historicalData1.close));
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.close).subtract(slPoints)).doubleValue());
                                }
                            }
                            LOGGER.info("setting sl price based on previous:" + historicalData1.timeStamp + " close:" + historicalData1.close + ":" + tradeData.getStockId() + ":" + triggerPriceAtomic.get() + ":" + tradeData.getUserId());
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
                        Date openDatetime = candleDateTimeFormat.parse(historicalData1.timeStamp);
                        String openDate = dateFormat.format(openDatetime);
                        if (candleDateTimeFormat.format(openDatetime).equals(openDate + "T" + tradeStrategy.getEntryTime() + ":00")) {
                            if ("SELL".equals(tradeData.getEntryType())) {
                                tradeData.setSellPrice(new BigDecimal(historicalData1.open));
                                if ("POINTS".equals(slType)) {
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.open).add(slValue)).doubleValue());
                                } else {
                                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, new BigDecimal(historicalData1.open));
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.open).add(slPoints)).doubleValue());
                                }
                            } else {
                                tradeData.setBuyPrice(new BigDecimal(historicalData1.open));
                                if ("POINTS".equals(slType)) {
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.open).subtract(slValue)).doubleValue());
                                } else {
                                    BigDecimal slPoints = MathUtils.percentageValueOfAmount(slValue, new BigDecimal(historicalData1.open));
                                    triggerPriceAtomic.getAndSet(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(historicalData1.open).subtract(slPoints)).doubleValue());
                                }
                            }
                            LOGGER.info("setting sl price based on current:" + historicalData1.timeStamp + "  open:" + historicalData1.open + ":" + tradeData.getStockId() + ":" + triggerPriceAtomic.get() + ":" + tradeData.getUserId());
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
                        if ("SS".equals(strategy.getIndex())) {
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
                                tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
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
                                    tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: " + finalSelected.getValue().getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
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
                                    tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + finalSelected.getValue().getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(), "exp-trade");

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
                        if ("SS".equals(strategy.getIndex())) {
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
                                tradeData.setStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                                tradeData.setTradeStrategy(strategy);
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
                                    tradeSedaQueue.sendTelemgramSeda("Options traded for user:" + user.getName() + " strike: " + finalSelected.getValue().getZerodhaSymbol() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
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
                                    tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + finalSelected.getValue().getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(), "exp-trade");

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
                                                tradeDataRetry.setStockId(Integer.valueOf(strikeData.getZerodhaId()));
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

public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData, boolean orderPlaced) {
    try {
        tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData, orderPlaced, this.getAlgoName());
        LOGGER.info("sucessfully saved trade data");
    } catch (Exception e) {
        LOGGER.info(e.getMessage());
    }

}

//@Scheduled(cron = "${tradeEngine.load.bbs.update}")
public void bbsUpdate() throws ParseException {
    Date date = new Date();
    System.out.println("started bbs update");
    //      MDC.put("run_time",candleDateTimeFormat.format(date));
    String currentDateStr = dateFormat.format(date);
    List<HistoricalData> dataArrayList = new ArrayList();
    List<IndicatorData> indicatorDataList = indicatorDataRepo.getLast20IndicatorData();
    List<HistoricalDataExtended> dataArrayList3MHistory = mapBBSData(indicatorDataList);
    IndicatorData lastCandle = indicatorDataList.get(indicatorDataList.size() - 1);
    int j = lastCandle.getIndicatorDataKey() + 1;
    try {

        String stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:15:00&to=" + currentDateStr + "+15:30:00";
        System.out.println(historicURL);
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.println(response);
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            int i = 0;
            dataArrayList.addAll(historicalData.dataArrayList);

        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    List<HistoricalDataExtended> dataArrayList3M = new ArrayList<>();
    dataArrayList3M.addAll(dataArrayList3MHistory);
    List<HistoricalDataExtended> dataArrayList3MRecentTemp = DataResampler.resampleOHLCData(dataArrayList, 3);
    dataArrayList3M.addAll(dataArrayList3MRecentTemp);
    LinkedList<Double> closingPrices = new LinkedList<>();
    int i = 0;
    while (i < dataArrayList3M.size()) {
        HistoricalDataExtended historicalDataExtended = dataArrayList3M.get(i);
        closingPrices.add(historicalDataExtended.getClose());
        if (closingPrices.size() > 20) {
            closingPrices.removeFirst();
        }
        if (i >= 20 - 1 && historicalDataExtended.sma == 0) {
            double sma = MathUtils.calculateSMA(closingPrices);
            double standardDeviation = MathUtils.calculateStandardDeviation(closingPrices, sma);
            double lowerBand = sma - 2 * standardDeviation;
            double upperBand = sma + 2 * standardDeviation;
            double bandwidth = upperBand - lowerBand;
            // Store the calculated values back in the candle
            historicalDataExtended.setSma(sma);
            historicalDataExtended.setBolingerLowerBand(lowerBand);
            historicalDataExtended.setBolingerUpperBand(upperBand);
            historicalDataExtended.setBolingerBandwith(bandwidth);
            IndicatorData indicatorData = new IndicatorData();
            indicatorData.setDataKey("BNF-260105-3M");
            indicatorData.setIndicatorDataKey(j);
            indicatorData.setOpen(BigDecimal.valueOf(historicalDataExtended.open));
            indicatorData.setClose(BigDecimal.valueOf(historicalDataExtended.close));
            indicatorData.setLow(BigDecimal.valueOf(historicalDataExtended.low));
            indicatorData.setHigh(BigDecimal.valueOf(historicalDataExtended.high));
            indicatorData.setBbSma(BigDecimal.valueOf(historicalDataExtended.getSma()));
            indicatorData.setBbLowerband(BigDecimal.valueOf(historicalDataExtended.getBolingerLowerBand()));
            indicatorData.setBbUpperband(BigDecimal.valueOf(historicalDataExtended.getBolingerUpperBand()));
            Timestamp timestamp = new Timestamp(candleDateTimeFormat.parse(historicalDataExtended.getTimeStamp()).getTime());
            indicatorData.setCandleTime(timestamp);
            try {
                indicatorDataRepo.save(indicatorData);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        i++;
        j++;
    }
    System.out.println(new Gson().toJson(dataArrayList3M));
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
}

@Scheduled(cron = "${tradeEngine.websocket.initialize}")
public void tickerInitialize() throws KiteException {
    try {
        if (websocketEnabled) {
            User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
            KiteConnect kiteConnect = user.getKiteConnect();
            tickerProvider = new KiteTicker(kiteConnect.getAccessToken(), kiteConnect.getApiKey());
            addIndexOptionstoWebsocket();
            tickerProvider.setTryReconnection(true);
            tickerProvider.setMaximumRetries(10);
            tickerProvider.setMaximumRetryInterval(30);
            // listOfTokens.add(Long.parseLong("65611015"));
            listOfTokens.add(Long.parseLong("256265"));
            tickerProvider.subscribe(listOfTokens);
            tickerProvider.connect();

            if (tickerProvider.isConnectionOpen()) {
                System.out.println("Websocket Connected");
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                System.out.println("added token:" + gson.toJson(listOfTokens));
            }
            tickerProvider.setOnDisconnectedListener(new OnDisconnect() {
                @Override
                public void onDisconnected() {
                    LOGGER.error("websocket disconnected");
                    tradeSedaQueue.sendTelemgramSeda("Websocket disconnected", "exp-trade");
                }
            });
            tickerProvider.setOnConnectedListener(() -> {
                System.out.println("adding token on connected:" + gson.toJson(listOfTokens));
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                System.out.println("added token:" + gson.toJson(listOfTokens));
            });
            /** Set listener to get order updates.*/
            tickerProvider.setOnOrderUpdateListener(new OnOrderUpdate() {
                @Override
                public void onOrderUpdate(Order order) {
                    //    System.out.println("trade engine order update " + order.orderId+":"+order.status+":"+order.symbol+":"+order.orderType+":"+order.product+":"+order.quantity);
                    tradeSedaQueue.sendWebsocketOrderUpdateSeda(gson.toJson(order));
                }
            });

            /** Set error listener to listen to errors.*/
            tickerProvider.setOnErrorListener(new OnError() {
                @Override
                public void onError(Exception exception) {
                    //handle here.
                    LOGGER.info("websocket exception: {}", exception.getMessage());
                    tradeSedaQueue.sendTelemgramSeda("Websocket error: " + exception.getMessage(), "exp-trade");
                }

                @Override
                public void onError(KiteException kiteException) {
                    LOGGER.info("websocket kite exception: {}", kiteException.getMessage());
                    tradeSedaQueue.sendTelemgramSeda("Websocket error: " + kiteException.getMessage(), "exp-trade");
                }

                @Override
                public void onError(String error) {
                    LOGGER.info("websocket error: {}", error);
                    tradeSedaQueue.sendTelemgramSeda("Websocket error: " + error, "exp-trade");
                }
            });

            tickerProvider.setOnTickerArrivalListener(new OnTicks() {
                @Override
                public void onTicks(ArrayList<Tick> ticks) {
                    try {
                        //     System.out.println("ticks size " + ticks.size());
                        // if (!ticks.isEmpty()) {
                        tradeSedaQueue.sendWebsocketTicksSeda(gson.toJson(ticks));
                        //   }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    } catch (Exception e) {

        e.printStackTrace();
        LOGGER.info("error:" + e.getMessage());
    }

}

CSVWriter tradeStrikeWriter;

@PostConstruct
public void initializetradeStrike() throws IOException {
    Date date = new Date();
    tradeStrikeWriter = new CSVWriter(new FileWriter("/home/ubuntu/trade_instrument_" + dateFormat.format(date) + ".csv", true));
    try {
        String[] dataHeader = {"instrument_token", "strike_name", "expiry", "index"};
        tradeStrikeWriter.writeNext(dataHeader);
        tradeStrikeWriter.flush();
    } catch (Exception e) {
        e.printStackTrace();
    }
}

@Scheduled(cron = "${tradeEngine.websocket.addStrike}")
public void addIndexOptionstoWebsocket() throws IOException {
    if (websocketEnabled) {
        Date date = new Date();
        CSVWriter csvWriter = new CSVWriter(new FileWriter("/home/ubuntu/instrument_" + dateFormat.format(date) + ".csv", true));
        try {
            String[] dataHeader = {"instrument_token", "strike_name", "expiry", "index"};
            csvWriter.writeNext(dataHeader);
            csvWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            try {
                String stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                LOGGER.info("NIFTY BANK:{}", stockId);
                listOfTokens.add(Long.parseLong(stockId));
                int bnfAtm = getAtm(stockId, "BNF");
                int bnfAtmLow = bnfAtm - 1000;
                int bnfAtmHigh = bnfAtm + 1000;
                while (bnfAtmLow < bnfAtmHigh) {
                    Map<String, String> strikes = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(bnfAtmLow));
                    bnfAtmLow = bnfAtmLow + 100;
                    strikes.forEach((key, value) -> {
                        listOfTokens.add(Long.parseLong(value));
                        try {
                            String[] dataHeader = {value, key, zerodhaTransactionService.bankBiftyExpDate, "BNF"};
                            csvWriter.writeNext(dataHeader);
                            csvWriter.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                String nstockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                LOGGER.info("NIFTY :{}", nstockId);
                listOfTokens.add(Long.parseLong(nstockId));
                int niftyAtm = getAtm(nstockId, "NF");
                int niftyAtmLow = niftyAtm - 400;
                int niftyAtmHigh = niftyAtm + 400;
                while (niftyAtmLow < niftyAtmHigh) {
                    Map<String, String> strikes = zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(niftyAtmLow));
                    niftyAtmLow = niftyAtmLow + 50;
                    strikes.forEach((key, value) -> {
                        listOfTokens.add(Long.parseLong(value));
                        try {
                            String[] dataHeader = {value, key, zerodhaTransactionService.expDate, "NF"};
                            csvWriter.writeNext(dataHeader);
                            csvWriter.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                String nstockId = zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
                listOfTokens.add(Long.parseLong(nstockId));
                LOGGER.info("NIFTY MID SELECT :{}", nstockId);
                int niftyAtm = getAtm(nstockId, "MC");
                int niftyAtmLow = niftyAtm - 200;
                int niftyAtmHigh = niftyAtm + 200;
                while (niftyAtmLow < niftyAtmHigh) {
                    Map<String, String> strikes = zerodhaTransactionService.midcpWeeklyOptions.get(String.valueOf(niftyAtmLow));
                    niftyAtmLow = niftyAtmLow + 25;
                    strikes.forEach((key, value) -> {
                        listOfTokens.add(Long.parseLong(value));
                        try {
                            String[] dataHeader = {value, key, zerodhaTransactionService.midCpExpDate, "MC"};
                            csvWriter.writeNext(dataHeader);
                            csvWriter.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                String nstockId = zerodhaTransactionService.niftyIndics.get("SENSEX");
                LOGGER.info("SENSEX :{}", nstockId);
                listOfTokens.add(Long.parseLong(nstockId));
                int niftyAtm = getAtm(nstockId, "SS");
                int niftyAtmLow = niftyAtm - 1000;
                int niftyAtmHigh = niftyAtm + 1000;
                while (niftyAtmLow < niftyAtmHigh) {
                    Map<String, String> strikes = zerodhaTransactionService.sensexWeeklyOptions.get(String.valueOf(niftyAtmLow));
                    niftyAtmLow = niftyAtmLow + 100;
                    strikes.forEach((key, value) -> {
                        listOfTokens.add(Long.parseLong(value));
                        try {
                            String[] dataHeader = {value, key, zerodhaTransactionService.sensexExpDate, "SS"};
                            csvWriter.writeNext(dataHeader);
                            csvWriter.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                String fnstockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                LOGGER.info("NIFTY FIN SERVICE :{}", fnstockId);
                listOfTokens.add(Long.parseLong(fnstockId));
                int finAtm = getAtm(fnstockId, "FN");
                int finAtmLow = finAtm - 400;
                int finAtmHigh = finAtm + 400;
                while (finAtmLow < finAtmHigh) {
                    Map<String, String> strikes = zerodhaTransactionService.finNiftyWeeklyOptions.get(String.valueOf(finAtmLow));
                    strikes.forEach((key, value) -> {
                        listOfTokens.add(Long.parseLong(value));
                        try {
                            String[] dataHeader = {value, key, zerodhaTransactionService.finExpDate, "FN"};
                            csvWriter.writeNext(dataHeader);
                            csvWriter.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    finAtmLow = finAtmLow + 50;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (tickerProvider.isConnectionOpen()) {
                    tickerProvider.subscribe(listOfTokens);
                    tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                    System.out.println("added token:" + gson.toJson(listOfTokens));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

@Scheduled(cron = "${tradeEngine.websocket.tick.export}")
public void tickExport() throws IOException {
    if (websocketEnabled) {
        if (dataExport) {
            Date date = new Date();
            zippingDirectory.zipFile("tick_" + dateFormat.format(date) + ".csv", "tick_" + dateFormat.format(date), "/home/ubuntu", "instrument_" + dateFormat.format(date) + ".csv", "trade_instrument_" + dateFormat.format(date) + ".csv");
            telegramClient.sendDocumentToTelegram("/home/ubuntu/tick_" + dateFormat.format(date) + ".zip", "Tick_" + dateFormat.format(date));
            FileUtils.delete(new File("/home/ubuntu/tick_" + dateFormat.format(date) + ".zip"));
            // FileUtils.delete(new File("/home/ubuntu/tick_" + dateFormat.format(date) + ".csv"));
            // FileUtils.delete(new File("/home/ubuntu/instrument_" + dateFormat.format(date) + ".csv"));
            //FileUtils.delete(new File("/home/ubuntu/trade_instrument_" + dateFormat.format(date) + ".csv"));
        }
    }
}

public int getAtm(String strikeId, String index) {
    Date date = new Date();
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_MONTH, -7);
    Date date1 = cal.getTime();
    //      MDC.put("run_time",candleDateTimeFormat.format(date));
    String currentDateStr = dateFormat.format(date);
    String fromDateStr = dateFormat.format(date1);
    String historicURL = "https://api.kite.trade/instruments/historical/" + strikeId + "/day?from=" + fromDateStr + "+00:00:00&to=" + currentDateStr + "+00:00:00";
    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
    HistoricalData historicalDataRes = new HistoricalData();
    JSONObject json = new JSONObject(response);
    String status = json.getString("status");
    if (!status.equals("error")) {
        historicalDataRes.parseResponse(json);
        Optional<HistoricalData> optionalHistoricalLatestData = Optional.ofNullable(historicalDataRes.dataArrayList.get(historicalDataRes.dataArrayList.size() - 1));
        if (optionalHistoricalLatestData.isPresent()) {
            HistoricalData historicalData = optionalHistoricalLatestData.get();
            return commonUtil.findATM((int) historicalData.close, index);
        }
    }
    return 0;
}

public List<HistoricalDataExtended> mapBBSData(List<IndicatorData> indicatorDataList) {
    List<HistoricalDataExtended> dataArrayList3M = new ArrayList<>();
    indicatorDataList.forEach(indicatorData -> {
        HistoricalDataExtended historicalDataExtended = new HistoricalDataExtended();
        historicalDataExtended.open = indicatorData.getOpen().doubleValue();
        historicalDataExtended.high = indicatorData.getHigh().doubleValue();
        historicalDataExtended.low = indicatorData.getLow().doubleValue();
        historicalDataExtended.close = indicatorData.getClose().doubleValue();
        historicalDataExtended.sma = indicatorData.getBbSma().doubleValue();
        historicalDataExtended.bolingerLowerBand = indicatorData.getBbLowerband().doubleValue();
        historicalDataExtended.bolingerUpperBand = indicatorData.getBbUpperband().doubleValue();
        historicalDataExtended.timeStamp = candleDateTimeFormat.format(indicatorData.getCandleTime().getTime());
        dataArrayList3M.add(historicalDataExtended);
    });
    return dataArrayList3M;
}

public void addStriketoWebsocket(Long token) throws KiteException {
    try {
        if (tickerProvider != null && tickerProvider.isConnectionOpen()) {
            listOfTokens.add(token);
            tickerProvider.subscribe(listOfTokens);
            tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);

        } else {
            tickerInitialize();
            if (tickerProvider.isConnectionOpen()) {
                listOfTokens.add(token);
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                //LOGGER.info("added token:" + token);
            }
        }
        LOGGER.info("added token:" + token);
    } catch (Exception e) {
        e.printStackTrace();
    }
}

public void addTradeStriketoWebsocket(Long token, String stockName, String index) throws KiteException {
    try {
        try {
            String[] dataHeader = {String.valueOf(token), stockName, "", index};
            tradeStrikeWriter.writeNext(dataHeader);
            tradeStrikeWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (tickerProvider != null && tickerProvider.isConnectionOpen()) {
            listOfTokens.add(token);
            tickerProvider.subscribe(listOfTokens);
            tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
            // LOGGER.info("added token:" + token);
        } else {
            tickerInitialize();
            if (tickerProvider.isConnectionOpen()) {
                listOfTokens.add(token);
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                //  LOGGER.info("added token:" + token);
            }
        }
        LOGGER.info("added token:" + token);
    } catch (Exception e) {
        e.printStackTrace();
    }
}

public void TableToImage() {
    String[] columnNames = {"Column 1", "Column 2", "Column 3"};
    DefaultTableModel model = new DefaultTableModel(columnNames, 0);
    JTable table = new JTable(model);

    openTrade.forEach((key, value) -> value.forEach(tradeData -> {
        String qty = null;
        String buyPrice = null;
        String sellPrice = null;
        if (tradeData.getQty() > 0) {
            qty = String.valueOf(tradeData.getQty());
        }
        if (tradeData.getBuyPrice() != null) {
            buyPrice = tradeData.getBuyPrice().toString();
        }
        if (tradeData.getSellPrice() != null) {
            sellPrice = tradeData.getSellPrice().toString();
        }
        String[] newRow = {tradeData.getTradeStrategy().getTradeStrategyKey(), tradeData.getStockName(), qty, buyPrice,
                sellPrice};
        model.addRow(newRow);
    }));

    // Render to BufferedImage
    table.setPreferredScrollableViewportSize(table.getPreferredSize());
    BufferedImage bufferedImage = new BufferedImage(table.getWidth(), table.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bufferedImage.createGraphics();
    table.printAll(g2d);
    g2d.dispose();

    // Save as PNG
    try {
        ImageIO.write(bufferedImage, "png", new File("/home/hasvanth/pl.png"));
    } catch (IOException e) {
        e.printStackTrace();
    }
}

DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

@Scheduled(cron = "${tradeEngine.limit.order.monitor}")
public void limitOrderMonitor() {
    openTrade.entrySet().stream().forEach(userTradeData -> {
        String userId = userTradeData.getKey();
        //  User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
        //  BrokerWorker brokerWorker = workerFactory.getWorker(user);
        List<TradeData> tradeDataList = userTradeData.getValue();
        tradeDataList.stream().filter(trade -> !trade.isSLHit && !trade.isExited && trade.isOrderPlaced && trade.isSlPlaced).forEach(tradeData -> {
            if (tradeData.getTradeStrategy().isWebsocketSlEnabled() && tradeData.isWebsocketSlModified()) {
                if (tradeData.getWebsocketSlTime() != null) {
                    LocalDateTime websocketTime = LocalDateTime.parse(tradeData.getWebsocketSlTime(), dateTimeFormatter);
                    LocalDateTime now = LocalDateTime.now();
                    Duration duration = Duration.between(websocketTime, now);
                    if (duration.getSeconds() > 30) {
                        String slId = tradeData.getSlOrderId();
                        OrderParams orderParams = new OrderParams();
                        orderParams.triggerPrice = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                        orderParams.price = tradeData.getSlPrice().add(tradeData.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                        orderParams.orderType = "MARKET";
                        postOrderModifyDataToSeda(tradeData, orderParams, slId);
                        tradeSedaQueue.sendTelemgramSeda("converted websocket limit to market order:" + userId + ":" + tradeData.getStockName() + ":" + tradeData.getWebsocketSlTime(), "exp-trade");
                    } else {
                        tradeSedaQueue.sendTelemgramSeda("limit order placed, monitoring sl for 30 sec exit:" + userId + ":" + tradeData.getStockName() + ":" + tradeData.getWebsocketSlTime(), "error");
                        LOGGER.info("limit order placed, monitoring sl for 30 sec exit:{},{},{}", userId, tradeData.getStockName(), tradeData.getWebsocketSlTime());
                    }
                } else {
                    LOGGER.error("websocket time is null:{},{}", userId, tradeData.getStockName());
                }
            }
        });
    });
}

@Scheduled(cron = "${tradeEngine.candle.order.monitor}")
public void candleMonitor() {
    openTrade.forEach((userId, tradeDataList) -> {
        //  User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
        //  BrokerWorker brokerWorker = workerFactory.getWorker(user);
        tradeDataList.stream().filter(trade -> !trade.isSLHit && !trade.isExited && trade.isOrderPlaced && trade.isSlPlaced).forEach(tradeData -> {
            if (tradeData.getTradeStrategy().isWebsocketSlEnabled()) {
                if (!tradeData.isWebsocketSlModified()) {
                    Date date = new Date();
                    String currentDateStr = dateFormat.format(date);
                    Calendar candleCalenderMin = Calendar.getInstance();
                    Calendar calendarCurrentMin = Calendar.getInstance();
                    candleCalenderMin.add(Calendar.MINUTE, -1);
                    Date currentMinDate = calendarCurrentMin.getTime();
                    Date candleCurrentMinDate = candleCalenderMin.getTime();
                    String candleHourMinStr = hourMinFormat.format(candleCurrentMinDate);
                    String currentHourMinStr = hourMinFormat.format(currentMinDate);
                    String stockId = String.valueOf(tradeData.getStockId());
                    String historicURL = "https://api.kite.trade/instruments/historical/" + tradeData.getStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), stockId, currentHourMinStr);
                    // LOGGER.info("Index response: {}:{}", tradeData, response);
                    HistoricalData historicalData = new HistoricalData();
                    JSONObject json = new JSONObject(response);
                    String status = json.getString("status");
                    if (!status.equals("error")) {
                        historicalData.parseResponse(json);
                        if (!historicalData.dataArrayList.isEmpty()) {
                            HistoricalData optionalHistoricalLatestData = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                            if (optionalHistoricalLatestData != null) {
                                if (optionalHistoricalLatestData.high >= tradeData.getSlPrice().doubleValue() && tradeData.isSlPriceCalculated && !tradeData.isSlPlaced) {
                                    try {
                                        User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
                                        BrokerWorker brokerWorker = workerFactory.getWorker(user);
                                        TradeStrategy strategy = tradeData.getTradeStrategy();
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
                                            LocalDateTime currentDateTime = LocalDateTime.now();
                                            tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                                            mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                        }
                                        mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                        LOGGER.info("placed limit based on candle high:" + userId + ":" + tradeData.getStockName() + " candle time:" + optionalHistoricalLatestData.timeStamp + " candle high: " + optionalHistoricalLatestData.high + " trade sl:" + tradeData.getSlPrice(), "exp-trade");
                                        tradeSedaQueue.sendTelemgramSeda("placed limit based on candle high:" + userId + ":" + tradeData.getStockName() + ":" + tradeData.getWebsocketSlTime(), "exp-trade");
                                    } catch (Exception e) {
                                        LOGGER.error("error while placing sl limit order based on candle high:{},{}", userId, tradeData.getStockName());
                                    } catch (KiteException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LOGGER.error("websocket time is null:{},{}", userId, tradeData.getStockName());
                }
            }
        });
    });
}

public void postOrderModifyDataToSeda(TradeData tradeData, OrderParams orderParams, String orderId) {
    //  tradeData.getTradeStrategy().getUserSubscriptions().getUserSubscriptionList().stream().forEach(userSubscription -> {
    User userB = userList.getUser().stream().filter(user -> tradeData.getUserId().equals(user.getName())).findFirst().get();
    OrderSedaData orderSedaData = new OrderSedaData();
    orderSedaData.setOrderParams(orderParams);
    orderSedaData.setUser(userB);
    orderSedaData.setOrderModificationType("modify");
    orderSedaData.setOrderId(orderId);
    // tradeData.isExited=true;
    // tradeData.isSLHit=true;
    tradeSedaQueue.sendOrderPlaceSeda(orderSedaData);
}

@Scheduled(cron = "${tradeEngine.position.data}")
public void sendPositionData() {
    try { //TODO: if everything or open main position closed then override position
        AtomicDouble pnl = new AtomicDouble(0);
        User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
        BrokerWorker brokerWorker = workerFactory.getWorker(user);
        openTrade.entrySet().stream().forEach(userTradeData -> {
            String userId = userTradeData.getKey();
            if (userId.equals(user.getName())) {
                List<TradeData> tradeDataList = userTradeData.getValue();
                tradeDataList.stream().forEach(tradeData -> {
                    if (tradeData.isExited || tradeData.isSLHit) {
                        try {
                            if (tradeData.getSellPrice() != null && tradeData.getBuyPrice() != null) {
                                double pl = tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).doubleValue();
                                if (tradeData.getBuyTradedPrice() != null) {
                                    pl = tradeData.getSellPrice().subtract(tradeData.getBuyTradedPrice()).doubleValue();
                                }
                                double plq = pl * tradeData.getQty();
                                pnl.getAndAdd(plq);
                                //   LOGGER.info("closed position:{}:pl {}", tradeData.getStockName(), plq);
                            }
                        } catch (Exception e) {
                            LOGGER.info("error while calculating pl for closed position:{}:{}", tradeData.getStockName(), e.getMessage());
                        }
                        // else{
                        //   double pl=tradeData.getBuyPrice().subtract(tradeData.getSellPrice()).doubleValue();
                        //pnl.getAndAdd(pl);
                        // }
                    } else {
                        try {
                            String[] instrument = new String[]{""};
                            String quoteStr = "NFO:" + tradeData.getStockName();
                            if ("SS".equals(tradeData.getTradeStrategy().getIndex())) {
                                quoteStr = "BFO:" + tradeData.getStockName();
                            }
                            instrument[0] = quoteStr;
                            Map<String, LTPQuote> quoteMap = brokerWorker.getQuotesLtp(user, instrument);
                            LTPQuote ltpQuote = quoteMap.get(quoteStr);
                            if (ltpQuote != null) {
                                // LOGGER.info("Last trade price:{}:{}", tradeData.getStockName(), ltpQuote.lastPrice);
                                if ("SELL".equals(tradeData.getEntryType()) && tradeData.getSellPrice() != null && tradeData.getSellPrice().doubleValue() > 0
                                        && ltpQuote.lastPrice > 0) {
                                    double pl = tradeData.getSellPrice().subtract(new BigDecimal(ltpQuote.lastPrice)).doubleValue();
                                    double plq = pl * tradeData.getQty();
                                    pnl.getAndAdd(plq);
                                    // LOGGER.info("open position:{} pl: {},quote: {}", tradeData.getStockName(), plq,ltpQuote.lastPrice);
                                }
                                if ("BUY".equals(tradeData.getEntryType()) && tradeData.getBuyPrice() != null && tradeData.getBuyPrice().doubleValue() > 0
                                        && ltpQuote.lastPrice > 0) {
                                    double pl = (new BigDecimal(ltpQuote.lastPrice).subtract(tradeData.getSellPrice())).doubleValue();
                                    double plq = pl * tradeData.getQty();
                                    pnl.getAndAdd(plq);
                                    // LOGGER.info("open position:{} pl: {},quote: {}", tradeData.getStockName(), plq,ltpQuote.lastPrice);
                                }
                            } else {
                                LOGGER.info("zerodha ltpquote is empty:{}", tradeData.getStockName());
                            }
                            /*
                        else{
                            double pl=tradeData.getBuyPrice().subtract(tradeData.getSellPrice()).doubleValue();
                            pnl.getAndAdd(pl);
                        }*/

                        } catch (Exception | KiteException e) {
                            LOGGER.info("error while getting ltpquote:{}:{}", tradeData.getStockName(), e.getMessage());
                        }
                    }
                });

            }
        });
        PositionPLDataEntity livePLDataEntity = new PositionPLDataEntity();
        String dataKey = UUID.randomUUID().toString();
        livePLDataEntity.setDataKey(dataKey);
        livePLDataEntity.setPl(BigDecimal.valueOf(pnl.get()));
        LocalDateTime localDateTime = LocalDateTime.now();
        livePLDataEntity.setDataTime(Timestamp.valueOf(localDateTime));
        positionPLDataRepo.save(livePLDataEntity);
    } catch (Exception e) {
        LOGGER.info("error while calculating live pl:{}", e.getMessage());
    }
}
}
