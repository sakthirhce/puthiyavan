package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.LivePLDataEntity;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.repo.LivePLDataRepo;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
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

import javax.swing.text.html.Option;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class WebSocketTicksSedaProcessor implements Processor {
    Gson gson = new Gson();
    NumberFormat formatter = new DecimalFormat();
    @Autowired
    TradeEngine tradeEngine;
    @Value("${data.export}")
    boolean dataExport;
    @Autowired
    BrokerWorkerFactory workerFactory;
    public static final Logger LOGGER = LoggerFactory.getLogger(WebSocketTicksSedaProcessor.class.getName());
    @Autowired
    TickData tickData;
    @Autowired
    UserList userList;
    @Autowired
    TradeSedaQueue tradeSedaQueue;
    DecimalFormat decimalFormat = new DecimalFormat("0.00");
    Type listType = new TypeToken<List<Tick>>() {}.getType();
    @Value("${home.path}")
    String trendPath;
    CSVWriter csvWriter;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat hourtimeFormat = new SimpleDateFormat("HH:mm");
    public WebSocketTicksSedaProcessor() throws IOException {
        try {
                Date date = new Date();
                csvWriter = new CSVWriter(new FileWriter("/home/ubuntu/tick_" + dateFormat.format(date) + ".csv", true));
                String minTime=hourtimeFormat.format(date);
                String[] dataHeader = {"instrument_token", "tick_time", "last_price"};
                try {
                    Map<String, Double> minutePrice = new HashMap<>();
                    minutePrice.put(minTime, 0.0);
                    if(tickData!=null) {
                        Map<String, Map<String, Double>> tickCurrentPrice = tickData.tickCurrentPrice;
                        if (tickCurrentPrice != null) {
                            tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY BANK"), minutePrice);
                            tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY 50"), minutePrice);
                            tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE"), minutePrice);
                            tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT"), minutePrice);
                            tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("SENSEX"), minutePrice);
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                csvWriter.writeNext(dataHeader);
                csvWriter.flush();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Autowired
    LivePLDataRepo livePLDataRepo;
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            ArrayList<Tick> ticks = gson.fromJson(message, listType);
            LocalTime currentTime = LocalTime.now();
            LocalDateTime currentDateTime = LocalDateTime.now();
            if (!ticks.isEmpty()) {
                try {
                    LocalTime tickStartTime = LocalTime.of(9, 14);
                    LocalTime tickEndTime = LocalTime.of(15, 31);
                    if (currentTime.isAfter(tickStartTime) && currentTime.isBefore(tickEndTime)) {

                        if (dataExport) {
                            ticks.stream().forEach(tick -> {
                              /*  try {
                                    Date date = new Date();
                                    Map<String, Map<String, Double>> tickCurrentPrice=tickData.tickCurrentPrice;
                                    String minTime = hourtimeFormat.format(date);
                                    if (tickCurrentPrice.containsKey(String.valueOf(tick.getInstrumentToken()))) {
                                        Map<String, Double> minutePrice = tickCurrentPrice.get(String.valueOf(tick.getInstrumentToken()));
                                        minutePrice.put(minTime, tick.getLastTradedPrice());
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }*/
                                String[] dataHeader = {String.valueOf(tick.getInstrumentToken()), candleDateTimeFormat.format(tick.getTickTimestamp()), String.valueOf(tick.getLastTradedPrice())};
                                csvWriter.writeNext(dataHeader);
                            });
                            csvWriter.flush();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
              //  Optional<Tick> tickOptional = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == 65611015).findFirst();
                //    }
                tradeEngine.openTrade.entrySet().stream().forEach(userTradeData -> {
                    String userId = userTradeData.getKey();
                    User user = userList.getUser().stream().filter(user1 -> userId.equals(user1.getName())).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                  //  if (userId.equals(user.getName())) {
                        List<TradeData> tradeDataList = userTradeData.getValue();
                        tradeDataList.forEach(tradeData -> {
                            Optional<Tick> tickOp = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == tradeData.getStockId()).findFirst();
                            if (tickOp.isPresent()) {
                                Tick tick = tickOp.get();
                                TradeStrategy strategy = tradeData.getTradeStrategy();
                                if (tradeData.isOrderPlaced && !tradeData.isExited && tradeData.isSlPlaced()) {
                                    double lastTradedPrice = tick.getLastTradedPrice();
                                    try{
                                    if(!tradeData.isWebsocketSlModified()) {
                                        if ("SELL".equals(tradeData.getEntryType())) {
                                            BigDecimal testPrice = tradeData.getSlPrice();
                                            if (lastTradedPrice > testPrice.doubleValue()) {
                                                LOGGER.info("last traded price above sl:{}:{}", tradeData.getStockName(), lastTradedPrice);
                                                Order order1;
                                                try {
                                                    order1 = brokerWorker.getOrder(user, tradeData.getSlOrderId());
                                                } catch (IOException | KiteException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                OrderParams orderParams = new OrderParams();
                                                orderParams.triggerPrice = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                                                orderParams.price = tradeData.getSlPrice().add(tradeData.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                if(!tradeData.isWebsocketSlModified()) {
                                                    postOrderModifyDataToSeda(tradeData, orderParams, order1.orderId);
                                                    //  brokerWorker.modifyOrder(order1.orderId, orderParams, user, tradeData);
                                                    tradeData.setWebsocketSlModified(true);
                                                    tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                                                }
                                            }
                                        } else {
                                            BigDecimal testPrice = tradeData.getSlPrice();
                                            if (lastTradedPrice < testPrice.doubleValue()) {
                                                LOGGER.info("last traded price below sl:{}:{}", tradeData.getStockName(), lastTradedPrice);
                                                Order order1;
                                                try {
                                                    order1 = brokerWorker.getOrder(user, tradeData.getSlOrderId());
                                                } catch (IOException | KiteException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                OrderParams orderParams = new OrderParams();
                                                    orderParams.triggerPrice = MathUtils.roundToNearestFivePaiseUP(tradeData.getSlPrice()).doubleValue();
                                                orderParams.price = tradeData.getSlPrice().subtract(tradeData.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                if(!tradeData.isWebsocketSlModified()) {
                                                    postOrderModifyDataToSeda(tradeData, orderParams, order1.orderId);
                                                    tradeData.setWebsocketSlModified(true);
                                                    tradeData.setWebsocketSlTime(dateTimeFormatter.format(currentDateTime));
                                                }
                                            }
                                        }

                                    }
                                    }catch (Exception e){
                                        e.printStackTrace();
                                        tradeSedaQueue.sendTelemgramSeda("Error while processing websocket dynamic SL" +
                                                " order:" + user.getName() + ",Exception:" + e.getMessage() + ":" + tradeData.getTradeStrategy().getTradeStrategyKey(), "exp-trade");
                                    }/*
                                   try {
                                       if ("BUY".equals(tradeData.getEntryType())) {
                                           if (tradeData.getBuyPrice().doubleValue() > 0) {
                                               double pl = lastTradedPrice - tradeData.getBuyPrice().doubleValue();
                                               LivePLDataEntity livePLDataEntity=new LivePLDataEntity();
                                               livePLDataEntity.setTradeStrategyKey(strategy.getTradeStrategyKey());
                                               String dataKey = UUID.randomUUID().toString();
                                               livePLDataEntity.setDataKey(dataKey);
                                               livePLDataEntity.setPl(new BigDecimal(pl));
                                               livePLDataEntity.setClose(new BigDecimal(lastTradedPrice));
                                               livePLDataEntity.setStockName(tradeData.getStockName());
                                               LocalDateTime localDateTime=LocalDateTime.now();
                                               livePLDataEntity.setDataTime(Timestamp.valueOf(localDateTime));
                                               livePLDataEntity.setEntryType("BUY");
                                               livePLDataEntity.setIndex(strategy.getIndex());
                                               livePLDataEntity.setOpenTradeDataKey(tradeData.getDataKey());
                                               String strikeType="PE";
                                               if(tradeData.getStockName().contains("CE")){
                                                   strikeType="CE";
                                               }
                                               livePLDataEntity.setStrikeType(strikeType);
                                               livePLDataRepo.save(livePLDataEntity);
                                           }
                                       } else {
                                           if (tradeData.getSellPrice().doubleValue() > 0) {
                                               double pl = tradeData.getSellPrice().doubleValue() - lastTradedPrice;
                                               LivePLDataEntity livePLDataEntity=new LivePLDataEntity();
                                               livePLDataEntity.setTradeStrategyKey(strategy.getTradeStrategyKey());
                                               String dataKey = UUID.randomUUID().toString();
                                               livePLDataEntity.setDataKey(dataKey);
                                               livePLDataEntity.setPl(new BigDecimal(pl));
                                               livePLDataEntity.setOpenTradeDataKey(tradeData.getDataKey());
                                               livePLDataEntity.setClose(new BigDecimal(lastTradedPrice));
                                               livePLDataEntity.setStockName(tradeData.getStockName());
                                               LocalDateTime localDateTime=LocalDateTime.now();
                                               livePLDataEntity.setDataTime(Timestamp.valueOf(localDateTime));
                                               livePLDataEntity.setEntryType("SELL");
                                               livePLDataEntity.setIndex(strategy.getIndex());
                                               String strikeType="PE";
                                               if(tradeData.getStockName().contains("CE")){
                                                   strikeType="CE";
                                               }
                                               livePLDataEntity.setStrikeType(strikeType);
                                               livePLDataRepo.save(livePLDataEntity);
                                           }
                                       }
                                   }catch (Exception e){
                                       LOGGER.info("error while inserting record into live pl db"+e.getMessage());
                                   }*/
                                }
                                if (strategy.isTrailEnabled()) {
                                    if (tradeData.isOrderPlaced && !tradeData.isExited && tradeData.isSlPlaced()) {
                                        System.out.printf("stock:"+tradeData.getStockName()+":"+tick.getLastTradedPrice()+":"+tradeData.getTradeStrategy().getTradeStrategyKey());
                                        double entryPrice;
                                        if ("BUY".equals(tradeData.getEntryType())) {
                                            entryPrice = tradeData.getBuyPrice().doubleValue();
                                        } else {
                                            entryPrice = tradeData.getSellPrice().doubleValue();
                                        }
                                        double trailingStopLoss =
                                                updatedTrailPrice(tradeData.getInitialSLPrice().doubleValue(), entryPrice, tick.getLastTradedPrice(), strategy.getTrailPointMove().intValue(), strategy.getTrailSlMoves().intValue(), tradeData.getEntryType());
                                        try {
                                            Order order1 = brokerWorker.getOrder(user, tradeData.getSlOrderId());
                                            OrderParams orderParams = new OrderParams();
                                            if ("BUY".equals(tradeData.getEntryType())) {
                                                if (Double.parseDouble(order1.triggerPrice) < trailingStopLoss) {
                                                    orderParams.price= new BigDecimal(trailingStopLoss).subtract(new BigDecimal(trailingStopLoss).divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
                                                } else {
                                                    trailingStopLoss = 0;
                                                }
                                            } else {

                                                if (Double.parseDouble(order1.triggerPrice) > trailingStopLoss) {
                                                    orderParams.price= new BigDecimal(trailingStopLoss).add(new BigDecimal(trailingStopLoss).divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP).doubleValue();
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
                                        } catch (IOException e) {
                                            tradeSedaQueue.sendTelemgramSeda("error while modifying trail sl Option " + tradeData.getStockName() + ":" + user.getName() + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                            throw new RuntimeException(e);
                                        } catch (KiteException e) {
                                            tradeSedaQueue.sendTelemgramSeda("error while modifying trail sl Option " + tradeData.getStockName() + ":" + user.getName() + " placed retry" + ":" + strategy.getTradeStrategyKey(), user.telegramBot.getGroupId());
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                            }
                        });

                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void postOrderModifyDataToSeda(TradeData tradeData,OrderParams orderParams,String orderId){
      //  tradeData.getTradeStrategy().getUserSubscriptions().getUserSubscriptionList().stream().forEach(userSubscription -> {
            User userB=userList.getUser().stream().filter(user -> tradeData.getUserId().equals(user.getName())).findFirst().get();
            OrderSedaData orderSedaData=new OrderSedaData();
            orderSedaData.setOrderParams(orderParams);
            orderSedaData.setUser(userB);
            orderSedaData.setOrderModificationType("modify");
            orderSedaData.setOrderId(orderId);
           // tradeData.isExited=true;
           // tradeData.isSLHit=true;
            tradeSedaQueue.sendOrderPlaceSeda(orderSedaData);
       // });
    }
    public double updatedTrailPrice(double initialSL,double entryPrice,double newPrice,int pointMove,int increaseSL,String entryType) {
        // Calculate the trailing stop-loss based on the price movement
        double trailingStopLoss=0;
        int localPriceMove = (int)(newPrice - entryPrice);
        System.out.println("price move diff:"+localPriceMove+":pointMove:"+pointMove+":initialSL:"+initialSL+":increaseSL:"+increaseSL+":entryPrice:"+entryPrice+":newPrice:"+newPrice);
        if (localPriceMove >= pointMove) {
            // For every this.pointMove, increase stop-loss by this.increaseSL points
            for(int i=increaseSL;i<=localPriceMove;i=i+increaseSL) {
                double trailingStopLossNew ;
                if ("BUY".equals(entryType)) {
                     trailingStopLossNew = initialSL + i;
                }else {
                    trailingStopLossNew = initialSL - i;
                }
                if (newPrice > trailingStopLossNew && "BUY".equals(entryType)) {
                    trailingStopLoss = trailingStopLossNew;
                }else if(newPrice < trailingStopLossNew && "SELL".equals(entryType)){
                    trailingStopLoss = trailingStopLossNew;
                }
            }
            System.out.printf("trailingStopLoss:"+trailingStopLoss);
        }
    //    System.out.printf("trailingStopLoss:"+trailingStopLoss);
        return trailingStopLoss;
    }
    /*public static void main(String[] args) throws IOException {
        WebSocketTicksSedaProcessor webSocketTicksSedaProcessor=new WebSocketTicksSedaProcessor();
       // webSocketTicksSedaProcessor.updatedTrailPrice(152,162,172.3,7,10,"BUY");
        webSocketTicksSedaProcessor.updatedTrailPrice(152,162,156,7,10,"BUY");
    }*/
}
