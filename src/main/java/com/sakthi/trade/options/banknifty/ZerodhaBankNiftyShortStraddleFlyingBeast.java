/*
package com.sakthi.trade.options.banknifty;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ZerodhaBankNiftyShortStraddleFlyingBeast {

    @Autowired
    EventDayConfiguration eventDayConfiguration;

    @Value("${filepath.trend}")
    String trendPath;
    @Value("${home.path}")
    String homeFilePath;

    @Autowired
    CommonUtil commonUtil;
    String expDate = "";


    @Value("${telegram.straddle.bot.token}")
    String telegramToken;

    @Value("${banknifty.historic.straddle.flying}")
    String bankniftyLot;

    public Map<String, TradeData> flyingStraddleMap = new HashMap<>();

    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;
    private java.util.concurrent.Executors Executors;

    ExecutorService executorService= java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

@Autowired
ZerodhaAccount zerodhaAccount;
  //  @Scheduled(cron = "${banknifty.historic.straddle.flying1}")
  */
/*  public void zerodhaBankNifty917(){
        zerodhaBankNifty();
    }*//*


    //included in straddle check
    */
/*
    @Scheduled(cron = "${banknifty.historic.straddle.flying2}")
    public void zerodhaBankNifty0920(){
        zerodhaBankNifty();
    }*//*
*/
/*
    @Scheduled(cron = "${banknifty.historic.straddle.flying3}")
    public void zerodhaBankNifty1015(){
        zerodhaBankNifty();
    }
    @Scheduled(cron = "${banknifty.historic.straddle.flying4}")
    public void zerodhaBankNifty1115(){
        zerodhaBankNifty();
    }*//*

    public void zerodhaBankNifty(){
        StopWatch stopWatch=new StopWatch();
        stopWatch.start();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat sdftimeFormat = new SimpleDateFormat("HHmm");
        String currentDate=format.format(date);
        String niftyBank=zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
    String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from="+currentDate+"+09:00:00&to="+currentDate+"+15:15:00";
 //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.print(response);
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if(!status.equals("error")) {
            historicalData.parseResponse(json);
            System.out.println();
            HistoricalData historicalDataLastClose=historicalData.dataArrayList.get(historicalData.dataArrayList.size()-1);
       //     historicalData.dataArrayList.stream().forEach(historicalData1->{
            try {
                Date openDatetime = sdf.parse(historicalDataLastClose.timeStamp);
                String openDate = format.format(openDatetime);
             //   if (sdf.format(openDatetime).equals(openDate + "T09:15:00")) {
                    System.out.println(historicalDataLastClose.close);
                    int atmStrike=commonUtil.findATM((int)historicalDataLastClose.close);
                    log.info("Bank Nifty:"+atmStrike);
                    int qty = 25 * Integer.valueOf(bankniftyLot);
                    final Map<String,String> atmStrikesStraddle=zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                    atmStrikesStraddle.entrySet().forEach(entry->{
                        System.out.println(entry.getKey() + " " + entry.getValue());
                    });
                    atmStrikesStraddle.entrySet().stream().filter(atmStrikeStraddle->atmStrikeStraddle.getKey().contains(String.valueOf(atmStrike))).forEach(atmBankStrikeMap->{
                        executorService.submit(()-> {
                            System.out.println(atmBankStrikeMap.getKey());
                            OrderParams orderParams=new OrderParams();
                            orderParams.tradingsymbol=atmBankStrikeMap.getKey();
                            orderParams.exchange= "NFO";
                            orderParams.quantity= qty;
                            orderParams.orderType="MARKET";
                            orderParams.product="MIS";
                            orderParams.transactionType="SELL";
                            orderParams.validity="DAY";
                            Order order=null;
                            TradeData tradeData=new TradeData();
                            tradeData.setStockName(atmBankStrikeMap.getKey());
                            try {
                                order=zerodhaAccount.kiteSdk.placeOrder(orderParams,"regular");
                                tradeData.setEntryOrderId(order.orderId);
                                tradeData.isOrderPlaced = true;
                                tradeData.setQty(qty);
                                tradeData.setEntryType("SELL");
                                sendMessage.sendToTelegram("Flying Straddle option sold for strike: " + atmBankStrikeMap.getKey(), telegramToken);

                            } catch (KiteException e) {
                                tradeData.isErrored = true;
                                log.info("Error while placing straddle order: "+e.message);
                                if(order!=null) {
                                    sendMessage.sendToTelegram("Error while placing Flying straddle order: " + atmBankStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                                }
                                else{
                                    sendMessage.sendToTelegram("Error while placing Flying straddle order: " + atmBankStrikeMap.getKey(), telegramToken);

                                }
                                //e.printStackTrace();
                            } catch (IOException e) {
                                tradeData.isErrored = true;
                                log.info("Error while placing straddle order: "+e.getMessage());
                                if(order!=null) {
                                    sendMessage.sendToTelegram("Error while placing Flying straddle order: " + atmBankStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage+",Exception:"+e.getMessage(), telegramToken);
                                }
                                else{
                                    sendMessage.sendToTelegram("Error while placing Flying straddle order: " + atmBankStrikeMap.getKey()+",Exception:"+e.getMessage(), telegramToken);

                                }

                            }
                            flyingStraddleMap.put(atmBankStrikeMap.getKey()+"-"+sdftimeFormat.format(date), tradeData);

                        });
                    });

              //  }
            } catch (ParseException e) {
                e.printStackTrace();
            }


        }
        stopWatch.stop();
        log.info("process completed in ms:"+stopWatch.getTotalTimeMillis());
    }


    @Scheduled(cron = "${stradle.sl.scheduler}")
    public void sLMonitorScheduler() {
        // log.info("short straddle SLMonitor scheduler started");
        if (flyingStraddleMap != null) {
            flyingStraddleMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {

                        TradeData trendTradeData = map.getValue();
                       // System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                        List<Order> orderList= null;
                        try {
                            orderList = zerodhaAccount.kiteSdk.getOrders();
                         //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                        } catch (KiteException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        orderList.stream().forEach(order -> {
                            if(trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced  &&"COMPLETE".equals(order.status) && "SELL".equals(order.transactionType)) {
                                trendTradeData.setSellPrice(new BigDecimal(order.averagePrice));
                                trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                trendTradeData.setSellTime(order.exchangeTimestamp.toString());
                                BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(4))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
                                trendTradeData.setSlPrice(price);
                                OrderParams orderParams = new OrderParams();
                                orderParams.tradingsymbol = trendTradeData.getStockName();
                                orderParams.exchange = "NFO";
                                orderParams.quantity = Integer.valueOf(order.filledQuantity);
                                orderParams.orderType = "SL-M";
                                orderParams.product = "MIS";
                              //  orderParams.price=price.doubleValue();
                                orderParams.transactionType = "BUY";
                                orderParams.validity = "DAY";
                                orderParams.triggerPrice=price.doubleValue();
                                Order orderResponse = null;
                                try {
                                    orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");
                                    trendTradeData.setSlOrderId(orderResponse.orderId);
                                    trendTradeData.isSlPlaced = true;
                                    sendMessage.sendToTelegram("Flying Placed SL order for: " + trendTradeData.getStockName(), telegramToken);
                                   log.info("Flying SL order placed for: " + trendTradeData.getStockName());

                                } catch (KiteException e) {
                                    log.info("Error while placing  Flying straddle order: "+e.message);
                                    sendMessage.sendToTelegram("Error while placing Flying straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message, telegramToken);
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    log.info("Error while placing straddle order: "+e.getMessage());
                                    sendMessage.sendToTelegram("Error while placing Flying straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken);
                                    e.printStackTrace();
                                }
                            }

                            else if( order.orderId.equals(trendTradeData.getSlOrderId())&& trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit ){
                                if("CANCELLED".equals(order.status)) {
                                    trendTradeData.isSLCancelled = true;
                                    String message = MessageFormat.format("Broker Cancelled SL Order for Flying {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }else if("COMPLETE".equals(order.status)) {
                                    trendTradeData.isSLHit = true;
                                    trendTradeData.isExited = true;
                                    String message = MessageFormat.format("SL Hit for Flying {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }
                            }
                        }); });

        }
    }
    @Scheduled(cron = "${straddle.exit.position.scheduler}")
    public void exitPositions() throws KiteException, IOException {
        log.info("Straddle Exit positions scheduler started");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        flyingStraddleMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited && !orbTradeDataEntity.getValue().isSLHit ).forEach(trendMap -> {
                executor.submit(() -> {
                    TradeData trendTradeData = trendMap.getValue();
                    if (trendTradeData.getSlOrderId() != null && trendTradeData.getSlOrderId() != "") {
                        try {
                            if(!trendTradeData.isSLCancelled && !trendTradeData.isSLHit){
                            Order order=zerodhaAccount.kiteSdk.cancelOrder(trendTradeData.getSlOrderId(),"regular");
                            trendMap.getValue().isSLCancelled = true;
                            String message = MessageFormat.format("System Cancelled SL Flying {0}", trendMap.getValue().getStockName());
                            log.info(message);
                            sendMessage.sendToTelegram(message, telegramToken);}
                        } catch (KiteException e) {
                            e.printStackTrace();
                            String message = MessageFormat.format("Error while System cancelling SL Flying {0},[1]", trendMap.getValue().getStockName(),e.message);
                            log.info(message);
                            sendMessage.sendToTelegram(message, telegramToken);
                        } catch (IOException e) {
                            e.printStackTrace();
                            String message = MessageFormat.format("Error while System cancelling SL Flying {0},{1}", trendMap.getValue().getStockName(),e.getMessage());
                        log.info(message);
                        sendMessage.sendToTelegram(message, telegramToken);
                        }
                    }

            });

        });
*/
/*
        List<Position> positions=zerodhaAccount.kiteSdk.getPositions().get("net");
        positions.stream().filter(position-> "MIS".equals(position.product) &&(position.netQuantity>0 || position.netQuantity<0)).forEach(position->{
            if(flyingStraddleMap.get(position.tradingSymbol)!=null) {
                OrderParams orderParams = new OrderParams();
                orderParams.tradingsymbol = position.tradingSymbol;
                orderParams.exchange = "NFO";
                orderParams.quantity = Math.abs(position.netQuantity);
                orderParams.orderType = "MARKET";
                orderParams.product = "MIS";
                //orderParams.price=price.doubleValue();
                if(position.netQuantity>0){
                orderParams.transactionType = "SELL";
                }else {
                    orderParams.transactionType = "BUY";
                }
                orderParams.validity = "DAY";
                Order orderResponse = null;
                try {
                    orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");
                    flyingStraddleMap.get(position.tradingSymbol).isExited=true;
                    String message = MessageFormat.format("Closed Position Flying {0}", flyingStraddleMap.get(position.tradingSymbol).getStockName());
                    log.info(message);
                    sendMessage.sendToTelegram(message, telegramToken);
                } catch (KiteException e) {
                    log.info("Error while placing straddle order: " + e.message);
                    sendMessage.sendToTelegram("Error while exiting Flying order: " + flyingStraddleMap.get(position.tradingSymbol).getStockName() + ": Exception: " + e.message + " order Input:"+ new Gson().toJson(orderParams) +" positions: "+new Gson().toJson(position), telegramToken);
                    e.printStackTrace();
                } catch (IOException e) {
                    log.info("Error while placing straddle order: " + e.getMessage());
                    sendMessage.sendToTelegram("Error while exiting Flying order: " + flyingStraddleMap.get(position.tradingSymbol).getStockName() + ": Exception: " + e.getMessage() + " order Input:"+ new Gson().toJson(orderParams) +" positions: "+new Gson().toJson(position), telegramToken);
                    e.printStackTrace();
                }
            }
        });*//*




    }
    @Scheduled(cron = "${straddle.monitor.position.scheduler}")
    public void monitorPositions() throws KiteException, IOException {
        List<Position> positions=zerodhaAccount.kiteSdk.getPositions().get("net");
        positions.stream().filter(position-> position.netQuantity==0  && "MIS".equals(position.product)&& flyingStraddleMap.get(position.tradingSymbol)!=null && !flyingStraddleMap.get(position.tradingSymbol).isExited).forEach(position->{
            TradeData tradeData=flyingStraddleMap.get(position.tradingSymbol);
            List<com.zerodhatech.models.Order> orderList= null;
            try {
                orderList = zerodhaAccount.kiteSdk.getOrders();
                //   System.out.println("get trade response:"+new Gson().toJson(orderList));
            } catch (KiteException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            orderList.stream().forEach(order -> {

                if( ("OPEN".equals(order.status)|| "TRIGGER PENDING".equals(order.status))  && order.orderId.equals(tradeData.getSlOrderId())&& tradeData.isSlPlaced && !tradeData.isSLCancelled && !tradeData.isExited && !tradeData.isSLHit ){
                    tradeData.isExited=true;
                    try {
                        Order orderC=zerodhaAccount.kiteSdk.cancelOrder(tradeData.getSlOrderId(),"regular");
                    } catch (KiteException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    tradeData.isSLCancelled = true;
                    String message = MessageFormat.format("System Cancelled SL {0}", tradeData.getStockName());
                    log.info(message);
                    sendMessage.sendToTelegram(message, telegramToken);
                }
            });

        });
    }
}
*/
