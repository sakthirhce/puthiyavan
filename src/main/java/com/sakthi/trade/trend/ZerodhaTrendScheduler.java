
package com.sakthi.trade.trend;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.sakthi.trade.zerodha.models.OHLCQuoteExtended;
import com.sakthi.trade.zerodha.models.QuoteExtended;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.OHLCQuote;
import com.zerodhatech.models.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class ZerodhaTrendScheduler {

    @Value("${preoopen.filepath:/home/hasvanth/Downloads/PreOpen_FO_}")
    String preOpenFile;
    @Value("${secban.filepath:/home/hasvanth/Downloads/}")
    String secBan;

    @Value("${fyers.order.place.api}")
    String orderPlaceURL;

    @Value("${trend.stock.margin}")
    String margin;
    @Value("${home.path}")
    String homeFilePath;
    @Value("${telegram.trend.bot.token}")
    String telegramToken;

    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;

    @Value("${fyers.order.place.api}")
    String orderPlaceAPIUrl;
    @Value("${fyers.positions}")
    String positionsURL;

    @Value("${fyers.exit.positions}")
    String exitPositionsURL;

    @Value("${filepath.trend}")
    String trendPath;

    @Value("${trend.scheduler.enabled:false}")
    boolean trendEnabled;

    @Autowired
    FyerTransactionMapper fyerTransactionMapper;

    @Autowired
    TransactionService transactionService;


    @Autowired
    SendMessage sendMessage;

    @Autowired
    EventDayConfiguration eventDayConfiguration;

    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    @Autowired
    CommonUtil commonUtil;

    @Autowired
    ZerodhaAccount zerodhaAccount;

    @Scheduled(cron = "${trend.scheduler}")
    public void trendScheduler() throws Exception, KiteException {
        long startTime = System.nanoTime();
        log.info("zerodha trend processor started");
        LocalDate localDate = LocalDate.now();
        DateTimeFormatter simpleDateFormat = DateTimeFormatter.ofPattern("ddMMMyyyy");
        DateTimeFormatter simpleDateFormat1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        String date = localDate.format(simpleDateFormat);
        /*FileReader fileSecBan = new FileReader(secBan + localDate.format(simpleDateFormat1) + ".csv");
        //FileReader fileSecBan = new FileReader(secBan+"14092020.csv");
        BufferedReader readerSecBan = new BufferedReader(fileSecBan);
        String lineSecBan = "";
        String csvSplitBy = ",";
        List<String> secBanList = new ArrayList<>();
        int k = 0;
        while ((lineSecBan = readerSecBan.readLine()) != null) {
            if (k > 0) {
                String[] data = lineSecBan.split(csvSplitBy);
                secBanList.add(data[1]);
            }
            k++;

        }*/
        int m = 0;
        Set<String> symbolSet = zerodhaTransactionService.lsSymbols.keySet();
        String[] symbolArray = symbolSet
                .toArray(new String[0]);
        int index = 0;


        for (String str : symbolSet)
            symbolArray[index++] = "NSE:" + str;
        log.info("request symbols:"+symbolArray.toString());
        Map<String, Quote> quoteMap = zerodhaAccount.kiteSdk.getQuote(symbolArray);
        log.info("response trend quote:"+new Gson().toJson(quoteMap));
        Map<String, Double> positiveList= new HashMap<>();
        Map<String, Double> negList = new HashMap<>();
        quoteMap.entrySet().stream().forEach(quoteK -> {

            Quote quote=quoteK.getValue();
            BigDecimal diff = new BigDecimal(quote.lastPrice).subtract(new BigDecimal(quote.ohlc.open));
            DecimalFormat df = new DecimalFormat("0.00");
            BigDecimal perCh = diff.divide(new BigDecimal(quote.ohlc.open),2,BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"));
            if(perCh.compareTo(new BigDecimal("0"))>0)
            positiveList.put(quoteK.getKey(),perCh.doubleValue());
            if(perCh.compareTo(new BigDecimal("0"))<0)
                negList.put(quoteK.getKey(),perCh.doubleValue());
        });
        List<Map.Entry<String, Double>> sortedPositiveMap=sortByValue(positiveList);
        for (Map.Entry<String, Double> map : sortedPositiveMap) {
            log.info("positive sorted: "+map.getKey()+":"+map.getValue());
        }
        List<Map.Entry<String, Double>> sortedNegativeMap=sortByValue(negList);
        for (Map.Entry<String, Double> map : sortedNegativeMap) {
            log.info("negative sorted: "+map.getKey()+":"+map.getValue());
        }
        int startIndex=sortedPositiveMap.size()-5;
        if(startIndex<0){
            startIndex=0;
        }
        if(sortedPositiveMap.size()>0) {
            List<Map.Entry<String, Double>> sortedPositiveMapSub = sortedPositiveMap.subList(startIndex, sortedPositiveMap.size());
             for (Map.Entry<String, Double> map : sortedPositiveMapSub) {
                log.info("positive top: "+map.getKey()+":"+map.getValue());
            }
        }
        int endIndex=sortedNegativeMap.size();
        if(endIndex>5){
            endIndex=5;
        }
        if(endIndex>0) {
            List<Map.Entry<String, Double>> sortedNegMapSub = sortedNegativeMap.subList(0, endIndex);
            for (Map.Entry<String, Double> map : sortedNegMapSub) {
                log.info("negative top: "+map.getKey()+":"+map.getValue());
            }
        }


    }

    public static List<Map.Entry<String, Double>> sortByValue(Map<String, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        // put data from sorted list to hashmap
        HashMap<String, Double> temp = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return list;
    }
}
/*
        historicWebsocket.preOpenDetailsMap.entrySet().stream().forEach(preOpenData->{
                    PreOpenDetails preOpenDetails=preOpenData.getValue();
                    Double stockPrice=preOpenDetails.getOpenPrice();
                    if(!secBanList.contains(preOpenDetails.getStockName()) && stockPrice>100 && stockPrice<10000) {
                        preopenData.put(preOpenDetails.getStockName(), preOpenDetails.getOpenPrice());
                }
            });*//*

        orbScheduler.preOpenData=preopenData;
        long endTime = System.nanoTime();
        long processDuration = (endTime - startTime) / 1000000;
        log.info("Successfully retrived pre open fo data from nse with time: " + processDuration);
        return preopenData;
    }
    //@Scheduled(cron = "${trend.scheduler}")
    public void trendScheduler() throws Exception, KiteException {
        log.info("trend scheduler started process");
        Map<String,Double> stockList=getPreOpenStockList();
        Session session=historicWebsocket.session;
        if (session==null){
            session = historicWebsocket.createHistoricWebSocket();
        }
        for (Map.Entry<String, Double> entry : stockList.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());

            try {
                String payload = orbIntraday15minHistoricInput("5min", entry.getKey());
               // log.info("5 min payload: "+ payload);
                session.getBasicRemote().sendText(payload);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
   // @Scheduled(cron = "${trend.scheduler.live}")
    public void trendLive() throws Exception, KiteException {
        Map<String,EventDayData> eventDayMap=eventDayConfiguration.eventDayConfig();
      //  trendScheduler();
       // Thread.sleep(120000);
        Date currentDate= new Date();
        String strCurrentDate=new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if( !trendEnabled || (eventDayMap.containsKey(strCurrentDate)&& eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE"))){
            log.info("trend diabled | no trade day. not starting trend");
        }else {
            log.info("trend processor started");
            DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
            LocalDate localDate = LocalDate.now();
            Set<String> symbolSet=zerodhaTransactionService.lsSymbols.keySet();

            String[] arrayOfSym = new String[symbolSet.size()];
            int index = 0;
            for (String str : symbolSet)
                arrayOfSym[index++] = "NSE:"+str;

            Map<String, Quote>  quoteMap=zerodhaAccount.kiteSdk.getQuote(arrayOfSym);
            quoteMap.entrySet().parallelStream().forEach(quote->{
                System.out.println(quote.getValue());

            });
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/trend_" + dtf1.format(localDate) + ".csv"));

            Map<String, Double> stockList = new HashMap<>();
            Map<String, Double> stockPriceList = new HashMap<>();
            String[] line;
            int i = 0;
            while ((line = csvReader.readNext()) != null) {
                if (i > 0) {
                    stockList.put(line[0], Double.valueOf(line[1]));
                    stockPriceList.put(line[0], Double.valueOf(line[3]));
                }
                i++;
            }

            List<Map.Entry<String, Double>> list =
                    new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());

            // Sort the list
            Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
                public int compare(Map.Entry<String, Double> o1,
                                   Map.Entry<String, Double> o2) {
                    return (o1.getValue()).compareTo(o2.getValue());
                }
            });
            String[] marginInfo = margin.split(":");
            int startIndex = list.size() - 5;
            if (startIndex < 0) {
                startIndex = 0;
            }
            for (Map.Entry<String, Double> map : list.subList(startIndex, list.size())) {
                try {
                    double allocatedAmount = (Double.valueOf(marginInfo[1]) / Double.valueOf(marginInfo[0])) * Double.valueOf(marginInfo[2]);
                    int quantity = (int) (allocatedAmount / stockPriceList.get(map.getKey()).doubleValue());
                    System.out.println(map.getKey() + ":" + quantity);
                    PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(map.getKey(), Order.BUY, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, quantity, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                    Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                    String response = transactionService.callAPI(request);
                    System.out.println("buy response: " + response);
                    OrderResponseDTO orderResponseDTO = new Gson().fromJson(response, OrderResponseDTO.class);
                    TradeData trendTradeData = new TradeData();
                    trendTradeData.setStockName(map.getKey());
                    trendTradeData.setSlPrice(new BigDecimal(stockPriceList.get(map.getKey())));
                    Date date = new Date();
                    trendTradeData.setCreateTimestamp(date);
                    if (orderResponseDTO.getS().equals("ok")) {
                        trendTradeData.setEntryOrderId(orderResponseDTO.getId());
                        trendTradeData.setQty(quantity);
                        trendTradeData.isOrderPlaced = true;

                        trendTradeData.setEntryType("BUY");
                        sendMessage.sendToTelegram("Bought stock: " + map.getKey() + ": Quantity :" + quantity, telegramToken);
                    } else {
                        trendTradeData.isErrored = true;
                        sendMessage.sendToTelegram("Error occurred while placing trending stocks: " + map.getKey() + ": Status :" + orderResponseDTO.getS() + ": Error Message :" + orderResponseDTO.getMessage(), telegramToken);
                    }
                    trendTradeMap.put(map.getKey(), trendTradeData);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendMessage.sendToTelegram("error while processing trend stocks", telegramToken);
                }
            }
        }
    }

    public void trendLiveBackTest() throws Exception {
        Map<String,EventDayData> eventDayMap=eventDayConfiguration.eventDayConfig();
        //  trendScheduler();
        // Thread.sleep(120000);
        Date currentDate= new Date();
        String strCurrentDate=new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if(eventDayMap.containsKey(strCurrentDate)&& eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE")){
            log.info("no trade day. not starting trend");
        }else {
            log.info("trend processor started");
            DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
            LocalDate localDate = LocalDate.now();
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/trend_" + dtf1.format(localDate) + ".csv"));
            CSVReader csvReaderNeg = new CSVReader(new FileReader(trendPath + "/trend_negative_" + dtf1.format(localDate) + ".csv"));
            Map<String, Double> stockList = new HashMap<>();
            Map<String, Double> stockPriceList = new HashMap<>();
            Map<String, Double> stockClosePriceList = new HashMap<>();
            Map<String, Double> stockNegList = new HashMap<>();
            Map<String, Double> stockPriceNegList = new HashMap<>();
            String[] line;
            int i = 0;
            while ((line = csvReader.readNext()) != null) {
                if (i > 0) {
                    stockList.put(line[0], Double.valueOf(line[1]));
                    stockPriceList.put(line[0], Double.valueOf(line[3]));
                    stockClosePriceList.put(line[0], Double.valueOf(line[2]));
                }
                i++;
            }
            int j = 0;
            while ((line = csvReaderNeg.readNext()) != null) {
                if (j > 0) {
                    stockNegList.put(line[0], Double.valueOf(line[1]));
                    stockPriceNegList.put(line[0], Double.valueOf(line[3]));
                    stockClosePriceList.put(line[0], Double.valueOf(line[2]));
                }
                j++;
            }

            List<Map.Entry<String, Double>> list =
                    new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());
            List<Map.Entry<String, Double>> Neglist =
                    new LinkedList<Map.Entry<String, Double>>(stockNegList.entrySet());
            // Sort the list
            Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
                public int compare(Map.Entry<String, Double> o1,
                                   Map.Entry<String, Double> o2) {
                    return (o1.getValue()).compareTo(o2.getValue());
                }
            });
            Collections.sort(Neglist, new Comparator<Map.Entry<String, Double>>() {
                public int compare(Map.Entry<String, Double> o1,
                                   Map.Entry<String, Double> o2) {
                    return (o1.getValue()).compareTo(o2.getValue());
                }
            });


            String[] marginInfo = margin.split(":");
            int startIndex = list.size() - 5;
            if (startIndex < 0) {
                startIndex = 0;
            }
            System.out.println("total size:"+list.size()+"sub list"+list.subList(startIndex,list.size()));
            System.out.println("total pos :"+list);
            int toNegIndex=5-list.subList(startIndex,list.size()).size();
            if(toNegIndex>0) {
                System.out.println("total neg size:" + Neglist.size() + "sub list" + Neglist.subList(0, toNegIndex));
                System.out.println("total neg :" + Neglist);
            }

            for (Map.Entry<String, Double> map : list.subList(startIndex, list.size())) {
                try {
                    double allocatedAmount = (Double.valueOf(marginInfo[1]) / Double.valueOf(marginInfo[0])) * Double.valueOf(marginInfo[2]);
                    int quantity = (int) (allocatedAmount / stockPriceList.get(map.getKey()).doubleValue());
                    System.out.println(map.getKey() + ":" + quantity);
                    TradeData trendTradeData = new TradeData();
                    trendTradeData.setStockName(map.getKey());
                    trendTradeData.setSlPrice(new BigDecimal(stockPriceList.get(map.getKey())));
                    Date date = new Date();
                    trendTradeData.setCreateTimestamp(date);
                    trendTradeData.isOrderPlaced = true;
                    trendTradeData.setBuyPrice(new BigDecimal(stockClosePriceList.get(map.getKey())));
                    trendTradeData.setEntryType("BUY");

                } catch (Exception e) {
                    e.printStackTrace();
                    sendMessage.sendToTelegram("error while processing trend stocks", telegramToken);
                }
            }
        }
    }
   // @Scheduled(cron = "${trend.sl.scheduler}")
    public void sLMonitorScheduler() {
      //  log.info("Trend SLMonitor scheduler started");
        if (trendTradeMap != null) {
            trendTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && !map.getValue().isSlPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {
                        TradeData trendTradeData = map.getValue();
                        Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getEntryOrderId());
                        String response = transactionService.callAPI(request);
                     //   System.out.println(response);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                            Order order;
                            OrderDetailsDTO orderDetailsDTO=orderStatusResponseDTO.getOrderDetails();
                           // BigDecimal price = (orderDetailsDTO.getTradedPrice().divide(new BigDecimal(2))).setScale(0, RoundingMode.HALF_UP);
                            if (trendTradeData.getEntryType() == "BUY") {

                                order = Order.SELL;
                            } else {
                                order = Order.BUY;
                            }
                            trendTradeData.setBuyTradedPrice(orderDetailsDTO.getTradedPrice());
                            trendTradeData.setFyersSymbol(orderStatusResponseDTO.getOrderDetails().getSymbol());
                            PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(trendTradeData.getStockName(), order, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, orderStatusResponseDTO.getOrderDetails().getQty(), new BigDecimal(0), trendTradeData.getSlPrice(), new BigDecimal(0));
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
            if(trendTradeMap!=null) {
                trendTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().isSlPlaced && !map.getValue().isSLCancelled && !map.getValue().isExited && map.getValue().getEntryOrderId() != null && map.getValue().getSlOrderId() != null)
                        .forEach(map -> {
                            TradeData trendTradeData = map.getValue();
                            Request request = transactionService.createGetRequest(orderStatusAPIUrl, trendTradeData.getSlOrderId());
                            String response = transactionService.callAPI(request);
                         //   System.out.println(response);
                            OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                            if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() != 6) {
                                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                                    map.getValue().isExited = true;
                                    map.getValue().setSlTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                    map.getValue().isSLHit=true;
                                    String message = MessageFormat.format("Stop loss Hit stock {0}", trendTradeData.getStockName());
                                    log.info(message);
                                    sendMessage.sendToTelegram(message, telegramToken);
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

  //  @Scheduled(cron = "${trend.exit.position.scheduler}")
    public void exitPositions() {
        log.info("Trend Exit positions scheduler started");
        try {
            Request request = transactionService.createGetRequest(positionsURL, null);
            String response = transactionService.callAPI(request);
            trendTradeMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited).forEach(trendMap -> {
                try {
                    if (trendMap.getValue().getSlOrderId() != null && trendMap.getValue().getSlOrderId() != "") {
                        Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, trendMap.getValue().getSlOrderId());
                        String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() != 2 && orderStatusResponseDTO.getOrderDetails().getStatus() == 6) {
                            CancelRequestDTO cancelRequestDTO = fyerTransactionMapper.prepareCancelRequest(Long.parseLong(trendMap.getValue().getSlOrderId()));
                            Request cancelRequest = transactionService.createPostPutDeleteRequest(HttpMethod.DELETE, orderPlaceAPIUrl, new Gson().toJson(cancelRequestDTO));
                            String cancelResponse = transactionService.callAPI(cancelRequest);
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(cancelResponse, OrderResponseDTO.class);
                            if (orderResponseDTO.getS().equals("ok")) {
                                trendMap.getValue().isSLCancelled = true;
                                String message = MessageFormat.format("System Cancelled SL {0}", trendMap.getValue().getStockName());
                                log.info(message);
                                sendMessage.sendToTelegram(message,telegramToken);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            OpenPositionsResponseDTO openPositionsResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
            openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
                try {
                    if(netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType()=="INTRADAY" ) {
                        Integer openQty = netPositionDTO.getBuyQty() - netPositionDTO.getSellQty();
                        if(openQty>0 || openQty<0) {
                        Optional<Map.Entry<String, TradeData>> trendMap = trendTradeMap.entrySet().stream().filter(tradeData -> tradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
                        if (trendMap.isPresent()) {
                            String id = netPositionDTO.getId();
                            ExitPositionRequestDTO exitPositionRequestDTO = new ExitPositionRequestDTO(id);
                            Request exitPositionRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, exitPositionsURL, new Gson().toJson(exitPositionRequestDTO));
                            String exitPositionsResponse = transactionService.callAPI(exitPositionRequest);
                            String message = MessageFormat.format("Exited Positions response: {0}", exitPositionsResponse);
                            System.out.println(message);
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
                    sendMessage.sendToTelegram(teleMessage,telegramToken);
                }
            });
        } catch (Exception e){
            e.printStackTrace();
            String teleMessage = "Entire Exit position process failed. review and close positions manually";
            sendMessage.sendToTelegram(teleMessage,telegramToken);
        }
    }
    public String orbIntraday15minHistoricInput(String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String currentDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.now());
        String fromDate = LocalDate.now() + "T09:15:00";
        if (LocalDateTime.parse(currentDate).isBefore(LocalDateTime.parse(LocalDate.now() + "T09:15:00"))) {
            fromDate = LocalDate.now().minus(Period.ofDays(1)) + "T09:15:00";
        }
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(currentDate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return new Gson().toJson(historicRequestDTO);
    }
    @PreDestroy
    public void saveDatatoFile() throws IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/trend_trade_data_"+dtf1.format(localDate)+".csv",false));
        trendTradeMap.entrySet().stream().forEach(trendTrade->
        {

            try {
                String[] data={trendTrade.getKey(),new Gson().toJson(trendTrade)};
                csvWriter.writeNext(data);
                csvWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter.close();

    }

    @Scheduled(cron = "${orb.exit.position.details.report}")
    public void trendDetailsReport() throws IOException {
           commonUtil.tradeReport(trendTradeMap,"/trade_report/trend_trade_report.csv");

    }
    @Scheduled(cron = "${orb.position.close.price}")
    public void positionClosePrice() {
        log.info("positions close price scheduler started");
        trendTradeMap.entrySet().stream().forEach(orbTradeData -> {
            log.info("positions close price tradedata:"+new Gson().toJson(orbTradeData));
            if (orbTradeData.getValue().getExitOrderId() != null && orbTradeData.getValue().getExitOrderId() != "") {
                Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeData.getValue().getExitOrderId());
                String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                log.info("positions close price response:"+slOrderStatusResponse);
                OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                    if (orbTradeData.getValue().getEntryType() == "BUY") {
                        orbTradeData.getValue().setSellTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                        orbTradeData.getValue().setSellTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                    } else {
                        orbTradeData.getValue().setBuyTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                        orbTradeData.getValue().setBuyTime(orderStatusResponseDTO.getOrderDetails().getOrderDateTime());
                    }

                }

            }
        });
    }

    public void newTrend() throws IOException, CsvValidationException {

        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;
        int i = 0;
        Calendar fromcalendar = Calendar.getInstance();
        fromcalendar.add(DAY_OF_MONTH, -5);
        Date fromdate = fromcalendar.getTime();
        Calendar tocalendar = Calendar.getInstance();
        tocalendar.add(DAY_OF_MONTH, -1);
        // tocalendar.add(DAY_OF_MONTH, -2);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date todate = tocalendar.getTime();
        String uri = "";
        while ((line = csvReader.readNext()) != null) {
            if (i == 3) {

                uri = uri + "i=NSE:" + line[1].trim();
            } else if (i > 3) {
                uri = uri + "&i=NSE:" + line[1].trim();
            }
            i++;
        }

        Map<String, Double> stockList = new HashMap<>();
        Map<String, Double> stockNegList = new HashMap<>();
        zerodhaTransactionService.lsSymbols.entrySet().stream().forEach(fostock -> {
            System.out.println(fostock.getKey() + ":");
            String historicURL = "https://api.kite.trade/instruments/historical/" + fostock.getValue() + "/5minute?from=2020-12-23+09:00:00&to=2020-12-23+11:15:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            System.out.print(response);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            String status = json.getString("status");

            if(!status.equals("error")) {
                historicalData.parseResponse(json);

                Map<String, Double> stockPrice = new HashMap<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                HistoricalData historicalData1 = historicalData.dataArrayList.get(0);
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = sdf1.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T09:15:00")) {
                        stockPrice.put("OPEN", historicalData1.open);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                historicalData.dataArrayList.stream().forEach(ohlc -> {
                    //  System.out.println(ohlc.getKey()+":"+ohlc.getValue().instrumentToken+":"+ohlc.getValue().lastPrice+":"+ohlc.getValue().ohlc.open);
                    Date dateTime = null;
                    try {
                     //   System.out.println(ohlc.timeStamp);
                        dateTime = sdf.parse(ohlc.timeStamp);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    String openDate = sdf1.format(dateTime);
                    if (sdf.format(dateTime).equals(openDate + "T11:10:00")) {
                        BigDecimal diff = new BigDecimal(ohlc.close).subtract(new BigDecimal(stockPrice.get("OPEN")));
                        DecimalFormat df = new DecimalFormat("0.00");
                        double perCh = (diff.doubleValue() / stockPrice.get("OPEN")) * 100;
                        if (perCh > 3) {
                            stockList.put(fostock.getKey(), Double.parseDouble(df.format(perCh)));
                        }
                        if (perCh < -3) {
                            stockNegList.put(fostock.getKey(), Double.parseDouble(df.format(perCh)));
                        }
                    }


                });
            }
        */
/*String  ohlcQuotesURI="https://api.kite.trade/quote/ohlc?"+uri;
        System.out.println(ohlcQuotesURI);
        String response1=transactionService.callAPI(transactionService.createZerodhaGetRequest(ohlcQuotesURI));
        System.out.println(response1);
        final OHLCQuoteResponse fromJson = new Gson().fromJson(response1,
                new TypeToken<OHLCQuoteResponse>(){}.getType());
        System.out.println(fromJson.getStatus());
        Map<String, Double> stockList = new HashMap<>();
        Map<String, Double> stockNegList = new HashMap<>();
        System.out.println("size:"+fromJson.getOhlcQuoteMap().size());
        fromJson.getOhlcQuoteMap().entrySet().parallelStream().forEach(ohlc->{
          //  System.out.println(ohlc.getKey()+":"+ohlc.getValue().instrumentToken+":"+ohlc.getValue().lastPrice+":"+ohlc.getValue().ohlc.open);
            BigDecimal diff = new BigDecimal(ohlc.getValue().lastPrice).subtract(new BigDecimal(ohlc.getValue().ohlc.open));
            DecimalFormat df = new DecimalFormat("0.00");
            double perCh = (diff.doubleValue() / ohlc.getValue().ohlc.open) * 100;
           // System.out.println(ohlc.getKey()+":"+perCh);
            if(perCh>3){
            stockList.put(ohlc.getKey(),perCh);}
            if(perCh<-3){
                stockNegList.put(ohlc.getKey(),perCh);}
        });
        System.out.println("stockList size:"+stockList.size());
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());
        List<Map.Entry<String, Double>> negList =
                new LinkedList<Map.Entry<String, Double>>(stockNegList.entrySet());
        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        Collections.sort(negList, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        System.out.println("list size:"+list.size());
        int fromPStart=0;
        if(list.size()>5){
            fromPStart=list.size()-5;
        }
        for (Map.Entry<String, Double> map : list.subList(fromPStart, list.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
        System.out.println("negList size:"+negList.size());
        int fromStart=0;
        if(negList.size()>5){
            fromStart=negList.size()-5;
        }
        for (Map.Entry<String, Double> map : negList.subList(fromStart, negList.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
        stopWatch.stop();
        System.out.println("total time:"+stopWatch.getTotalTimeMillis());*//*

        });
        System.out.println("stockList size:"+stockList.size());
        List<Map.Entry<String, Double>> list =
                new LinkedList<Map.Entry<String, Double>>(stockList.entrySet());
        List<Map.Entry<String, Double>> negList =
                new LinkedList<Map.Entry<String, Double>>(stockNegList.entrySet());
        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        Collections.sort(negList, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        System.out.println("list size:"+list.size());
        int fromPStart=0;
        if(list.size()>5){
            fromPStart=list.size()-5;
        }
        for (Map.Entry<String, Double> map : list.subList(fromPStart, list.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
        System.out.println("negList size:"+negList.size());
        int fromStart=0;
        if(negList.size()>5){
            fromStart=negList.size()-5;
        }
        for (Map.Entry<String, Double> map : negList.subList(fromStart, negList.size())) {
            System.out.println(map.getKey()+":"+map.getValue());
        }
    }

}*/