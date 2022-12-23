package com.sakthi.trade.options.nifty;

import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.EventDayData;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.truedata.HistoricResponseDTO;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class NiftyShortStraddleOI {

    @Autowired
    EventDayConfiguration eventDayConfiguration;


    @Value("${filepath.trend}")
    String trendPath;
    @Value("${home.path}")
    String homeFilePath;
    @Autowired
    CommonUtil commonUtil;
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

    @Value("${straddle.banknifty.lot}")
    String bankniftyLot;
    @Autowired
    FyerTransactionMapper fyerTransactionMapper;

    @Autowired
    TransactionService transactionService;


    public Map<String, TradeData> tradeDataHashMap = new HashMap<>();

    @Autowired
    TelegramMessenger sendMessage;
    private java.util.concurrent.Executors Executors;

    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    public void triggerNIFTY(LocalDate tradeDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String historicURL = "https://history.truedata.in/getbars?symbol=NIFTY 50&from="+formatter.format(tradeDate)+"T09:00:00&to="+formatter.format(tradeDate)+"T15:30:00&response=csv&interval=1min";
        String response = transactionService.callAPI(transactionService.createGetTrueRequest(historicURL));
        BufferedReader bufReader = new BufferedReader(new StringReader(response));
        try {
            String line=null;
            List<HistoricalDataExtended> historicalDataExtendedList=new ArrayList<>();
            int i=0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            long atm=0L;
            while( (line=bufReader.readLine()) != null )
            {
                if(i>0) {
                    String[] data = line.split(",");
                    HistoricalDataExtended historicalDataExtended = new HistoricalDataExtended();
                    historicalDataExtended.setOpen(Double.valueOf(data[1]));
                    historicalDataExtended.setHigh(Double.valueOf(data[2]));
                    historicalDataExtended.setLow(Double.valueOf(data[3]));
                    historicalDataExtended.setClose(Double.valueOf(data[4]));
                    historicalDataExtended.setVolume(Integer.valueOf(data[5]));
                    historicalDataExtended.setOi(Long.valueOf(data[6]));
                    historicalDataExtended.setTimeStamp(data[0]);
                    historicalDataExtendedList.add(historicalDataExtended);
                    if(data[0].equals(formatter1.format(tradeDate)+"T09:19:00")){
                        atm=findATM((int)Double.parseDouble(data[4]));
                    }
                }
                i++;
            }
            List<HistoricalDataExtended> ceList=new ArrayList<>();
            List<HistoricalDataExtended> peList=new ArrayList<>();
            getWeeklyExpiryOptionsDetailsBackTest(formatter1.format(tradeDate));
            String exptrueDataExpDateCE = trueDataExpDate + atm + "CE";
            String exptrueDataExpDatePE = trueDataExpDate + atm + "PE";
            List<String> atmList=new ArrayList<>();
            atmList.add(exptrueDataExpDateCE);
            atmList.add(exptrueDataExpDatePE);
            AtomicLong ceOi=new AtomicLong();
            AtomicLong peOi=new AtomicLong();
            atmList.stream().forEach(atmStrike-> {
                        String historicURLCE = "https://history.truedata.in/getbars?symbol=" + atmStrike + "&from="+formatter.format(tradeDate)+"T09:00:00&to="+formatter.format(tradeDate)+"T15:30:00&response=csv&interval=1min";
                        String responseCE = transactionService.callAPI(transactionService.createGetTrueRequest(historicURLCE));
                try {

                    if(atmStrike.contains("CE")){
                        ceOi.getAndAdd(convertStringToHistoric(ceList,new BufferedReader(new StringReader(responseCE)),formatter1.format(tradeDate)));
                    }else{
                        peOi.getAndAdd(convertStringToHistoric(peList,new BufferedReader(new StringReader(responseCE)),formatter1.format(tradeDate)));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            if(ceOi.get()>0 && peOi.get()>0) {
                if (ceOi.get() > peOi.get()) {
                    log.info("CE oi is more than PE,CE OI:" + ceOi.get() + "PE Oi:" + peOi.get());
                    trade(exptrueDataExpDateCE, ceList, 25);

                } else if (ceOi.get() < peOi.get()) {
                    log.info("PE oi is more than PE,CE OI:" + ceOi.get() + "PE Oi:" + peOi.get());
                    trade(exptrueDataExpDatePE, peList, 25);
                }
            }
           // System.out.println(new Gson().toJson(historicalDataExtendedList));
        } catch (Exception e) {
            e.printStackTrace();
        }



    }
    public void trade(String atmStrike, List<HistoricalDataExtended> list,double slPerc) throws IOException {

        TradeData shortTradeData = new TradeData();
        log.info("Short straddle process started");
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Nifty_Backtest/options_straddle_short_oi.csv", true));
        Map<String, Map<String,BigDecimal>> dataMap=new HashMap<>();
        log.info("straddle:" + atmStrike);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
        list.stream().forEach(tickData -> {
            try {
                String strTickDateTime = tickData.timeStamp;
                final Date endDate = sdf1.parse(strTickDateTime);
                final String evaluateDate = sdf1.format(endDate);
                String checkTime = evaluateDate + "T09:29:00";
                String closeTime = evaluateDate + "T15:09:00";
                Date tickDateTime = null;
                try {
                    tickDateTime = sdf.parse(strTickDateTime);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                BigDecimal closePrice = new BigDecimal(tickData.close);
             if (tickDateTime.after(sdf.parse(checkTime)) && !shortTradeData.isOrderPlaced ) {
                 shortTradeData.setEntryType("SHORT");
                 shortTradeData.setStockName(atmStrike);
                 shortTradeData.setSellTime(strTickDateTime);
                 shortTradeData.setQty(75);
                 shortTradeData.setSellPrice(new BigDecimal(tickData.open));
                 shortTradeData.isOrderPlaced=true;
                 BigDecimal price = ((new BigDecimal(tickData.open).divide(new BigDecimal(2))).add(new BigDecimal(tickData.open))).setScale(0, RoundingMode.HALF_UP);
                 shortTradeData.setSlPrice(price);

                } else if (tickDateTime.after(sdf.parse(checkTime)) && shortTradeData.isOrderPlaced && !shortTradeData.isExited && new BigDecimal(tickData.high).compareTo(shortTradeData.getSlPrice()) > 0) {
                 shortTradeData.setBuyPrice(shortTradeData.getSlPrice());
                 shortTradeData.isExited = true;
                 shortTradeData.setBuyTime(strTickDateTime);
                    String[] data = {shortTradeData.getStockName(), shortTradeData.getBuyTime(), shortTradeData.getBuyPrice().toString(), shortTradeData.getSellTime(), shortTradeData.getSellPrice().toString(), shortTradeData.getSellPrice().subtract(shortTradeData.getBuyPrice()).multiply(new BigDecimal(shortTradeData.getQty())).toString(), evaluateDate,shortTradeData.getEntryType()};
                    csvWriter.writeNext(data);
                    csvWriter.flush();

                }
                else if (sdf.format(tickDateTime).equals(closeTime) && shortTradeData.isOrderPlaced && !shortTradeData.isExited) {
                 shortTradeData.setBuyPrice(closePrice);
                 shortTradeData.setBuyTime(strTickDateTime);
                 shortTradeData.isExited = true;
                 String[] data = {shortTradeData.getStockName(), shortTradeData.getBuyTime(), shortTradeData.getBuyPrice().toString(), shortTradeData.getSellTime(), shortTradeData.getSellPrice().toString(), shortTradeData.getSellPrice().subtract(shortTradeData.getBuyPrice()).multiply(new BigDecimal(shortTradeData.getQty())).toString(), evaluateDate,shortTradeData.getEntryType()};
                 csvWriter.writeNext(data);
                 csvWriter.flush();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }



    public long convertStringToHistoric(List<HistoricalDataExtended> list,BufferedReader bufferedReader,String tradeDate) throws IOException {
        String line;
        int i=0;
        long intialoi=0L;
        long closeOi=0L;
        while( (line=bufferedReader.readLine()) != null )
        {
            if(i>0) {
                String[] data = line.split(",");
                HistoricalDataExtended historicalDataExtended = new HistoricalDataExtended();
                historicalDataExtended.setOpen(Double.valueOf(data[1]));
                historicalDataExtended.setHigh(Double.valueOf(data[2]));
                historicalDataExtended.setLow(Double.valueOf(data[3]));
                historicalDataExtended.setClose(Double.valueOf(data[4]));
                historicalDataExtended.setVolume(Integer.valueOf(data[5]));
                historicalDataExtended.setOi(Long.valueOf(data[6]));
                historicalDataExtended.setTimeStamp(data[0]);
                if(data[0].equals(tradeDate+"T09:15:00")){
                    intialoi=Long.valueOf(data[6]);
                }
                if(data[0].equals(tradeDate+"T09:29:00")){
                    closeOi=Long.valueOf(data[6]);
                }
                list.add(historicalDataExtended);
            }
            i++;
        }
        return closeOi-intialoi;
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
    public int findATM(int currentValue) {
        int a = (Integer.valueOf(currentValue) / 100) * 100;
        int b = a + 100;
        return (Integer.valueOf(currentValue) - a > b - Integer.valueOf(currentValue)) ? b : a;
    }

    public void shortStraddleTradeProcessLongBackTest(HistoricResponseDTO historicResponseDTO) throws CsvValidationException, ParseException, IOException {
        Map<String, EventDayData> eventDayMap = eventDayConfiguration.eventDayConfig();
        Date currentDate = new Date();
        String strCurrentDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/options_straddle.csv", true));
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
                    BigDecimal highPrice = new BigDecimal(tickData.get(2).toString());
                    BigDecimal lowPrice = new BigDecimal(tickData.get(3).toString());
                    if (sdf.format(tickDateTime).equals(checkTime) && !tradeData.isOrderPlaced) {
                        BigDecimal sl = closePrice.add(closePrice.divide(new BigDecimal("2")));
                        // BigDecimal sl = closePrice.multiply(new BigDecimal("2"));
                        tradeData.setStockName(historicResponseDTO.getSymbol());
                        tradeData.isOrderPlaced = true;
                        tradeData.setSellTime(strTickDateTime);
                        tradeData.setSlPrice(sl);
                        tradeData.setQty(25);
                        tradeData.setSellPrice(closePrice);
                        tradeData.setEntryType("SHORT");

                    } else if (tickDateTime.after(sdf.parse(checkTime)) && tradeData.isOrderPlaced && !tradeData.isExited && highPrice.compareTo(tradeData.getSlPrice()) > 0) {
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


                    } else if (tickDateTime.after(sdf.parse(checkTime)) && longTradeData.isOrderPlaced && !longTradeData.isExited && lowPrice.compareTo(longTradeData.getSlPrice()) < 0) {
                        longTradeData.setSellPrice(longTradeData.getSlPrice());
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        //   tradeData=new TradeData();


                    }
else if (tickDateTime.after(sdf.parse(checkTime)) && longTradeData.isOrderPlaced && !longTradeData.isExited && closePrice.compareTo(longTradeData.getBuyPrice().add(longTradeData.getBuyPrice().divide(new BigDecimal(2)))) > 0) {
                        longTradeData.setSellPrice(closePrice);
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        //   tradeData=new TradeData();


                    }

                    else if (sdf.format(tickDateTime).equals(closeTime) && tradeData.isOrderPlaced && !tradeData.isExited) {
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
}
/* @Scheduled(cron = "${banknifty.historic.straddle.quote.test}")
    public void shortStraddleTradeScheduleTest() {

        log.info("bank nifty test scheduler started");
        String payload = historicInput("5min", "NIFTY BANK");
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


    }*//*

    public void shortStraddleTradeScheduleBacktest(String date) {
        log.info("Short straddle scheduler started");
        String payload = historicInput("5min", "NIFTY BANK", date);
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
                        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/week_fo.csv"));
                        String[] lineS = null;
                        expDate = expDate + " " + finalValue;
                        String exptrueDataExpDateCE = trueDataExpDate + finalValue + "CE";
                        String exptrueDataExpDatePE = trueDataExpDate + finalValue + "PE";
                        sendMessage.sendToTelegram("Bank Nifty ATM Option selected for Straddle: " + expDate, telegramToken);
                        ExecutorService executorService= java.util.concurrent.Executors.newFixedThreadPool(5);
                        while ((lineS = csvReader.readNext()) != null) {
                            int retryCount=0;
                            final String[] line=lineS;
                            executorService.submit(()-> {
                                if (line[1].contains(expDate)) {
                                    log.info(line[9]);
                                    //sell options
                                    int qty = Integer.valueOf(line[3]) * Integer.valueOf(bankniftyLot);
                                    log.info("started sell: ");
                                    PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(line[9], Order.SELL, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, qty, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                                    Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                                    String response = transactionService.callAPI(request);
                                    log.info("sell response: " + response);
                                    OrderResponseDTO orderResponseDTO = new Gson().fromJson(response, OrderResponseDTO.class);
                                    TradeData trendTradeData = new TradeData();
                                    trendTradeData.setStockName(line[9]);
                                    Date date = new Date();
                                    trendTradeData.setCreateTimestamp(date);
                                    if (orderResponseDTO.getS().equals("ok")) {
                                        trendTradeData.setEntryOrderId(orderResponseDTO.getId());
                                        trendTradeData.isOrderPlaced = true;
                                        trendTradeData.setQty(qty);
                                        trendTradeData.setEntryType("SELL");
                                        try {
                                            if (line[9].contains("CE")) {
                                                trendTradeData.setTrueDataSymbol(exptrueDataExpDateCE);
                                            } else if (line[9].contains("PE")) {
                                                trendTradeData.setTrueDataSymbol(exptrueDataExpDatePE);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        sendMessage.sendToTelegram("Straddle option sold for strike: " + line[9], telegramToken);
                                    } else {
                                        trendTradeData.isErrored = true;
                                        sendMessage.sendToTelegram("Error while placing straddle order: " + line[9] + ": Status: " + orderResponseDTO.getS() + ": error message:" + orderResponseDTO.getMessage(), telegramToken);
                                        for (int i = 0; i < 3; i++) {
                                            if (trendTradeData.isErrored) {

                                            }
                                        }
                                    }
                                    tradeDataHashMap.put(line[9], trendTradeData);
                                }
                            });
                        }
                        try{
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
                        }
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
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/options_straddle.csv", true));
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
                        tradeData.setQty(25);
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
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/options_straddle.csv", true));
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
                    BigDecimal highPrice = new BigDecimal(tickData.get(2).toString());
                    BigDecimal lowPrice = new BigDecimal(tickData.get(3).toString());
                    if (sdf.format(tickDateTime).equals(checkTime) && !tradeData.isOrderPlaced) {
                        BigDecimal sl = closePrice.add(closePrice.divide(new BigDecimal("2")));
                       // BigDecimal sl = closePrice.multiply(new BigDecimal("2"));
                        tradeData.setStockName(historicResponseDTO.getSymbol());
                        tradeData.isOrderPlaced = true;
                        tradeData.setSellTime(strTickDateTime);
                        tradeData.setSlPrice(sl);
                        tradeData.setQty(25);
                        tradeData.setSellPrice(closePrice);
                        tradeData.setEntryType("SHORT");

                    } else if (tickDateTime.after(sdf.parse(checkTime)) && tradeData.isOrderPlaced && !tradeData.isExited && highPrice.compareTo(tradeData.getSlPrice()) > 0) {
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


                    } else if (tickDateTime.after(sdf.parse(checkTime)) && longTradeData.isOrderPlaced && !longTradeData.isExited && lowPrice.compareTo(longTradeData.getSlPrice()) < 0) {
                        longTradeData.setSellPrice(longTradeData.getSlPrice());
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        //   tradeData=new TradeData();


                    }*/
/*else if (tickDateTime.after(sdf.parse(checkTime)) && longTradeData.isOrderPlaced && !longTradeData.isExited && closePrice.compareTo(longTradeData.getBuyPrice().add(longTradeData.getBuyPrice().divide(new BigDecimal(2)))) > 0) {
                        longTradeData.setSellPrice(closePrice);
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        //   tradeData=new TradeData();


                    }*//*

                    else if (sdf.format(tickDateTime).equals(closeTime) && tradeData.isOrderPlaced && !tradeData.isExited) {
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
    public void longShortBackTest(HistoricResponseDTO historicResponseDTO) throws Exception {
        Map<String, EventDayData> eventDayMap = eventDayConfiguration.eventDayConfig();
        Date currentDate = new Date();
        String strCurrentDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/options_straddle_long_short.csv", true));
        if (eventDayMap.containsKey(strCurrentDate) && eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")) {
            log.info("no trade day. not starting straddle");
        } else {
            TradeData tradeData = new TradeData();
            TradeData longTradeData = new TradeData();
            log.info("Short straddle process started");
            List<List<Object>> list = historicResponseDTO.getData();
            Map<String, Map<String,BigDecimal>> dataMap=new HashMap<>();
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
                    if (sdf.format(tickDateTime).equals(checkTime)) {
                        Map<String,BigDecimal> stringDoubleMap=new HashMap<>();
                        stringDoubleMap.put("closePrice",new BigDecimal(tickData.get(4).toString()));
                        stringDoubleMap.put("buyPrice",closePrice.add(closePrice.divide(new BigDecimal("2"))));
                        dataMap.put(historicResponseDTO.getSymbol(),stringDoubleMap);

                    } else if (tickDateTime.after(sdf.parse(checkTime)) && !longTradeData.isOrderPlaced && closePrice.compareTo(dataMap.get(historicResponseDTO.getSymbol()).get("buyPrice")) > 0) {
                        longTradeData.setEntryType("LONG");
                        longTradeData.setStockName(historicResponseDTO.getSymbol());
                        longTradeData.setBuyTime(strTickDateTime);
                        longTradeData.setQty(25);
                        longTradeData.setBuyPrice(dataMap.get(historicResponseDTO.getSymbol()).get("buyPrice"));
                        longTradeData.isOrderPlaced=true;
                        longTradeData.setSlPrice(dataMap.get(historicResponseDTO.getSymbol()).get("closePrice"));


                    } else if (tickDateTime.after(sdf.parse(checkTime)) && longTradeData.isOrderPlaced && !longTradeData.isExited && closePrice.compareTo(longTradeData.getSlPrice()) < 0) {
                        longTradeData.setSellPrice(longTradeData.getSlPrice());
                        longTradeData.isExited = true;
                        longTradeData.setSellTime(strTickDateTime);
                        String[] data = {longTradeData.getStockName(), longTradeData.getBuyTime(), longTradeData.getBuyPrice().toString(), longTradeData.getSellTime(), longTradeData.getSellPrice().toString(), longTradeData.getSellPrice().subtract(longTradeData.getBuyPrice()).multiply(new BigDecimal(longTradeData.getQty())).toString(), evaluateDate,longTradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                        tradeData.setStockName(historicResponseDTO.getSymbol());
                        tradeData.isOrderPlaced = true;
                        tradeData.setSellTime(strTickDateTime);
                        tradeData.setSlPrice(longTradeData.getBuyPrice());
                        tradeData.setQty(25);
                        tradeData.setSellPrice(longTradeData.getSlPrice());
                        tradeData.setEntryType("SHORT");


                    }else if (tickDateTime.after(sdf.parse(checkTime)) && tradeData.isOrderPlaced && !tradeData.isExited && closePrice.compareTo(tradeData.getSlPrice()) > 0) {
                        tradeData.setBuyPrice(closePrice);
                        tradeData.isExited = true;
                        tradeData.setBuyTime(strTickDateTime);
                        String[] data = {tradeData.getStockName(), tradeData.getBuyTime(), tradeData.getBuyPrice().toString(), tradeData.getSellTime(), tradeData.getSellPrice().toString(), tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(tradeData.getQty())).toString(), evaluateDate,tradeData.getEntryType()};
                        csvWriter.writeNext(data);
                        csvWriter.flush();
                    }
                    else if (sdf.format(tickDateTime).equals(closeTime) && tradeData.isOrderPlaced && !tradeData.isExited) {
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
        int a = (Integer.valueOf(currentValue) / 100) * 100;
        int b = a + 100;
        return (Integer.valueOf(currentValue) - a > b - Integer.valueOf(currentValue)) ? b : a;
    }

    @Scheduled(cron = "${banknifty.get.week.expiry.details}")
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
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/week_fo.csv", false));

        SimpleDateFormat format = new SimpleDateFormat("yy MMM dd");
        SimpleDateFormat format1 = new SimpleDateFormat("yyMMdd");
        expDate = "BANKNIFTY " + format.format(date);
        trueDataExpDate = "BANKNIFTY " + format1.format(date);
        System.out.println(expDate);
        while ((line = csvReader.readNext()) != null) {
            if (line[1].contains(expDate)) {
               // System.out.println(line[1]);
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
        trueDataExpDate = "BANKNIFTY" + format2.format(date1);
        System.out.println(trueDataExpDate);
        watch.stop();
        System.out.println("Total execution time using StopWatch in millis: "
                + watch.getTotalTimeMillis());
    }

  //  @Scheduled(cron = "${stradle.sl.scheduler}")
    public void sLMonitorScheduler() {
       // log.info("short straddle SLMonitor scheduler started");
        if (tradeDataHashMap != null) {
            tradeDataHashMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && !map.getValue().isSlPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {
                        TradeData trendTradeData = map.getValue();
                        Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getEntryOrderId());
                        String response = transactionService.callAPI(request);
                       // System.out.println(response);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                            Order order;
                            OrderDetailsDTO orderDetailsDTO = orderStatusResponseDTO.getOrderDetails();
                            BigDecimal price = (orderDetailsDTO.getTradedPrice().divide(new BigDecimal(2))).setScale(0, RoundingMode.HALF_UP);
                            trendTradeData.setSellPrice(orderDetailsDTO.getTradedPrice());
                            trendTradeData.setSlPrice(price);
                            trendTradeData.setFyersSymbol(orderDetailsDTO.getSymbol());
                            order = Order.BUY;
                            PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(trendTradeData.getStockName(), order, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, orderStatusResponseDTO.getOrderDetails().getQty()*2, new BigDecimal(0), price.add(orderDetailsDTO.getTradedPrice()), new BigDecimal(0));
                            String payload = new Gson().toJson(placeOrderRequestDTO);
                            Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                            String slResponse = transactionService.callAPI(slRequest);
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                            trendTradeData.setSellTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                            trendTradeData.setSellTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                            if (orderResponseDTO.getS().equals("ok")) {

                                trendTradeData.setSlOrderId(orderResponseDTO.getId());
                                trendTradeData.isSlPlaced = true;

                            }
                            log.info(slResponse);
                        }
                    });
            if (tradeDataHashMap != null) {
                tradeDataHashMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().isSlPlaced && !map.getValue().isSLCancelled && !map.getValue().isExited && map.getValue().getEntryOrderId() != null && map.getValue().getSlOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getSlOrderId());
                            String response = transactionService.callAPI(request);
                       //     System.out.println(response);
                            OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                            if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() != 6) {
                                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2 && !trendTradeData.isReverseSLPlaced) {
                                  //  map.getValue().isExited = true;
                                    String message = MessageFormat.format("Stop loss Hit stock {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    trendTradeData.setSlTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                    trendTradeData.isReverseTradePlaced=true;
                                    trendTradeData.setSlTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                                    sendMessage.sendToTelegram(message, telegramToken);
                                        String message1 = MessageFormat.format("Took Long position stock {0}", trendTradeData.getStockName());
                                        PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestFODTO(trendTradeData.getStockName(), Order.SELL, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, trendTradeData.getQty(), new BigDecimal(0), trendTradeData.getSellPrice(), new BigDecimal(0));
                                        String payload = new Gson().toJson(placeOrderRequestDTO);
                                        Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                                        String slResponse = transactionService.callAPI(slRequest);
                                        trendTradeData.setReverseSlPrice(trendTradeData.getSellPrice());
                                        OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                                        if (orderResponseDTO.getS().equals("ok")) {
                                            trendTradeData.setSlOrderId(orderResponseDTO.getId());
                                            trendTradeData.isSlPlaced = true;
                                            trendTradeData.isReverseSLPlaced=true;
                                        }else {
                                            String teleMessage = trendTradeData.getStockName() + ":long position order placement failed with status :" + orderResponseDTO.getS() + " Error :" + orderResponseDTO.getMessage();
                                            sendMessage.sendToTelegram(teleMessage, telegramToken);
                                        }
                                        sendMessage.sendToTelegram(message1, telegramToken);

                                } else   if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2 && trendTradeData.isReverseSLPlaced && !trendTradeData.isReverseSLHit) {
                                    trendTradeData.setReverseSlTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                    trendTradeData.setReverseSlTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                                    String message = MessageFormat.format("Reverse Order Stop loss Hit stock {0}", trendTradeData.getStockName());
                                    sendMessage.sendToTelegram(message, telegramToken);
                                    trendTradeData.isReverseSLHit=true;
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

  //  @Scheduled(cron = "${straddle.exit.position.scheduler}")
    public void exitPositions() {
        log.info("Straddle Exit positions scheduler started");
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
                    tradeDataHashMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited).forEach(trendMap -> {
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
        ExecutorService executorSer = Executors.newFixedThreadPool(5);
        openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
            executorSer.execute(()-> {
                                        try {
                                            if (netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType() == "INTRADAY") {
                                                Integer netQty = netPositionDTO.getNetQty();
                                                Integer openQty;
                                                Optional<Map.Entry<String, TradeData>> trendMap = tradeDataHashMap.entrySet().stream().filter(tradeData -> tradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
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
                                                        Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                                                        String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                            */
/*  String id = netPositionDTO.getId();
                              ExitPositionRequestDTO exitPositionRequestDTO = new ExitPositionRequestDTO(id);
                              Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, exitPositionsURL, new Gson().toJson(exitPositionRequestDTO));
                              String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                          *//*

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
        });

    }
   // @Scheduled(cron = "${position.monitor}")
    public void positionsMonitor() {
        Request ordersRequest = transactionService.createGetRequest(orderPlaceAPIUrl, null);
        String ordersResponse = transactionService.callAPI(ordersRequest);
        com.sakthi.trade.domain.OrderResponseDTO ordersResponseDTO = new Gson().fromJson(ordersResponse, com.sakthi.trade.domain.OrderResponseDTO.class);
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);
        OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
        log.info("Open Positions: " + new Gson().toJson(openPositionsResponseDTO));
        openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
            try {
                if (netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType() == "INTRADAY") {
                    Integer netQty = netPositionDTO.getNetQty();
                    Integer openQty;
                    Optional<Map.Entry<String, TradeData>> trendMap = tradeDataHashMap.entrySet().stream().filter(tradeData -> tradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
                    if (netQty == 0) {

                    }else{

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
   // @Scheduled(cron = "${common.exit.position.scheduler}")
    public void exitCommonPositions() {
        log.info("Trend Exit positions scheduler started");
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);
        OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
        log.info(new Gson().toJson(openPositionsResponseDTO));
        openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
            try {
                  if(netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType()=="INTRADAY" ) {
                Integer openQty = netPositionDTO.getBuyQty() - netPositionDTO.getSellQty();
                     if(openQty>0 && openQty<0) {
                String id = netPositionDTO.getId();
                ExitPositionRequestDTO exitPositionRequestDTO = new ExitPositionRequestDTO(id);
                Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, exitPositionsURL, new Gson().toJson(exitPositionRequestDTO));
                String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                String message = MessageFormat.format("Exited Positions response: {0}", exitPositionsResponse);
                         log.info(message);
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
                  }
                  }
            } catch (Exception e) {
                e.printStackTrace();
                String teleMessage = netPositionDTO.getSymbol() + ":Error while trying to close Postion";
                sendMessage.sendToTelegram(teleMessage, telegramToken);
            }
        });
    }
    public String historicInput(String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(java.time.LocalDateTime.now());
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
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(java.time.LocalDateTime.now());
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
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/banknifty_straddle_trade_data_"+dtf1.format(localDate)+".csv",false));
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
    @Scheduled(cron = "${orb.exit.position.details.report}")
    public void straddleDetailsReport() throws IOException {
        commonUtil.bankNiftyTradeReport(tradeDataHashMap,"/trade_report/bank_nifty_trade_report.csv");
    }
    @Scheduled(cron = "${orb.position.close.price}")
    public void positionClosePrice() {
        log.info("positions close price scheduler started");
        tradeDataHashMap.entrySet().stream().forEach(orbTradeData -> {
            log.info("positions close price tradedata:"+new Gson().toJson(orbTradeData));
            if (orbTradeData.getValue().getExitOrderId() != null && orbTradeData.getValue().getExitOrderId() != "") {
                Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeData.getValue().getExitOrderId());
                String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                log.info("positions close price:"+slOrderStatusResponse);
                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                    if (orbTradeData.getValue().getEntryType() == "SELL" && orbTradeData.getValue().isReverseSLPlaced) {
                        orbTradeData.getValue().setReverseSellTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                        orbTradeData.getValue().setReverseExitTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                    } else {
                        orbTradeData.getValue().setBuyTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                        orbTradeData.getValue().setBuyTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                    }
                }
            }
        });
    }

    public void zerodhaBankNifty(){
        StopWatch stopWatch=new StopWatch();
        stopWatch.start();
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String currentDate=format.format(date);
        String niftyBank=zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from="+currentDate+"+09:00:00&to="+currentDate+"+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.print(response);
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if(!status.equals("error")) {
            historicalData.parseResponse(json);
            System.out.println();
            historicalData.dataArrayList.stream().forEach(historicalData1->{
            try {
                Date openDatetime = sdf.parse(historicalData1.timeStamp);
                String openDate = format.format(openDatetime);
                if (sdf.format(openDatetime).equals(openDate + "T09:15:00")) {
                    System.out.println(historicalData1.close);
                    int atmStrike=findATM((int)historicalData1.close);
                    Map<String,String> atmStrikes=zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(atmStrike));
                    atmStrikes.entrySet().stream().forEach(atmStrikeMap->{
                        System.out.println(atmStrikeMap.getKey());
                    });

                }
            } catch (ParseException e) {
                e.printStackTrace();
            }});

        }
        stopWatch.stop();
        log.info("process completed in ms:"+stopWatch.getTotalTimeMillis());
    }

}
*/
