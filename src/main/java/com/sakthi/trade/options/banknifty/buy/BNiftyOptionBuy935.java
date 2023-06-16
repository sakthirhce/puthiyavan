package com.sakthi.trade.options.banknifty.buy;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class BNiftyOptionBuy935 {
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    public Map<String, String> atmStrikeMap = new HashMap<>();
    @Autowired
    CommonUtil commonUtil;
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;
    @Autowired
    TransactionService transactionService;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    TelegramMessenger sendMessage;

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    UserList userList;
    List<OpenTradeDataEntity> openTradeDataEntities = new ArrayList<>();
    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;
    Gson gson = new Gson();
    @Autowired
    MathUtils mathUtils;
    @Autowired
    BrokerWorkerFactory workerFactory;
    ExecutorService executorService1 = java.util.concurrent.Executors.newFixedThreadPool(1);
    public String getAlgoName() {
        return "BNIFTY_BUY_935";
    }
    public static final Logger LOGGER = LoggerFactory.getLogger(BNiftyOptionBuy935.class);
    //@Scheduled(cron = "${bniftyBuy935.schedule.entry}")
    public void buy() throws ParseException, KiteException, IOException {
        sendMessage.sendToTelegram("bniftyBuy935 execution startede", telegramToken);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Date date = new Date();
        String currentDate = format.format(date);
        Map<String, StrikeData> rangeSelected;
        rangeSelected=mathUtils.getPriceRangeSortedWithLowRange(currentDate,450,350,"09:34:00","BNF");

        rangeSelected.forEach((key, value) -> {
            try {
                sendMessage.sendToTelegram("RANGE STRIKE:"+key+":"+value, telegramToken);
            }catch (Exception e){
                log.error("error:"+e);
            }
        });
        try {
                    rangeSelected.entrySet().stream().forEach(atmNiftyStrikeMap -> {

                                AtomicDouble triggerPriceAtomic = new AtomicDouble();
                                AtomicDouble closePriceAtomic = new AtomicDouble();
                                try {
                                    String historicPriceURL = "https://api.kite.trade/instruments/historical/" + atmNiftyStrikeMap.getValue().getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                    String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicPriceURL));
                                    HistoricalData historicalPriceData = new HistoricalData();
                                    JSONObject priceJson = new JSONObject(priceResponse);
                                    String responseStatus = priceJson.getString("status");

                                    if (!responseStatus.equals("error")) {
                                        historicalPriceData.parseResponse(priceJson);
                                        historicalPriceData.dataArrayList.forEach(historicalDataPrice -> {
                                            try {
                                                Date priceDatetime = sdf.parse(historicalDataPrice.timeStamp);
                                                String priceDate = format.format(priceDatetime);
                                                if (sdf.format(priceDatetime).equals(priceDate + "T09:34:00")) {
                                                    LOGGER.info("DATA:" + historicalDataPrice.timeStamp + ":" + atmNiftyStrikeMap.getKey() + ":" + historicalDataPrice.timeStamp);
                                                    closePriceAtomic.getAndSet(historicalDataPrice.close);
                                                    BigDecimal triggerPriceTemp = (MathUtils.percentageValueOfAmount(new BigDecimal(15), new BigDecimal(historicalDataPrice.close)).add(new BigDecimal(historicalDataPrice.close))).setScale(0, RoundingMode.HALF_UP);
                                                    triggerPriceAtomic.getAndSet(triggerPriceTemp.doubleValue());
                                                }
                                            } catch (ParseException e) {
                                                LOGGER.info("error:"+atmNiftyStrikeMap.getKey()+" exception :"+e.getMessage()+" element:"+gson.toJson(historicalDataPrice));
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    LOGGER.info("error:"+atmNiftyStrikeMap.getKey()+":"+e.getMessage());
                                }
                                LOGGER.info(atmNiftyStrikeMap.getKey());
                                OrderParams orderParams = new OrderParams();
                                orderParams.tradingsymbol = atmNiftyStrikeMap.getValue().getZerodhaSymbol();
                                orderParams.exchange = "NFO";
                                orderParams.orderType = "SL";
                                orderParams.product = "NRML";
                                orderParams.transactionType = "BUY";
                                orderParams.validity = "DAY";
                                double triggerPrice = triggerPriceAtomic.get();
                                orderParams.triggerPrice = triggerPrice;
                                BigDecimal price = BigDecimal.valueOf(triggerPrice).setScale(0, RoundingMode.HALF_UP).add(BigDecimal.valueOf(triggerPrice).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                                orderParams.price = price.doubleValue();
                                LocalDate localDate = LocalDate.now();
                                DayOfWeek dow = localDate.getDayOfWeek();
                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                String todayCaps = today.toUpperCase();
                                userList.getUser().stream().filter(
                                        user -> user.getBniftyBuy935() != null && user.getBniftyBuy935().isNrmlEnabled() && user.getBniftyBuy935().getLotConfig().containsKey(todayCaps)
                                ).forEach(user -> {
                                    BrokerWorker brokerWorker = workerFactory.getWorker(user);
                                    AtomicInteger qty = new AtomicInteger(1);
                                    user.getBniftyBuy935().getLotConfig().entrySet().forEach(day -> {
                                        String lotValue = day.getKey();
                                        if (lotValue.contains(todayCaps)) {
                                            int value = Integer.parseInt(day.getValue());
                                            qty.getAndSet(value);
                                        }
                                    });
                                    if (user.getBniftyBuy935().getLotConfig() != null && user.getBniftyBuy935().buyConfig.isEnabled()) {
                                        user.getBniftyBuy935().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                            if (lotValue.contains(todayCaps)) {
                                                int value = (Integer.parseInt(value1));
                                                qty.getAndAdd(value);
                                            }});
                                    }
                                    Order order = null;
                                    orderParams.quantity = 25 * qty.get();
                                    TradeData tradeData = new TradeData();
                                    String dataKey = UUID.randomUUID().toString();
                                    tradeData.setDataKey(dataKey);
                                    tradeData.setStockName(atmNiftyStrikeMap.getValue().getZerodhaSymbol());
                                    tradeData.setStrikeId(atmNiftyStrikeMap.getValue().getDhanId());
                                    try {
                                        order = brokerWorker.placeOrder(orderParams,user, tradeData);

                                        tradeData.setEntryOrderId(order.orderId);
                                        tradeData.isOrderPlaced = true;
                                        tradeData.setQty(25 * qty.get());
                                        tradeData.setEntryType("BUY");
                                        tradeData.setUserId(user.getName());
                                        tradeData.setStockId(Integer.parseInt(atmNiftyStrikeMap.getValue().getZerodhaId()));
                                        tradeData.setBuyPrice(BigDecimal.valueOf(triggerPriceAtomic.get()));
                                        user.getBniftyBuy935().straddleTradeMap.put(atmNiftyStrikeMap.getKey(), tradeData);
                                        //mapTradeDataToSaveOpenTradeDataEntity(tradeData,true);
                                        String message="option buy limit order placed for for user:" + user.getName() + " strike: " + atmNiftyStrikeMap.getKey()+":"+getAlgoName();
                                        try {
                                            sendMessage.sendToTelegram(message, telegramToken);
                                        }catch (Exception e){
                                            log.error("error:"+e);
                                        }
                                    } catch (KiteException e) {
                                        tradeData.isErrored = true;
                                        LOGGER.info("Error while placing bnf nifty buy order: "+ atmNiftyStrikeMap.getKey()+":" + e.message+":"+e.code+":"+getAlgoName());
                                        sendMessage.sendToTelegram("Error while placing nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + user.getName() + ",Exception:" + e.message+":"+getAlgoName()+" Input:"+new Gson().toJson(orderParams), telegramToken);

                                    } catch (IOException e) {
                                        tradeData.isErrored = true;
                                        LOGGER.info("Error while placing bnf nifty buy order: "+ atmNiftyStrikeMap.getKey()+":" + e.getMessage());
                                        if (order != null) {
                                            sendMessage.sendToTelegram("Error while placing bnf nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ",Exception:" + e.getMessage()+":"+getAlgoName(), telegramToken);
                                        } else {
                                            sendMessage.sendToTelegram("Error while placing bnf nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + user.getName() + ",Exception:" + e.getMessage()+":"+getAlgoName(), telegramToken);
                                        }
                                    }
                                });
                            });

                  ///  }
                } catch (Exception e) {
                    e.printStackTrace();
                }
           // });
        //}
    }

   // @Scheduled(cron = "${bniftyBuy935.schedule.slMonitor}")
    public void sLMonitorScheduler() {
        //  LOGGER.info("short straddle SLMonitor scheduler started");

        userList.getUser().stream().filter(user -> user.getBniftyBuy935() != null && user.getBniftyBuy935().isNrmlEnabled()).forEach(user -> {
            BrokerWorker brokerWorker= workerFactory.getWorker(user);
            if (user.getBniftyBuy935().straddleTradeMap != null) {
                user.getBniftyBuy935().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null)
                        .forEach(map -> {
                            try {
                                TradeData trendTradeData = map.getValue();
                                List<Order> orderList = null;
                                try {
                                    orderList = brokerWorker.getOrders(user);
                                } catch (KiteException | IOException e) {
                                    e.printStackTrace();
                                }
                                orderList.forEach(order -> {
                                    if (!trendTradeData.isErrored && !trendTradeData.isSLCancelled && !trendTradeData.isExited && !trendTradeData.isSLHit) {
                                        if (order.orderId.equals(trendTradeData.getSlOrderId()) && trendTradeData.isSlPlaced) {
                                            if ("CANCELLED".equals(order.status)) {
                                                trendTradeData.isSLCancelled = true;

                                                String message = MessageFormat.format("Broker Cancelled SL Order for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                                LOGGER.info(message);
                                                try {
                                                    sendMessage.sendToTelegram(message, telegramToken);
                                                } catch (Exception e) {
                                                    log.error("error:" + e);
                                                }


                                            }
                                        }
                                        if (trendTradeData.getEntryOrderId().equals(order.orderId) && !trendTradeData.isSlPlaced) {
                                            if ("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) {

                                                if ("BUY".equals(order.transactionType)) {
                                                    LOGGER.info("935 buy completed" + trendTradeData.trueDataSymbol + ":" + trendTradeData.getEntryOrderId());
                                                    try {
                                                        //   LOGGER.info("buy completed");
                                                        trendTradeData.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                                                        try {
                                                            BigDecimal slipage = (trendTradeData.getBuyPrice().subtract(trendTradeData.getBuyTradedPrice())).multiply(new BigDecimal(50)).setScale(0, RoundingMode.UP);
                                                            String message = MessageFormat.format("Option Buy Triggered for {0}", trendTradeData.getStockName() + ":" + user.getName() + ":" + getAlgoName());
                                                            LOGGER.info(message);
                                                            sendMessage.sendToTelegram(message, telegramToken);
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                        }
                                                        mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,true);

                                                        //LOGGER.info("inside options stop sell");

                                                        //BigDecimal triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(new BigDecimal(10))).setScale(0, RoundingMode.HALF_UP);
                                                        BigDecimal triggerPriceTemp = (trendTradeData.getBuyPrice().subtract(MathUtils.percentageValueOfAmount(new BigDecimal(15), trendTradeData.getBuyPrice()))).setScale(0, RoundingMode.HALF_UP);
                                                        //    if (qty.get() > 0 && user.getBniftyBuy935().getBuyConfig() != null && user.getBniftyBuy935().getBuyConfig().isEnabled()) {
                                                        LOGGER.info("buy price:" + trendTradeData.getBuyPrice().doubleValue() + " sl price: " + triggerPriceTemp.doubleValue());
                                                        OrderParams orderParams = new OrderParams();
                                                        orderParams.tradingsymbol = trendTradeData.getStockName();
                                                        orderParams.exchange = "NFO";
                                                        orderParams.quantity = trendTradeData.getQty();
                                                        orderParams.triggerPrice = triggerPriceTemp.doubleValue();

                                                        BigDecimal price = triggerPriceTemp.subtract(triggerPriceTemp.divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                                                        orderParams.price = price.doubleValue();
                                                        orderParams.orderType = "SL";
                                                        orderParams.product = "NRML";
                                                        orderParams.transactionType = "SELL";
                                                        orderParams.validity = "DAY";
                                                        Order orderd;

                                                        try {
                                                            LOGGER.info("input:" + gson.toJson(orderParams));
                                                            orderd = brokerWorker.placeOrder(orderParams, user,trendTradeData);
                                                            trendTradeData.isSlPlaced = true;
                                                            trendTradeData.setSlPrice(triggerPriceTemp);
                                                            trendTradeData.setSlOrderId(orderd.orderId);
                                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,false);
                                                            try {
                                                                LOGGER.info("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL");
                                                                sendMessage.sendToTelegram("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL" + ":" + getAlgoName(), telegramToken);
                                                            } catch (Exception e) {
                                                                LOGGER.info("error:" + e);
                                                            }
                                                            ///sendMessage.sendToTelegram("Nifty option : " + trendTradeData.getStockName() + ":" + user.getName() + " bought and placed SL", telegramToken);

                                                        } catch (KiteException | IOException e) {
                                                            // tradeData.isErrored = true;
                                                            LOGGER.info("Nifty option order: " + e.getMessage() + ":" + user.getName());
                                                            sendMessage.sendToTelegram("Nifty option order: " + trendTradeData.getStockName() + ":" + user.getName() + ": Status: " + order.status + ": error message:" + order.statusMessage + ":" + getAlgoName(), telegramToken);
                                                            //e.printStackTrace();
                                                        }

                                                    } catch (Exception e) {
                                                        LOGGER.info("error while placing sl:" + e.getMessage() + trendTradeData.getEntryOrderId() + ":" + trendTradeData.getStockName());
                                                    }
                                                }
                                            }
                                        }
                                        if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && "SELL".equals(order.transactionType) && order.orderId.equals(trendTradeData.getSlOrderId()) && !trendTradeData.isExited && trendTradeData.isSlPlaced) {
                                            trendTradeData.isSLHit = true;
                                            trendTradeData.isExited = true;
                                            trendTradeData.setExitOrderId(order.orderId);
                                            trendTradeData.setSellPrice(trendTradeData.getSlPrice());
                                            trendTradeData.setSellTradedPrice(new BigDecimal(order.averagePrice));
                                            BigDecimal slipage = (trendTradeData.getSellTradedPrice().subtract(trendTradeData.getSellPrice())).multiply(new BigDecimal(25)).setScale(0, RoundingMode.UP);
                                            String message = MessageFormat.format("SL Hit for {0}" + ": sl sell slipage" + slipage.toString(), map.getKey() + ":" + user.getName() + ":" + getAlgoName());
                                            LOGGER.info(message);
                                            sendMessage.sendToTelegram(message, telegramToken);
                                            mapTradeDataToSaveOpenTradeDataEntity(trendTradeData,false);
                                        }

                                    } else if ("REJECTED".equals(order.status) && !trendTradeData.isErrored && order.orderId.equals(trendTradeData.getSlOrderId())) {
                                        String message = MessageFormat.format("SL order placement rejected for {0}", map.getKey() + ":" + user.getName() + ":" + order.status + ":" + order.statusMessage + ":" + getAlgoName());
                                        LOGGER.info(message);
                                        trendTradeData.isErrored = true;
                                        sendMessage.sendToTelegram(message, telegramToken);
                                    }


                                });
                            }catch (Exception e){
                                LOGGER.info("error while processing sl monitor:"+e.getMessage());
                            }
                        });

            }
        });
    }
  //  @Scheduled(cron = "${banknifty.intraday.buy.exit.schedule}")
    public void buyIntradayExit() {
        userList.getUser().stream().filter(user -> user.getBniftyBuy935() != null && user.getBniftyBuy935().isNrmlEnabled()).forEach(user -> {
            try {
                BrokerWorker brokerWorker= workerFactory.getWorker(user);
                List<Order> orders = brokerWorker.getOrders(user);
                List<Position> positions = brokerWorker.getPositions(user);
                LOGGER.info(new Gson().toJson(positions));
                if (user.getBniftyBuy935().straddleTradeMap != null) {
                    user.getBniftyBuy935().straddleTradeMap.entrySet().stream().filter(map -> map.getValue().isOrderPlaced && map.getValue().getEntryOrderId() != null && !map.getValue().isExited).forEach(straddleTradeMap -> {
                        TradeData tradeData = straddleTradeMap.getValue();
                        if (user.getName().equals(straddleTradeMap.getValue().getUserId())) {
                            if (user.getBniftyBuy935().getLotConfig() != null && user.getBniftyBuy935().buyConfig.isEnabled()) {
                                LocalDate localDate = LocalDate.now();
                                DayOfWeek dow = localDate.getDayOfWeek();
                                String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
                                String todayCaps = today.toUpperCase();
                                AtomicInteger qty = new AtomicInteger(0);
                                user.getBniftyBuy935().getBuyConfig().getLotConfig().forEach((lotValue, value1) -> {
                                    if (lotValue.contains(todayCaps)) {
                                        int value = (Integer.parseInt(value1) * 25);
                                        qty.getAndSet(value);
                                        orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(tradeData.getSlOrderId())).forEach(orderr -> {
                                            try {
                                                // if(straddleTradeMap.getKey().contains("BUY")) {
                                                OrderParams orderParams = new OrderParams();
                                                orderParams.quantity = Integer.parseInt(orderr.quantity) - qty.get();
                                                tradeData.setQty(orderParams.quantity);
                                                orderParams.tradingsymbol = tradeData.getStockName();
                                                orderParams.exchange = "NFO";
                                                orderParams.orderType =orderr.orderType;
                                                orderParams.validity = "DAY";
                                                orderParams.price = Double.parseDouble(orderr.price);
                                                orderParams.triggerPrice = Double.parseDouble(orderr.triggerPrice);
                                                orderParams.orderType = "SL";
                                                orderParams.product = "NRML";
                                                System.out.println("input for bnf sl mod: "+new Gson().toJson(orderParams));
                                                Order order = brokerWorker.modifyOrder(tradeData.getSlOrderId(), orderParams, user,tradeData);
                                                sendMessage.sendToTelegram("sl buy qty modified for nrml:" + tradeData.getUserId() + ": new sl qty:" + tradeData.getQty()+":"+getAlgoName(), telegramToken);
                                                //}
                                            } catch (Exception e) {
                                                LOGGER.info("exception:"+ e.getMessage());
                                            }
                                        });
                                        positions.stream().filter(position -> ("NRML".equals(position.product)||"MARGIN".equals(position.product)) && tradeData.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).forEach(position -> {
                                            //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                                            OrderParams orderParams = new OrderParams();
                                            orderParams.tradingsymbol = position.tradingSymbol;
                                            orderParams.exchange = "NFO";
                                            orderParams.quantity = qty.get();
                                            orderParams.orderType = "MARKET";
                                            orderParams.product = "NRML";
                                            //orderParams.price=price.doubleValue();
                                            if (position.netQuantity > 0) {
                                                orderParams.transactionType = "SELL";
                                                orderParams.validity = "DAY";
                                                com.zerodhatech.models.Order orderResponse = null;
                                                try {
                                                    orderResponse = brokerWorker.placeOrder(orderParams, user,tradeData);
                                                    LOGGER.info(new Gson().toJson(orderResponse));
                                                    //  openTradeDataEntity.isExited=true;
                                                    tradeData.setQty(qty.get());
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeData,false);
                                                    String message = MessageFormat.format("Closed Intraday Buy Position {0}", orderParams.tradingsymbol);
                                                    LOGGER.info(message);
                                                    TradeData tradeDataBuy=gson.fromJson(gson.toJson(tradeData),TradeData.class);
                                                    String dataKey = UUID.randomUUID().toString();
                                                    tradeDataBuy.setDataKey(dataKey);
                                                    tradeDataBuy.isExited=true;
                                                    tradeDataBuy.setExitOrderId(orderResponse.orderId);
                                                    mapTradeDataToSaveOpenTradeDataEntity(tradeDataBuy,true);
                                                    user.getBniftyBuy935().straddleTradeMap.put(tradeDataBuy.getStockName() + "-BUY-INTRADAY", tradeDataBuy);
                                                    sendMessage.sendToTelegram(message + ":" + tradeData.getUserId()+":"+getAlgoName(), telegramToken);

                                                } catch (KiteException e) {
                                                    LOGGER.info("Error while exiting straddle order: " + e.message);
                                                    sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position)+":"+getAlgoName(), telegramToken);
                                                    e.printStackTrace();
                                                } catch (IOException e) {
                                                    LOGGER.info("Error while exiting straddle order: " + e.getMessage());

                                                    sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position)+":"+getAlgoName(), telegramToken);
                                                    e.printStackTrace();
                                                }
                                            }
                                            //  }
                                        });
                                    }
                                });


                            }

                        }
                    });
                }

            } catch (Exception | KiteException e) {
                LOGGER.info(e.getMessage());
            }
        });
    }
    public List<OpenTradeDataEntity> openTradeDataEntities1 = new ArrayList<>();
   // @Scheduled(cron = "${bniftyBuy935.schedule.nextday.load}")
    public void loadNrmlPositions() {
        openTradeDataEntities1 = openTradeDataRepo.findAll();
        openTradeDataEntities1.forEach(openTradeDataEntity -> {
            if (openTradeDataEntity.getAlgoName().equals(this.getAlgoName())) {
                if (!openTradeDataEntity.isExited && !openTradeDataEntity.isErrored) {
                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    BrokerWorker brokerWorker= workerFactory.getWorker(user);
                    List<Position> positions = brokerWorker.getPositions(user);
                    positions.stream().filter(position -> ("NRML".equals(position.product)||"MARGIN".equals(position.product)) && openTradeDataEntity.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).findFirst().ifPresent(position -> {
                        int positionQty = Math.abs(position.netQuantity);
                        if (positionQty != openTradeDataEntity.getQty()) {
                            //   openTradeDataEntity.setQty(positionQty);

                            LOGGER.info("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ", over riding position qty as trade qty.");
                            sendMessage.sendToTelegram("Position qty mismatch for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId() + ",over riding position qty as trade qty."+":"+getAlgoName(), telegramToken);

                        }
                        openTradeDataEntity.isSlPlaced = false;
                        openTradeDataEntity.setSlOrderId(null);
                        openTradeDataEntities.add(openTradeDataEntity);
                        sendMessage.sendToTelegram("Open Position: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);


                    });
                } else {
                    String openStr = gson.toJson(openTradeDataEntity);
                    OpenTradeDataBackupEntity openTradeDataBackupEntity = gson.fromJson(openStr, OpenTradeDataBackupEntity.class);
                    openTradeDataBackupRepo.save(openTradeDataBackupEntity);
                    openTradeDataRepo.deleteById(openTradeDataEntity.getDataKey());
                }
            }
        });
    }

   // @Scheduled(cron = "${bniftyBuy935.schedule.nextday.priceUpdate}")
    public void exitPriceNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getBniftyBuy935() != null && user.getBniftyBuy935().isNrmlEnabled()).forEach(user -> {
            try {
                BrokerWorker brokerWorker= workerFactory.getWorker(user);
                List<Order> orders = brokerWorker.getOrders(user);
                List<Position> positions = brokerWorker.getPositions(user);
               // LOGGER.info(positions);
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited && user.getName().equals(openTradeDataEntity.getUserId())).forEach(openTradeDataEntity -> {
                    orders.stream().filter(order -> ("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getExitOrderId())).findFirst().ifPresent(orderr -> {
                        try {
                            Date date = new Date();
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                            String currentDate = format.format(date);
                            try {


                                String historicURL = "https://api.kite.trade/instruments/historical/" + openTradeDataEntity.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+09:34:00";
                                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                //System.out.print(openTradeDataEntity.getStockName() + " history api response:" + response);
                                HistoricalData historicalData = new HistoricalData();
                                JSONObject json = new JSONObject(response);
                                String status = json.getString("status");
                                if (!status.equals("error")) {
                                    historicalData.parseResponse(json);
                                }
                                if (historicalData.dataArrayList.size() > 0) {
                                    historicalData.dataArrayList.forEach(historicalData1 -> {
                                        try {
                                            Date openDatetime = sdf.parse(historicalData1.timeStamp);
                                            String openDate = format.format(openDatetime);
                                            if (sdf.format(openDatetime).equals(openDate + "T09:33:00")) {
                                                BigDecimal triggerPriceTemp = ((new BigDecimal(historicalData1.close).divide(new BigDecimal(5))).add(new BigDecimal(historicalData1.close))).setScale(0, RoundingMode.HALF_UP);
                                                if ("SELL".equals(orderr.transactionType)) {
                                                    openTradeDataEntity.setSellPrice(new BigDecimal(historicalData1.close));
                                                } else {
                                                    openTradeDataEntity.setBuyPrice(new BigDecimal(orderr.averagePrice));
                                                }
                                                LOGGER.info("setting  9:34 exit :" + openTradeDataEntity.getStockId() + ":" + historicalData1.close);
                                            }
                                        } catch (ParseException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                }

                            } catch (Exception e) {
                                LOGGER.info(e.getMessage());
                            }
                            openTradeDataEntity.isExited = true;
                            if ("SELL".equals(orderr.transactionType)) {
                                openTradeDataEntity.setSellTradedPrice(new BigDecimal(orderr.averagePrice));
                            } else {
                                openTradeDataEntity.setBuyTradedPrice(new BigDecimal(orderr.averagePrice));
                            }
                            saveTradeData(openTradeDataEntity);
                        } catch (Exception e) {
                            LOGGER.info(e.getMessage());
                        }
                    });
                });
            } catch (Exception | KiteException e) {
                LOGGER.info(e.getMessage());
            }
        });
    }

   // @Scheduled(cron = "${bniftyBuy935.schedule.nextday.slPlace}")
    public void placeSLNrmlPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            try {
                OrderParams orderParams = new OrderParams();
                orderParams.tradingsymbol = openTradeDataEntity.getStockName();
                orderParams.exchange = "NFO";
                orderParams.quantity = openTradeDataEntity.getQty();
                orderParams.triggerPrice = openTradeDataEntity.getSlPrice().doubleValue();
                Date date = new Date();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                String currentDate = format.format(date);
                orderParams.orderType = "SL";
                orderParams.product = "NRML";
                HistoricalData historicalData = new HistoricalData();
                String status = "error";
                try {
                    String historicURL = "https://api.kite.trade/instruments/historical/" + openTradeDataEntity.getStockId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+11:15:00";
                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                    //System.out.print(openTradeDataEntity.getStockName() + " history api response:" + response);

                    JSONObject json = new JSONObject(response);
                    status = json.getString("status");
                    if (!status.equals("error")) {
                        historicalData.parseResponse(json);
                    }
                } catch (Exception e) {
                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage());
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage()+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling 9:16 historic api:" + e.getMessage()+":"+getAlgoName(), telegramToken);
                    }
                }

                if (openTradeDataEntity.getEntryType().equals("BUY")) {
                    orderParams.transactionType = "SELL";
                    if (historicalData.dataArrayList.size() > 0) {
                        HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);

                        if (!status.equals("error") && lastMin.close < openTradeDataEntity.getSlPrice().doubleValue()) {
                            LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":inside buy entry type  sl condition, close is buy than sl price:" + lastMin.close + ":" + openTradeDataEntity.getSlPrice().doubleValue());
                            //  openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                            orderParams.orderType = "MARKET";
                        }
                    }
                    BigDecimal price = openTradeDataEntity.getSlPrice().subtract(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                    orderParams.price = price.doubleValue();

                } else {
                    orderParams.transactionType = "BUY";
                    if (historicalData.dataArrayList.size() > 0) {
                        HistoricalData lastMin = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                        if (!status.equals("error") && lastMin.close > openTradeDataEntity.getSlPrice().doubleValue()) {
                            LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":inside sell entry type sl condition, close is greater than sl price:" + lastMin.close + ":" + openTradeDataEntity.getSlPrice().doubleValue());
                            // openTradeDataEntity.setSlPrice(new BigDecimal(lastMin.close));
                            orderParams.orderType = "MARKET";
                        }
                    }
                    BigDecimal price = openTradeDataEntity.getSlPrice().add(openTradeDataEntity.getSlPrice().divide(new BigDecimal(100)).multiply(new BigDecimal(5))).setScale(0, RoundingMode.HALF_UP);
                    orderParams.price = price.doubleValue();
//                    LOGGER.info(price.doubleValue());

                }

                orderParams.validity = "DAY";
                Order orderd;
                try {
                    LOGGER.info("inside order placement");

                    User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
                    BrokerWorker brokerWorker= workerFactory.getWorker(user);
                    LOGGER.info("order params" + new Gson().toJson(orderParams));
                    orderd = brokerWorker.placeOrder(orderParams,user,null);
                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":order response:" + new Gson().toJson(orderd));
                    openTradeDataEntity.isSlPlaced = true;
                    openTradeDataEntity.setSlOrderId(orderd.orderId);
                    openTradeDataRepo.save(openTradeDataEntity);
                    if (orderParams.transactionType.equals("SELL")) {
                        sendMessage.sendToTelegram("placed sl order for:" + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram("placed sl order for:" + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    }
                } catch (Exception e) {

                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage());
                    if (orderParams.transactionType.equals("SELL")) {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage()+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.getMessage()+":"+getAlgoName(), telegramToken);
                    }
                } catch (KiteException e) {
                    LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.message);
                    if (orderParams.transactionType.equals("SELL")) {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.message+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while calling zerodha 9:16 sl order" + e.message+":"+getAlgoName(), telegramToken);
                    }
                    // throw new RuntimeException(e);
                }
            } catch (Exception e) {
                LOGGER.info(openTradeDataEntity.getUserId() + ":" + openTradeDataEntity.getStockName() + ":error while placing 9:16 sl order" + e.getMessage());
            }
        });


    }


  //  @Scheduled(cron = "${bniftyBuy935.schedule.nextday.exit}")
    public void exitNrmlPositions() {
        userList.getUser().stream().filter(user -> user.getBniftyBuy935() != null && user.getBniftyBuy935().isNrmlEnabled()).forEach(user -> {
            try {
                BrokerWorker brokerWorker= workerFactory.getWorker(user);
                List<Order> orders = brokerWorker.getOrders(user);
                List<Position> positions = brokerWorker.getPositions(user);
                //LOGGER.info(positions);
                openTradeDataEntities.stream().filter(openTradeDataEntity -> !openTradeDataEntity.isExited && user.getName().equals(openTradeDataEntity.getUserId())).forEach(openTradeDataEntity -> {
                            orders.stream().filter(order -> ("OPEN".equals(order.status) || "TRIGGER PENDING".equals(order.status)) && order.orderId.equals(openTradeDataEntity.getSlOrderId())).forEach(orderr -> {
                                try {
                                    Order order = brokerWorker.cancelOrder(orderr.orderId, user);
                                } catch (KiteException | IOException e) {
                                    LOGGER.info(e.getMessage());
                                }
                            });
                            positions.stream().filter(position -> ("NRML".equals(position.product)||"MARGIN".equals(position.product)) && openTradeDataEntity.getStockName().equals(position.tradingSymbol) && (position.netQuantity != 0)).forEach(position -> {
                                //   if(straddleTradeMap.get(position.tradingSymbol)!=null) {
                                OrderParams orderParams = new OrderParams();
                                orderParams.tradingsymbol = position.tradingSymbol;
                                orderParams.exchange = "NFO";
                                orderParams.quantity = openTradeDataEntity.getQty();
                                orderParams.orderType = "MARKET";
                                orderParams.product = "NRML";
                                //orderParams.price=price.doubleValue();
                                if (position.netQuantity > 0) {
                                    orderParams.transactionType = "SELL";
                                } else {
                                    orderParams.transactionType = "BUY";
                                }
                                orderParams.validity = "DAY";
                                Order orderResponse;
                                try {
                                    orderResponse = brokerWorker.placeOrder(orderParams,user,null);
                                    LOGGER.info(new Gson().toJson(orderResponse));
                                    //  openTradeDataEntity.isExited=true;
                                    openTradeDataEntity.setExitOrderId(orderResponse.orderId);
                                    saveTradeData(openTradeDataEntity);
                                    String message = MessageFormat.format("Closed Position {0}", orderParams.tradingsymbol);
                                    LOGGER.info(message);
                                    if (orderParams.transactionType.equals("SELL")) {
                                        sendMessage.sendToTelegram(message + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                                    } else {
                                        sendMessage.sendToTelegram(message + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                                    }
                                } catch (KiteException e) {
                                    LOGGER.info("Error while exiting straddle order: " + e.message);
                                    if (orderParams.transactionType.equals("SELL")) {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position)+":"+getAlgoName(), telegramToken);
                                    } else {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.message + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position)+":"+getAlgoName(), telegramToken);
                                    }
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    LOGGER.info("Error while exiting straddle order: " + e.getMessage());
                                    if (orderParams.transactionType.equals("SELL")) {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position)+":"+getAlgoName(), telegramToken);
                                    } else {
                                        sendMessage.sendToTelegram("Error while exiting order: " + orderParams.tradingsymbol + ": Exception: " + e.getMessage() + " order Input:" + new Gson().toJson(orderParams) + " positions: " + new Gson().toJson(position)+":"+getAlgoName(), telegramToken);
                                    }
                                    e.printStackTrace();
                                }
                                //  }
                            });

                        }
                );
            } catch (KiteException | IOException e) {
                LOGGER.info(e.getMessage());
            }


        });
    }

  //  @Scheduled(cron = "${bniftyBuy935.schedule.nextday.slMonitor}")
    public void sLNrmlMonitorPositions() {
        openTradeDataEntities.stream().filter(openTradeDataEntity -> openTradeDataEntity.isSlPlaced && !openTradeDataEntity.isExited).forEach(openTradeDataEntity -> {
            User user = userList.getUser().stream().filter(user1 -> user1.getName().equals(openTradeDataEntity.getUserId())).findFirst().get();
            BrokerWorker brokerWorker= workerFactory.getWorker(user);
            List<Order> orderList = null;
            List<Position> positions = null;
            try {
                orderList = brokerWorker.getOrders(user);
                //   LOGGER.info("get trade response:"+new Gson().toJson(orderList));
            } catch (KiteException | IOException e) {
                e.printStackTrace();
            }

            Optional<Order> sLOrder = orderList.stream().filter(order1 -> openTradeDataEntity.getSlOrderId().equals(order1.orderId)).findFirst();
            if (sLOrder.isPresent()) {
                Order order = sLOrder.get();
                if (("COMPLETE".equals(order.status) || "TRADED".equals(order.status)) && !openTradeDataEntity.isExited) {
                    openTradeDataEntity.isExited = true;
                    openTradeDataEntity.isSLHit = true;
                    if ("SELL".equals(order.transactionType)) {
                        openTradeDataEntity.setSellPrice(openTradeDataEntity.getSlPrice());
                        openTradeDataEntity.setSellTradedPrice(new BigDecimal(order.averagePrice));
                    } else {
                        openTradeDataEntity.setBuyTradedPrice(new BigDecimal(order.averagePrice));
                        openTradeDataEntity.setBuyTradedPrice(openTradeDataEntity.getSlPrice());
                    }
                    saveTradeData(openTradeDataEntity);
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram("SL Hit for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    }
                }
                if ("CANCELLED".equals(order.status) && !openTradeDataEntity.isSlCancelled) {
                    openTradeDataEntity.isSlCancelled = true;
                    saveTradeData(openTradeDataEntity);
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram("SL Cancelled order for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    }
                }
                if ("REJECTED".equals(order.status) && !openTradeDataEntity.isErrored) {
                    openTradeDataEntity.isErrored = true;
                    saveTradeData(openTradeDataEntity);
                    if (openTradeDataEntity.getEntryType().equals("SELL")) {
                        sendMessage.sendToTelegram("SL order placement rejected for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    } else {
                        sendMessage.sendToTelegram("SL order placement rejected for: " + openTradeDataEntity.getStockName() + ":" + openTradeDataEntity.getUserId()+":"+getAlgoName(), telegramToken);
                    }
                }
            }
        });
    }

    @Autowired
    TradeDataMapper tradeDataMapper;
    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData,boolean orderPlaced) {
        try {/*
            OpenTradeDataEntity openTradeDataEntity = new OpenTradeDataEntity();
            openTradeDataEntity.setDataKey(tradeData.getDataKey());
            openTradeDataEntity.setAlgoName(this.getAlgoName());
            openTradeDataEntity.setStockName(tradeData.getStockName());
            openTradeDataEntity.setEntryType(tradeData.getEntryType());
            openTradeDataEntity.setUserId(tradeData.getUserId());
            openTradeDataEntity.isOrderPlaced = tradeData.isOrderPlaced;
            openTradeDataEntity.isSlPlaced = tradeData.isSlPlaced();
            openTradeDataEntity.isExited = tradeData.isExited();
            openTradeDataEntity.isErrored = tradeData.isErrored;
            openTradeDataEntity.isSLHit = tradeData.isSLHit;
            openTradeDataEntity.setBuyTradedPrice(tradeData.getBuyTradedPrice());
            openTradeDataEntity.setSellTradedPrice(tradeData.getSellTradedPrice());
            openTradeDataEntity.setExitOrderId(tradeData.getExitOrderId());
            openTradeDataEntity.setBuyPrice(tradeData.getBuyPrice());
            openTradeDataEntity.setSellPrice(tradeData.getSellPrice());
            openTradeDataEntity.setSlPrice(tradeData.getSlPrice());
            openTradeDataEntity.setQty(tradeData.getQty());
            openTradeDataEntity.setSlPercentage(tradeData.getSlPercentage());
            openTradeDataEntity.setEntryOrderId(tradeData.getEntryOrderId());
            openTradeDataEntity.setSlOrderId(tradeData.getSlOrderId());
            openTradeDataEntity.setStockId(tradeData.getStockId());
            Date date = new Date();
            if(orderPlaced) {
                String tradeDate = format.format(date);
                openTradeDataEntity.setTradeDate(tradeDate);
                tradeData.setTradeDate(tradeDate);
            }else{
                openTradeDataEntity.setTradeDate(tradeData.getTradeDate());
            }
            saveTradeData(openTradeDataEntity);*/
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData,orderPlaced,this.getAlgoName());
            //LOGGER.info("sucessfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }

    public void saveTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            Date date = new Date();
            String tradeDate = format.format(date);
            openTradeDataEntity.setModifyDate(tradeDate);
            openTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }
}
