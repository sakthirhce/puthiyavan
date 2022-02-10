/*
package com.sakthi.trade.orb;

import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.domain.ExitPositionRequestDTO;
import com.sakthi.trade.domain.OpenPositionsResponseDTO;
import com.sakthi.trade.eventday.EventDayConfiguration;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.fyer.transactions.*;
import com.sakthi.trade.fyer.transactions.OrderResponseDTO;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.truedata.HistoricRequestDTO;
import com.sakthi.trade.truedata.RealTimeSubscribeRequestDTO;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.websocket.truedata.HistoricWebsocket;
import com.sakthi.trade.websocket.truedata.RealtimeWebsocket;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;

import javax.annotation.PreDestroy;
import javax.websocket.Session;

@Component
@Slf4j
public class OrbScheduler {

    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;

    @Value("${fyers.order.place.api}")
    String orderPlaceAPIUrl;
    @Value("${fyers.positions}")
    String positionsURL;
    @Value("${filepath.trend}")
    String trendPath;
    @Value("${home.path}")
    String homeFilePath;
    @Value("${preoopen.filepath:/home/hasvanth/Downloads/PreOpen_FO_}")
    String preOpenFile;
    @Value("${secban.filepath:/home/hasvanth/Downloads/}")
    String secBan;

    @Value("${fyers.exit.positions}")
    String exitPositionsURL;

    @Autowired
    TransactionService transactionService;

    @Autowired
    FyerTransactionMapper fyerTransactionMapper;


    List<String> stockList = null;

    @Autowired
    RealtimeWebsocket realtimeWebsocket;

    @Autowired
    HistoricWebsocket historicWebsocket;

    @Autowired
    CommonUtil commonUtil;
    @Value("${chromedriver.path}")
    String driverPath;

    @Autowired
    SendMessage sendMessage;

    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    public Map<String,Double> preOpenData;
    @Value("${orb.scheduler.enabled:false}")
    boolean orbEnabled;
    @Autowired
    EventDayConfiguration eventDayConfiguration;

    private static final Logger logger = LoggerFactory.getLogger(OrbScheduler.class);

    public List<Map.Entry<String, Double> > getOrbStockList() throws Exception {
        long startTime = System.nanoTime();

        LocalDate localDate=LocalDate.now();
        DateTimeFormatter simpleDateFormat=DateTimeFormatter.ofPattern("ddMMMyyyy");
        DateTimeFormatter simpleDateFormat1=DateTimeFormatter.ofPattern("ddMMyyyy");
        String date=localDate.format(simpleDateFormat);
        FileReader fileSecBan = new FileReader(secBan+localDate.format(simpleDateFormat1)+".csv");
        //FileReader fileSecBan = new FileReader(secBan+"14092020.csv");
        BufferedReader readerSecBan =new BufferedReader(fileSecBan);
        String lineSecBan = "";
        String csvSplitBy=",";
        List<String> secBanList=new ArrayList<>();
        int k=0;
        while ((lineSecBan = readerSecBan.readLine()) != null) {
            if(k>0) {
                String[] data = lineSecBan.split(csvSplitBy);
                secBanList.add(data[1]);
            }
            k++;

        }
        int m=0;
        //orbStockDataRepo.deleteAll();
       // List<OrbStockDataEntity> finalList = new ArrayList<>();
      //  List<OrbStockDataEntity> orbStockDataEntityList = new ArrayList<>();
        Map<String,Double> preopenDataMap=new HashMap<>();
        Map<String,Double> preopenData=new HashMap<>();
    //    while ((line = reader.readLine()) != null) {
        historicWebsocket.preOpenDetailsMap.entrySet().stream().forEach(preOpenData->{
            PreOpenDetails preOpenDetails=preOpenData.getValue();
                Double stockPrice=preOpenDetails.getOpenPrice();
                if(!secBanList.contains(preOpenDetails.getStockName()) && stockPrice>100 && stockPrice<5000) {
                    preopenDataMap.put(preOpenDetails.getStockName(), preOpenDetails.getPerCh());
                    preopenData.put(preOpenDetails.getStockName(), preOpenDetails.getOpenPrice());
                 */
/*   OrbStockDataEntity orbStockDataEntity = new OrbStockDataEntity();
                    orbStockDataEntity.setSymbol(data[0]);
                    orbStockDataEntity.setChangePercentage(new BigDecimal(data[6]));
                    orbStockDataEntity.setQuantity(Integer.valueOf(data[8].replace(",", "")));
                    orbStockDataEntity.setStockPrice(new BigDecimal(data[4].replace(",", "")));
                    orbStockDataEntityList.add(orbStockDataEntity);*//*

                }
            });
        preOpenData=preopenData;
        // Create a list from elements of HashMap
        List<Map.Entry<String, Double> > list =
                new LinkedList<Map.Entry<String, Double> >(preopenDataMap.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double> >() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2)
            {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        List<Map.Entry<String, Double> > arlist =new ArrayList<>();
        arlist.addAll(list.subList(0, 5));
        arlist.addAll(list.subList(list.size() - 5, list.size()));
        String orBStrockList= "Orb: ";
        for (Map.Entry<String, Double> a:arlist
             ) {
            orBStrockList=orBStrockList+a.getKey()+", ";
            System.out.println("filetered:"+a.getKey());
        }
try {
    sendMessage.sendToTelegram(orBStrockList,telegramToken);
}catch (Exception e){
    e.printStackTrace();
}
      */
/*  orbStockDataRepo.saveAll(orbStockDataEntityList);
        List<OrbStockDataEntity> orbStockDataEntities = orbStockDataRepo.findAllORB();
        finalList.addAll(orbStockDataEntities.subList(0, 5));
        finalList.addAll(orbStockDataEntities.subList(orbStockDataEntities.size() - 5, orbStockDataEntities.size()));*//*

        long endTime = System.nanoTime();
        long processDuration = (endTime - startTime) / 1000000;
        logger.info("Successfully retrived pre open fo data from nse with time: " + processDuration);
        return arlist;
    }

    @Scheduled(cron = "${orb.data.15min.scheduler}")
    public void ORB15MinDataScheduler() {
        logger.info("ORB15MinData scheduler started");
        if (stockList != null) {
            Session session = historicWebsocket.session;
           // List<OrbTradeDataEntity> orbTradeDataEntityList = orbTradeDataRep.getOrbTradeDataEntityByCreateTimestampIn(new Date());
            if (historicWebsocket.orbTradePriceDTOS == null) {
              //  if (orbTradeDataEntityList.size() == 0) {
                    for (String stock : stockList) {
                        try {
                            String payload = orbIntraday15minHistoricInput("15min", stock);
                            //logger.info("15 min payload: "+ payload);
                            if (session!=null && session.isOpen()) {
                                session.getBasicRemote().sendText(payload);
                            } else {
                                session = historicWebsocket.createHistoricWebSocket();
                                session.getBasicRemote().sendText(payload);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
//                } else {
//                    historicWebsocket.orbTradePriceDTOS = new HashMap<>();
//                    orbTradeDataEntityList.stream().forEach(orbTradeDataEntity -> {
//                        logger.info("adding stock from table" + orbTradeDataEntity.getStockName());
//                        historicWebsocket.orbTradePriceDTOS.put(orbTradeDataEntity.getStockId(), orbTradeDataEntity);
//                    });
//                }
            }
        }
    }

    @Scheduled(cron = "${orb.live.scheduler}")
    public void ORBScheduler() throws CsvValidationException, ParseException, IOException {
        Map<String,EventDayData> eventDayMap=eventDayConfiguration.eventDayConfig();
        Date currentDate= new Date();
        String strCurrentDate=new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
        if(!orbEnabled || (eventDayMap.containsKey(strCurrentDate)&& eventDayMap.get(strCurrentDate).getEventInformation().equals("NO_TRADE"))){
            log.info("orb disabled | no trade day. not starting straddle");
        }else {
            logger.info(" ORBScheduler scheduler started");
            List<Map.Entry<String, Double>> data = new ArrayList<>();
            try {
                logger.info("downloading data from nse: ");
                data = getOrbStockList();
                logger.info("successfully downloaded data from nse.");

            } catch (Exception e) {
                logger.error("error while downloading data from nse: " + e.getMessage());

            }
            String payload = new Gson().toJson(prepareRealtimeQuoteInput(data));
            logger.info("orb tick subscribe: " + payload);
            try {
                Session session = realtimeWebsocket.session;
                if (session != null && session.isOpen()) {
                    session.getBasicRemote().sendText(payload);
                } else {
                    session = realtimeWebsocket.createRealtimeWebSocket();
                    session.getBasicRemote().sendText(payload);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public RealTimeSubscribeRequestDTO prepareRealtimeQuoteInput(List<Map.Entry<String, Double> > list, String... a) {
        if (stockList == null) {
            stockList = new ArrayList<>();
        }
        RealTimeSubscribeRequestDTO realTimeSubscribeRequestDTO = new RealTimeSubscribeRequestDTO();
        realTimeSubscribeRequestDTO.setMethod("addsymbol");
        list.stream().forEach(orbData ->
        {
            String str = orbData.getKey();
            String stockSymbol = StringEscapeUtils.unescapeJava(str);
            stockList.add(stockSymbol);
        });
         realTimeSubscribeRequestDTO.setSymbols(stockList);
        return realTimeSubscribeRequestDTO;
    }

    public String orbIntraday15minHistoricInput(String interval, String stock) {
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

   // @Scheduled(cron = "${orb.sl.scheduler}")
    public void ORBSLMonitorScheduler() {
        //logger.info("Exit ORBSLMonitor scheduler started");
        if (historicWebsocket.orbTradePriceDTOS != null) {
            historicWebsocket.orbTradePriceDTOS.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && !map.getValue().isSlPlaced && map.getValue().getEntryOrderId() != null)
                    .forEach(map -> {

                        TradeData orbTradeDataEntity = map.getValue();
                        Request request = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeDataEntity.getEntryOrderId());
                        String response = transactionService.callAPI(request);
                       // System.out.println(response);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                            Order order;
                            BigDecimal price;
                            if (orbTradeDataEntity.getEntryType() == "BUY") {
                                order = Order.SELL;
                                orbTradeDataEntity.setBuyTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                price = orbTradeDataEntity.getLowPrice();
                            } else {
                                order = Order.BUY;
                                orbTradeDataEntity.setSellTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                price = orbTradeDataEntity.getHighPrice();
                            }
                            orbTradeDataEntity.setFyersSymbol(orderStatusResponseDTO.getOrderDetails().getSymbol());
                            orbTradeDataEntity.setQty(orderStatusResponseDTO.getOrderDetails().getQty());
                            PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(orbTradeDataEntity.getStockName(), order, OrderType.STOP_LOSS_MARKET, ProductType.INTRADAY, Validity.DAY, orderStatusResponseDTO.getOrderDetails().getQty(), new BigDecimal(0), price, new BigDecimal(0));
                            String payload = new Gson().toJson(placeOrderRequestDTO);
                            Request slRequest = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceAPIUrl, payload);
                            String slResponse = transactionService.callAPI(slRequest);
                            OrderResponseDTO orderResponseDTO = new Gson().fromJson(slResponse, OrderResponseDTO.class);
                            if (orderResponseDTO.getS().equals("ok")) {
                                orbTradeDataEntity.setSlOrderId(orderResponseDTO.getId());
                                orbTradeDataEntity.setSlPrice(price);
                                orbTradeDataEntity.isSlPlaced = true;
                            }
                           // System.out.println(slResponse);
                        }
                    });
        }
        if(historicWebsocket.orbTradePriceDTOS!=null) {
            historicWebsocket.orbTradePriceDTOS.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().isSlPlaced && !map.getValue().isSLCancelled && !map.getValue().isExited && map.getValue().getEntryOrderId() != null && map.getValue().getSlOrderId() != null)
                    .forEach(map -> {
                        TradeData orbTradeDataEntity = map.getValue();
                        Request request = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeDataEntity.getSlOrderId());
                        String response = transactionService.callAPI(request);
                   //     System.out.println(response);
                        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
                        if (orderStatusResponseDTO.getOrderDetails() !=null && orderStatusResponseDTO.getOrderDetails().getStatus() != 6) {
                            if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                                map.getValue().isExited = true;
                                map.getValue().setSlTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                                String message = MessageFormat.format("Stop loss Hit stock {0}", orbTradeDataEntity.getStockName());
                                log.info(message);
                                log.info(message);
                                map.getValue().isSLHit=true;
                                sendMessage.sendToTelegram(message, telegramToken);
                            }
                            if (orderStatusResponseDTO.getOrderDetails().getStatus() == 1) {
                                map.getValue().isSLCancelled = true;
                                String message = MessageFormat.format("Broker Cancelled SL Order of {0}", orbTradeDataEntity.getStockName());
                                log.info(message);
                                sendMessage.sendToTelegram(message, telegramToken);
                            }

                        }
                    });
        }
    }

    //@Scheduled(cron = "${orb.exit.position.scheduler}")
    public void exitPositions() {
        logger.info("Orb Exit positions scheduler started");
        Request request = transactionService.createGetRequest(positionsURL, null);
        String response = transactionService.callAPI(request);
        Map<Integer, TradeData> orbTradeDataEntityMap = historicWebsocket.orbTradePriceDTOS;
        orbTradeDataEntityMap.entrySet().stream().filter(orbTradeDataEntity -> orbTradeDataEntity.getValue().isSlPlaced && !orbTradeDataEntity.getValue().isExited).forEach(orbTradeData -> {
           try {
               if (orbTradeData.getValue().getSlOrderId() != null && orbTradeData.getValue().getSlOrderId() != "") {
                   Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeData.getValue().getSlOrderId());
                   String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                   OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                   if (orderStatusResponseDTO.getOrderDetails().getStatus() != 2 && orderStatusResponseDTO.getOrderDetails().getStatus() == 6) {
                       CancelRequestDTO cancelRequestDTO = fyerTransactionMapper.prepareCancelRequest(Long.parseLong(orbTradeData.getValue().getSlOrderId()));
                       Request cancelRequest = transactionService.createPostPutDeleteRequest(HttpMethod.DELETE, orderPlaceAPIUrl, new Gson().toJson(cancelRequestDTO));
                       String cancelResponse = transactionService.callAPI(cancelRequest);
                       OrderResponseDTO orderResponseDTO = new Gson().fromJson(cancelResponse, OrderResponseDTO.class);
                       if (orderResponseDTO.getS().equals("ok")) {
                           orbTradeData.getValue().isSLCancelled = true;
                           String message = MessageFormat.format("System Cancelled SL {0}", orbTradeData.getValue().getStockName());
                           log.info(message);
                           sendMessage.sendToTelegram(message, telegramToken);
                       }
                   }
               }
           }catch(Exception e){
               e.printStackTrace();
           }
        });
        OpenPositionsResponseDTO openPositionsResponseDTO=new Gson().fromJson(response,OpenPositionsResponseDTO.class);
        openPositionsResponseDTO.getNetPositions().stream().forEach(netPositionDTO -> {
            try {
                if (netPositionDTO.getProductType().equals("INTRADAY") || netPositionDTO.getProductType() == "INTRADAY") {

                    Optional<Map.Entry<Integer, TradeData>> orbMap = orbTradeDataEntityMap.entrySet().stream().filter(orbTradeData -> orbTradeData.getValue().getFyersSymbol().equals(netPositionDTO.getSymbol())).findFirst();
                    if (orbMap.isPresent()) {
                        TradeData orbTradeDataEntity=orbMap.get().getValue();
                        Integer openQty = netPositionDTO.getBuyQty() - netPositionDTO.getSellQty();
                        if(openQty>0 || openQty<0) {
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
                                orbTradeDataEntity.setExitOrderId(orderResponseDTO.getId());
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
            }
            catch (Exception e){
                e.printStackTrace();
                String teleMessage = netPositionDTO.getSymbol() + ":Error while trying to close Postion";
                sendMessage.sendToTelegram(teleMessage,telegramToken);
            }
        });
    }
    @PreDestroy
    public void saveDatatoFile() throws IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/orb_trade_data_"+dtf1.format(localDate)+".csv",false));
        historicWebsocket.orbTradePriceDTOS.entrySet().stream().forEach(tradeData->
        {

            try {
                String[] data={tradeData.getKey().toString(),new Gson().toJson(tradeData)};
                csvWriter.writeNext(data);
                csvWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        csvWriter.close();

    }
  //  @Scheduled(cron = "${orb.position.close.price}")
    public void positionClosePrice() {
        logger.info("positions close price scheduler started");
        Map<Integer, TradeData> orbTradeDataEntityMap = historicWebsocket.orbTradePriceDTOS;
        orbTradeDataEntityMap.entrySet().stream().forEach(orbTradeData -> {
            log.info("positions close price tradedata:"+new Gson().toJson(orbTradeData));
            if (orbTradeData.getValue().getExitOrderId() != null && orbTradeData.getValue().getExitOrderId() != "") {
                Request slOrderStatusRequest = transactionService.createGetRequest(orderStatusAPIUrl, orbTradeData.getValue().getExitOrderId());
                String slOrderStatusResponse = transactionService.callAPI(slOrderStatusRequest);
                log.info("positions close price response:"+slOrderStatusResponse);
                OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(slOrderStatusResponse, OrderStatusResponseDTO.class);
                if (orderStatusResponseDTO.getOrderDetails().getStatus() == 2) {
                    if (orbTradeData.getValue().getEntryType() == "BUY") {
                        orbTradeData.getValue().setSellTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                    } else {
                        orbTradeData.getValue().setBuyTradedPrice(orderStatusResponseDTO.getOrderDetails().getTradedPrice());
                    }
                }
            }
        });
    }
   // @Scheduled(cron = "${orb.exit.position.details.report}")
    public void orbDetailsReport() throws IOException {
        CSVWriter csvWriter=new CSVWriter(new FileWriter(homeFilePath+"/trade_report/orb_trade_report.csv",true));
        historicWebsocket.orbTradePriceDTOS.entrySet().stream().forEach(tradeDataMap->
        {
            try {
                TradeData tradeData=tradeDataMap.getValue();
                String[] data={String.valueOf(tradeDataMap.getKey()),tradeData.getEntryType(),tradeData.getBuyTradedPrice()!=null?tradeData.getBuyTradedPrice().toString():"0",tradeData.getSellTradedPrice()!=null?tradeData.getSellTradedPrice().toString():"0",String.valueOf(tradeData.getQty()),String.valueOf(tradeData.isSLHit)};
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
