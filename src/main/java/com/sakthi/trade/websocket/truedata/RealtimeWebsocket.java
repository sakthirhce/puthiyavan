/*
package com.sakthi.trade.websocket.truedata;

import com.google.gson.Gson;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.fyer.transactions.OrderResponseDTO;
import com.sakthi.trade.fyer.transactions.PlaceOrderRequestDTO;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.truedata.RealTimeSubscribeRequestDTO;
import com.sakthi.trade.truedata.TickResponseDTO;
import lombok.SneakyThrows;
import okhttp3.Request;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.websocket.ContainerProvider;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

@Component
public class RealtimeWebsocket {
    @Value("${truedata.wss}")
    String truedataWss;
    @Value("${truedata.username}")
    String truedataUsername;
    @Value("${truedata.password}")
    String truedataPassword;
    @Value("${truedata.realtime.port}")
    String truedataRealTimeDataPort;
    @Value("${fyers.order.place.api}")
    String orderPlaceURL;
    public Session session = null;
    String truedataURL = null;
    @Autowired
    FyerTransactionMapper fyerTransactionMapper;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;
    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;
    @Autowired
    HistoricWebsocket historicWebsocket;
    public Map<String,String> trueDataSymbolIdMap=new HashMap<>();

    //@Scheduled(cron="${truedata.websocket.scheduler.start}")
    public Session createRealtimeWebSocket() throws Exception{

        if (session == null) {
            truedataURL = truedataWss.replace("port", truedataRealTimeDataPort);
            truedataURL = truedataURL.replace("input_username", truedataUsername);
            truedataURL = truedataURL.replace("input_password", truedataPassword);
            WebSocketContainer webSocketContain = null;
            try {
                webSocketContain = ContainerProvider.getWebSocketContainer();
                webSocketContain.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
                webSocketContain.setDefaultMaxTextMessageBufferSize(1024 * 1024);
                session = webSocketContain.connectToServer(WebSocketClientEndPoint.class, new URI(truedataURL));
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @SneakyThrows
                    @Override
                    public void onMessage(String message) {
                      //  System.out.println(message);
                       */
/* if (message.contains("symbolsadded")) {
                            try {
                                RealtimeSubscriptionResponeDTO realtimeSubscriptionResponeDTO=new Gson().fromJson(message,RealtimeSubscriptionResponeDTO.class);
                                realtimeSubscriptionResponeDTO.getSymbollist().stream().forEach( subscribedSymbol-> {

                                                String[] subData = subscribedSymbol.split(":");
                                            if (subData[0].contains("NIFTY")) {
                                                trueDataSymbolIdMap.put(subData[1], subData[0]);
                                            }
                                        }
                                );
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            }
*//*

                        if (message.contains("trade")) {

                            TickResponseDTO tickResponseDTO = new Gson().fromJson(message, TickResponseDTO.class);
                            List<String> tradeData = tickResponseDTO.getTrade();
                            String symbolId = tradeData.get(0);
                          */
/*  if (trueDataSymbolIdMap.containsKey(symbolId)) {
                                bankNiftyStraddleLong.bankNiftyStraddleProcessor(tickResponseDTO);
                            } else { *//*

                                Map<Integer, TradeData> orbTradeDataEntityMap = historicWebsocket.orbTradePriceDTOS;
                                if (orbTradeDataEntityMap != null && orbTradeDataEntityMap.containsKey(Integer.valueOf(symbolId))) {
                                    TradeData orbTradeDataEntity = orbTradeDataEntityMap.get(Integer.valueOf(symbolId));
                                    //System.out.println(message+ ":" + orbTradeDataEntity.getStockName());
                                    BigDecimal lastTradedPrice = new BigDecimal(tradeData.get(2));
                                    if (!orbTradeDataEntity.isOrderPlaced && !orbTradeDataEntity.isErrored) {
                                        if (lastTradedPrice.compareTo(orbTradeDataEntity.getHighPrice()) == 1) {
                                            orbTradeDataEntity.setBuyPrice(lastTradedPrice);
                                            Integer qty = orbTradeDataEntity.getAmountPerStock().divide(orbTradeDataEntity.getHighPrice(), 1).intValue();
                                            if (qty > 0) {
                                                try {
                                                    String teleMessage = orbTradeDataEntity.getStockName() + ":BUY:Qty:" + qty + ":Price:" + lastTradedPrice;
                                                    sendMessage.sendToTelegram(teleMessage, telegramToken);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(orbTradeDataEntity.getStockName(), Order.BUY, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, qty, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                                                Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                                                String response = transactionService.callAPI(request);
                                                System.out.println("buy response: " + response);
                                                OrderResponseDTO orderResponseDTO = new Gson().fromJson(response, OrderResponseDTO.class);
                                                if (orderResponseDTO.getS().equals("ok")) {
                                                    orbTradeDataEntity.setEntryOrderId(orderResponseDTO.getId());
                                                    orbTradeDataEntity.isOrderPlaced = true;
                                                    orbTradeDataEntity.setEntryType("BUY");
                                                } else {
                                                    orbTradeDataEntity.isErrored = true;
                                                }
                                            }
                                        }
                                        if (lastTradedPrice.compareTo(orbTradeDataEntity.getLowPrice()) < 0) {
                                            orbTradeDataEntity.setSellPrice(lastTradedPrice);
                                            Integer qty = orbTradeDataEntity.getAmountPerStock().divide(orbTradeDataEntity.getLowPrice(), 1).intValue();
                                            if (qty > 0) {
                                                try {
                                                    String teleMessage = orbTradeDataEntity.getStockName() + ":Sell Qty:" + qty + ":Price:" + lastTradedPrice;
                                                    sendMessage.sendToTelegram(teleMessage, telegramToken);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO(orbTradeDataEntity.getStockName(), Order.SELL, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, qty, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
                                                Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
                                                String response = transactionService.callAPI(request);
                                                System.out.println("buy response: " + response);
                                                OrderResponseDTO orderResponseDTO = new Gson().fromJson(response, OrderResponseDTO.class);
                                                if (orderResponseDTO.getS().equals("ok")) {
                                                    orbTradeDataEntity.setEntryOrderId(orderResponseDTO.getId());
                                                    orbTradeDataEntity.isOrderPlaced = true;
                                                    orbTradeDataEntity.setEntryType("SELL");
                                                } else {
                                                    orbTradeDataEntity.isErrored = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                   // }
                });
                System.out.println("Connected to Realtime WS endpoint: "+ truedataURL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return session;
    }
  //  @Scheduled(cron="${truedata.websocket.scheduler.close}")
    public void destory(){
        close();
    }
    @PreDestroy
    private void close(){
        try{
            if(session!=null && session.isOpen()) {
                session.close();
                System.out.println("Realtime Connection closed: " + truedataURL);
            }
            truedataURL = null;
            session=null;
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public RealTimeSubscribeRequestDTO prepareRealtimeQuoteInput(List<Map.Entry<String, Double> > list, String... a) {
            List<String> stockList = new ArrayList<>();

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

}*/
