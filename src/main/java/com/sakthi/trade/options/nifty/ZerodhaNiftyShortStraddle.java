/*
package com.sakthi.trade.options.nifty;

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

@Component
@Slf4j
public class ZerodhaNiftyShortStraddle {

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

    @Value("${straddle.nifty.lot}")
    String niftyLot;


    public Map<String, TradeData> straddleTradeMap = new HashMap<>();

    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;
    private java.util.concurrent.Executors Executors;

    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

@Autowired
ZerodhaAccount zerodhaAccount;
    @Scheduled(cron = "${nifty.historic.straddle.quote}")
    public void zerodhaBankNifty(){
        StopWatch stopWatch=new StopWatch();
        stopWatch.start();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String currentDate=format.format(date);
        String nifty=zerodhaTransactionService.niftyIndics.get("NIFTY 50");
    String historicURL = "https://api.kite.trade/instruments/historical/" + nifty + "/5minute?from="+currentDate+"+09:00:00&to="+currentDate+"+11:15:00";
 //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.print(response);
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if(!status.equals("error")) {
            historicalData.parseResponse(json);
            System.out.println();
            ExecutorService executorService= java.util.concurrent.Executors.newFixedThreadPool(5);
            historicalData.dataArrayList.stream().forEach(historicalData1->{
            try {
                Date openDatetime = sdf.parse(historicalData1.timeStamp);
                String openDate = format.format(openDatetime);
                if (sdf.format(openDatetime).equals(openDate + "T09:15:00")) {
                    System.out.println(historicalData1.close);
                    int atmStrike=commonUtil.findATM((int)historicalData1.close);
                    int qty = 75 * Integer.valueOf(niftyLot);
                    Map<String,String> atmStrikes=zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike));
                    atmStrikes.entrySet().stream().forEach(atmStrikeMap->{
                        executorService.submit(()-> {
                            System.out.println(atmStrikeMap.getKey());
                            OrderParams orderParams=new OrderParams();
                            orderParams.tradingsymbol=atmStrikeMap.getKey();
                            orderParams.exchange= "NFO";
                            orderParams.quantity= qty;
                            orderParams.orderType="MARKET";
                            orderParams.product="MIS";
                            orderParams.transactionType="SELL";
                            orderParams.validity="DAY";
                            Order order=null;
                            TradeData tradeData=new TradeData();
                            tradeData.setStockName(atmStrikeMap.getKey());
                            try {
                                order=zerodhaAccount.kiteSdk.placeOrder(orderParams,"regular");
                                tradeData.setEntryOrderId(order.orderId);
                                tradeData.isOrderPlaced = true;
                                tradeData.setQty(qty);
                                tradeData.setEntryType("SELL");
                                sendMessage.sendToTelegram("Straddle option sold for strike: " + atmStrikeMap.getKey(), telegramToken);

                            } catch (KiteException e) {
                                tradeData.isErrored = true;
                                log.info("Error while placing straddle order: "+e.message);
                                if(order!=null) {
                                    sendMessage.sendToTelegram("Error while placing straddle order: " + atmStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage, telegramToken);
                                }
                                else{
                                    sendMessage.sendToTelegram("Error while placing straddle order: " + atmStrikeMap.getKey(), telegramToken);

                                }
                                //e.printStackTrace();
                            } catch (IOException e) {
                                tradeData.isErrored = true;
                                log.info("Error while placing straddle order: "+e.getMessage());
                                if(order!=null) {
                                    sendMessage.sendToTelegram("Error while placing straddle order: " + atmStrikeMap.getKey() + ": Status: " + order.status + ": error message:" + order.statusMessage+",Exception:"+e.getMessage(), telegramToken);
                                }
                                else{
                                    sendMessage.sendToTelegram("Error while placing straddle order: " + atmStrikeMap.getKey()+",Exception:"+e.getMessage(), telegramToken);

                                }

                            }
                            straddleTradeMap.put(atmStrikeMap.getKey(), tradeData);

                        });
                    });

                }
            } catch (ParseException e) {
                e.printStackTrace();
            }});

        }
        stopWatch.stop();
        log.info("process completed in ms:"+stopWatch.getTotalTimeMillis());
    }


    @Scheduled(cron = "${stradle.sl.scheduler}")
    public void sLMonitorScheduler() {
        // log.info("short straddle SLMonitor scheduler started");
        if (straddleTradeMap != null) {
            straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {

                        TradeData trendTradeData = map.getValue();
                        System.out.println(" trade data:"+new Gson().toJson(trendTradeData));
                        List<Order> orderList= null;
                        try {
                            orderList = zerodhaAccount.kiteSdk.getOrders();
                            System.out.println("get trade response:"+new Gson().toJson(orderList));
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
                                BigDecimal price = ((new BigDecimal(order.averagePrice).divide(new BigDecimal(2))).add(new BigDecimal(order.averagePrice))).setScale(0, RoundingMode.HALF_UP);
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
                                    sendMessage.sendToTelegram("Placed SL order for: " + trendTradeData.getStockName(), telegramToken);
                                   log.info("SL order placed for: " + trendTradeData.getStockName());

                                } catch (KiteException e) {
                                    log.info("Error while placing straddle order: "+e.message);
                                    sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + " error message:" + e.message, telegramToken);
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    log.info("Error while placing straddle order: "+e.getMessage());
                                    sendMessage.sendToTelegram("Error while placing straddle SL order: " + trendTradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken);
                                    e.printStackTrace();
                                }
                            }

                            else if( order.orderId.equals(trendTradeData.getSlOrderId())&& trendTradeData.isSlPlaced && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit ){
                                if("CANCELLED".equals(order.status)) {
                                    trendTradeData.isSLCancelled = true;
                                    String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }else if("COMPLETE".equals(order.status))
                                {
                                    trendTradeData.isSLHit = true;
                                    trendTradeData.isExited = true;
                                    String message = MessageFormat.format("SL Hit for {0}", trendTradeData.getStockName());
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

        straddleTradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited && !orbTradeDataEntity.getValue().isSLHit ).forEach(trendMap -> {
                executor.submit(() -> {
                    TradeData trendTradeData = trendMap.getValue();
                    if (trendTradeData.getSlOrderId() != null && trendTradeData.getSlOrderId() != "") {
                        try {
                            if(!trendTradeData.isSLCancelled){
                            Order order=zerodhaAccount.kiteSdk.cancelOrder(trendTradeData.getSlOrderId(),"regular");
                            trendMap.getValue().isSLCancelled = true;
                            String message = MessageFormat.format("System Cancelled SL {0}", trendMap.getValue().getStockName());
                            log.info(message);
                            sendMessage.sendToTelegram(message, telegramToken);}
                        } catch (KiteException e) {
                            e.printStackTrace();
                            String message = MessageFormat.format("Error while System cancelling SL {0},[1]", trendMap.getValue().getStockName(),e.message);
                            log.info(message);
                            sendMessage.sendToTelegram(message, telegramToken);
                        } catch (IOException e) {
                            e.printStackTrace();
                            String message = MessageFormat.format("Error while System cancelling SL {0},{1}", trendMap.getValue().getStockName(),e.getMessage());
                        log.info(message);
                        sendMessage.sendToTelegram(message, telegramToken);
                        }
                    }

            });

        });

        List<Position> positions=zerodhaAccount.kiteSdk.getPositions().get("net");
        positions.stream().filter(position-> position.netQuantity>0 || position.netQuantity<0).forEach(position->{
            if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                OrderParams orderParams = new OrderParams();
                orderParams.tradingsymbol = position.tradingSymbol;
                orderParams.exchange = "NFO";
                orderParams.quantity = position.netQuantity;
                orderParams.orderType = "MARKET";
                orderParams.product = "MIS";
                //orderParams.price=price.doubleValue();
                if(position.netQuantity>0){
                orderParams.transactionType = "SELL";
                }else {
                    orderParams.transactionType = "BUY";
                }
                orderParams.validity = "DAY";
                com.zerodhatech.models.Order orderResponse = null;
                try {
                    orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");
                    straddleTradeMap.get(position.tradingSymbol).isExited=true;
                    String message = MessageFormat.format("Closed Position {0}", straddleTradeMap.get(position.tradingSymbol).getStockName());
                    log.info(message);
                    sendMessage.sendToTelegram(message, telegramToken);
                } catch (KiteException e) {
                    log.info("Error while placing straddle order: " + e.message);
                    sendMessage.sendToTelegram("Error while exiting order: " + straddleTradeMap.get(position.tradingSymbol).getStockName() + ": Exception: " + e.message, telegramToken);
                    e.printStackTrace();
                } catch (IOException e) {
                    log.info("Error while placing straddle order: " + e.getMessage());
                    sendMessage.sendToTelegram("Error while exiting order: " + straddleTradeMap.get(position.tradingSymbol).getStockName() + ": Exception: " + e.getMessage(), telegramToken);
                    e.printStackTrace();
                }
            }
        });



    }
    @Scheduled(cron = "${straddle.monitor.position.scheduler}")
    public void monitorPositions() throws KiteException, IOException {
        List<Position> positions=zerodhaAccount.kiteSdk.getPositions().get("net");
        positions.stream().filter(position-> position.netQuantity==0 && straddleTradeMap.get(position.tradingSymbol)!=null && !straddleTradeMap.get(position.tradingSymbol).isExited).forEach(position->{
            TradeData tradeData=straddleTradeMap.get(position.tradingSymbol);
            straddleTradeMap.get(position.tradingSymbol).isExited=true;
            if(!tradeData.isSLCancelled){
                try {
                    Order order=zerodhaAccount.kiteSdk.cancelOrder(tradeData.getSlOrderId(),"regular");
                } catch (KiteException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                tradeData.isSLCancelled = true;
                String message = MessageFormat.format("System Cancelled SL {0}", tradeData.getStockName());
                log.info(message);
                sendMessage.sendToTelegram(message, telegramToken);}
        });
    }


}
*/
