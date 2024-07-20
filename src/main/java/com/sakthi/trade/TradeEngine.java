package com.sakthi.trade;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.ValueType;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.*;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.service.*;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ExpiryDayDetails;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
@Slf4j
public class TradeEngine {
    public static final Logger LOGGER = LoggerFactory.getLogger(TradeEngine.class.getName());
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    //public Map<String, List<TradeData>> openTrade = Collections.synchronizedMap(new LinkedHashMap<>()); // stock_id: List of trades
    public Map<String, String> lsHoliday = new HashMap<>();
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    @Value("${websocket.freak.sl.type}")
    public String freakSlType;
    @Autowired
    ExpiryDayDetails expiryDayDetails;

    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    public TransactionService transactionService;

    ExecutorService executorThread = Executors.newFixedThreadPool(2);
    ExecutorService executorThreadIndex = Executors.newFixedThreadPool(2);
    ExecutorService executorThreadRangeOptions = Executors.newFixedThreadPool(2);
    ExecutorService sLMonitor = Executors.newFixedThreadPool(1);
    ExecutorService targetMonitor = Executors.newFixedThreadPool(1);
    ExecutorService exitThread = Executors.newFixedThreadPool(1);
    @Autowired
    TradeDataMapper tradeDataMapper;
    @Autowired
    public UserList userList;
    @Autowired
    RangeExecution rangeExecution;
    @Autowired
    BrokerWorkerFactory workerFactory;
    Gson gson = new Gson();
    @Autowired
    MathUtils mathUtils;
    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;
    @Autowired
    ZerodhaWebsocket zerodhaWebsocket;

    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Value("${filepath.trend}")
    String trendPath;
    boolean isHolidayAlertTriggered = false;

    @Autowired
    PositionPLDataRepo positionPLDataRepo;
    @Autowired
    LoadStrategyService loadStrategyService;
    @Autowired
    public TradingStrategyAndTradeData tradingStrategyAndTradeData;
    @Autowired
    TradeHelperService tradeHelperService;
    
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
        loadStrategyService.loadStrategy();
    }

  // @PostConstruct
    public void loadMISPositions() {
       Iterable<OpenTradeDataEntity> openTradeDataEntities1 = openTradeDataRepo.findAll();
       LOGGER.info("started mis data loading");
       List<OpenTradeDataEntity> openList = new ArrayList<>();
       openTradeDataEntities1.forEach(openTradeDataEntity -> {
           //if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
           if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
               openList.add(openTradeDataEntity);
           }
           // }
       });
       openList.forEach(openTradeDataEntity -> {
           LOGGER.info("open data: {}:{}", openTradeDataEntity.getStockName(), openTradeDataEntity.getUserId());
           TradeStrategy tradeStrategyList = tradeStrategyRepo.getStrategyByStrategyKey(openTradeDataEntity.tradeStrategyKey);
           //System.out.println(gson.toJson(tradeStrategyList));
           LocalDate currentDate = LocalDate.now();
           LocalDate tradeDate= LocalDate.parse(openTradeDataEntity.tradeDate);
                  if (currentDate.equals(tradeDate)) {
                      if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
                          User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                          BrokerWorker brokerWorker = workerFactory.getWorker(user);
                          List<Position> positions = brokerWorker.getRateLimitedPositions(user);
                          positions.stream().filter(position -> "MIS".equals(position.product)
                                  && openTradeDataEntity.getStockName().equals(position.tradingSymbol)
                                  && (position.netQuantity != 0)).findFirst().ifPresent(position -> {
                              int positionQty = Math.abs(position.netQuantity);
                              if (positionQty != openTradeDataEntity.getQty()) {
                                  //   openTradeDataEntity.setQty(positionQty);
                                  tradeSedaQueue.sendTelemgramSeda("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty." + ":" + getAlgoName(), "exp-trade");
                                  LOGGER.info("Position qty mismatch for: {}:{}, over riding position qty as trade qty.", openTradeDataEntity.getStockName(), openTradeDataEntity.getUserId());

                              }
                              openTradeDataEntity.isSlPlaced = false;
                              tradeStrategyList.setWebsocketSlEnabled(true);
                              openTradeDataEntity.isOrderPlaced = true;

                              TradeData tradeData = tradeDataMapper.mapTradeDataEntityToTradeData(openTradeDataEntity);
                              List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                              if (tradeDataList == null) {
                                  tradeDataList = new ArrayList<>();
                              }
                              tradeData.setTradeStrategy(tradeStrategyList);
                              //tradeDataList.add(tradeData);
                              LOGGER.info("adding it to list");
                              addTradeIfNotExists(user.getName(), tradeData, tradeDataList);
                              //    tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                      /*  try {
                            zerodhaWebsocket.addTradeStriketoWebsocket(Long.parseLong(tradeData.getStrikeId()), tradeData.getStockName(), tradeStrategyList.getIndex());
                        } catch (Exception | KiteException e) {
                            log.error("error while adding overnight position to websocket:{}, error: {}", tradeData.getStockName(), e.getMessage());
                            e.printStackTrace();
                        }*/

                          });
                      }
                  }
       });
    }
    @Scheduled(cron = "${tradeEngine.load.open.data}")
    public void loadNrmlPositions() {
        Iterable<OpenTradeDataEntity> openTradeDataEntities1 = openTradeDataRepo.findAll();
         LOGGER.info("started overnight data loading");
        List<OpenTradeDataEntity> openList = new ArrayList<>();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            //if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored && !openTradeDataEntity.isSLHit) {
                    openList.add(openTradeDataEntity);
                }
           // }
        });
        // System.out.println(openList);
        //    if (!tradingStrategyAndTradeData.openTrade.isEmpty()) {
        openList.forEach(openTradeDataEntity -> {
            LOGGER.info("open data: {}:{}", openTradeDataEntity.getStockName(), openTradeDataEntity.getUserId());
            TradeStrategy tradeStrategyList = tradeStrategyRepo.getStrategyByStrategyKey(openTradeDataEntity.tradeStrategyKey);
            //System.out.println(gson.toJson(tradeStrategyList));
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
                            tradeSedaQueue.sendTelemgramSeda("MIS Open Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty." + ":" + getAlgoName(), "exp-trade");
                            LOGGER.info("MIS Position qty mismatch for: {}:{}, over riding position qty as trade qty.", openTradeDataEntity.getStockName(), openTradeDataEntity.getUserId());

                        }
                        openTradeDataEntity.isSlPlaced = false;
                        tradeStrategyList.setWebsocketSlEnabled(false);
                        openTradeDataEntity.isOrderPlaced = true;
                        openTradeDataEntity.setSlOrderId(null);

                        TradeData tradeData = tradeDataMapper.mapTradeDataEntityToTradeData(openTradeDataEntity);
                        List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                        if (tradeDataList == null) {
                            tradeDataList = new ArrayList<>();
                        }
                        tradeData.setTradeStrategy(tradeStrategyList);
                        //tradeDataList.add(tradeData);
                        LOGGER.info("adding it to list");
                        addTradeIfNotExists(user.getName(),tradeData, tradeDataList);
                    //    tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                      /*  try {
                            zerodhaWebsocket.addTradeStriketoWebsocket(Long.parseLong(tradeData.getStrikeId()), tradeData.getStockName(), tradeStrategyList.getIndex());
                        } catch (Exception | KiteException e) {
                            log.error("error while adding overnight position to websocket:{}, error: {}", tradeData.getStockName(), e.getMessage());
                            e.printStackTrace();
                        }*/

                    });
                } else {
                    String openStr = gson.toJson(openTradeDataEntity);
                    OpenTradeDataBackupEntity openTradeDataBackupEntity = gson.fromJson(openStr, OpenTradeDataBackupEntity.class);
                    openTradeDataBackupRepo.save(openTradeDataBackupEntity);
                    openTradeDataRepo.deleteById(openTradeDataEntity.getDataKey());
                }
            }
        });
        LOGGER.info("over night data:"+ gson.toJson(tradingStrategyAndTradeData.openTrade));
    }
    public void addTradeIfNotExists(String userName, TradeData newTradeData, List<TradeData> tradeDataList) {
        boolean exists = false;
        for (TradeData tradeData : tradeDataList) {
            if (tradeData.getStockName().equals(newTradeData.getStockName())) {
                System.out.println("exist");
                exists = true;
                break;
            }
        }

        if (!exists) {
            tradeDataList.add(newTradeData);
            tradingStrategyAndTradeData.openTrade.put(userName, tradeDataList);
            tradeSedaQueue.sendTelemgramSeda("MIS Open Position: " + newTradeData.getStockName() + ":" + newTradeData.getUserId() + ":" + getAlgoName(), "exp-trade");
        }
    }
    @Scheduled(cron = "${tradeEngine.execute.strategy}")
    public void executeStrategy() throws IOException, CsvValidationException {
        Date date=new Date();
        //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = tradingStrategyAndTradeData.dateFormat.format(date);
       //         String currentDateStr="2024-06-28";
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
         //   Date currentMinDate = calendarCurrentMin.getTime();
            Date candleCurrentMinDate = candleCalenderMin.getTime();
            String candleHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(candleCurrentMinDate);
          //  String candleHourMinStr="09:34";
            // System.out.println(candleHourMinStr);
            Date currentMinDate = calendarCurrentMin.getTime();
            String currentHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(currentMinDate);
           //  String  currentHourMinStr="09:35";
             System.out.println(currentHourMinStr);
            //   log.info(currentHourMinStr + ":" + "executing");
            executorThreadRangeOptions.submit(() -> tradingStrategyAndTradeData.openTrade.forEach((userId, tradeList) -> tradeList.stream().filter(trade -> trade.range && trade.getEntryOrderId() == null)
                    .forEach(tradeData -> {
                TradeStrategy strategy = tradeData.getTradeStrategy();
                Map<String, Double> orbHighLow = new HashMap<>();
                // if ("ORB".equals(strategy.getRangeType())) {
                try {
                    if ((strategy.getRangeBreakTime()).equals(currentHourMinStr)) {
                        String historicURL = "https://api.kite.trade/instruments/historical/" + tradeData.getZerodhaStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), String.valueOf(tradeData.getZerodhaStockId()), currentHourMinStr);
                        // LOGGER.info("Index response: {}:{}", index, response);
                        HistoricalData historicalData = new HistoricalData();
                        JSONObject json = new JSONObject(response);
                        String status = json.getString("status");
                        if (!status.equals("error")) {
                            historicalData.parseResponse(json);
                        }
                        rangeExecution.orbHighLowOptions(historicalData.dataArrayList, orbHighLow, strategy, tradeData, currentDateStr);
                        tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData,false);
                    }
                } catch (Exception e) {
                    log.error("error while executing range high low for options:{},{}", e.getMessage(), tradeData.getStockName());
                }
            })));
            executorThread.submit(() -> {
                //ORB range break code starts
                try {
                    tradingStrategyAndTradeData.rangeStrategyMap.forEach((index, strategyList) -> rangeExecution.processStrategy(index, strategyList, currentDateStr, currentHourMinStr, candleHourMinStr));
                } catch (Exception e) {
                    log.info("error3 while executing range options:{}", e.getMessage());
                    e.printStackTrace();
                }
                //ORB range break code End
                tradingStrategyAndTradeData.strategyMap.forEach((index, timeStrategyListMap) -> {
                    List<TradeStrategy> strategies = timeStrategyListMap.get(currentHourMinStr);
                    //  log.info(currentHourMinStr + ":" + "executing strategyMap");
                    if (strategies != null && !strategies.isEmpty()) {
                        log.info("{}:executing strategyMap", currentHourMinStr);
                        executorThreadIndex.submit(() -> {
                            String stockId = null;
                            String response1 = null;
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
                                }else if ("BNX".equals(index)) {
                                    stockId = zerodhaTransactionService.niftyIndics.get("BANKEX");
                                }
                                String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), stockId, currentHourMinStr);
                                LOGGER.info("Index response: {}:{}", index, response);
                                HistoricalData historicalData = new HistoricalData();
                                response1 = response;
                                JSONObject json = new JSONObject(response);
                                String status = json.getString("status");
                                if (!status.equals("error")) {
                                    historicalData.parseResponse(json);
                                    Optional<HistoricalData> optionalHistoricalLatestData = historicalData.dataArrayList.stream().filter(candle -> {
                                        try {
                                            //      System.out.println(candle.timeStamp);
                                            Date candleDateTime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(candle.timeStamp);
                                            return tradingStrategyAndTradeData.hourMinFormat.format(candleDateTime).equals(candleHourMinStr);
                                        } catch (ParseException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }).findFirst();
                                    if (optionalHistoricalLatestData.isPresent()) {
                                        String finalStockId = stockId;
                                        Optional.of(optionalHistoricalLatestData).ifPresent(lastHistoricalDataOp -> {
                                            HistoricalData lastHistoricalData = lastHistoricalDataOp.get();
                                            LOGGER.info("last candle time and close: {}:{}", lastHistoricalData.timeStamp, lastHistoricalData.close);
                                            // List<TradeStrategy> strategies = timeStrategyListMap.get(currentHourMinStr);
                                            try {
                                                LOGGER.info("TICK atmStrike:{}", expiryDayDetails.currentATMList.get(Long.parseLong(finalStockId)));
                                            } catch (Exception e) {
                                                LOGGER.error("error while getting tick atm strike:{}", e.getMessage());
                                            }
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
                                                                                Date candleDateTime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(candle.timeStamp);
                                                                                return tradingStrategyAndTradeData.hourMinFormat.format(candleDateTime).equals(candleHourMinStr);
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
                                                            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                                                orderParams.exchange = "BFO";
                                                            }
                                                            if ("MIS".equals(strategy.getTradeValidity())) {
                                                                orderParams.product = "MIS";
                                                            } else {
                                                                orderParams.product = "NRML";
                                                            }
                                                            if ("MIS".equals(strategy.getTradeValidity()) && "BUY".equals(strategy.getOrderType()) && !strategy.isHedge()) {
                                                                orderParams.product = "NRML";
                                                            }
                                                            orderParams.transactionType = strategy.getOrderType();
                                                            orderParams.validity = "DAY";
                                                            if (strategy.getTradeStrategyKey().length() <= 20) {
                                                                orderParams.tag = strategy.getTradeStrategyKey();
                                                            }

                                                            strategy.getUserSubscriptions().getUserSubscriptionList().forEach(userSubscription -> userList.getUser().stream().filter(
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
                                                                    tradeData.setZerodhaStockId(Integer.parseInt(strikeData.getZerodhaId()));
                                                                    try {
                                                                        zerodhaWebsocket.addTradeStriketoWebsocket(Long.parseLong(strikeData.getZerodhaId()), tradeData.getStockName(), index);
                                                                    } catch (Exception e) {
                                                                        e.printStackTrace();
                                                                    }
                                                                    tradeData.setStrikeId(strikeData.getDhanId());
                                                                    tradeData.setTradeStrategy(strategy);
                                                                    order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                                    if (order != null) {
                                                                        tradeData.setEntryOrderId(order.orderId);
                                                                    }
                                                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, true);
                                                                    List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                                                    if (tradeDataList == null) {
                                                                        tradeDataList = new ArrayList<>();
                                                                    }
                                                                    tradeDataList.add(tradeData);
                                                                    tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                                                    LOGGER.info("trade data{}", new Gson().toJson(tradeData));
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
                                                                    LOGGER.info("Error while placing straddle order: {}", e);
                                                                    e.printStackTrace();
                                                                    tradeSedaQueue.sendTelemgramSeda("Error while placing straddle order: " + strikeData.getZerodhaSymbol() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");

                                                                    } catch (KiteException e) {
                                                                        e.printStackTrace();
                                                                    LOGGER.info("KITE Error while placing straddle order: {}", e);
                                                                    }
                                                                }));
                                                            });
                                                        });

                                                } catch (Exception e) {
                                                    LOGGER.error("Error1 while executing strategy:{}", e.getMessage());
                                                    tradeSedaQueue.sendTelemgramSeda("Error1 while executing strategy :" + strategy.getTradeStrategyKey() + ":" + e.getMessage(), "error");
                                                }
                                            });
                                            //  });


                                        });
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("response:{} error:{}", response1, e.getMessage());
                                tradeSedaQueue.sendTelemgramSeda("Error2 while executing index strategy :" + index + ":" + e.getMessage(), "error");
                            }
                        });
                    }
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

    public void exitCode(String currentDateStr, String currentHourMinStr) {
        exitThread.submit(() -> tradingStrategyAndTradeData.openTrade.forEach((userId, tradeDataList) -> {
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
                                    (tradingStrategyAndTradeData.dateFormat.parse(tradeData.getTradeDate()).before(tradingStrategyAndTradeData.dateFormat.parse(currentDateStr)) &&
                                            (strategy.getTradeValidity().equals("BTST") || strategy.getTradeValidity().equals("CNC"))) || ("SELL".equals(strategy.getOrderType())
                                    && "MIS".equals(strategy.getTradeValidity()) && strategy.isRangeBreak() && "NF".equals(strategy.getIndex()))) {
                                if (currentHourMinStr.equals(strategy.getExitTime())) {
                                    if (!tradeData.getTradeStrategy().isNoSl()) {
                                        orders.stream().filter(order -> ("OPEN".equals(order.status)
                                                || "TRIGGER PENDING".equals(order.status))
                                                && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                            try {
                                                LOGGER.info(strategy.getTradeStrategyKey() + ":" + "sl order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName());
                                                brokerWorker.cancelOrder(orderr.orderId, user);
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Triggered sl cancellation:" + tradeData.getUserId() + ":stike:" + tradeData.getStockName(), "exp-trade");
                                            } catch (Exception e) {
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                LOGGER.info(e.getMessage());
                                            } catch (KiteException e) {
                                                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                                e.printStackTrace();
                                            }
                                        });
                                    }
                                    orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status))
                                            && order.orderId.equals(tradeData.getEntryOrderId()) && !tradeData.isOrderPlaced()).forEach(orderr -> {
                                        try {
                                            LOGGER.info(strategy.getTradeStrategyKey() + ":" + "retry order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName());
                                            brokerWorker.cancelOrder(orderr.orderId, user);
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Retry cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "exp-trade");
                                        } catch (Exception e) {
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                            LOGGER.info(e.getMessage());
                                        } catch (KiteException e) {
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                            e.printStackTrace();
                                        }
                                    });
                                    orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status))
                                            && order.orderId.equals(tradeData.getEntryOrderId())).forEach(orderr -> {
                                        try {
                                            LOGGER.info(strategy.getTradeStrategyKey() + ":" + "Pending order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName());
                                            brokerWorker.cancelOrder(orderr.orderId, user);
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Pending order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "exp-trade");
                                        } catch (Exception e) {
                                            LOGGER.info(e.getMessage());
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                        } catch (KiteException e) {
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                            e.printStackTrace();
                                        }
                                    });
                                    try{
                                    positions.stream().filter(position -> (tradeData.getStockName().equals(position.tradingSymbol)
                                            && tradeData.isOrderPlaced() && !tradeData.isExited() && (position.netQuantity != 0)))
                                            .forEach(position -> {
                                        OrderParams orderParams = new OrderParams();
                                        orderParams.tradingsymbol = position.tradingSymbol;
                                        orderParams.exchange = "NFO";
                                        if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
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
                                        if ("MIS".equals(strategy.getTradeValidity()) && "BUY".equals(strategy.getOrderType()) && !strategy.isHedge()) {
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
                                            tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                            //   tradeSedaQueue.sendTelemgramSeda(message + ":" + tradeData.getUserId() + ":" + getAlgoName(), "error");

                                        } catch (Exception e) {
                                            LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                            tradeSedaQueue.sendTelemgramSeda("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position) + ":" + getAlgoName(), "error");
                                            LOGGER.error("error:{}", e.getMessage());
                                        } catch (KiteException e) {
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                            e.printStackTrace();
                                        }

                                    });}
                                    catch (Exception e) {
                                        LOGGER.info(e.getMessage());
                                        tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while exiting cancellation:" + tradeData.getUserId() + ":strike:" + tradeData.getStockName(), "error");
                                    }
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
        }));
    }

    @Scheduled(cron = "${tradeEngine.cnc.sl}")
    public void cncSlCode() {
      //  LOGGER.info("executing cnc SL");
        Date date = new Date();
        String currentDateStr = tradingStrategyAndTradeData.dateFormat.format(date);
        Calendar candleCalenderMin = Calendar.getInstance();
        Calendar calendarCurrentMin = Calendar.getInstance();
        candleCalenderMin.add(Calendar.MINUTE, -1);
        Date currentMinDate = calendarCurrentMin.getTime();
        Date candleCurrentMinDate = candleCalenderMin.getTime();
        String candleHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(candleCurrentMinDate);
        String currentHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(currentMinDate);
        tradingStrategyAndTradeData.openTrade.forEach((userId, tradeData) -> {
      //      LOGGER.info("executing cnc SL:{}:", userId);
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
                        if (tradingStrategyAndTradeData.dateFormat.parse(trendTradeData.getTradeDate()).before(tradingStrategyAndTradeData.dateFormat.parse(currentDateStr))
                                && strategy != null && (strategy.getTradeValidity().equals("BTST")
                                || strategy.getTradeValidity().equals("CNC"))
                                && (isTimeGreaterThan(currentTime, cncSLTime) && isTimeBefore(currentTime, cncSLEndTime))) {
                            if (!trendTradeData.isSlPlaced && trendTradeData.isOrderPlaced) {
                                LOGGER.info("executing cnc SL:{}:{}", userId, trendTradeData.getStockName());
                                //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                try {
                                    // tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
                                    double strikePrice = 0;
                                    try {
                                        String historicURL1 = "https://api.kite.trade/instruments/historical/" + trendTradeData.getZerodhaStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
                                        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL1), String.valueOf(trendTradeData.getZerodhaStockId()), currentHourMinStr);
                                        HistoricalData historicalStrikeData = new HistoricalData();
                                        JSONObject strikeJson = new JSONObject(priceResponse);
                                        Optional<HistoricalData> historicalStrikeDataLastData = Optional.empty();
                                        String strikeStatus = strikeJson.getString("status");
                                        if (!strikeStatus.equals("error")) {
                                            historicalStrikeData.parseResponse(strikeJson);

                                            historicalStrikeDataLastData = historicalStrikeData.dataArrayList.stream().filter(candle -> {
                                                try {
                                                    Date candleDateTime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(candle.timeStamp);
                                                    return tradingStrategyAndTradeData.hourMinFormat.format(candleDateTime).equals(candleHourMinStr);
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
                                    if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                        orderParams.exchange = "BFO";
                                    }
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
                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
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
    public void slImmediateAndTarget() {
        Date date = new Date();
        //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = tradingStrategyAndTradeData.dateFormat.format(date);
        Calendar calendarCurrentMin = Calendar.getInstance();
        Date currentMinDate = calendarCurrentMin.getTime();
        String currentHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(currentMinDate);
        sLMonitor.submit(() -> {
            //  Type type = new TypeToken<List<TradeData>>(){}.getType();
            // String data="[{\"stockName\":\"NIFTY2331617500PE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"959a60d1-cb97-4006-850e-50653f62008a\",\"buyPrice\":168,\"buyTradedPrice\":168,\"zerodhaStockId\":14236418,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":true,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000131603\",\"slOrderId\":\"230310000161496\",\"slPrice\":138,\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"modifyDate\":\"2023-03-10\",\"tradeStrategy\":{\"tradeStrategyKey\":\"NFBCD15117btst\",\"index\":\"NF\",\"entryTime\":\"09:17\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:23\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":true,\"simpleMomentumType\":\"POINTS_UP\",\"simpleMomentumValue\":10.00,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":1,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09:17 points 10 sl 30 CNC\"}},{\"stockName\":\"NIFTY2331617350CE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"f11cade6-bea7-4a66-873f-58dfc63f568f\",\"buyPrice\":165,\"zerodhaStockId\":14234626,\"userId\":\"LTK728\",\"isOrderPlaced\":false,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000131648\",\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"NFBCD15117btst\",\"index\":\"NF\",\"entryTime\":\"09:17\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:23\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":true,\"simpleMomentumType\":\"POINTS_UP\",\"simpleMomentumValue\":10.00,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":1,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09:17 points 10 sl 30 CNC\"}},{\"stockName\":\"NIFTY2331617500PE\",\"tradeDate\":\"2023-03-10\",\"qty\":50,\"dataKey\":\"68f5c02b-9913-418b-a5d0-255201e5cf06\",\"sellPrice\":17372.95000000000072759576141834259033203125,\"zerodhaStockId\":14236418,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryType\":\"BUY\",\"strike\":0,\"isErrored\":true,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"NORF23\",\"index\":\"NF\",\"entryTime\":\"09:23\",\"tradeValidity\":\"MIS\",\"exitTime\":\"15:10\",\"intradayExitTime\":\"15:10\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"PRICE_RANGE\",\"strikePriceRangeLow\":150.00,\"strikePriceRangeHigh\":200.00,\"orderType\":\"BUY\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"LIMIT\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":true,\"strategyEnabled\":true,\"rangeBreakTime\":\"09:23\",\"rangeStartTime\":\"09:16\",\"rangeBreakSide\":\"High,Low\",\"rangeBreakInstrument\":\"INDEX\",\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":1,\"lotSize\":50,\"positionalLotSize\":0,\"slType\":\"POINTS\",\"slValue\":30.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"nifty buy 09-16 to 09:23 ORB sl 30\",\"rangeLow\":0,\"rangeHigh\":17413.9000000000014551915228366851806640625}},{\"stockName\":\"BANKNIFTY2331640700CE\",\"tradeDate\":\"2023-03-10\",\"qty\":25,\"dataKey\":\"22c3f32c-503c-494c-aff4-55fbe5343bfa\",\"sellPrice\":222.2,\"sellTradedPrice\":222.2,\"zerodhaStockId\":14149378,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000542149\",\"entryType\":\"SELL\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"BNFABOST135\",\"index\":\"BNF\",\"entryTime\":\"09:35\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:34\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"OTM2\",\"orderType\":\"SELL\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"MARKET\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":0,\"lotSize\":25,\"positionalLotSize\":0,\"slType\":\"PERCENT\",\"slValue\":50.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"bnf stbt sell 9:35 CNC sl 50 OTM2\"}},{\"stockName\":\"BANKNIFTY2331640300PE\",\"tradeDate\":\"2023-03-10\",\"qty\":25,\"dataKey\":\"5b0da0b2-5240-461f-8b72-a02b2867a969\",\"sellPrice\":252.5,\"sellTradedPrice\":252.5,\"zerodhaStockId\":14147586,\"userId\":\"LTK728\",\"isOrderPlaced\":true,\"isSLCancelled\":false,\"isCancelled\":false,\"isSLHit\":false,\"isReverseSLHit\":false,\"isSlPlaced\":false,\"isReverseTradePlaced\":false,\"isReverseSLPlaced\":false,\"isExited\":false,\"entryOrderId\":\"230310000542164\",\"entryType\":\"SELL\",\"strike\":0,\"isErrored\":false,\"pyramidCount\":0,\"pyramidQty\":0,\"stopLossCount\":0,\"isSLModified\":false,\"rentryCount\":0,\"tradeStrategy\":{\"tradeStrategyKey\":\"BNFABOST135\",\"index\":\"BNF\",\"entryTime\":\"09:35\",\"tradeValidity\":\"BTST\",\"exitTime\":\"09:34\",\"tradeDays\":\"All\",\"userId\":\"LTK728\",\"strikeSelectionType\":\"OTM2\",\"orderType\":\"SELL\",\"strikeType\":\"PE,CE\",\"entryOrderType\":\"MARKET\",\"exitOrderType\":\"MARKET\",\"simpleMomentum\":false,\"rangeBreak\":false,\"strategyEnabled\":true,\"reentry\":false,\"intradayLotSiz\":0,\"intradayLotSize\":0,\"lotSize\":25,\"positionalLotSize\":0,\"slType\":\"PERCENT\",\"slValue\":50.00,\"slOrderType\":\"LIMIT\",\"target\":false,\"aliasName\":\"bnf stbt sell 9:35 CNC sl 50 OTM2\"}}]";
            //List<TradeData> prodList = gson.fromJson(data, type);
            //  tradingStrategyAndTradeData.openTrade.put("LTK728",prodList);
            tradingStrategyAndTradeData.openTrade.forEach((userId, tradeData) -> {
                //   LOGGER.info("Insde SL:"+userId);

                User user = userList.getUser().stream().filter(
                        user1 -> user1.getName().equals(userId)
                ).findFirst().get();
                //        List<Position> positions=brokerWorker.getPositions(user);
                BrokerWorker brokerWorker = workerFactory.getWorker(user);
                List<Position> positions = brokerWorker.getPositions(user);

                try {
                    List<Order> orderList = brokerWorker.getOrders(user);

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
                                            e.printStackTrace();
                                            LOGGER.info("error:" + e);
                                        }
                                    }
                                }
                                //Start: set order placed flag for buy && entry price for buy & sell
                                if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced) {
                                    if (isOrderCompleteOrTraded(order)) {
                                        if (!trendTradeData.isOrderPlaced) {
                                            trendTradeData.isOrderPlaced = true;
                                        }

                                        if (!trendTradeData.isSlPriceCalculated) {
                                            try {
                                                double strikePrice = getStrikePrice(trendTradeData, currentDateStr, currentHourMinStr, strategy);

                                                updateTrendTradeData(trendTradeData, order, strikePrice, strategy);

                                                logOrderExecution(trendTradeData, user, strategy);
                                            } catch (Exception e) {
                                                handleException(e, trendTradeData);
                                            }
                                        }
                                    }
                                }
                                //End: set order placed flag for buy && entry price for buy & sell
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
                                    if (!trendTradeData.isSlPriceCalculated && !trendTradeData.isErrored & !trendTradeData.getTradeStrategy().isHedge() && !trendTradeData.isNoSl()) {
                                        //    if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))) {
                                        try {
                                            // tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, true);
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
                                            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                                orderParams.exchange = "BFO";
                                            }
                                            if (("MIS".equals(strategy.getTradeValidity()) && "BUY".equals(strategy.getOrderType())) && !strategy.isHedge()) {
                                                orderParams.product = "NRML";
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
                                            orderParams.price = price.doubleValue();
                                            orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                            trendTradeData.setSlPrice(triggerPriceTemp);
                                            System.out.println("trade sl data"+ gson.toJson(trendTradeData));
                                            try {
                                                LOGGER.info("input:" + gson.toJson(orderParams));

                                                if(!strategy.isWebsocketSlEnabled() && !strategy.isHedge() && !strategy.isNoSl()) {
                                                    LOGGER.info("websocket not enabled for:{}", gson.toJson(orderParams));
                                                    try {
                                                        orderParams.price=MathUtils.roundToNearestFivePaiseUP(price).doubleValue();
                                                        trendTradeData.setInitialSLPrice(price);
                                                        orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                        trendTradeData.isSlPlaced = true;
                                                        trendTradeData.setSlOrderId(orderd.orderId);
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
                                    }/*else {
                                        //TODO: add time check
                                        tradeSedaQueue.sendTelemgramSeda("Error while placing sl order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + getAlgoName()+":"+strategy.getTradeStrategyKey());
                                    }*/

                                }

                                //Target order cancellation and exit flag: start
                                if ( strategy.isTarget() && order.orderId.equals(trendTradeData.getTargetOrderId()))
                                {
                                if (("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) &&
                                        (trendTradeData.isExited || trendTradeData.isSLHit))
                                        {
                                        try {
                                            LOGGER.info(strategy.getTradeStrategyKey() + ":" + "target order cancellation on sl hit:" + trendTradeData.getUserId() + ":strike:" + trendTradeData.getStockName());
                                            brokerWorker.cancelOrder(order.orderId, user);
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "Triggered sl cancellation:" + trendTradeData.getUserId() + ":stike:" + trendTradeData.getStockName(), "exp-trade");
                                        } catch (Exception e) {
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + trendTradeData.getUserId() + ":strike:" + trendTradeData.getStockName(), "error");
                                            LOGGER.info(e.getMessage());
                                        } catch (KiteException e) {
                                            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + "error while order cancellation:" + trendTradeData.getUserId() + ":strike:" + trendTradeData.getStockName(), "error");
                                            e.printStackTrace();
                                        }

                                }
                                if ("COMPLETE".equals(order.status) || "TRADED".equals(order.status))
                                {
                                     trendTradeData.isExited = true;
                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                }

                                }
                                //Target order cancellation and exit flag: End
                                if ("OPEN".equals(order.status) && order.orderId.equals(trendTradeData.getSlOrderId())
                                        && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                    // List<Position> positions=brokerWorker.getPositions(user);
                                    positions.stream().filter(position -> position.tradingSymbol.equals(trendTradeData.getStockName())).findFirst().ifPresent(position -> {
                                        Double lastPrice = position.lastPrice;
                                        String message = MessageFormat.format("Sl open lastPrice:" + lastPrice + "sl price:" + trendTradeData.getSlPrice(), trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getTradeStrategyKey());
                                        LOGGER.info(message);

                                    });
                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " SL open for long time, please check on priority" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                }
                                if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status))
                                        && order.orderId.equals(trendTradeData.getSlOrderId())
                                        && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
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
                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
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
                                            reentryTradeData.setZerodhaStockId(trendTradeData.getZerodhaStockId());
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
                                            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                                orderParams.exchange = "BFO";
                                            }
                                            orderParams.transactionType = strategy.getOrderType();
                                            orderParams.validity = "DAY";
                                            Order orderd = null;
                                            try {
                                                LOGGER.info("input:" + gson.toJson(orderParams));
                                                orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                reentryTradeData.setEntryOrderId(orderd.orderId);
                                                List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                                tradeDataList.add(reentryTradeData);
                                                tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                                tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(reentryTradeData, false);

                                                try {
                                                    LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                                    tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                    LOGGER.info("error:" + e);
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                                tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                            } catch (KiteException e) {
                                                e.printStackTrace();
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
                                        reentryTradeData.setZerodhaStockId(trendTradeData.getZerodhaStockId());
                                        reentryTradeData.setStrikeId(trendTradeData.getStrikeId());
                                        reentryTradeData.setTradeStrategy(strategy);
                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                        orderParams.exchange = "NFO";
                                        if (strategy.getTradeStrategyKey().length() <= 20) {
                                            orderParams.tag = strategy.getTradeStrategyKey();
                                        }
                                        if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
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
                                            List<TradeData> tradeDataList = tradingStrategyAndTradeData.openTrade.get(user.getName());
                                            tradeDataList.add(reentryTradeData);
                                            tradingStrategyAndTradeData.openTrade.put(user.getName(), tradeDataList);
                                            tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(reentryTradeData, false);

                                            try {
                                                LOGGER.info("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry");
                                                tradeSedaQueue.sendTelemgramSeda("Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                LOGGER.info("error:" + e);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            LOGGER.info("error while placing retry order: " + e.getMessage() + ":" + user.getName());
                                            tradeSedaQueue.sendTelemgramSeda("Error while placing retry order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + e.getMessage() + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                        } catch (KiteException e) {
                                            e.printStackTrace();
                                            LOGGER.info("kite error while placing retry order: " + e.getMessage() + ":" + user.getName());
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
                                                tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                                //  brokerWorker.modifyOrder(order1.orderId, orderParams, user, tradeDataMod);
                                                tradeSedaQueue.sendTelemgramSeda("executed trail sl " + trendTradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey() + ": trail price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
                                            } catch (Exception e) {
                                                tradeSedaQueue.sendTelemgramSeda("error while modifying Option " + trendTradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), "exp-trade");
                                                e.printStackTrace();
                                                LOGGER.info("kite error while changing trail order: " + e.getMessage() + ":" + user.getName());
                                            }
                                        });
                                    }
                                }

                            } else if ("REJECTED".equals(order.status)
                                    && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                                String message = MessageFormat.format("SL order placement rejected for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + strategy.getTradeStrategyKey()+": Retrying one more time");
                                LOGGER.info(message);
                                tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
                                Pattern pattern = Pattern.compile("TRANSACTION REJECTED DUE TO SLOW PARTITION");
                                Matcher matcher = pattern.matcher(order.statusMessage);
                                if (matcher.find()) {
                                    OrderParams orderParams = new OrderParams();
                                    if (strategy.getTradeStrategyKey().length() <= 20) {
                                        orderParams.tag = strategy.getTradeStrategyKey();
                                    }
                                    orderParams.tradingsymbol = trendTradeData.getStockName();
                                    orderParams.exchange = "NFO";
                                    orderParams.quantity = trendTradeData.getQty();
                                    orderParams.orderType = freakSlType;
                                    if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
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
                                        price = triggerPriceTemp.add(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                        orderParams.transactionType = "BUY";
                                        if (trendTradeData.getSellPrice() != null) {
                                            LOGGER.info("sell price:{} sl price: {}", trendTradeData.getSellPrice().doubleValue(), triggerPriceTemp.doubleValue());
                                        }
                                    }
                                  //  orderParams.triggerPrice = trendTradeData.getTempSlPrice().doubleValue();
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
                                    orderParams.price = price.doubleValue();
                                    orderParams.triggerPrice = triggerPriceTemp.doubleValue();
                                    trendTradeData.setSlPrice(triggerPriceTemp);
                                    System.out.println("trade sl data"+ gson.toJson(trendTradeData));
                                    try {
                                        LOGGER.info("input:" + gson.toJson(orderParams));

                                        if(!strategy.isWebsocketSlEnabled() && !strategy.isHedge() && !strategy.isNoSl()) {
                                            LOGGER.info("websocket not enabled for:{}", gson.toJson(orderParams));
                                            try {
                                                orderParams.price=MathUtils.roundToNearestFivePaiseUP(price).doubleValue();
                                                trendTradeData.setInitialSLPrice(price);
                                                orderd = brokerWorker.placeOrder(orderParams, user, trendTradeData);
                                                trendTradeData.isSlPlaced = true;
                                                trendTradeData.isErrored = false;
                                                trendTradeData.setSlOrderId(orderd.orderId);
                                                trendTradeData.isSlPriceCalculated = true;
                                            } catch (IOException | KiteException e) {
                                                e.printStackTrace();
                                            }
                                        }else {
                                            trendTradeData.isSlPriceCalculated = true;
                                        }
                                        tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                        try {
                                            LOGGER.info("Option sl retry" + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL");
                                            if(trendTradeData.getSellPrice()!=null) {
                                                tradeSedaQueue.sendTelemgramSeda("Option sl retry " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey() + ":sell price: " + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSellPrice()).doubleValue() + " sl price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
                                            }
                                            if(trendTradeData.getBuyPrice()!=null) {
                                                tradeSedaQueue.sendTelemgramSeda("Option sl retry " + trendTradeData.getStockName() + ":" + user.getName() + " traded and placed SL" + ":" + strategy.getTradeStrategyKey() + ":sell price: " + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getBuyPrice()).doubleValue() + " sl price:" + MathUtils.roundToNearestFivePaiseUP(trendTradeData.getSlPrice()).doubleValue(), "exp-trade");
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
                                    }

                                }else {
                                    trendTradeData.isErrored = true;
                                }

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
                                    tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    LOGGER.error("error while setting exit flag and price: {}", e.getMessage());
                                }
                            }
                        });
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error("common error:{}", e.getMessage());
                } catch (KiteException e) {
                    e.printStackTrace();
                    LOGGER.error("error:{}", e.getMessage());
                }
            });

        });
        try {
            targetMonitor.submit(() -> {
                tradingStrategyAndTradeData.openTrade.forEach((userId, tradeData) -> {
                    //   LOGGER.info("Insde SL:"+userId);
                    User user = userList.getUser().stream().filter(
                            user1 -> user1.getName().equals(userId)
                    ).findFirst().get();
                    //        List<Position> positions=brokerWorker.getPositions(user);
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    try {
                        tradeData.stream().filter(order -> order.getEntryOrderId() != null && !order.isTargetOrderPlaced()
                                        && order.isOrderPlaced && order.getTradeStrategy().isTarget() &&
                                        !order.isExited).
                                forEach(trendTradeData -> {
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
                                    // orderParams.product = order.product;
                                    orderParams.validity = "DAY";
                                    Order orderd = null;
                                    BigDecimal price;
                                    //   BigDecimal triggerPriceTemp;
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
                                    System.out.println("trade sl data" + gson.toJson(trendTradeData));
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
                                                LOGGER.error("error while placing target kiteorder:{}",e.getMessage());
                                                e.printStackTrace();
                                                throw e;
                                            }
                                        }
                                    } catch (Exception | KiteException e) {
                                        LOGGER.error("error1 while placing target kite order:{}",e.getMessage());
                                        e.printStackTrace();
                                    }
                                });
                    } catch (Exception e) {
                        LOGGER.error("error2 while placing target kite order:{}",e.getMessage());
                        e.printStackTrace();
                    }
                });
            });
        }catch (Exception e){
            LOGGER.error("error while placing target order:{}",e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isOrderCompleteOrTraded(Order order) {
        return "COMPLETE".equals(order.status) || "TRADED".equals(order.status);
    }

    private double getStrikePrice(TradeData trendTradeData, String currentDateStr, String currentHourMinStr, TradeStrategy strategy) {
        try {
            String historicURL = "https://api.kite.trade/instruments/historical/" + trendTradeData.getZerodhaStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
            String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), String.valueOf(trendTradeData.getZerodhaStockId()), currentHourMinStr);
            JSONObject strikeJson = new JSONObject(priceResponse);

            if (!"error".equals(strikeJson.getString("status"))) {
                HistoricalData historicalStrikeData = new HistoricalData();
                historicalStrikeData.parseResponse(strikeJson);

                Optional<HistoricalData> historicalStrikeDataLastData = historicalStrikeData.dataArrayList.stream()
                        .filter(candle -> isEntryCandleTime(candle, strategy))
                        .findFirst();

                if (historicalStrikeDataLastData.isPresent()) {
                    return historicalStrikeDataLastData.get().close;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while getting current price of script: " + trendTradeData.getStockName(), e);
        }
        return 0;
    }

    private boolean isEntryCandleTime(HistoricalData candle, TradeStrategy strategy) {
        try {
            Date candleDateTime = tradingStrategyAndTradeData.candleDateTimeFormat.parse(candle.timeStamp);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime time = LocalTime.parse(strategy.getEntryTime(), formatter);
            LocalTime entryCandleTime = time.minusMinutes(1);
            return tradingStrategyAndTradeData.hourMinFormat.format(candleDateTime).equals(entryCandleTime.format(formatter));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateTrendTradeData(TradeData trendTradeData, Order order, double strikePrice, TradeStrategy strategy) {
        if ("BUY".equals(trendTradeData.getEntryType())) {
            trendTradeData.setBuyTradedPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
            trendTradeData.setBuyTime(tradingStrategyAndTradeData.exchangeDateTimeFormat.format(order.exchangeUpdateTimestamp));
        } else {
            trendTradeData.setSellPrice(BigDecimal.valueOf(strikePrice));
            trendTradeData.setSellTradedPrice(MathUtils.roundToNearestFivePaiseUP(new BigDecimal(order.averagePrice)));
            trendTradeData.setSellTime(tradingStrategyAndTradeData.exchangeDateTimeFormat.format(order.exchangeUpdateTimestamp));
        }

        if (strategy.isHedge()) {
            trendTradeData.isExited = true;
            if ("BUY".equals(trendTradeData.getEntryType())) {
                trendTradeData.setSellPrice(BigDecimal.ZERO);
            }
        }
    }

    private void logOrderExecution(TradeData trendTradeData, User user, TradeStrategy strategy) {
        String message = MessageFormat.format("Order Executed for {0}",
                trendTradeData.getStockName() + ":" + user.getName() + ":" +
                        strategy.getTradeStrategyKey() + ":" + trendTradeData.getSellPrice() + ":" +
                        trendTradeData.getBuyPrice());
        LOGGER.info(message);
        tradeSedaQueue.sendTelemgramSeda(message, "exp-trade");
    }

    private void handleException(Exception e, TradeData trendTradeData) {
        e.printStackTrace();
        LOGGER.error("Error processing order for " + trendTradeData.getStockName() + ": " + e.getMessage());
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

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Scheduled(cron = "${tradeEngine.limit.order.monitor}")
    public void limitOrderMonitor() {
        tradingStrategyAndTradeData.openTrade.entrySet().stream().forEach(userTradeData -> {
            String userId = userTradeData.getKey();
            //  User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
            //  BrokerWorker brokerWorker = workerFactory.getWorker(user);
            List<TradeData> tradeDataList = userTradeData.getValue();
            tradeDataList.stream().filter(trade -> !trade.isSLHit && !trade.isExited && trade.isOrderPlaced && trade.isSlPlaced && !trade.isErrored && "LIMIT".equals(freakSlType)).forEach(tradeData -> {
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
        tradingStrategyAndTradeData.openTrade.forEach((userId, tradeDataList) -> {
            //  User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
            //  BrokerWorker brokerWorker = workerFactory.getWorker(user);
            tradeDataList.stream().filter(trade -> !trade.isSLHit && !trade.isExited
                    && trade.isOrderPlaced && trade.isSlPlaced).forEach(tradeData -> {
                if (tradeData.getTradeStrategy().isWebsocketSlEnabled()) {
                    if (!tradeData.isWebsocketSlModified()) {
                        Date date = new Date();
                        String currentDateStr = tradingStrategyAndTradeData.dateFormat.format(date);
                        Calendar candleCalenderMin = Calendar.getInstance();
                        Calendar calendarCurrentMin = Calendar.getInstance();
                        candleCalenderMin.add(Calendar.MINUTE, -1);
                        Date currentMinDate = calendarCurrentMin.getTime();
                        Date candleCurrentMinDate = candleCalenderMin.getTime();
                        String candleHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(candleCurrentMinDate);
                        String currentHourMinStr = tradingStrategyAndTradeData.hourMinFormat.format(currentMinDate);
                        String stockId = String.valueOf(tradeData.getZerodhaStockId());
                        String historicURL = "https://api.kite.trade/instruments/historical/" + tradeData.getZerodhaStockId() + "/minute?from=" + currentDateStr + "+09:00:00&to=" + currentDateStr + "+15:35:00";
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
                                            if ("SS".equals(strategy.getIndex()) || "BNX".equals(strategy.getIndex())) {
                                                orderParams.exchange = "BFO";
                                            }
                                            orderParams.validity = "DAY";
                                            orderParams.transactionType = "BUY";
                                            orderParams.orderType = "LIMIT";
                                            orderParams.price = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                                            if (!tradeData.isWebsocketSlModified()) {
                                                Order order = brokerWorker.placeOrder(orderParams, user, tradeData);
                                                tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                                // postOrderModifyDataToSeda(tradeData, orderParams, null,"placeOrder");
                                                tradeData.isSlPlaced = true;
                                                tradeData.setSlOrderId(order.orderId);
                                                tradeData.setWebsocketSlModified(true);
                                                LocalDateTime currentDateTime = LocalDateTime.now();
                                                tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                                                tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                            }
                                            tradeHelperService.mapTradeDataToSaveOpenTradeDataEntity(tradeData, false);
                                            LOGGER.info("placed limit based on candle high:" + userId + ":" + tradeData.getStockName() + " candle time:" + optionalHistoricalLatestData.timeStamp + " candle high: " + optionalHistoricalLatestData.high + " trade sl:" + tradeData.getSlPrice(), "exp-trade");
                                            tradeSedaQueue.sendTelemgramSeda("placed limit based on candle high:" + userId + ":" + tradeData.getStockName() + ":" + tradeData.getWebsocketSlTime(), "exp-trade");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            LOGGER.error("error while placing sl limit order based on candle high:{},{}", userId, tradeData.getStockName());
                                        } catch (KiteException e) {
                                            e.printStackTrace();
                                            LOGGER.error("kite error while placing sl limit order based on candle high:{},{}", userId, tradeData.getStockName());
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
            tradingStrategyAndTradeData.openTrade.entrySet().stream().forEach(userTradeData -> {
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
                                        double pl = (new BigDecimal(ltpQuote.lastPrice).subtract(tradeData.getBuyPrice())).doubleValue();
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
