package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.telegram.TelegramMessenger;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.swing.text.html.Option;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
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
    public Map<String, Map<String, Double>> tickCurrentPrice = new HashMap<>();
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
    SimpleDateFormat hourtimeFormat = new SimpleDateFormat("HH:mm");
    public WebSocketTicksSedaProcessor() throws IOException {
        try {
                Date date = new Date();
                csvWriter = new CSVWriter(new FileWriter("/home/ubuntu/tick_" + dateFormat.format(date) + ".csv", true));
                String minTime=hourtimeFormat.format(date);
                String[] dataHeader = {"instrument_token", "tick_time", "last_price"};
                Map<String, Double> minutePrice=new HashMap<>();
                minutePrice.put(minTime,0.0);
                tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY BANK"),minutePrice);
                tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY 50"),minutePrice);
                tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE"),minutePrice);
                tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT"),minutePrice);
                tickCurrentPrice.put(zerodhaTransactionService.niftyIndics.get("SENSEX"),minutePrice);
                csvWriter.writeNext(dataHeader);
                csvWriter.flush();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            String message = camelContextRoute.getIn().getBody().toString();
            ArrayList<Tick> ticks = gson.fromJson(message, listType);
            if (!ticks.isEmpty()) {
                try {
                    LocalTime currentTime = LocalTime.now();
                    LocalTime tickStartTime = LocalTime.of(9, 14);
                    LocalTime tickEndTime = LocalTime.of(15, 31);
                    if (currentTime.isAfter(tickStartTime) && currentTime.isBefore(tickEndTime)) {

                        if (dataExport) {
                            ticks.stream().forEach(tick -> {
                                try {
                                    Date date = new Date();
                                    String minTime = hourtimeFormat.format(date);
                                    if (tickCurrentPrice.containsKey(String.valueOf(tick.getInstrumentToken()))) {
                                        Map<String, Double> minutePrice = tickCurrentPrice.get(String.valueOf(tick.getInstrumentToken()));
                                        minutePrice.put(minTime, tick.getLastTradedPrice());
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                                String[] dataHeader = {String.valueOf(tick.getInstrumentToken()), candleDateTimeFormat.format(tick.getTickTimestamp()), String.valueOf(tick.getLastTradedPrice())};
                                csvWriter.writeNext(dataHeader);
                            });
                            csvWriter.flush();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Optional<Tick> tickOptional = ticks.stream().filter(tickTemp -> tickTemp.getInstrumentToken() == 65611015).findFirst();
                //    }
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
                                if (strategy.isTrailEnabled()) {
                                    if (tradeData.isOrderPlaced && !tradeData.isExited && tradeData.isSlPlaced()) {
                                        double entryPrice;
                                        if ("BUY".equals(tradeData.getEntryType())) {
                                            entryPrice = tradeData.getBuyPrice().doubleValue();
                                        } else {
                                            entryPrice = tradeData.getSellPrice().doubleValue();
                                        }
                                        double trailingStopLoss = updatedTrailPrice(tradeData.getInitialSLPrice().doubleValue(), entryPrice, tick.getLastTradedPrice(), strategy.getTrailPointMove().intValue(), strategy.getTrailSlMoves().intValue(), tradeData.getEntryType());
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
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public double updatedTrailPrice(double initialSL,double entryPrice,double newPrice,int pointMove,int increaseSL,String entryType) {
        // Calculate the trailing stop-loss based on the price movement
        double trailingStopLoss=0;
        int localPriceMove = (int)(newPrice - entryPrice);
        if (localPriceMove >= pointMove) {
            // For every this.pointMove, increase stop-loss by this.increaseSL points
            for(int i=pointMove;i<=localPriceMove;i=i+pointMove) {
                double trailingStopLossNew ;
                if ("BUY".equals(entryType)) {
                     trailingStopLossNew = initialSL + (i * increaseSL);
                }else {
                    trailingStopLossNew = initialSL - (i * increaseSL);
                }
                if (newPrice > trailingStopLossNew && "BUY".equals(entryType)) {
                    trailingStopLoss = trailingStopLossNew;
                }else if(newPrice < trailingStopLossNew && "SELL".equals(entryType)){
                    trailingStopLoss = trailingStopLossNew;
                }
            }
        }
        System.out.printf("trailingStopLoss:"+trailingStopLoss);
        return trailingStopLoss;
    }
}
