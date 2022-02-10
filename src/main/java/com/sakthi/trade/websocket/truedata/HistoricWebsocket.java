/*

package com.sakthi.trade.websocket.truedata;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.AutomationController;
import com.sakthi.trade.domain.OrbTradeData;
import com.sakthi.trade.domain.PreOpenDetails;
import com.sakthi.trade.domain.TradeData;*/
/*
import com.sakthi.trade.options.banknifty.BankNiftyShortStraddle;
import com.sakthi.trade.options.nifty.NiftyShortStraddle;
import com.sakthi.trade.orb.OrbScheduler;
import com.sakthi.trade.trend.TrendScheduler;*//*

import com.sakthi.trade.truedata.HistoricRequestDTO;
import com.sakthi.trade.truedata.HistoricResponseDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.websocket.ContainerProvider;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Calendar.DAY_OF_MONTH;

@Component
@Slf4j
public class HistoricWebsocket {
    @Value("${truedata.wss}")
    String truedataWss;
    @Value("${truedata.username}")
    String truedataUsername;
    @Value("${truedata.password}")
    String truedataPassword;
    @Value("${truedata.historic.port}")
    String truedataHistoricDataPort;
    @Value("${filepath.trend}")
    String trendPath;
    @Value("${backtest.enabled}")
    boolean backtestEnabled;
    public Session session = null;
    String truedataURL = null;
    public Map<Integer, TradeData> orbTradePriceDTOS = null;
    public Map<String, PreOpenDetails> preOpenDetailsMap = new HashMap<>();
*/
/*
    @Autowired
    OrbScheduler orbScheduler;*//*


    @Autowired
    AutomationController automationController;
*/
/*
    @Autowired
    BankNiftyShortStraddle bankNiftyShortStraddle;
    @Autowired
    TrendScheduler trendScheduler;
    @Autowired
    NiftyShortStraddle niftyShortStraddle;*//*


    AtomicInteger atomicInteger = new AtomicInteger();

    public ObservableList<HistoricResponseDTO> historicResponseDTOObservableList = FXCollections.observableArrayList();
    ExecutorService executorService = Executors.newFixedThreadPool(10);
  //  @Scheduled(cron = "${truedata.websocket.scheduler.start}")
    public Session createHistoricWebSocket() throws IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/trend_"+dtf1.format(localDate)+".csv",false));
        String[] dataHeader={"StockName","PerChange","Closing Price","PreOpenPrice","Date"};
        CSVWriter csvWriterNeg=new CSVWriter(new FileWriter(trendPath+"/trend_negative_"+dtf1.format(localDate)+".csv",false));
        csvWriter.writeNext(dataHeader);
        csvWriter.flush();
        csvWriterNeg.writeNext(dataHeader);
        csvWriterNeg.flush();
        Map<String, Double> trendStockMap = new HashMap<>();
        if (session == null) {
            truedataURL = truedataWss.replace("port", truedataHistoricDataPort);
            truedataURL = truedataURL.replace("input_username", truedataUsername);
            truedataURL = truedataURL.replace("input_password", truedataPassword);
            WebSocketContainer webSocketContain = null;
            try {
                webSocketContain = ContainerProvider.getWebSocketContainer();
                webSocketContain.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
                webSocketContain.setDefaultMaxTextMessageBufferSize(1024 * 1024);
                session = webSocketContain.connectToServer(WebSocketClientEndPoint.class, new URI(truedataURL));
                // Optional<OrbConfigEntity> orbConfigEntity=orbConfigRepo.findById("test");
                BigDecimal amount = new BigDecimal("20000");


*/
/* if(orbConfigEntity.isPresent()){
                   // amount=new BigDecimal((orbConfigEntity.get().getAllocatedCapital()/10)*orbConfigEntity.get().getMargin());
                }*//*
*/
/*

                final BigDecimal amountPerStock = amount;
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @SneakyThrows
                    @Override
                    public void onMessage(String message) {

                      System.out.println(message);
                        if (message.contains("data")) {
                            HistoricResponseDTO historicResponseDTO = new Gson().fromJson(message, HistoricResponseDTO.class);
                            List<List<Object>> list = historicResponseDTO.getData();
                            Map<String,BigDecimal> historicPrice=new HashMap<>();
                            if (list != null) {
                                List<Object> list1 = list.get(0);
                                if (list1 != null && historicResponseDTO.getInterval().equals("15min")) {
                                    if (orbTradePriceDTOS == null) {
                                        orbTradePriceDTOS = new HashMap<>();
                                    }
                                    TradeData orbTradeData = new TradeData();
                                    orbTradeData.setStockName(historicResponseDTO.getSymbol());
                                    orbTradeData.setStockId(historicResponseDTO.getSymbolid());
                                    orbTradeData.setLowPrice(new BigDecimal(list1.get(3).toString()));
                                    orbTradeData.setHighPrice(new BigDecimal(list1.get(2).toString()));
                                    orbTradeData.setAmountPerStock(amountPerStock);
                                    Date date = new Date();
                                    orbTradeData.setCreateTimestamp(date);
                                    orbTradePriceDTOS.put(historicResponseDTO.getSymbolid(), orbTradeData);
                                } else if(historicResponseDTO.getSymbol().contains("CRUDE") && historicResponseDTO.getInterval().equals("5min")) {
                                    TradeData trendTradeData=new TradeData();
                                    trendTradeData.setStockName(historicResponseDTO.getSymbol());
                                    log.info("crude:"+historicResponseDTO.getSymbol());
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                    SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                                    historicPrice.put("High",new BigDecimal(list1.get(2).toString()));
                                    historicPrice.put("Low",new BigDecimal(list1.get(3).toString()));
                                    list.stream().forEach(tickData -> {
                                        try {
                                            String strTickDateTime = tickData.get(0).toString();
                                            final Date endDate = sdf1.parse(strTickDateTime);
                                            final String evaluateDate = sdf1.format(endDate);
                                            String checkTime = evaluateDate + "T13:00:00";
                                            Date closeTime = sdf.parse(evaluateDate + "T17:30:00");
                                            Date tickDateTime = null;
                                            try {
                                                tickDateTime = sdf.parse(strTickDateTime);

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                             BigDecimal highPrice=new BigDecimal(tickData.get(2).toString());
                                            BigDecimal closePrice = new BigDecimal(tickData.get(4).toString());
                                              BigDecimal lowPrice=new BigDecimal(tickData.get(3).toString());

                                            if (tickDateTime.after(sdf.parse(checkTime)) && tickDateTime.before(closeTime)) {

                                                if (closePrice.compareTo(historicPrice.get("High"))>0 && !trendTradeData.isOrderPlaced) {
                                                    trendTradeData.isOrderPlaced=true;
                                                    trendTradeData.setEntryType("Buy");
                                                    trendTradeData.setBuyPrice(closePrice);
                                                    trendTradeData.setBuyTime(strTickDateTime);
                                                  *//*

*/
/*  String[] data={historicResponseDTO.getSymbol(),String.valueOf(perCh),String.valueOf(closePrice),sdf.format(tickDateTime)};
                                                    csvWriter.writeNext(data);
                                                    csvWriter.flush();*//*
*/
/*

                                                } else if(closePrice.compareTo(historicPrice.get("Low"))<0 && !trendTradeData.isOrderPlaced){
                                                    trendTradeData.isOrderPlaced=true;
                                                    trendTradeData.setEntryType("Sell");
                                                    trendTradeData.setSellPrice(closePrice);
                                                    trendTradeData.setSellTime(strTickDateTime);
                                                }

                                            } else if(tickDateTime.equals(closeTime) && trendTradeData.isOrderPlaced){
                                                if (trendTradeData.isOrderPlaced & !trendTradeData.isExited) {
                                                    if (trendTradeData.getEntryType().equals("Sell")) {
                                                        trendTradeData.isExited = true;
                                                        trendTradeData.setBuyPrice(closePrice);
                                                        trendTradeData.setBuyTime(strTickDateTime);
                                                    } else if(trendTradeData.getEntryType().equals("Buy")){
                                                        trendTradeData.isExited = true;
                                                        trendTradeData.setSellPrice(closePrice);
                                                        trendTradeData.setSellTime(strTickDateTime);
                                                    }
                                                  *//*

*/
/*  String[] data={historicResponseDTO.getSymbol(),String.valueOf(perCh),String.valueOf(closePrice),sdf.format(tickDateTime)};
                                                    csvWriter.writeNext(data);
                                                    csvWriter.flush();*//*
*/
/*

                                                }

                                            }else if (tickDateTime.before(sdf.parse(checkTime))) {
                                            if(highPrice.compareTo(historicPrice.get("High"))>0){
                                                historicPrice.put("High",highPrice);
                                            }
                                                if(lowPrice.compareTo(historicPrice.get("Low"))<0){
                                                    historicPrice.put("Low",lowPrice);
                                                }
                                            }

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    CSVWriter csvWriter1=new CSVWriter(new FileWriter(trendPath+"/crude_"+dtf1.format(localDate)+".csv",true));
                                    if(trendTradeData.getSellPrice()!=null && trendTradeData.getBuyPrice()!=null) {
                                        String[] data = {trendTradeData.getStockName(), trendTradeData.getBuyPrice().toString(), trendTradeData.getSellPrice().toString(), trendTradeData.getBuyTime(), trendTradeData.getSellTime(), trendTradeData.getEntryType()};
                                        csvWriter1.writeNext(data);
                                        csvWriter1.flush();
                                    }

                                }
                               else if(historicResponseDTO.getSymbol().equals("NIFTY BANK") && historicResponseDTO.getInterval().equals("5min")){
                                try {
                                    log.info("NIFTY BANK: "+message);
                                    executorService.execute(new Runnable() {
                                        @SneakyThrows
                                        public void run() {
                                           if(!backtestEnabled) {
                                               try{
                                               Date currentDate =new Date();
                                               SimpleDateFormat sdfo = new SimpleDateFormat("dd-MM-yyyy");
                                               SimpleDateFormat sdfrom = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
                                               Date toDate = sdfrom.parse(sdfo.format(currentDate)+"T09:20:00");
                                          *//*

*/
/*     if(currentDate.before(toDate)){
                                                   System.out.println("current time:"+sdfo.format(currentDate));
                                                   System.out.println(message);
                                               }else {*//*
*/
/*

                                                   bankNiftyShortStraddle.shortStraddleTradeProcessor(historicResponseDTO);
                                               }
                                               //}
                                               catch (Exception e){
                                                   System.out.println("exception while processing bank nifty history web socket response:"+e.getMessage());
                                               }
                                           }
                                        }});
                                    //enable for backtest
                                    Date currentDate =new Date();
                                    SimpleDateFormat sdfo = new SimpleDateFormat("dd-MM-yyyy");
                                    SimpleDateFormat sdfrom = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
                                    Date fromDate = sdfrom.parse(sdfo.format(currentDate)+"T15:30:00");
                                    Date toDate = sdfrom.parse(sdfo.format(currentDate)+"T09:00:00");
                                    if(backtestEnabled || (currentDate.after(fromDate) || currentDate.before(toDate))) {
                                        bankNiftyShortStraddle.shortStraddleTradeBackTest(historicResponseDTO);
                                    }
                                    }catch(Exception e){
                                    e.printStackTrace();
                                    }
                                }
                                else if(historicResponseDTO.getSymbol().equals("NIFTY 50") && historicResponseDTO.getInterval().equals("5min")){
                                    try {
                                        log.info("NIFTY 50: "+message);
                                        executorService.execute(new Runnable() {
                                            @SneakyThrows
                                            public void run() {
                                    //            niftyShortStraddle.shortStraddleTradeProcessor(historicResponseDTO);
                                            }});
                                        //enable below fof nifty straddle backtest
                                      //       niftyShortStraddle.shortStraddleTradeBackTest(historicResponseDTO);
                                    }catch(Exception e){
                                        e.printStackTrace();
                                    }
                                }
                               else if(historicResponseDTO.getSymbol().contains("BANKNIFTY")){
                                    System.out.println(message);
                                    try { executorService.execute(new Runnable() {
                                        @SneakyThrows
                                        public void run() {
                                            Date currentDate =new Date();
                                            SimpleDateFormat sdfo = new SimpleDateFormat("dd-MM-yyyy");
                                            SimpleDateFormat sdfrom = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
                                            Date fromDate = sdfrom.parse(sdfo.format(currentDate)+"T15:30:00");
                                            Date toDate = sdfrom.parse(sdfo.format(currentDate)+"T09:00:00");
                                            if(backtestEnabled || (currentDate.after(fromDate) || currentDate.before(toDate))) {
                                                bankNiftyShortStraddle.shortStraddleTradeProcessLongBackTest(historicResponseDTO);
                                                //bankNiftyShortStraddle.longShortBackTest(historicResponseDTO);
                                            }
                                           // enable for realtime
                                            if(!backtestEnabled) {
                                                bankNiftyShortStraddle.shortStraddleTradeProcessor(historicResponseDTO);
                                            }
                                        }});
                                    }catch(Exception e){
                                        e.printStackTrace();
                                    }

                                }
                                else if(historicResponseDTO.getSymbol().startsWith("NIFTY20")){
                                    System.out.println(message);
                                    try {
                                        niftyShortStraddle.shortStraddleTradeProcessLongBackTest(historicResponseDTO);
                                       // niftyShortStraddle.shortStraddleTradeProcessBackTest(historicResponseDTO);
                                    }catch(Exception e){
                                        e.printStackTrace();
                                    }

                                }
                                else if (list1 != null && !"NIFTY 50".equals(historicResponseDTO.getSymbol()) && !"NIFTY BANK".equals(historicResponseDTO.getSymbol()) && !"NIFTY-I".equals(historicResponseDTO.getSymbol()) && historicResponseDTO.getInterval().equals("5min")) {
                                    //  log.info("trend:"+historicResponseDTO.getSymbol());
                                    // if(historicResponseDTO.getSymbol().contains("HDFC")){

                                    // }

                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                                        String[] stockList = {"INDUSINDBK", "JUBLFOOD", "HDFC", "HDFCBANK", "ONGC", "ZEEL", "HAVELLS", "ONGC", "BAJFINANCE", "LUPIN", "HDFCBANK", "DIVISLAB", "ADANIPORTS", "PAGEIND"};
                                        List<String> lsStockLst = Arrays.asList(stockList);

                                        PreOpenDetails preOpenDetails = new PreOpenDetails();
                                        list.stream().forEach(tickData -> {
                                            try { String strTickDateTime = tickData.get(0).toString();
                                                final Date endDate = sdf1.parse(strTickDateTime);
                                                final String evaluateDate = sdf1.format(endDate);
                                                String preOpenTime = evaluateDate + "T09:05:00";
                                                String checkTime = evaluateDate + "T11:10:00";
                                                Date tickDateTime = null;
                                                try {
                                                    tickDateTime = sdf.parse(strTickDateTime);

                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                if (Optional.ofNullable(trendScheduler.trendTradeMap.get(historicResponseDTO.getSymbol())).isPresent()) {
                                                if(tickDateTime.after(sdf.parse(checkTime))){

                                                }
                                                } else {


                                                    if (sdf.format(tickDateTime).equals(preOpenTime)) {
                                                        preOpenDetails.setOpenPrice(Double.parseDouble(tickData.get(4).toString()));
                                                        preOpenDetails.setStockName(historicResponseDTO.getSymbol());
                                                        preOpenDetailsMap.put(historicResponseDTO.getSymbol(), preOpenDetails);
                                                    }
                                                    BigDecimal closePrice = new BigDecimal(tickData.get(4).toString());
                                                    if (sdf.format(tickDateTime).equals(checkTime)) {
                                                        double preopenPrice = preOpenDetailsMap.get(historicResponseDTO.getSymbol()).getOpenPrice();
                                                        BigDecimal diff = closePrice.subtract(new BigDecimal(preopenPrice));
                                                        DecimalFormat df = new DecimalFormat("0.00");
                                                        double perCh = (diff.doubleValue() / preOpenDetails.getOpenPrice()) * 100;

                                                        if (lsStockLst.contains(historicResponseDTO.getSymbol())) {
                                                            System.out.println(historicResponseDTO.getSymbol() + ":" + strTickDateTime + ":" + perCh);
                                                        }
                                                        if (perCh > 3) {
                                                            log.info("trend per above 3:" + historicResponseDTO.getSymbol() + ":" + strTickDateTime);
                                                            //   System.out.println(historicResponseDTO.getSymbol());
                                                            trendStockMap.put(historicResponseDTO.getSymbol(), perCh);
                                                            String[] data = {historicResponseDTO.getSymbol(), String.valueOf(perCh), String.valueOf(closePrice), String.valueOf(preopenPrice), sdf.format(tickDateTime)};
                                                            csvWriter.writeNext(data);
                                                            csvWriter.flush();
                                                        }
                                                        if (perCh < -3) {
                                                            log.info("trend per below 3:" + historicResponseDTO.getSymbol() + ":" + strTickDateTime);
                                                            //   System.out.println(historicResponseDTO.getSymbol());
                                                            trendStockMap.put(historicResponseDTO.getSymbol(), perCh);
                                                            String[] data = {historicResponseDTO.getSymbol(), String.valueOf(perCh), String.valueOf(closePrice), String.valueOf(preopenPrice), sdf.format(tickDateTime)};
                                                            csvWriterNeg.writeNext(data);
                                                            csvWriterNeg.flush();
                                                        }

                                                    }
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        });

                                }
                                else if(historicResponseDTO.getInterval().equals("EOD")){
                                    List<Object> previousDayData =list.get(list.size()-1);
                                    BigDecimal close=new BigDecimal(previousDayData.get(4).toString());
                                    PreOpenDetails preOpenDetails=preOpenDetailsMap.get(historicResponseDTO.getSymbol());
                                    if(preOpenDetails==null){
                                        preOpenDetails= new PreOpenDetails();
                                    }
                                    if(preOpenDetails!=null){
                                        preOpenDetails.setStockName(historicResponseDTO.getSymbol());
                                        preOpenDetails.setPreviousClose(Double.parseDouble(previousDayData.get(4).toString()));
                                        preOpenDetailsMap.put(historicResponseDTO.getSymbol(),preOpenDetails);
                                    }
                                }
                            }

                            *//*

*/
/*if (historicResponseDTO.getInterval().equals("5min")) {
                                historicResponseDTOObservableList.add(historicResponseDTO);
                            }*//*
*/
/*

                        }
                    }
                });
                System.out.println("Connected to Historic WS endpoint: " + truedataURL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return session;
    }

    public String historicInput(String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(java.time.LocalDateTime.now());
        String fromDate = java.time.LocalDate.now() + "T09:15:00";
        if (LocalDateTime.parse(currentDate).isBefore(LocalDateTime.parse(java.time.LocalDate.now() + "T09:15:00"))) {
            fromDate = java.time.LocalDate.now().minus(Period.ofDays(1)) + "T09:15:00";
        }
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(currentDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }
    public String historicInputPreOpen(String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        // String fromDate = java.time.LocalDate.now().minus(Period.ofDays(1)) + "T09:00:00";
        String fromDate = java.time.LocalDate.now() + "T09:00:00";
        String toDate = java.time.LocalDate.now() + "T09:10:00";
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(toDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }
   // @Scheduled(cron = "${truedata.websocket.scheduler.heartbeat}")
    public void heartBeat() {
        String payload = historicInput("5min","NIFTY-I");
        try{
            if (session!=null && session.isOpen()) {
                session.getBasicRemote().sendText(payload);
            } else {
                session = createHistoricWebSocket();
                session.getBasicRemote().sendText(payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


  //  @Scheduled(cron = "${truedata.websocket.scheduler.eod.data}")
    public void eodData() throws IOException, CsvValidationException {
        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;
        int i=0;
        Calendar fromcalendar = Calendar.getInstance();
        fromcalendar.add(DAY_OF_MONTH, -5);
        Date fromdate = fromcalendar.getTime();
        Calendar tocalendar = Calendar.getInstance();
        tocalendar.add(DAY_OF_MONTH, -1);
       // tocalendar.add(DAY_OF_MONTH, -2);
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat("yyyy-MM-dd");
        Date todate = tocalendar.getTime();
        while ((line = csvReader.readNext()) != null) {
            if (i > 3) {

                String payload = historicInputEOD(line[1].trim(),simpleDateFormat.format(fromdate),simpleDateFormat.format(todate));
              //  System.out.println(payload);
                try {
                    if (session != null && session.isOpen()) {
                        session.getBasicRemote().sendText(payload);
                    } else {
                        session = createHistoricWebSocket();
                        session.getBasicRemote().sendText(payload);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            i++;
        }
    }

    //@Scheduled(cron = "${truedata.websocket.scheduler.preopen.data}")
    public void preOpenSchedule() throws IOException, CsvValidationException {

        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;
        int i=0;
        while ((line = csvReader.readNext()) != null) {
            if (i > 3) {
                String payload = historicInputPreOpen("5min", line[1].trim());
           //c     System.out.println(payload);
                try {
                    if (session != null && session.isOpen()) {
                        session.getBasicRemote().sendText(payload);
                    } else {
                        session = createHistoricWebSocket();
                        session.getBasicRemote().sendText(payload);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            i++;
        }
    }

   // @Scheduled(cron = "${truedata.websocket.scheduler.preopen.populate}")
    public void preOpenPopulate() throws IOException, CsvValidationException {
        DecimalFormat dc=new DecimalFormat("0.00");
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/nifty_straddle_trade_data_"+dtf1.format(localDate)+".csv",false));


        preOpenDetailsMap.entrySet().stream().forEach(preOpenDetails->{
            String stockName=preOpenDetails.getKey();
            PreOpenDetails preOpenDetailsO=preOpenDetails.getValue();
            double chPer=((preOpenDetailsO.getOpenPrice()-preOpenDetailsO.getPreviousClose())/preOpenDetailsO.getPreviousClose())*100;
          //  System.out.println(stockName+" previousClose:"+preOpenDetailsO.getPreviousClose()+" preOpen:"+preOpenDetailsO.getOpenPrice()+ " chPer:"+dc.format(chPer));
            preOpenDetailsO.setPerCh(chPer);
            String[] data={stockName,String.valueOf(preOpenDetailsO.getPreviousClose()),String.valueOf(preOpenDetailsO.getOpenPrice()),dc.format(chPer)};
            csvWriter.writeNext(data);
            try {
                csvWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter.close();
    }
   // @Scheduled(cron = "${truedata.websocket.scheduler.close}")
    public void destory() {
        close();
    }

    @PreDestroy
    private void close() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
                System.out.println("Historic Connection closed: " + truedataURL);
            }

            truedataURL = null;
            try {
                //    orbTradeDataRepo.deleteAll();
                if (orbTradePriceDTOS != null) {
                    // orbTradeDataRepo.saveAll(new ArrayList<>(orbTradePriceDTOS.values()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String historicInputEOD(String stock,String fromDate,String toDate) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(toDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval("EOD");
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }
}
*/

