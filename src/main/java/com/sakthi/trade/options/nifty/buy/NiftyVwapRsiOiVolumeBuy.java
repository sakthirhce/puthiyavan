package com.sakthi.trade.options.nifty.buy;

import com.google.gson.Gson;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainCategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class NiftyVwapRsiOiVolumeBuy {
    @Autowired
    ZerodhaAccount zerodhaAccount;
    @Autowired
    CommonUtil commonUtil;
    @Value("${filepath.trend}")
    String trendPath;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;
    @Autowired
    TransactionService transactionService;


    @Value("${fyers.positions}")
    String positionsURL;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    Map<String, TradeData> tradeMap = new HashMap<>();
    @Autowired
    SendMessage sendMessage;
    @Autowired
    FyerTransactionMapper fyerTransactionMapper;
    @Value("${nifty.vwap.rsi.oi.volume.lot}")
    String lotSize;
    @Value("${nifty.vwap.rsi.oi.volume.amount}")
    String vwapAmount;
    @Value("${nifty.vwap.rsi.oi.volume.sl.trail.percentage}")
    String trialPercent;

    @Value("${fyers.order.place.api}")
    String orderPlaceAPIUrl;
    @Value("${nifty.vwap.rsi.oi.volume.enabled}")
    boolean enabled;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    ExecutorService executorSer = Executors.newFixedThreadPool(5);
    SimpleDateFormat simpleDateFormatMi = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    public boolean isEnabled=true;
    @Scheduled(cron = "${nifty.vwap.rsi.oi.volume.scheduler}")
    public void buy() throws ParseException, KiteException, IOException {
        if(enabled){
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -1);
        Date date = cal.getTime();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        //   String currentDate=format.format(date);
        //start:test
        Calendar calendar = Calendar.getInstance();
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday


        calendar.add(DAY_OF_MONTH, -6);
        Calendar calendarCurrentDate = Calendar.getInstance();
        calendarCurrentDate.add(DAY_OF_MONTH, -1);
        Date cdate = calendar.getTime();

        String startDate = format.format(cdate);
     String currentDate = format.format(date);
     // String currentDate = format.format(calendarCurrentDate.getTime());
        String nifty = zerodhaTransactionService.niftyIndics.get("NIFTY 50");

        HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse(currentDate + " 09:15:00"), sdfformat.parse(sdfformat.format(date)), nifty, "5minute", false, false);
        //    HistoricalData historicalData=zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse("2021-01-05 09:00:00"),sdfformat.parse("2021-01-09 03:30:00"),niftyBank,"5minute",false,false);
        if (historicalData.dataArrayList.size() > 0) {
            HistoricalData historicalDataOpen = historicalData.dataArrayList.get(0);
            HistoricalData historicalDataClose = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
            // historicalData.dataArrayList.stream().forEach(historicalData1-> {
            try {
                AtomicLong openOrders= new AtomicLong();
                tradeMap.entrySet().stream().filter(tradeMap-> !tradeMap.getValue().isExited && tradeMap.getValue().isOrderPlaced).findFirst().ifPresent(tradeMap->{
                           openOrders.set(1);
                });


                Date openDatetime = sdf.parse(historicalDataClose.timeStamp);
                String openDate = format.format(openDatetime);
                Date closeDatetime = sdf.parse(historicalDataClose.timeStamp);
                String closeDate = format.format(closeDatetime);
                if (closeDate.equals(currentDate) && openDatetime.after(sdf.parse(openDate + "T09:14:00")) && closeDatetime.before(sdf.parse(closeDate + "T15:00:00"))) {
                    int atmStrike = commonUtil.findATM((int) historicalDataClose.close);
                    Map<String, String> putITMs=zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike+50));
                    Map<String, String> callITMs=zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike-50));
                    Map<String, String> atmStrikes = zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike));
                    callITMs.entrySet().stream().filter(callIT->callIT.getKey().contains("CE")).findFirst().ifPresent(callITM->{
                        atmStrikes.put(callITM.getKey(),callITM.getValue());
                    });
                    putITMs.entrySet().stream().filter(putITM->putITM.getKey().contains("PE")).findFirst().ifPresent(putITM->{
                        atmStrikes.put(putITM.getKey(),putITM.getValue());
                    });
                    atmStrikes.entrySet().stream().forEach(atmStrikeMap -> {
                        try {
                            String historicURL = "https://api.kite.trade/instruments/historical/" + atmStrikeMap.getValue() + "/5minute?from=" + startDate + "+09:00:00&to=" + simpleDateFormatMi.format(date) + ":59&oi=1";
                            //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-05+09:00:00&to=2021-01-05+04:15:00";
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                            HistoricalDataExtended historicalDataOp = new HistoricalDataExtended();
                            JSONObject json = new JSONObject(response);
                            String status = json.getString("status");
                            MathUtils.SMA sma20 = new MathUtils.SMA(20);
                            MathUtils.SMA sma50 = new MathUtils.SMA(50);
                            MathUtils.RSI rsi = new MathUtils.RSI(14);
                            if (!status.equals("error")) {
                                historicalDataOp.parseResponse(json);
                                calculateVwap(historicalDataOp.dataArrayList);
                                historicalDataOp.dataArrayList.stream().forEach(historicalDataExtended -> {
                                    double oi20 = sma20.compute(historicalDataExtended.oi);
                                    historicalDataExtended.setOima20(oi20);
                                    double vol50 = sma50.compute(historicalDataExtended.volume);
                                    historicalDataExtended.setVolumema50(vol50);
                                    double rsi14 = rsi.compute(historicalDataExtended.close);
                                    historicalDataExtended.setRsi(rsi14);

                                });

                            }
                            //    if (openDate.equals(currentDate)) {
                            try {
                                saveChart(atmStrikeMap.getKey(), currentDate, historicalDataOp.dataArrayList);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            HistoricalDataExtended historicalDataExtendedClose = historicalDataOp.dataArrayList.get(historicalDataOp.dataArrayList.size() - 1);
                            log.info("last candle:" + new Gson().toJson(historicalDataExtendedClose));
                            boolean vapCo = historicalDataExtendedClose.close > historicalDataExtendedClose.vwap;

                            boolean volumeCo = historicalDataExtendedClose.volume > historicalDataExtendedClose.volumema50;
                            boolean oiCo = historicalDataExtendedClose.oima20 > historicalDataExtendedClose.oi;
                            boolean rsiCo = historicalDataExtendedClose.rsi > 60;
                            log.info(atmStrikeMap.getKey()+": close>vwap:" + vapCo + " volume >volume 50:" + volumeCo + " oi20 >oi:" + oiCo + " rsi >60:" + rsiCo);
                            if (vapCo && volumeCo && rsiCo && oiCo && openOrders.get() ==0 && isEnabled) {
                                if (tradeMap.get(atmStrikeMap.getKey()) == null || (tradeMap.get(atmStrikeMap.getKey()) != null && !tradeMap.get(atmStrikeMap.getKey()).isOrderPlaced)) {

                                    TradeData tradeData = null;
                                 //   Order order = Order.BUY;
                                 /*   Calendar c = Calendar.getInstance();
                                    c.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
                                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyMdd");
                                    Date expDate = c.getTime();
                                    String option = "";
                                    if (atmStrikeMap.getKey().contains("CE")) {
                                        option = "CE";
                                    } else {
                                        option = "PE";
                                    }*/
                                    String symbol = "NSE:"+atmStrikeMap.getKey();
                                    BigDecimal qty=new BigDecimal(vwapAmount).divide(new BigDecimal(historicalDataExtendedClose.close).multiply(new BigDecimal("75")),0,RoundingMode.DOWN);
                                    int qtyInt=qty.intValue() * 75;
                                    System.out.println(atmStrikeMap.getKey());
                                    OrderParams orderParams=new OrderParams();
                                    orderParams.tradingsymbol=atmStrikeMap.getKey();
                                    orderParams.exchange= "NFO";
                                    orderParams.quantity= qty.intValue()*75;
                                    orderParams.orderType="MARKET";
                                    orderParams.product="MIS";
                                    orderParams.transactionType="BUY";
                                    orderParams.validity="DAY";
                                    com.zerodhatech.models.Order order=null;
                                    try {
                                        order=zerodhaAccount.kiteSdk.placeOrder(orderParams,"regular");
                                        sendMessage.sendToTelegram("Nifty:VWAP buy triggered for ATM option script:: " +atmStrikeMap.getKey(), telegramToken);
                                        if (tradeMap.get(atmStrikeMap.getKey()) != null) {
                                            tradeData = tradeMap.get(atmStrikeMap.getKey());
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setQty(qtyInt);
                                            tradeData.setBuyPrice(new BigDecimal(historicalDataClose.close));
                                            tradeData.setFyersSymbol(symbol);
                                            tradeData.setEntryOrderId(order.orderId);
                                        } else {
                                            tradeData = new TradeData();
                                            tradeData.setStockName(atmStrikeMap.getKey());
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setEntryType("BUY");
                                            tradeData.setQty(qtyInt);
                                            tradeData.setBuyPrice(new BigDecimal(historicalDataClose.close));
                                            tradeData.setZerodhaExchangeId(atmStrikeMap.getValue());
                                            tradeData.setFyersSymbol(symbol);
                                            tradeData.setStrike(atmStrike);
                                            tradeData.setEntryOrderId(order.orderId);
                                        }
                                    }catch (Exception e){
                                        e.printStackTrace();
                                        sendMessage.sendToTelegram("Nifty:Error while placing VWAP buy for ATM option script:" + atmStrikeMap.getKey(), telegramToken);
                                    }
                             /*       PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(symbol, order, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, qty, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                                    String payload = new Gson().toJson(placeOrderRequestDTO);
                                    Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                                    String orderResponse = transactionService.callOrderPlaceAPI(request);
                                    OrderResponseDTO orderResponseDTO = new Gson().fromJson(orderResponse, OrderResponseDTO.class);
                                    if (orderResponseDTO.getS().equals("ok")) {
                                        sendMessage.sendToTelegram("Nifty:VWAP buy triggered for ATM option script:" + atmStrikeMap.getKey(), telegramToken);
                                        if (tradeMap.get(atmStrikeMap.getKey()) != null) {
                                            tradeData = tradeMap.get(atmStrikeMap.getKey());
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setQty(qty);
                                            tradeData.setBuyPrice(new BigDecimal(historicalDataClose.close));
                                            tradeData.setFyersSymbol(symbol);
                                            tradeData.setEntryOrderId(orderResponseDTO.getId());
                                        } else {
                                            tradeData = new TradeData();
                                            tradeData.setStockName(atmStrikeMap.getKey());
                                            tradeData.isOrderPlaced = true;
                                            tradeData.setEntryType("BUY");
                                            tradeData.setQty(Integer.valueOf(lotSize) * 75);
                                            tradeData.setBuyPrice(new BigDecimal(historicalDataClose.close));
                                            tradeData.setZerodhaExchangeId(atmStrikeMap.getValue());
                                            tradeData.setFyersSymbol(symbol);
                                            tradeData.setStrike(atmStrike);
                                            tradeData.setEntryOrderId(orderResponseDTO.getId());
                                        }
                                    }else {
                                        sendMessage.sendToTelegram("Nifty:Error while placing VWAP buy for ATM option script:" + atmStrikeMap.getKey(), telegramToken);

                                    }*/
                                    tradeMap.put(atmStrikeMap.getKey(), tradeData);
                                }
                            }
                            //  }
                        } catch (Exception | KiteException e) {
                            e.printStackTrace();
                        }

                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            //  });
        }
        stopWatch.stop();
        log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());}
    }

    @Scheduled(cron = "${nifty.vwap.rsi.oi.volume.sl.monitor}")
    public void buyslMonitor() throws ParseException, KiteException, IOException {
        if(tradeMap.entrySet().stream().filter(tradeMap -> tradeMap.getValue().isOrderPlaced).findFirst().isPresent()) {
            tradeMap.entrySet().stream().filter(tradeMap -> tradeMap.getValue().isOrderPlaced).forEach(tradeMap -> {
                TradeData tradeData = tradeMap.getValue();
                try{
             /*   if (tradeData.isOrderPlaced && !tradeData.isSlPlaced) {
                    Request request = transactionService.createGetRequest(orderStatusAPIUrl, tradeData.getEntryOrderId());
                    String response = transactionService.callAPI(request);
                           OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);*/
                    List<com.zerodhatech.models.Order> orderList= null;
                    try {
                        orderList = zerodhaAccount.kiteSdk.getOrders();
                        //   System.out.println("get trade response:"+new Gson().toJson(orderList));
                    } catch (KiteException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

              //      if (orderStatusResponseDTO.getOrderDetails() != null && orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                    orderList.stream().forEach(order -> {
                        if (tradeData.getEntryOrderId().equals(order.orderId) && !tradeData.isSlPlaced && "COMPLETE".equals(order.status) && "BUY".equals(order.transactionType)) {
                            tradeData.setBuyPrice(new BigDecimal(order.averagePrice));
                            BigDecimal slPoints = (new BigDecimal(order.averagePrice).multiply(new BigDecimal(trialPercent))).divide(new BigDecimal(100)).setScale(0, BigDecimal.ROUND_DOWN);
                            BigDecimal slPrice = (new BigDecimal(order.averagePrice).subtract(slPoints)).setScale(0, RoundingMode.HALF_UP);
                            tradeData.setSlTrialPoints(slPoints);
                            tradeData.setSlPrice(slPrice);
                            OrderParams orderParams = new OrderParams();
                            orderParams.tradingsymbol = tradeData.getStockName();
                            orderParams.exchange = "NFO";
                            orderParams.quantity = Integer.valueOf(order.filledQuantity);
                            orderParams.orderType = "SL-M";
                            orderParams.product = "MIS";
                            //  orderParams.price=price.doubleValue();
                            orderParams.transactionType = "SELL";
                            orderParams.validity = "DAY";
                            orderParams.triggerPrice = slPrice.doubleValue();
                            com.zerodhatech.models.Order orderResponse = null;
                            try {
                                orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");
                                tradeData.setSlOrderId(orderResponse.orderId);
                                tradeData.isSlPlaced = true;
                                sendMessage.sendToTelegram("Vwap Placed SL order for: " + tradeData.getStockName(), telegramToken);
                                log.info("Vwap SL order placed for: " + tradeData.getStockName());

                            } catch (KiteException e) {
                                log.info("Error while placing Vwap order: " + e.message);
                                sendMessage.sendToTelegram("Error while placing vwap SL order: " + tradeData.getStockName() + " error message:" + e.message, telegramToken);
                                e.printStackTrace();
                            } catch (IOException e) {
                                log.info("Error while placing Vwap order: " + e.getMessage());
                                sendMessage.sendToTelegram("Error while placing vwap SL order: " + tradeData.getStockName() + ": error message:" + e.getMessage(), telegramToken);
                                e.printStackTrace();
                            }
                      /*  Order order = Order.SELL;
                        PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(tradeData.fyersSymbol, order, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, tradeData.getQty(), new BigDecimal(0), slPrice, new BigDecimal(0));
                        String payload = new Gson().toJson(placeOrderRequestDTO);
                        Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                        String slResponse = transactionService.callOrderPlaceAPI(slRequest);
                        OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                        if (orderResponseDTO.getS().equals("ok")) {
                            tradeData.setSlOrderId(orderResponseDTO.getId());
                            tradeData.isSlPlaced = true;
                            sendMessage.sendToTelegram("Nifty:VWAP SL Placed for ATM option script:" + tradeData.getStockName() + "Price:" + slPrice, telegramToken);
                        } else {
                            sendMessage.sendToTelegram("Nifty:Error while placing VWAP SL order for ATM option script:" + tradeData.getStockName() + "Price:" + slPrice, telegramToken);
                        }*/
                        }

                        if (tradeData.isOrderPlaced && tradeData.isSlPlaced  && !tradeData.isSLHit && !tradeData.isSLCancelled && tradeData.getSlOrderId().equals(order.orderId) && "TRIGGER PENDING".equals(order.status)) {

                            String[] str={"NFO:"+tradeData.getStockName()};
                            Map<String, LTPQuote>  ltpQuoteMap = null;
                            try {
                                log.info("quote request:"+str.toString());
                                ltpQuoteMap = zerodhaAccount.kiteSdk.getLTP(str);
                                log.info("quote Response:" +new Gson().toJson(ltpQuoteMap));
                            } catch (KiteException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            double lastPrice=0;
                            if(ltpQuoteMap.entrySet().stream().findFirst().isPresent()) {
                                lastPrice = ltpQuoteMap.entrySet().stream().findFirst().get().getValue().lastPrice;
                                if (lastPrice > 0) {
                                    //   HistoricalData historicalData = historicalDataOp.dataArrayList.get(historicalDataOp.dataArrayList.size() - 1);
                                    BigDecimal diff = new BigDecimal(lastPrice).subtract(tradeData.getBuyPrice());
                                    if (diff.compareTo(tradeData.getSlTrialPoints()) > 0) {
                                        BigDecimal mod = (diff.divide(tradeData.getSlTrialPoints(), 0, BigDecimal.ROUND_DOWN));
                                        log.info("mod cal:" +mod.toString());
                                        BigDecimal newSL = (tradeData.getBuyPrice().subtract(tradeData.getSlTrialPoints())).add(mod.multiply(tradeData.getSlTrialPoints())).setScale(0, RoundingMode.HALF_UP);
                                        log.info("new SL:" +newSL.toString());
                                        if (newSL.compareTo(tradeData.getSlPrice()) > 0) {
                                            OrderParams orderParams = new OrderParams();
                                            tradeData.setSlPrice(newSL);
                                           // orderParams.tradingsymbol = tradeData.getStockName();
                                        //    orderParams.exchange = "NFO";
                                            orderParams.quantity = Integer.valueOf(order.pendingQuantity);
                                            orderParams.orderType = "SL-M";
                                           // orderParams.product = "MIS";
                                            //  orderParams.price=price.doubleValue();
                                          //  orderParams.transactionType = "SELL";
                                            orderParams.validity = "DAY";
                                            orderParams.triggerPrice = newSL.doubleValue();
                                            com.zerodhatech.models.Order orderResponse=null;
                                            log.info("sl update:"+new Gson().toJson(orderParams));
                                            try {
                                                orderResponse =zerodhaAccount.kiteSdk.modifyOrder(tradeData.getSlOrderId(),orderParams,"regular");
                                                sendMessage.sendToTelegram("Nifty:VWAP Trial SL updated for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);

                                            } catch (KiteException e) {
                                                e.printStackTrace();
                                                sendMessage.sendToTelegram("Nifty:Error while updating VWAP Trial SL for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);

                                            } catch (IOException e) {
                                                e.printStackTrace();
                                                sendMessage.sendToTelegram("Nifty:Error while updating VWAP Trial SL for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);
                                            }
                                           /* tradeData.setSlPrice(newSL);
                                            ModifyOrder modifyOrder = new ModifyOrder();
                                            modifyOrder.setId(Integer.valueOf(tradeData.getSlOrderId()));
                                            modifyOrder.setStopLoss(newSL.doubleValue());
                                            String payload = new Gson().toJson(modifyOrder);
                                            Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.PUT, orderPlaceAPIUrl, payload);
                                            String slResponse = transactionService.callOrderPlaceAPI(slRequest);*/
                                        //    OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                                            if ("success".equals(orderResponse.status)) {
                                                sendMessage.sendToTelegram("Nifty:VWAP Trial SL updated for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);
                                            } else {
                                                sendMessage.sendToTelegram("Nifty:Error while updating VWAP Trial SL for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);

                                            }
                                            /*          } */
                                        }
                                    }
                                }
                            }
                        }
                        if (order.orderId.equals(tradeData.getSlOrderId()) && "COMPLETE".equals(order.status) && !tradeData.isSLHit) {
                            tradeData.isExited = true;
                            tradeData.isSLHit = true;
                            tradeData.setSellPrice(tradeData.getSlPrice());
                            sendMessage.sendToTelegram("Nifty:VWAP SL Hit for ATM option script:" + tradeData.getStockName() + "Price:" + tradeData.getSlPrice(), telegramToken);

                        }
                        if (order.orderId.equals(tradeData.getSlOrderId()) && "CANCELLED".equals(order.status) && !tradeData.isSLCancelled) {
                            tradeData.isSLCancelled = true;
                            String message = MessageFormat.format("Broker Cancelled SL Order of {0}", tradeData.getStockName());
                            log.info(message);
                            sendMessage.sendToTelegram(message, telegramToken);
                        }
                    });
                }catch (Exception e){
                    e.printStackTrace();
                }
            /*    try {


*//*
                    Request request = transactionService.createGetRequest(orderStatusAPIUrl, tradeData.getSlOrderId());
                    String slresponse = transactionService.callAPI(request);
                    //     System.out.println(response);
                    OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slresponse, OrderStatusResponseDTO.class);
                    if (orderStatusResponseDTO.getOrderDetails() != null && orderStatusResponseDTO.getOrderDetails().getStatus() == 6) {
                     //   String historicURL = "https://api.kite.trade/instruments/historical/" + tradeData.getZerodhaExchangeId() + "/5minute?from=" + format.format(new Date()) + "+09:00:00&to=" + sdfformat.format(new Date()) + "&oi=1";
                        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-05+09:00:00&to=2021-01-05+04:15:00";
                       String[] str={"NFO:"+tradeData.getStockName()};
                        Map<String, LTPQuote>  ltpQuoteMap =zerodhaAccount.kiteSdk.getLTP(str);
                        double lastPrice=0;
                       if(ltpQuoteMap.entrySet().stream().findFirst().isPresent()){
                           lastPrice=ltpQuoteMap.entrySet().stream().findFirst().get().getValue().lastPrice;

                       }
                      /*  String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                        HistoricalData historicalDataOp = new HistoricalData();
                        JSONObject json = new JSONObject(response);
                        String status = json.getString("status");
                        if (!status.equals("error")) {
                            historicalDataOp.parseResponse(json);
                            if (lastPrice > 0) {
                             //   HistoricalData historicalData = historicalDataOp.dataArrayList.get(historicalDataOp.dataArrayList.size() - 1);
                                BigDecimal diff = new BigDecimal(lastPrice).subtract(tradeData.getBuyPrice());
                                if (diff.compareTo(tradeData.getSlTrialPoints()) > 0) {
                                    BigDecimal mod = (diff.divide(tradeData.getSlTrialPoints(), 0, BigDecimal.ROUND_DOWN));
                                    BigDecimal newSL = (tradeData.getBuyPrice().subtract(tradeData.getSlTrialPoints())).add(mod.multiply(tradeData.getSlTrialPoints())).setScale(0, RoundingMode.HALF_UP);
                                    if (newSL.compareTo(tradeData.getSlPrice()) > 0) {
                                        tradeData.setSlPrice(newSL);
                                        ModifyOrder modifyOrder = new ModifyOrder();
                                        modifyOrder.setId(Integer.valueOf(tradeData.getSlOrderId()));
                                        modifyOrder.setStopLoss(newSL.doubleValue());
                                        String payload = new Gson().toJson(modifyOrder);
                                        Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.PUT, orderPlaceAPIUrl, payload);
                                        String slResponse = transactionService.callOrderPlaceAPI(slRequest);
                                        OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                                        if (orderResponseDTO.getS().equals("ok")) {
                                            sendMessage.sendToTelegram("Nifty:VWAP Trial SL updated for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);
                                        } else {
                                            sendMessage.sendToTelegram("Nifty:Error while updating VWAP Trial SL for ATM option script:" + tradeData.getStockName() + "new SL" + newSL, telegramToken);

                                        }
                                  }
                                }
                            }
                        }
                    }*//*
                   if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2 && !tradeData.isSLHit) {
                                tradeData.isExited = true;
                                tradeData.isSLHit = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                sendMessage.sendToTelegram("Nifty:VWAP SL Hit for ATM option script:" + tradeData.getStockName() + "Price:" + tradeData.getSlPrice(), telegramToken);

                    }
                    if (orderStatusResponseDTO.getOrderDetails().getStatus() == 1) {
                        tradeData.isSLCancelled = true;
                        String message = MessageFormat.format("Broker Cancelled SL Order of {0}", tradeData.getStockName());
                        log.info(message);
                        sendMessage.sendToTelegram(message, telegramToken);
                    }

                }
                }catch (Exception | KiteException e){
                    e.printStackTrace();
                }*/
            });
        }
}
  //  @Scheduled(cron = "${vwap.monitor.position.scheduler}")
    public void monitorPositions() throws KiteException, IOException {
        List<Position> positions=zerodhaAccount.kiteSdk.getPositions().get("net");
        positions.stream().filter(position-> position.netQuantity==0  && "MIS".equals(position.product)&& tradeMap.get(position.tradingSymbol)!=null && !tradeMap.get(position.tradingSymbol).isExited).forEach(position->{
            TradeData tradeData=tradeMap.get(position.tradingSymbol);
            tradeMap.get(position.tradingSymbol).isExited=true;
            if(!tradeData.isSLCancelled) {
                try {
                    com.zerodhatech.models.Order order = zerodhaAccount.kiteSdk.cancelOrder(tradeData.getSlOrderId(), "regular");
                    tradeData.isSLCancelled = true;
                    String message = MessageFormat.format("System Cancelled SL {0}", tradeData.getStockName());
                    log.info(message);
                    sendMessage.sendToTelegram(message, telegramToken);
                } catch (KiteException e) {
                    e.printStackTrace();
                    String message = MessageFormat.format("Error while cancelling SL {0}", tradeData.getStockName());
                    log.info(message);
                } catch (IOException e) {
                    e.printStackTrace();
                    String message = MessageFormat.format("Error while cancelling SL {0}", tradeData.getStockName());
                    log.info(message);
                }
            }
        });
    }
   /* public void positionMonitor(){
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);

        OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
       //
        if( openPositionsResponseDTO.getNetPositions().stream().findFirst().isPresent()) {
            openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
                executorSer.execute(() -> {
                    try {
                        if (netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType() == "INTRADAY") {
                            Integer netQty = netPositionDTO.getNetQty();
                            Optional<Map.Entry<String, TradeData>> trendMap = tradeMap.entrySet().stream().filter(tradeData -> tradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
                            if (netQty == 0 && trendMap.isPresent() && !trendMap.get().getValue().isExited){
                                trendMap.get().getValue().isExited = true;
                                String teleMessage = netPositionDTO.getSymbol() + ":Closed Postion";
                                log.info(teleMessage);
                                sendMessage.sendToTelegram(teleMessage, telegramToken);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        }
    }
*/
    @Scheduled(cron = "${vwap.exit.position.scheduler}")
    public void exitPositions() throws KiteException, IOException {
        log.info("Straddle Exit positions scheduler started");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        tradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited && !orbTradeDataEntity.getValue().isSLHit ).forEach(trendMap -> {
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
        positions.stream().filter(position-> "MIS".equals(position.product) &&(position.netQuantity>0 || position.netQuantity<0)).forEach(position->{
            if(tradeMap.get(position.tradingSymbol)!=null) {
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
                com.zerodhatech.models.Order orderResponse = null;
                try {
                    orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");
                    tradeMap.get(position.tradingSymbol).isExited=true;
                    String message = MessageFormat.format("Closed Position {0}", tradeMap.get(position.tradingSymbol).getStockName());
                    log.info(message);
                    sendMessage.sendToTelegram(message, telegramToken);
                } catch (KiteException e) {
                    log.info("Error while placing straddle order: " + e.message);
                    sendMessage.sendToTelegram("Error while exiting order: " + tradeMap.get(position.tradingSymbol).getStockName() + ": Exception: " + e.message + " order Input:"+ new Gson().toJson(orderParams) +" positions: "+new Gson().toJson(position), telegramToken);
                    e.printStackTrace();
                } catch (IOException e) {
                    log.info("Error while placing straddle order: " + e.getMessage());
                    sendMessage.sendToTelegram("Error while exiting order: " + tradeMap.get(position.tradingSymbol).getStockName() + ": Exception: " + e.getMessage() + " order Input:"+ new Gson().toJson(orderParams) +" positions: "+new Gson().toJson(position), telegramToken);
                    e.printStackTrace();
                }
            }
        });



    }
    /*
    public void exitPositions() {
        log.info("Vwap Exit positions scheduler started");
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            tradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited).forEach(trendMap -> {
                if (trendMap.getValue().getSlOrderId() != null && trendMap.getValue().getSlOrderId() != "") {
                    Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, trendMap.getValue().getSlOrderId());
                    String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                    OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                    if (orderStatusResponseDTO.getOrderDetails() != null && orderStatusResponseDTO.getOrderDetails().getStatus() != 2 && orderStatusResponseDTO.getOrderDetails().getStatus() == 6) {
                        CancelRequestDTO cancelRequestDTO = fyerTransactionMapper.prepareCancelRequest(Long.parseLong(trendMap.getValue().getSlOrderId()));
                        Request cancelRequest = transactionService.createPostPutDeleteRequest(HttpMethod.DELETE, orderPlaceAPIUrl, new Gson().toJson(cancelRequestDTO));
                        String cancelResponse = transactionService.callAPI(cancelRequest);
                        OrderResponseDTO orderResponseDTO = new Gson().fromJson(cancelResponse, OrderResponseDTO.class);
                        if (orderResponseDTO.getS().equals("ok")) {
                            trendMap.getValue().isSLCancelled = true;
                            String message = MessageFormat.format("System Cancelled SL {0}", trendMap.getValue().getStockName());
                            log.info(message);
                            sendMessage.sendToTelegram(message, telegramToken);
                        }
                    }
                }
            });
        });
        OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
        log.info("Open Positions: "+new Gson().toJson(openPositionsResponseDTO));

        openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {

                try {
                    if (netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType() == "INTRADAY") {
                        Integer netQty = netPositionDTO.getNetQty();
                        Integer openQty;
                        Optional<Map.Entry<String, TradeData>> trendMap = tradeMap.entrySet().stream().filter(tradeData -> tradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
                        if (netQty > 0 || netQty < 0) {

                            if (trendMap.isPresent()) {
                                Order order;
                                if (netQty > 0) {
                                    openQty = trendMap.get().getValue().getQty();
                                    order = Order.SELL;
                                } else {
                                    openQty = trendMap.get().getValue().getQty();
                                    order = Order.BUY;
                                }
                                PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(netPositionDTO.getSymbol(), order, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, openQty, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                                Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, new Gson().toJson(placeOrderRequestDTO));
                                String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                              String message = MessageFormat.format("Exited Positions response: {0}", exitPositionsResponse);
                                log.info(message);
                                try {
                                    OrderResponseDTO orderResponseDTO = new Gson().fromJson(exitPositionsResponse, OrderResponseDTO.class);
                                    if (orderResponseDTO.getS().equals("ok")) {
                                        log.info(message);
                                        trendMap.get().getValue().setExitOrderId(orderResponseDTO.getId());
                                        String teleMessage = netPositionDTO.getSymbol() + ":Closed Postion:" + openQty;
                                        sendMessage.sendToTelegram(teleMessage, telegramToken);
                                    } else {
                                        String teleMessage = netPositionDTO.getSymbol() + ":exit position call failed with status :" + orderResponseDTO.getS() + " Error :" + orderResponseDTO.getMessage();
                                        sendMessage.sendToTelegram(teleMessage, telegramToken);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String teleMessage = netPositionDTO.getSymbol() + ":Error while trying to close Postion";
                    sendMessage.sendToTelegram(teleMessage, telegramToken);
                }
            });


    }*/
    public void calculateVwap(List<HistoricalDataExtended> historicalDataExtendeds) {
        int i = 0;
        double pre_cumulativeTotal = 0;
        double pre_volume = 0;
        while (i < historicalDataExtendeds.size()) {
            HistoricalDataExtended historicalDataExtended = historicalDataExtendeds.get(i);
            if (i == 0) {

                double averagePrice = (historicalDataExtended.high + historicalDataExtended.low + historicalDataExtended.close) / 3;
                historicalDataExtendeds.get(i).vwap = averagePrice;
                pre_cumulativeTotal = averagePrice * historicalDataExtended.volume;
                pre_volume = historicalDataExtended.volume;
            } else {
                double averagePrice = (historicalDataExtended.high + historicalDataExtended.low + historicalDataExtended.close) / 3;

                double current_cumulativeTotal = averagePrice * historicalDataExtended.volume;
                double cumulativeTotal = current_cumulativeTotal + pre_cumulativeTotal;
                double vwap = cumulativeTotal / (historicalDataExtended.volume + pre_volume);
                pre_cumulativeTotal = cumulativeTotal;
                pre_volume = historicalDataExtended.volume + pre_volume;
                historicalDataExtendeds.get(i).vwap = vwap;
            }
            i++;
        }

    }

    public void saveChart(String name, String date, List<HistoricalDataExtended> historicalDataExtendedList) {
//https://github.com/Arction/lcjs-showcase-audio
        DateAxis domainAxis = new DateAxis("Date");
        OHLCDataset priceDataset = getPriceDataSet(name, date, historicalDataExtendedList);
        NumberAxis priceAxis = new NumberAxis("Price");
        CandlestickRenderer priceRenderer = new CandlestickRenderer();
        XYPlot pricePlot = new XYPlot(priceDataset, domainAxis, priceAxis, priceRenderer);
        priceRenderer.setSeriesPaint(0, Color.BLACK);
        priceRenderer.setDrawVolume(true);
        priceAxis.setAutoRangeIncludesZero(false);
        XYDataset otherDataSet = createRSIXYDataSet(name, date, historicalDataExtendedList);
        XYDataset otherDataSet1 = createOIXYDataSet(name, date, historicalDataExtendedList);
        NumberAxis rsiAxis = new NumberAxis("RSI");
        XYItemRenderer rsiRenderer = new XYLineAndShapeRenderer(true, false);
        rsiRenderer.setSeriesPaint(0, Color.blue);
        XYPlot rsiPlot = new XYPlot(otherDataSet, domainAxis, rsiAxis, rsiRenderer);
        NumberAxis OIAxis = new NumberAxis("OI");
        XYItemRenderer oiRenderer = new XYLineAndShapeRenderer(true, false);
        oiRenderer.setSeriesPaint(0, Color.blue);
        oiRenderer.setSeriesPaint(1, Color.red);
        XYPlot oiPlot = new XYPlot(otherDataSet1, domainAxis, OIAxis, oiRenderer);

        //create the plot
        CategoryPlot plot = new CategoryPlot();

//add the first dataset, and render as bar values
        CategoryDataset volumeDataSet = createVolumeDataSet(name, date, historicalDataExtendedList);
        XYDataset volumeMADataSet = createVolumeMADataSet(name, date, historicalDataExtendedList);
        CategoryItemRenderer renderer = new BarRenderer();
        plot.setDataset(0, volumeDataSet);
        plot.setRenderer(0, renderer);

//add the second dataset, render as lines
      /*  CategoryItemRenderer renderer2 = new LineAndShapeRenderer();
        plot.setDataset(1, dataset);
        plot.setRenderer(1, renderer2);*/

        CombinedDomainXYPlot mainPlot = new CombinedDomainXYPlot(domainAxis);
        final CategoryAxis domainAxis1 = new CategoryAxis("Category");
        CombinedDomainCategoryPlot mainPlotC = new CombinedDomainCategoryPlot(domainAxis1);
        mainPlot.add(pricePlot);
        mainPlot.add(rsiPlot);
        mainPlot.add(oiPlot);
//       mainPlot.add(plot);

        JFreeChart chart = new JFreeChart(name, (Font) null, mainPlot, false);

        try {
            ChartUtilities.saveChartAsPNG(new File(trendPath + "/" + name + ".png"), chart, 1200, 600);
        } catch (Exception var22) {
            var22.printStackTrace();
        }
    }

    public OHLCDataset getPriceDataSet(String name, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        List<OHLCDataItem> dataItems = new ArrayList();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {
                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        if (cdate.equals(format.format(openDatetime))) {
                            double open = historicalDataExtended.open;
                            double high = historicalDataExtended.high;
                            double low = historicalDataExtended.low;
                            double close = historicalDataExtended.close;
                            double volume = historicalDataExtended.volume;
                            OHLCDataItem item = new OHLCDataItem(date, open, high, low, close, volume);
                            dataItems.add(item);
                        }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        Collections.reverse(dataItems);
        OHLCDataItem[] data = (OHLCDataItem[]) dataItems.toArray(new OHLCDataItem[dataItems.size()]);
        return new DefaultOHLCDataset(name, data);

    }

    public XYDataset createRSIXYDataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {

        TimeSeries s1 = new TimeSeries("RSI", Minute.class);


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.rsi);

                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }

    public XYDataset createOIXYDataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.oi);
                            s2.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.oima20);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        dataset1.addSeries(s2);
        return dataset1;
    }

    public CategoryDataset createVolumeDataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "Volume";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            //      s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(),day,month,year), historicalDataExtended.volume);
                            dataset.addValue(historicalDataExtended.volume, series1, date);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset;
    }

    public XYDataset createVolumeMADataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.volumema50);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }
}
