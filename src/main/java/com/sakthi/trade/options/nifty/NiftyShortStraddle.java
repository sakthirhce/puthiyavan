/*
package com.sakthi.trade.options.nifty;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.fyer.transactions.OrderDetailsDTO;
import com.sakthi.trade.fyer.transactions.OrderResponseDTO;
import com.sakthi.trade.fyer.transactions.OrderStatusResponseDTO;
import com.sakthi.trade.fyer.transactions.PlaceOrderRequestDTO;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.truedata.HistoricRequestDTO;
import com.sakthi.trade.truedata.HistoricResponseDTO;
import com.sakthi.trade.truedata.RealTimeSubscribeRequestDTO;
import com.sakthi.trade.websocket.truedata.HistoricWebsocket;
import com.sakthi.trade.websocket.truedata.RealtimeWebsocket;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.annotation.PreDestroy;
import javax.websocket.Session;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class NiftyShortStraddle {

    @Autowired
    EventDayConfiguration eventDayConfiguration;

    @Autowired
    HistoricWebsocket historicWebsocket;

    @Value("${filepath.trend}")
    String trendPath;

    @Autowired
    RealtimeWebsocket realtimeWebsocket;

    String expDate = "";

    String trueDataExpDate = "";

    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;

    @Value("${fyers.order.place.api}")
    String orderPlaceAPIUrl;
    @Value("${fyers.order.place.api}")
    String orderPlaceURL;

    @Value("${fyers.positions}")
    String positionsURL;

    @Value("${fyers.exit.positions}")
    String exitPositionsURL;

    @Value("${telegram.straddle.bot.token}")
    String telegramToken;

    @Value("${straddle.nifty.lot}")
    String niftyLot;

    @Autowired
    FyerTransactionMapper fyerTransactionMapper;

    @Autowired
    TransactionService transactionService;

    public Map<String, TradeData> tradeDataHashMap = new HashMap<>();

    @Autowired
    SendMessage sendMessage;

   // @Scheduled(cron = "${nifty.historic.straddle.quote}")
    public void shortStraddleTradeSchedule() {


        log.info("Short straddle scheduler started");
        String payload = historicInput("5min", "NIFTY 50");
        Session session = historicWebsocket.session;
        log.info(payload);
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(payload);
            } else {
                historicWebsocket.createHistoricWebSocket();
                session = historicWebsocket.session;
                session.getBasicRemote().sendText(payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void shortStraddleTradeScheduleBacktest(String date) {


        log.info("Short straddle scheduler started");
        String payload = historicInput("5min", "NIFTY 50", date);
        Session session = historicWebsocket.session;
        log.info(payload);
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(payload);
            } else {
                historicWebsocket.createHistoricWebSocket();
                session = historicWebsocket.session;
                session.getBasicRemote().sendText(payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void shortStraddleTradeProcessor(HistoricResponseDTO historicResponseDTO) throws CsvValidationException, ParseException, IOException {
        Map<String, EventDayData> eventDayMap = eventDayConfiguration.eventDayConfig();
        Date currentDate = new Date();
        String strCurrentDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if (eventDayMap.containsKey(strCurrentDate) && eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")) {
            log.info("no trade day. not starting straddle");
        } else {
            log.info("Short straddle process started");
            List<List<Object>> list = historicResponseDTO.getData();
            log.info("straddle:" + historicResponseDTO.getSymbol());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
            list.stream().forEach(tickData -> {
                try {
                    String strTickDateTime = tickData.get(0).toString();
                    final Date endDate = sdf1.parse(strTickDateTime);
                    final String evaluateDate = sdf1.format(endDate);
                    String checkTime = evaluateDate + "T09:15:00";
                    Date tickDateTime = null;
                    try {
                        tickDateTime = sdf.parse(strTickDateTime);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (sdf.format(tickDateTime).equals(checkTime)) {
                        BigDecimal closePrice = new BigDecimal(tickData.get(4).toString());
                        BigDecimal scaled = closePrice.setScale(0, RoundingMode.HALF_UP);
                        int finalValue = findATM(scaled.intValue());
                        System.out.println("rounded value:" + finalValue);
                        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/nifty_week_fo.csv"));
                        String[] line = null;
                        expDate = expDate + " " + finalValue;


                        sendMessage.sendToTelegram("Nifty ATM Option selected for Straddle: " + expDate, telegramToken);
                        while ((line = csvReader.readNext()) != null) {
                            if (line[1].contains(expDate)) {
                                System.out.println(line[9]);
                                //sell options
                                PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(line[9], Order.SELL, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, Integer.valueOf(line[3])*Integer.valueOf(niftyLot), new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                                Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                                String response = transactionService.callAPI(request);
                                System.out.println("sell response: " + response);
                                OrderResponseDTO orderResponseDTO = new Gson().fromJson(response, OrderResponseDTO.class);
                                TradeData trendTradeData = new TradeData();
                                trendTradeData.setStockName(line[9]);
                                Date date = new Date();
                                trendTradeData.setCreateTimestamp(date);
                                if (orderResponseDTO.getS().equals("ok")) {
                                    trendTradeData.setEntryOrderId(orderResponseDTO.getId());
                                    trendTradeData.isOrderPlaced = true;
                                    trendTradeData.setEntryType("SELL");
                                    sendMessage.sendToTelegram("Straddle option sold for strike: " + line[9], telegramToken);
                                } else {
                                    trendTradeData.isErrored = true;
                                    sendMessage.sendToTelegram("Error while placing straddle order: " + line[9] + ": Status: " + orderResponseDTO.getS() + ": error message:" + orderResponseDTO.getMessage(), telegramToken);
                                }
                                tradeDataHashMap.put(line[9]+"-SHORT", trendTradeData);
                            }
                        }*/
/*
                        try{
                            String exptrueDataExpDateCE = trueDataExpDate + finalValue + "CE";
                            String exptrueDataExpDatePE = trueDataExpDate + finalValue + "PE";
                            System.out.println(trueDataExpDate);

                            RealTimeSubscribeRequestDTO payload = realtimeWebsocket.prepareRealtimeQuoteInput(null, exptrueDataExpDatePE, exptrueDataExpDateCE);
                            Session session = realtimeWebsocket.session;
                            log.info(new Gson().toJson(payload));
                            try {
                                if (session != null && session.isOpen()) {
                                    session.getBasicRemote().sendText(new Gson().toJson(payload));
                                } else {
                                    realtimeWebsocket.createRealtimeWebSocket();
                                    session = realtimeWebsocket.session;
                                    session.getBasicRemote().sendText(new Gson().toJson(payload));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }*//*

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }

    public void shortStraddleTradeProcessBackTest(HistoricResponseDTO historicResponseDTO) throws CsvValidationException, ParseException, IOException {
        Map<String, EventDayData> eventDayMap = eventDayConfiguration.eventDayConfig();
        Date currentDate = new Date();
        String strCurrentDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/nifty_options_straddle.csv", true));
        if (eventDayMap.containsKey(strCurrentDate) && eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")) {
            log.info("no trade day. not starting straddle");
        } else {
            TradeData tradeData = new TradeData();
            TradeData longTradeData = new TradeData();
            log.info("Short straddle process started");
            List<List<Object>> list = historicResponseDTO.getData();
            log.info("straddle:" + historicResponseDTO.getSymbol());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
            list.stream().forEach(tickData -> {
                try {
                    String strTickDateTime = tickData.get(0).toString();
                    final Date endDate = sdf1.parse(strTickDateTime);
                    final String evaluateDate = sdf1.format(endDate);
                    String checkTime = evaluateDate + "T09:15:00";
                    String closeTime = evaluateDate + "T15:05:00";
                    Date tickDateTime = null;
                    try {
                        tickDateTime = sdf.parse(strTickDateTime);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    BigDecimal closePrice = new BigDecimal(tickData.get(4).toString());
                    if (sdf.format(tickDateTime).equals(checkTime) && !tradeData.isOrderPlaced) {
                        BigDecimal sl = closePrice.add(closePrice.divide(new BigDecimal("2")));
                        tradeData.setStockName(historicResponseDTO.getSymbol());
                        tradeData.isOrderPlaced = true;
                        tradeData.setSellTime(strTickDateTime);
                        tradeData.setSlPrice(sl);
                        tradeData.setQty(75);
                        tradeData.setSellPrice(closePrice);

                    } else if (tickDateTime.after(sdf.parse(checkTime)) && tradeData.isOrderPlaced && !tradeData.isExited && closePrice.compareTo(tradeData.getSlPrice()) > 0) {
                        tradeData.setBuyPrice(tradeData.getSlPrice());
                        tradeData.isExited = true;
                        tradeData.setBuyTime(strTickDateTime);
                        String[] data = {tradeData.getStockName(), tradeData.getBuyTime(), tradeData.getBuyPrice().toString(), tradeData.getSellTime(), tradeData.getSellPrice().toString(), tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(tradeData.getQty())).toString(), evaluateDate};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                     //   tradeData=new TradeData();


                    } else if (sdf.format(tickDateTime).equals(closeTime) && tradeData.isOrderPlaced && !tradeData.isExited) {
                        tradeData.setBuyPrice(closePrice);
                        tradeData.setBuyTime(strTickDateTime);
                        tradeData.isExited = true;
                        String[] data = {tradeData.getStockName(), tradeData.getBuyTime(), tradeData.getBuyPrice().toString(), tradeData.getSellTime(), tradeData.getSellPrice().toString(), tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(tradeData.getQty())).toString(), evaluateDate};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }

    public void shortStraddleTradeProcessLongBackTest(HistoricResponseDTO historicResponseDTO) throws CsvValidationException, ParseException, IOException {
        Map<String, EventDayData> eventDayMap = eventDayConfiguration.eventDayConfig();
        Date currentDate = new Date();
        String strCurrentDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/nifty_options_straddle.csv", true));
        if (eventDayMap.containsKey(strCurrentDate) && eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")) {
            log.info("no trade day. not starting straddle");
        } else {
            TradeData tradeData = new TradeData();
            TradeData longTradeData = new TradeData();
            log.info("Short straddle process started");
            List<List<Object>> list = historicResponseDTO.getData();
            log.info("straddle:" + historicResponseDTO.getSymbol());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
            list.stream().forEach(tickData -> {
                try {
                    String strTickDateTime = tickData.get(0).toString();
                    final Date endDate = sdf1.parse(strTickDateTime);
                    final String evaluateDate = sdf1.format(endDate);
                    String checkTime = evaluateDate + "T09:15:00";
                    String closeTime = evaluateDate + "T15:05:00";
                    Date tickDateTime = null;
                    try {
                        tickDateTime = sdf.parse(strTickDateTime);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    BigDecimal closePrice = new BigDecimal(tickData.get(4).toString());
                    if (sdf.format(tickDateTime).equals(checkTime) && !tradeData.isOrderPlaced) {
                        //BigDecimal sl = closePrice.add(closePrice.divide(new BigDecimal("2")));
                        BigDecimal sl = closePrice.multiply(new BigDecimal("2"));
                        tradeData.setStockName(historicResponseDTO.getSymbol());
                        tradeData.isOrderPlaced = true;
                        tradeData.setSellTime(strTickDateTime);
                        tradeData.setSlPrice(sl);
                        tradeData.setQty(75);
                        tradeData.setSellPrice(closePrice);
                        tradeData.setEntryType("SHORT");

                    } else if (tickDateTime.after(sdf.parse(checkTime)) && tradeData.isOrderPlaced && !tradeData.isExited && closePrice.compareTo(tradeData.getSlPrice()) > 0) {
                        tradeData.setBuyPrice(tradeData.getSlPrice());
                        tradeData.isExited = true;
                        tradeData.setBuyTime(strTickDateTime);
                        String[] data = {tradeData.getStockName(), tradeData.getBuyTime(), tradeData.getBuyPrice().toString(), tradeData.getSellTime(), tradeData.getSellPrice().toString(), tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(tradeData.getQty())).toString(), evaluateDate,tradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        longTradeData.setEntryType("LONG");
                        longTradeData.setStockName(historicResponseDTO.getSymbol());
                        longTradeData.setBuyTime(strTickDateTime);
                        longTradeData.setQty(tradeData.getQty());
                        longTradeData.setBuyPrice(tradeData.getSlPrice());
                        longTradeData.isOrderPlaced=true;
                        longTradeData.setSlPrice(tradeData.getSellPrice());


                    } else if (tickDateTime.after(sdf.parse(checkTime)) && longTradeData.isOrderPlaced && !longTradeData.isExited && closePrice.compareTo(longTradeData.getSlPrice()) < 0) {
                        longTradeData.setSellPrice(longTradeData.getSlPrice());
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        //   tradeData=new TradeData();


                    }else if (sdf.format(tickDateTime).equals(closeTime) && tradeData.isOrderPlaced && !tradeData.isExited) {
                        tradeData.setBuyPrice(closePrice);
                        tradeData.setBuyTime(strTickDateTime);
                        tradeData.isExited = true;
                        String[] data = {tradeData.getStockName(), tradeData.getBuyTime(), tradeData.getBuyPrice().toString(), tradeData.getSellTime(), tradeData.getSellPrice().toString(), tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(tradeData.getQty())).toString(),evaluateDate,tradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                    }
                    else if (sdf.format(tickDateTime).equals(closeTime) && longTradeData.isOrderPlaced && !longTradeData.isExited) {
                        longTradeData.setSellPrice(closePrice);
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }
    public void shortStraddleTradeBackTest(HistoricResponseDTO historicResponseDTO) throws CsvValidationException, ParseException, IOException {
        Map<String, EventDayData> eventDayMap = eventDayConfiguration.eventDayConfig();
        Date currentDate = new Date();
        String strCurrentDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if (eventDayMap.containsKey(strCurrentDate) && eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")) {
            log.info("no trade day. not starting straddle");
        } else {
            log.info("Short straddle process started");
            List<List<Object>> list = historicResponseDTO.getData();
            log.info("straddle:" + historicResponseDTO.getSymbol());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
            list.stream().forEach(tickData -> {
                try {
                    String strTickDateTime = tickData.get(0).toString();
                    final Date endDate = sdf1.parse(strTickDateTime);
                    final String evaluateDate = sdf1.format(endDate);
                    String checkTime = evaluateDate + "T09:15:00";
                    String closeTime = evaluateDate + "T15:05:00";
                    Date tickDateTime = null;
                    try {
                        tickDateTime = sdf.parse(strTickDateTime);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (sdf.format(tickDateTime).equals(checkTime)) {
                        BigDecimal closePrice = new BigDecimal(tickData.get(4).toString());
                        BigDecimal scaled = closePrice.setScale(0, RoundingMode.HALF_UP);
                        int finalValue = findATM(scaled.intValue());
                        System.out.println("rounded value:" + finalValue);
                        String[] line = null;
                        expDate = expDate + " " + finalValue;
                        String exptrueDataExpDate = trueDataExpDate + finalValue + "CE";
                        System.out.println(trueDataExpDate);

                        String payload = historicInput("5min", exptrueDataExpDate, evaluateDate);
                        Session session = historicWebsocket.session;
                        log.info(payload);
                        try {
                            if (session != null && session.isOpen()) {
                                session.getBasicRemote().sendText(payload);
                            } else {
                                historicWebsocket.createHistoricWebSocket();
                                session = historicWebsocket.session;
                                session.getBasicRemote().sendText(payload);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        exptrueDataExpDate = trueDataExpDate + finalValue + "PE";
                        payload = historicInput("5min", exptrueDataExpDate, evaluateDate);
                        log.info(payload);
                        try {
                            if (session != null && session.isOpen()) {
                                session.getBasicRemote().sendText(payload);
                            } else {
                                historicWebsocket.createHistoricWebSocket();
                                session = historicWebsocket.session;
                                session.getBasicRemote().sendText(payload);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }

    public int findATM(int currentValue) {
        int a = (Integer.valueOf(currentValue) / 50) * 50;
        int b = a + 50;
        return (Integer.valueOf(currentValue) - a > b - Integer.valueOf(currentValue)) ? b : a;
    }

  //  @Scheduled(cron = "${nifty.get.week.expiry.details}")
    public void getWeeklyExpiryOptionsDetails() throws IOException, CsvValidationException {
        StopWatch watch = new StopWatch();
        watch.start();
        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/NSE_FO.csv"));
        String[] line;
        Calendar calendar = Calendar.getInstance();
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday
        int dayadd = 5 - calendar.get(DAY_OF_WEEK);
        if (dayadd > 0) {
            calendar.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd < 0) {
            calendar.add(DAY_OF_MONTH, 6);
        }
        Date date = calendar.getTime();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/nifty_week_fo.csv", false));

        SimpleDateFormat format = new SimpleDateFormat("yy MMM dd");
        SimpleDateFormat format1 = new SimpleDateFormat("yyMMdd");
        expDate = "NIFTY " + format.format(date);
        trueDataExpDate = "NIFTY " + format1.format(date);
        System.out.println(expDate);
        while ((line = csvReader.readNext()) != null) {
            if (line[1].contains(expDate) && !line[1].contains("BANK"+expDate)) {
                System.out.println(line[1]);
                csvWriter.writeNext(line);
                csvWriter.flush();
            }

        }
        csvWriter.close();
        watch.stop();
        System.out.println("Total execution time using StopWatch in millis: "
                + watch.getTotalTimeMillis());
    }

    public void getWeeklyExpiryOptionsDetailsBackTest(String date) throws IOException, CsvValidationException, ParseException {
        System.out.println("backtest getWeeklyExpiryOptionsDetailsBackTest");
        StopWatch watch = new StopWatch();
        watch.start();
        String[] line;
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        calendar.setTime(format.parse(date));
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday
        int dayadd = 5 - calendar.get(DAY_OF_WEEK);
        if (dayadd > 0) {
            calendar.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd < 0) {
            calendar.add(DAY_OF_MONTH, 6);
        }
        Date date1 = calendar.getTime();

        SimpleDateFormat format2 = new SimpleDateFormat("yyMMdd");
        trueDataExpDate = "NIFTY" + format2.format(date1);
        System.out.println(trueDataExpDate);
        watch.stop();
        System.out.println("Total execution time using StopWatch in millis: "
                + watch.getTotalTimeMillis());
    }

  //  @Scheduled(cron = "${stradle.sl.scheduler}")
    public void sLMonitorScheduler() {
        log.info("short straddle SLMonitor scheduler started");
        if (tradeDataHashMap != null) {
            tradeDataHashMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && !map.getValue().isSlPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {
                        TradeData trendTradeData = map.getValue();
                        Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getEntryOrderId());
                        String response = transactionService.callAPI(request);
                        System.out.println(response);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getS().equals("ok")) {
                            Order order;
                            OrderDetailsDTO orderDetailsDTO = orderStatusResponseDTO.getOrderDetails();
                            BigDecimal price = (orderDetailsDTO.getTradedPrice().divide(new BigDecimal(2))).setScale(0, RoundingMode.HALF_UP);
                            trendTradeData.setSellPrice(orderDetailsDTO.getTradedPrice());
                            order = Order.BUY;
                            PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(trendTradeData.getStockName(), order, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, orderStatusResponseDTO.getOrderDetails().getQty()*2, new BigDecimal(0), price.add(orderDetailsDTO.getTradedPrice()), new BigDecimal(0));
                            String payload = new Gson().toJson(placeOrderRequestDTO);
                            Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                            String slResponse = transactionService.callAPI(slRequest);
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                            if (orderResponseDTO.getS().equals("ok")) {
                                trendTradeData.setSlOrderId(orderResponseDTO.getId());
                                trendTradeData.isSlPlaced = true;
                            }
                            System.out.println(slResponse);
                        }
                    });
            if (tradeDataHashMap != null) {
                tradeDataHashMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().isSlPlaced && !map.getValue().isSLCancelled && !map.getValue().isExited && map.getValue().getEntryOrderId() != null && map.getValue().getSlOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getSlOrderId());
                            String response = transactionService.callAPI(request);
                        //    System.out.println(response);
                            OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                            if (orderStatusResponseDTO.getOrderDetails().getStatus() != 6) {
                                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                                //    map.getValue().isExited = true;
                                    String message = MessageFormat.format("Stop loss Hit stock {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                    if(!trendTradeData.isReverseSLPlaced) {
                                        String message1 = MessageFormat.format("Took Long position stock {0}", trendTradeData.getStockName());
                                        PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(trendTradeData.getStockName(), Order.SELL, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, orderStatusResponseDTO.getOrderDetails().getQty(), new BigDecimal(0), trendTradeData.getSellPrice(), new BigDecimal(0));
                                        String payload = new Gson().toJson(placeOrderRequestDTO);
                                        Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                                        String slResponse = transactionService.callAPI(slRequest);
                                    OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                                    if (orderResponseDTO.getS().equals("ok")) {
                                        trendTradeData.setSlOrderId(orderResponseDTO.getId());
                                        trendTradeData.isSlPlaced = true;
                                        trendTradeData.isReverseSLPlaced=true;
                                    }
                                        sendMessage.sendToTelegram(message1, telegramToken);
                                    }
                                }
                                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 1) {
                                    map.getValue().isSLCancelled = true;
                                    String message = MessageFormat.format("Broker Cancelled SL Order of {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
                                }

                            }
                        });
            }
        }
    }

   // @Scheduled(cron = "${straddle.exit.position.scheduler}")
    public void exitPositions() {
        log.info("Nifty Exit positions scheduler started");
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);
        tradeDataHashMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited).forEach(trendMap -> {
            if (trendMap.getValue().getSlOrderId() != null && trendMap.getValue().getSlOrderId() != "") {
                Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, trendMap.getValue().getSlOrderId());
                String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                if (orderStatusResponseDTO.getOrderDetails().getStatus() != 2 && orderStatusResponseDTO.getOrderDetails().getStatus() == 6) {
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
        OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
        System.out.println(new Gson().toJson(openPositionsResponseDTO));
        openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
            try {
                Integer openQty = netPositionDTO.getBuyQty() - netPositionDTO.getSellQty();
                */
/*      if(netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType()=="INTRADAY" ) {

                    if(openQty>0) {*//*

                        String id = netPositionDTO.getId();
                        ExitPositionRequestDTO exitPositionRequestDTO = new ExitPositionRequestDTO(id);
                        Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, exitPositionsURL, new Gson().toJson(exitPositionRequestDTO));
                        String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                        String message = MessageFormat.format("Exited Positions response: {0}", exitPositionsResponse);
                        try {
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(exitPositionsResponse, OrderResponseDTO.class);
                            if (orderResponseDTO.getS().equals("ok")) {
                                log.info(message);
                                String teleMessage = netPositionDTO.getSymbol() + ":Closed Postion:" + openQty;
                                sendMessage.sendToTelegram(teleMessage, telegramToken);
                            } else {
                                String teleMessage = netPositionDTO.getSymbol() + ":exit position call failed with status :" + orderResponseDTO.getS() + " Error :" + orderResponseDTO.getMessage();
                                sendMessage.sendToTelegram(teleMessage, telegramToken);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
               */
/*     }
                }*//*

            } catch (Exception e) {
                e.printStackTrace();
                String teleMessage = netPositionDTO.getSymbol() + ":Error while trying to close Postion";
                sendMessage.sendToTelegram(teleMessage, telegramToken);
            }
        });
    }

    public String historicInput(String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.now());
        String fromDate = java.time.LocalDate.now() + "T09:15:00";
        String toDate = java.time.LocalDate.now() + "T16:00:00";
        if (LocalDateTime.parse(currentDate).isBefore(LocalDateTime.parse(java.time.LocalDate.now() + "T09:15:00"))) {
            fromDate = java.time.LocalDate.now().minus(Period.ofDays(1)) + "T09:15:00";
        }
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(toDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }

    public String historicInput(String interval, String stock, String date) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.now());
        String fromDate = date + "T09:15:00";
        String toDate = date + "T16:00:00";
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(toDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }

    @PreDestroy
    public void saveDatatoFile() throws IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/nifty_straddle_trade_data_"+dtf1.format(localDate)+".csv",false));
        tradeDataHashMap.entrySet().stream().forEach(tradeData->
        {

            try {
                String[] data={tradeData.getKey(),new Gson().toJson(tradeData)};
                csvWriter.writeNext(data);
                csvWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter.close();

    }
}
*/
