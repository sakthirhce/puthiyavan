package com.sakthi.trade.util;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ReverseEntry;
import com.sakthi.trade.zerodha.account.StraddleConfig;
import com.sakthi.trade.zerodha.account.TelegramBot;
import com.sakthi.trade.zerodha.account.User;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
@Service
public class OrderUtil {
    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    public static final Logger LOGGER = Logger.getLogger(OrderUtil.class.getName());
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    TransactionService transactionService;
    @Autowired
    TelegramMessenger sendMessage;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    @Autowired
    BrokerWorkerFactory workerFactory;
    //@Scheduled(cron = "${stradle.sl.scheduler}")
    public void sLMonitorScheduler(User user, String algoName, StraddleConfig straddleConfig) {
        // LOGGER.info("short straddle SLMonitor scheduler started");

            BrokerWorker brokerWorker = workerFactory.getWorker(user);
            if(user.getStraddleConfigOld() !=null &&  user.getStraddleConfigOld().isEnabled()){
                String botId = "";
                TelegramBot telegramBot = user.getTelegramBot();
                if (telegramBot != null) {
                    botId = telegramBot.getGroupId();
                }
                String botIdFinal = botId;
                if (straddleConfig != null) {
                    straddleConfig.straddleTradeMap
                            .forEach((key,value) -> {
                                if( value.isOrderPlaced &&value.getEntryOrderId() != null) {
                                    TradeData trendTradeData = value;
                                    // LOGGER.info(" trade data:"+new Gson().toJson(trendTradeData));
                                    List<Order> orderList = null;
                                    try {
                                        orderList = brokerWorker.getOrders(user);
                                        //   LOGGER.info("get trade response:"+new Gson().toJson(orderList));
                                    } catch (KiteException | IOException e) {
                                        e.printStackTrace();
                                    }
                                    orderList.forEach(order -> {
                                        if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced && "COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                            //   trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                            trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                            Date date = new Date();
                                            String currentDate = format.format(date);
                                            trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                            BigDecimal triggerPrice = (((new BigDecimal(order.averagePrice).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).add(new BigDecimal(order.averagePrice)))).setScale(0, RoundingMode.HALF_UP);
                                            AtomicDouble triggerPriceAtomic = new AtomicDouble();

                                            try {
                                                String historicURL = "https://api.kite.trade/instruments/historical/" + trendTradeData.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                                System.out.print(response);
                                                HistoricalData historicalData = new HistoricalData();
                                                JSONObject json = new JSONObject(response);
                                                String status = json.getString("status");

                                                if (!status.equals("error")) {
                                                    historicalData.parseResponse(json);

                                                    historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                                                        try {
                                                            Date openDatetime = sdf.parse(historicalData1.timeStamp);
                                                            String openDate = format.format(openDatetime);
                                                            if (sdf.format(openDatetime).equals(openDate + "T09:19:00")) {
                                                                BigDecimal triggerPriceTemp = (((new BigDecimal(historicalData1.close).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).add(new BigDecimal(historicalData1.close)))).setScale(0, RoundingMode.HALF_UP);
                                                                trendTradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                                                triggerPriceAtomic.addAndGet(triggerPriceTemp.doubleValue());
                                                                //  slPrice.put(trendTradeData.getStockId(), triggerPriceTemp);
                                                                LOGGER.info("setting sl price based on 9:19 close :" + trendTradeData.getStockId() + ":" + triggerPriceTemp + ":" + trendTradeData.getUserId());
                                                            }
                                                        } catch (ParseException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    });
                                                }
                                            } catch (Exception e) {
                                                LOGGER.info(e.getMessage());
                                            }
                                            if (triggerPriceAtomic.get() > 0) {
                                                triggerPrice = BigDecimal.valueOf(triggerPriceAtomic.get());
                                            }
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = trendTradeData.getStockName();
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                            orderParams.orderType = "SL";
                                            orderParams.product = "MIS";

                                            BigDecimal price = ((triggerPrice.setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)).multiply(new BigDecimal(5)).add(triggerPrice)).setScale(0, RoundingMode.HALF_UP);

                                            orderParams.triggerPrice = triggerPrice.doubleValue();
                                            trendTradeData.setSlPrice(triggerPrice);
                                            orderParams.price = price.doubleValue();
                                            orderParams.transactionType = "BUY";
                                            orderParams.validity = "DAY";
                                            com.zerodhatech.models.Order orderResponse = null;
                                            try {
                                                orderResponse = brokerWorker.placeOrder(orderParams,user, trendTradeData);
                                                trendTradeData.setSlOrderId(orderResponse.orderId);
                                                trendTradeData.isSlPlaced = true;
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,false,algoName);
                                                sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName() + ":" + user.getName() + ":" + algoName, straddleConfig.getTelegramToken(), botIdFinal);
                                                LOGGER.info("SL order placed for: " + trendTradeData.getStockName() + ":" + trendTradeData.getUserId());

                                            } catch (KiteException e) {
                                                LOGGER.info("Error while placing straddle order: " + e.message);
                                                sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message + ":" + user.getName() + ":" + algoName, straddleConfig.getTelegramToken(), botIdFinal);
                                                e.printStackTrace();
                                            } catch (IOException e) {
                                                LOGGER.info("Error while placing straddle order: " + e.getMessage());
                                                sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage() + ":" + user.getName() + ":" + algoName, straddleConfig.getTelegramToken(), botIdFinal);
                                                e.printStackTrace();
                                            }
                                        } else if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                            if ("CANCELLED".equals(order.status)) {
                                                trendTradeData.isSLCancelled = true;
                                                String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + algoName);
                                                LOGGER.info(message);
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,false,algoName);
                                                sendMessage.sendToTelegram(message, straddleConfig.getTelegramToken(), botIdFinal);
                                            } else if ("COMPLETE".equals(order.status)) {
                                                trendTradeData.isSLHit = true;
                                                trendTradeData.isExited = true;
                                                LocalDate localDate = LocalDate.now();
                                                DayOfWeek dow = localDate.getDayOfWeek();
                                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                                String todayCaps = today.toUpperCase();
                                                trendTradeData.setBuyPrice(trendTradeData.getSlPrice());
                                                trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                                String message = MessageFormat.format("SL Hit for {0}" + ":" + user.getName() + ":" + algoName, trendTradeData.getStockName());
                                                LOGGER.info(message);
                                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,false,algoName);
                                                sendMessage.sendToTelegram(message, straddleConfig.getTelegramToken(), botIdFinal);
                                                if (user.getStraddleConfigOld() != null && user.getStraddleConfigOld().reverseEntry != null && user.getStraddleConfigOld().reverseEntry.isEnabled()) {
                                                    ReverseEntry reverseEntry = user.getStraddleConfigOld().reverseEntry;
                                                    int totalRetry = 0;
                                                    AtomicInteger retryCount = new AtomicInteger();
                                                    reverseEntry.retryCountConfig.forEach((lotValue, value1) -> {
                                                        if (lotValue.contains(todayCaps)) {
                                                            int value2 = (Integer.parseInt(value1));
                                                            retryCount.getAndSet(value2);
                                                        }
                                                    });
                                                    TradeData parentTradeData = null;
                                                    double triggerPrice = 0;
                                                    int stockId=0;
                                                    if (key.contains("REENTRY")) {
                                                        parentTradeData = user.getStraddleConfigOld().straddleTradeMap.get(trendTradeData.getParentEntry());
                                                        LOGGER.info("parent order:"+new Gson().toJson(parentTradeData)+":"+trendTradeData.getParentEntry()+": curent object"+new Gson().toJson(trendTradeData)+":"+key);
                                                        triggerPrice = parentTradeData.getSellPrice().doubleValue();
                                                        totalRetry = parentTradeData.getRentryCount();
                                                        LOGGER.info("retry count:"+totalRetry);
                                                        LOGGER.info("triggerPrice:"+triggerPrice);
                                                        stockId=parentTradeData.getStockId();
                                                    } else {
                                                        totalRetry = trendTradeData.getRentryCount();
                                                        triggerPrice = trendTradeData.getSellPrice().doubleValue();
                                                        LOGGER.info("retry count:"+totalRetry);
                                                        LOGGER.info("triggerPrice:"+triggerPrice);
                                                        stockId=trendTradeData.getStockId();
                                                    }
                                                    if (totalRetry < retryCount.get()) {
                                                        LOGGER.info("inside reverse entry sell:retry count:"+retryCount.get());
                                                        //.setRentryCount(1);

                                                        OrderParams orderParams = new OrderParams();
                                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                                        orderParams.exchange = "NFO";
                                                        orderParams.quantity = trendTradeData.getQty();
                                                        orderParams.orderType = "SL";
                                                        orderParams.product = "MIS";
                                                        orderParams.transactionType = "SELL";
                                                        orderParams.validity = "DAY";
                                                        com.zerodhatech.models.Order orderd = null;
                                                        orderParams.triggerPrice = triggerPrice;
                                                        BigDecimal price = new BigDecimal(triggerPrice).setScale(0, RoundingMode.HALF_UP).subtract(new BigDecimal(triggerPrice).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                                        orderParams.price = price.doubleValue();
                                                        TradeData reverseTrade = new TradeData();
                                                        reverseTrade.setParentEntry(trendTradeData.getStockName());
                                                        reverseTrade.setStockId(stockId);
                                                        reverseTrade.setUserId(user.getName());
                                                        String dataKey = UUID.randomUUID().toString();
                                                        reverseTrade.setDataKey(dataKey);

                                                        int retryCountN = 0;
                                                        if (key.contains("REENTRY") && parentTradeData != null) {
                                                            retryCountN = parentTradeData.getRentryCount() + 1;
                                                            parentTradeData.setRentryCount(retryCountN);
                                                        } else if (!key.contains("REENTRY")) {
                                                            retryCountN = 1;
                                                            trendTradeData.setRentryCount(retryCountN);
                                                        }
                                                        reverseTrade.setStockName(trendTradeData.getStockName());
                                                        String retryKey = trendTradeData.getStockName() + "_REENTRY_" + retryCountN;
                                                        try {
                                                            orderd = brokerWorker.placeOrder(orderParams,user, trendTradeData);
                                                            reverseTrade.setEntryOrderId(orderd.orderId);
                                                            reverseTrade.setSellPrice(new BigDecimal(triggerPrice));
                                                            reverseTrade.isOrderPlaced = true;
                                                            reverseTrade.setQty(trendTradeData.getQty());
                                                            reverseTrade.setEntryType("SELL");
                                                            mapTradeDataToSaveOpenTradeDataEntity(reverseTrade,true,algoName);
                                                            sendMessage.sendToTelegram("reentry Straddle option order placed for strike: " + retryKey + ":" + reverseTrade.getStockName() + ":" + user.getName() + ":" + algoName, straddleConfig.getTelegramToken(), botIdFinal);
                                                            user.getStraddleConfigOld().straddleTradeMap.put(retryKey, reverseTrade);

                                                        } catch (KiteException | IOException e) {
                                                            reverseTrade.isErrored = true;
                                                            LOGGER.info("Error while placing straddle order: " + e.getMessage() + ":" + new Gson().toJson(orderParams));
                                                            LOGGER.info("order response: " + new Gson().toJson(orderd));
                                                            if (order != null) {
                                                                sendMessage.sendToTelegram("Error while placing reentry straddle order: " + retryKey + ":" + reverseTrade.getStockName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ":" + algoName, straddleConfig.getTelegramToken(), botIdFinal);
                                                            } else {
                                                                sendMessage.sendToTelegram("Error while placing reentry straddle order: " + retryKey + ":" + reverseTrade.getStockName() + ":" + algoName, straddleConfig.getTelegramToken(), botIdFinal);
                                                            }
                                                            //e.printStackTrace();
                                                        }
                                                    }


                                                }
                                            }
                                        }

                                    });
                                }
                            });

                }
            }

       // });
    }

    //@Scheduled(cron = "${straddle.exit.price}")
    public void exitPriceNrmlPositions(User user, String algoName, StraddleConfig straddleConfig,String exitTime) {
            try {
                BrokerWorker brokerWorker= workerFactory.getWorker(user);
                List<Order> orders = brokerWorker.getOrders(user);
                List<Position> positions = brokerWorker.getPositions(user);
                LOGGER.info(new Gson().toJson(positions));
                if (straddleConfig != null) {
                    straddleConfig.straddleTradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isOrderPlaced && orbTradeDataEntity.getValue().isExited && !orbTradeDataEntity.getValue().isSLHit).forEach(trendMap -> {
                        TradeData trendTradeData = trendMap.getValue();
                        orders.stream().filter(order -> "COMPLETE".equals(order.status) && order.orderId.equals(trendTradeData.getExitOrderId())).findFirst().ifPresent(orderr -> {
                            try {
                                Date date = new Date();
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                                String currentDate = format.format(date);
                                try {
                                    String historicURL = "https://api.kite.trade/instruments/historical/" + trendTradeData.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:11:00";
                                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                    System.out.print(trendTradeData.getStockName() + " history api 3:10 response:" + response);
                                    HistoricalData historicalData = new HistoricalData();
                                    JSONObject json = new JSONObject(response);
                                    String status = json.getString("status");
                                    if (!status.equals("error")) {
                                        historicalData.parseResponse(json);
                                    }
                                    if (historicalData.dataArrayList.size() > 0) {
                                        historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                                            try {
                                                Date openDatetime = sdf.parse(historicalData1.timeStamp);
                                                String openDate = format.format(openDatetime);
                                                if (sdf.format(openDatetime).equals(openDate + "T"+exitTime)) {
                                                    BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                    if ("SELL".equals(orderr.transactionType)) {
                                                        trendTradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                                    } else {
                                                        trendTradeData.setBuyTradedPrice(new BigDecimal(orderr.averagePrice));
                                                        trendTradeData.setBuyPrice(new BigDecimal(historicalData1.close));
                                                    }
                                                }
                                            } catch (ParseException e) {
                                                throw new RuntimeException(e);
                                            }
                                        });
                                    }

                                } catch (Exception e) {
                                    LOGGER.info(e.getMessage());
                                }
                                if ("SELL".equals(orderr.transactionType)) {
                                    trendTradeData.setSellTradedPrice(new BigDecimal(orderr.averagePrice));
                                } else {
                                    trendTradeData.setBuyTradedPrice(new BigDecimal(orderr.averagePrice));
                                }
                                mapTradeDataToSaveOpenTradeDataEntity(trendTradeData, false,algoName);
                            } catch (Exception e) {
                                LOGGER.info(e.getMessage());
                            }
                        });
                    });
                }

            } catch (Exception | KiteException e) {
                LOGGER.info(e.getMessage());
            }
    }
    @Autowired
    TradeDataMapper tradeDataMapper;
    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData,boolean orderPlaced,String algoName) {
        try {/*
            OpenTradeDataEntity openTradeDataEntity = new OpenTradeDataEntity();
            openTradeDataEntity.setDataKey(tradeData.getDataKey());
            openTradeDataEntity.setAlgoName(this.algoName);
            openTradeDataEntity.setStockName(tradeData.getStockName());
            openTradeDataEntity.setEntryType(tradeData.getEntryType());
            openTradeDataEntity.setUserId(tradeData.getUserId());
            openTradeDataEntity.isOrderPlaced = tradeData.isOrderPlaced;
            openTradeDataEntity.isSlPlaced = tradeData.isSlPlaced();
            openTradeDataEntity.isExited = tradeData.isExited();
            openTradeDataEntity.isErrored = tradeData.isErrored;
            openTradeDataEntity.isSLHit = tradeData.isSLHit;
            openTradeDataEntity.setBuyTradedPrice(tradeData.getBuyTradedPrice());
            openTradeDataEntity.setSellTradedPrice(tradeData.getSellTradedPrice());
            openTradeDataEntity.setExitOrderId(tradeData.getExitOrderId());
            openTradeDataEntity.setBuyPrice(tradeData.getBuyPrice());
            openTradeDataEntity.setSellPrice(tradeData.getSellPrice());
            openTradeDataEntity.setSlPrice(tradeData.getSlPrice());
            openTradeDataEntity.setQty(tradeData.getQty());
            openTradeDataEntity.setSlPercentage(tradeData.getSlPercentage());
            openTradeDataEntity.setEntryOrderId(tradeData.getEntryOrderId());
            openTradeDataEntity.setSlOrderId(tradeData.getSlOrderId());
            openTradeDataEntity.setStockId(tradeData.getStockId());
            Date date = new Date();
            if(orderPlaced) {
                String tradeDate = format.format(date);
                openTradeDataEntity.setTradeDate(tradeDate);
                tradeData.setTradeDate(tradeDate);
            }else{
                openTradeDataEntity.setTradeDate(tradeData.getTradeDate());
            }
            saveTradeData(openTradeDataEntity);*/
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData,orderPlaced,algoName);
            //LOGGER.info("sucessfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    public void saveTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            openTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }
}
