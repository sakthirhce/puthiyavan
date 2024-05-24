package com.sakthi.trade.seda;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.entity.PositionPLDataEntity;
import com.sakthi.trade.repo.PositionPLDataRepo;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.models.Position;
import com.zerodhatech.models.Tick;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class PositionDataSedaProcessor implements Processor {
    Gson gson = new Gson();
    NumberFormat formatter = new DecimalFormat();
    @Autowired
    TradeEngine tradeEngine;
    @Value("${data.export}")
    boolean dataExport;
    @Autowired
    BrokerWorkerFactory workerFactory;
    public static final Logger LOGGER = LoggerFactory.getLogger(PositionDataSedaProcessor.class.getName());
    @Autowired
    TickData tickData;
    @Autowired
    UserList userList;
    @Autowired
    TradeSedaQueue tradeSedaQueue;
    DecimalFormat decimalFormat = new DecimalFormat("0.00");
    Type positionList = new TypeToken<List<Position>>() {}.getType();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat hourtimeFormat = new SimpleDateFormat("HH:mm");
    public PositionDataSedaProcessor() throws IOException {
    }

    @Autowired
    PositionPLDataRepo positionPLDataRepo;
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            List<Position> positions = gson.fromJson(message, positionList);
            AtomicDouble pnl=new AtomicDouble(0);
            positions.stream().forEach(position -> {
                pnl.getAndAdd(position.pnl);
            });
            PositionPLDataEntity livePLDataEntity=new PositionPLDataEntity();
            String dataKey = UUID.randomUUID().toString();
            livePLDataEntity.setDataKey(dataKey);
            livePLDataEntity.setPl(BigDecimal.valueOf(pnl.get()));
            LocalDateTime localDateTime=LocalDateTime.now();
            livePLDataEntity.setDataTime(Timestamp.valueOf(localDateTime));
            positionPLDataRepo.save(livePLDataEntity);
            /*
                tradeEngine.openTrade.entrySet().stream().forEach(userTradeData -> {
                    String userId = userTradeData.getKey();
                    User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                    if (userId.equals(user.getName())) {
                        List<TradeData> tradeDataList = userTradeData.getValue();
                        tradeDataList.forEach(tradeData -> {
                            Optional<Tick> tickOp = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == tradeData.getStockId()).findFirst();
                            if (tickOp.isPresent()) {
                                Tick tick = tickOp.get();
                                TradeStrategy strategy = tradeData.getTradeStrategy();
                                if (tradeData.isOrderPlaced && !tradeData.isExited && tradeData.isSlPlaced()) {
                                   double lastTradedPrice= tick.getLastTradedPrice();
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
                                   }
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
                    }
                });
            }*/
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
