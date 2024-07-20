package com.sakthi.trade.service;

import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.ZippingDirectory;
import com.sakthi.trade.zerodha.ExpiryDayDetails;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.Tick;
import com.zerodhatech.ticker.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ZerodhaWebsocket {
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    public static final Logger LOGGER = LoggerFactory.getLogger(ZerodhaWebsocket.class.getName());
    KiteTicker tickerProvider;
    ArrayList<Long> listOfTokens = new ArrayList<>();
    @Value("${websocket.enabled:false}")
    boolean websocketEnabled;
    @Autowired
    ZippingDirectory zippingDirectory;
    @Autowired
    TelegramMessenger telegramClient;
    @Value("${data.export}")
    boolean dataExport;
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Autowired
    ExpiryDayDetails expiryDayDetails;
    Gson gson = new Gson();
    @Autowired
    public UserList userList;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    @Autowired
    CommonUtil commonUtil;
    @Autowired
    public TransactionService transactionService;
    @Scheduled(cron = "${tradeEngine.websocket.initialize}")
    public void tickerInitialize() throws KiteException {
        try {
            if (websocketEnabled) {
                User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
                KiteConnect kiteConnect = user.getKiteConnect();
                tickerProvider = new KiteTicker(kiteConnect.getAccessToken(), kiteConnect.getApiKey());
                addIndexOptionstoWebsocket();
                tickerProvider.setTryReconnection(true);
                tickerProvider.setMaximumRetries(10);
                tickerProvider.setMaximumRetryInterval(30);
                // listOfTokens.add(Long.parseLong("65611015"));
                listOfTokens.add(Long.parseLong("256265"));
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.connect();

                if (tickerProvider.isConnectionOpen()) {
                    System.out.println("Websocket Connected");
                    tickerProvider.subscribe(listOfTokens);
                    tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                    System.out.println("added token:" + gson.toJson(listOfTokens));
                }
                tickerProvider.setOnDisconnectedListener(new OnDisconnect() {
                    @Override
                    public void onDisconnected() {
                        LOGGER.error("websocket disconnected");
                        tradeSedaQueue.sendTelemgramSeda("Websocket disconnected", "exp-trade");
                    }
                });
                tickerProvider.setOnConnectedListener(() -> {
                    System.out.println("adding token on connected:" + gson.toJson(listOfTokens));
                    tickerProvider.subscribe(listOfTokens);
                    tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                    System.out.println("added token:" + gson.toJson(listOfTokens));
                });
                /** Set listener to get order updates.*/
                tickerProvider.setOnOrderUpdateListener(new OnOrderUpdate() {
                    @Override
                    public void onOrderUpdate(Order order) {
                        //    System.out.println("trade engine order update " + order.orderId+":"+order.status+":"+order.symbol+":"+order.orderType+":"+order.product+":"+order.quantity);
                        tradeSedaQueue.sendWebsocketOrderUpdateSeda(gson.toJson(order));
                    }
                });

                /** Set error listener to listen to errors.*/
                tickerProvider.setOnErrorListener(new OnError() {
                    @Override
                    public void onError(Exception exception) {
                        //handle here.
                        LOGGER.info("websocket exception: {}", exception.getMessage());
                        tradeSedaQueue.sendTelemgramSeda("Websocket error: " + exception.getMessage(), "exp-trade");
                    }

                    @Override
                    public void onError(KiteException kiteException) {
                        LOGGER.info("websocket kite exception: {}", kiteException.getMessage());
                        tradeSedaQueue.sendTelemgramSeda("Websocket error: " + kiteException.getMessage(), "exp-trade");
                    }

                    @Override
                    public void onError(String error) {
                        LOGGER.info("websocket error: {}", error);
                        tradeSedaQueue.sendTelemgramSeda("Websocket error: " + error, "exp-trade");
                    }
                });

                tickerProvider.setOnTickerArrivalListener(new OnTicks() {
                    @Override
                    public void onTicks(ArrayList<Tick> ticks) {
                        try {
                            //     System.out.println("ticks size " + ticks.size());
                            // if (!ticks.isEmpty()) {
                            tradeSedaQueue.sendWebsocketTicksSeda(gson.toJson(ticks));
                            //   }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e) {

            e.printStackTrace();
            LOGGER.info("error:" + e.getMessage());
        }

    }

    CSVWriter tradeStrikeWriter;

    public void addStriketoWebsocket(Long token) throws KiteException {
        try {
            if (tickerProvider != null && tickerProvider.isConnectionOpen()) {
                listOfTokens.add(token);
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);

            } else {
                tickerInitialize();
                if (tickerProvider.isConnectionOpen()) {
                    listOfTokens.add(token);
                    tickerProvider.subscribe(listOfTokens);
                    tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                    //LOGGER.info("added token:" + token);
                }
            }
            LOGGER.info("added token:" + token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addTradeStriketoWebsocket(Long token, String stockName, String index) throws KiteException {
        try {
            try {
                String[] dataHeader = {String.valueOf(token), stockName, "", index};
                tradeStrikeWriter.writeNext(dataHeader);
                tradeStrikeWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (tickerProvider != null && tickerProvider.isConnectionOpen()) {
                listOfTokens.add(token);
                tickerProvider.subscribe(listOfTokens);
                tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                // LOGGER.info("added token:" + token);
            } else {
                tickerInitialize();
                if (tickerProvider.isConnectionOpen()) {
                    listOfTokens.add(token);
                    tickerProvider.subscribe(listOfTokens);
                    tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                    //  LOGGER.info("added token:" + token);
                }
            }
            LOGGER.info("added token:" + token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @PostConstruct
    public void initializetradeStrike() throws IOException {
        Date date = new Date();
        tradeStrikeWriter = new CSVWriter(new FileWriter("/home/ubuntu/trade_instrument_" + dateFormat.format(date) + ".csv", true));
        try {
            String[] dataHeader = {"instrument_token", "strike_name", "expiry", "index"};
            tradeStrikeWriter.writeNext(dataHeader);
            tradeStrikeWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Scheduled(cron = "${tradeEngine.websocket.addStrike}")
    public void addIndexOptionstoWebsocket() throws IOException {
        if (websocketEnabled) {
            Date date = new Date();
            CSVWriter csvWriter = new CSVWriter(new FileWriter("/home/ubuntu/instrument_" + dateFormat.format(date) + ".csv", true));
            try {
                String[] dataHeader = {"instrument_token", "strike_name", "expiry", "index"};
                csvWriter.writeNext(dataHeader);
                csvWriter.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                try {
                    String stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                    LOGGER.info("NIFTY BANK:{}", stockId);
                    listOfTokens.add(Long.parseLong(stockId));
                    expiryDayDetails.indexIdList.put(Long.parseLong(stockId), "BNF");
                    int bnfAtm = getAtm(stockId, "BNF");
                    int bnfAtmLow = bnfAtm - 1000;
                    int bnfAtmHigh = bnfAtm + 1000;
                    while (bnfAtmLow < bnfAtmHigh) {
                        Map<String, String> strikes = zerodhaTransactionService.bankNiftyWeeklyOptions.get(String.valueOf(bnfAtmLow));
                        bnfAtmLow = bnfAtmLow + 100;
                        strikes.forEach((key, value) -> {
                            listOfTokens.add(Long.parseLong(value));
                            try {
                                String[] dataHeader = {value, key, zerodhaTransactionService.bankBiftyExpDate, "BNF"};
                                csvWriter.writeNext(dataHeader);
                                csvWriter.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    String nstockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                    LOGGER.info("NIFTY :{}", nstockId);
                    listOfTokens.add(Long.parseLong(nstockId));
                    expiryDayDetails.indexIdList.put(Long.parseLong(nstockId), "NF");
                    int niftyAtm = getAtm(nstockId, "NF");
                    int niftyAtmLow = niftyAtm - 400;
                    int niftyAtmHigh = niftyAtm + 400;
                    while (niftyAtmLow < niftyAtmHigh) {
                        Map<String, String> strikes = zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(niftyAtmLow));
                        niftyAtmLow = niftyAtmLow + 50;
                        strikes.forEach((key, value) -> {
                            listOfTokens.add(Long.parseLong(value));
                            try {
                                String[] dataHeader = {value, key, zerodhaTransactionService.expDate, "NF"};
                                csvWriter.writeNext(dataHeader);
                                csvWriter.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    String nstockId = zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
                    listOfTokens.add(Long.parseLong(nstockId));
                    LOGGER.info("NIFTY MID SELECT :{}", nstockId);
                    expiryDayDetails.indexIdList.put(Long.parseLong(nstockId), "MC");
                    int niftyAtm = getAtm(nstockId, "MC");
                    int niftyAtmLow = niftyAtm - 200;
                    int niftyAtmHigh = niftyAtm + 200;
                    while (niftyAtmLow < niftyAtmHigh) {
                        Map<String, String> strikes = zerodhaTransactionService.midcpWeeklyOptions.get(String.valueOf(niftyAtmLow));
                        niftyAtmLow = niftyAtmLow + 25;
                        strikes.forEach((key, value) -> {
                            listOfTokens.add(Long.parseLong(value));
                            try {
                                String[] dataHeader = {value, key, zerodhaTransactionService.midCpExpDate, "MC"};
                                csvWriter.writeNext(dataHeader);
                                csvWriter.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    String nstockId = zerodhaTransactionService.niftyIndics.get("SENSEX");
                    LOGGER.info("SENSEX :{}", nstockId);
                    listOfTokens.add(Long.parseLong(nstockId));
                    expiryDayDetails.indexIdList.put(Long.parseLong(nstockId), "SS");
                    int niftyAtm = getAtm(nstockId, "SS");
                    int niftyAtmLow = niftyAtm - 1000;
                    int niftyAtmHigh = niftyAtm + 1000;
                    while (niftyAtmLow < niftyAtmHigh) {
                        Map<String, String> strikes = zerodhaTransactionService.sensexWeeklyOptions.get(String.valueOf(niftyAtmLow));
                        niftyAtmLow = niftyAtmLow + 100;
                        strikes.forEach((key, value) -> {
                            listOfTokens.add(Long.parseLong(value));
                            try {
                                String[] dataHeader = {value, key, zerodhaTransactionService.sensexExpDate, "SS"};
                                csvWriter.writeNext(dataHeader);
                                csvWriter.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    String nstockId = zerodhaTransactionService.niftyIndics.get("BANKEX");
                    LOGGER.info("BANKEX :{}", nstockId);
                    listOfTokens.add(Long.parseLong(nstockId));
                    expiryDayDetails.indexIdList.put(Long.parseLong(nstockId), "BNX");
                    int niftyAtm = getAtm(nstockId, "BNX");
                    int niftyAtmLow = niftyAtm - 2000;
                    int niftyAtmHigh = niftyAtm + 2000;
                    while (niftyAtmLow < niftyAtmHigh) {
                        Map<String, String> strikes = zerodhaTransactionService.bankExWeeklyOptions.get(String.valueOf(niftyAtmLow));
                        niftyAtmLow = niftyAtmLow + 100;
                        strikes.forEach((key, value) -> {
                            listOfTokens.add(Long.parseLong(value));
                            try {
                                String[] dataHeader = {value, key, zerodhaTransactionService.bankExDate, "BNX"};
                                csvWriter.writeNext(dataHeader);
                                csvWriter.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    String fnstockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                    LOGGER.info("NIFTY FIN SERVICE :{}", fnstockId);
                    expiryDayDetails.indexIdList.put(Long.parseLong(fnstockId), "FN");
                    listOfTokens.add(Long.parseLong(fnstockId));
                    int finAtm = getAtm(fnstockId, "FN");
                    int finAtmLow = finAtm - 400;
                    int finAtmHigh = finAtm + 400;
                    while (finAtmLow < finAtmHigh) {
                        Map<String, String> strikes = zerodhaTransactionService.finNiftyWeeklyOptions.get(String.valueOf(finAtmLow));
                        strikes.forEach((key, value) -> {
                            listOfTokens.add(Long.parseLong(value));
                            try {
                                String[] dataHeader = {value, key, zerodhaTransactionService.finExpDate, "FN"};
                                csvWriter.writeNext(dataHeader);
                                csvWriter.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        finAtmLow = finAtmLow + 50;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (tickerProvider.isConnectionOpen()) {
                        tickerProvider.subscribe(listOfTokens);
                        tickerProvider.setMode(listOfTokens, KiteTicker.modeFull);
                        System.out.println("added token:" + gson.toJson(listOfTokens));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public int getAtm(String strikeId, String index) {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        Date date1 = cal.getTime();
        //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = dateFormat.format(date);
        String fromDateStr = dateFormat.format(date1);
        String historicURL = "https://api.kite.trade/instruments/historical/" + strikeId + "/day?from=" + fromDateStr + "+00:00:00&to=" + currentDateStr + "+00:00:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        HistoricalData historicalDataRes = new HistoricalData();
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalDataRes.parseResponse(json);
            Optional<HistoricalData> optionalHistoricalLatestData = Optional.ofNullable(historicalDataRes.dataArrayList.get(historicalDataRes.dataArrayList.size() - 1));
            if (optionalHistoricalLatestData.isPresent()) {
                HistoricalData historicalData = optionalHistoricalLatestData.get();
                return commonUtil.findATM((int) historicalData.close, index);
            }
        }
        return 0;
    }

    @Scheduled(cron = "${tradeEngine.websocket.tick.export}")
    public void tickExport() throws IOException {
        if (websocketEnabled) {
            if (dataExport) {
                Date date = new Date();
                zippingDirectory.zipFile("tick_" + dateFormat.format(date) + ".csv", "tick_" + dateFormat.format(date), "/home/ubuntu", "instrument_" + dateFormat.format(date) + ".csv", "trade_instrument_" + dateFormat.format(date) + ".csv");
                telegramClient.sendDocumentToTelegram("/home/ubuntu/tick_" + dateFormat.format(date) + ".zip", "Tick_" + dateFormat.format(date));
                FileUtils.delete(new File("/home/ubuntu/tick_" + dateFormat.format(date) + ".zip"));
                // FileUtils.delete(new File("/home/ubuntu/tick_" + dateFormat.format(date) + ".csv"));
                // FileUtils.delete(new File("/home/ubuntu/instrument_" + dateFormat.format(date) + ".csv"));
                //FileUtils.delete(new File("/home/ubuntu/trade_instrument_" + dateFormat.format(date) + ".csv"));
            }
        }
    }
}
