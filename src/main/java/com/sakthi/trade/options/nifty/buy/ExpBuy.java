package com.sakthi.trade.options.nifty.buy;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.options.Strategy;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.util.OrderUtil;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

@Service
@Slf4j
public class ExpBuy implements Strategy {
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    TelegramMessenger sendMessage;

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    @Autowired
    UserList userList;
    Gson gson = new Gson();
    @Autowired
    BrokerWorkerFactory workerFactory;
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;

    @Autowired
    MathUtils mathUtils;
    public static final Logger LOGGER = Logger.getLogger(NiftyOptionBuy935.class.getName());
    public String getAlgoName() {
        return "EXPIRY_BUY";
    }

    @Autowired
    OrderUtil orderUtil;
    @Override
    @Scheduled(cron = "${exp.buy.entry.time}")
    public void entry() {

        //NIFTY FIN SERVICE
        //NIFTY 50
        // String fnifty = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
        // String nifty = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
        Date date = new Date();
        String currentDate = format.format(date);
        String index=null;
        int lotSize1=0;
        boolean expDay=false;
        if(zerodhaTransactionService.expDate.equals(currentDate)){
            index="NF";
            lotSize1=50;
            expDay=true;
        }else if(zerodhaTransactionService.finExpDate.equals(currentDate)){
            index="FN";
            lotSize1=40;
            expDay=true;
        }
         final int lotSize=lotSize1;
        if (expDay && lotSize1>0) {
            Map<Double, Map.Entry<String, StrikeData>> strikes = mathUtils.getPriceCloseToPremium(currentDate, 5, "14:44:00", index);
            try {
                strikes.entrySet().stream().forEach(atmNiftyStrikeMap -> {
                    Map.Entry<String, StrikeData> strikeDataEntry = atmNiftyStrikeMap.getValue();
                    Double triggerPriceD = atmNiftyStrikeMap.getKey();
                    LOGGER.info(strikeDataEntry.getValue().getZerodhaSymbol());
                    OrderParams orderParams = new OrderParams();
                    orderParams.tradingsymbol = strikeDataEntry.getValue().getZerodhaSymbol();
                    orderParams.exchange = "NFO";
                    orderParams.orderType = "SL";
                    orderParams.product = "NRML";
                    orderParams.transactionType = "BUY";
                    orderParams.validity = "DAY";
                    double triggerPrice = triggerPriceD * 300;
                    orderParams.triggerPrice = triggerPrice;
                    BigDecimal price = BigDecimal.valueOf(triggerPrice).setScale(0, RoundingMode.HALF_UP).add(BigDecimal.valueOf(triggerPrice).setScale(0, RoundingMode.HALF_UP).divide(new BigDecimal(100))).setScale(0, RoundingMode.HALF_UP);
                    orderParams.price = price.doubleValue();
                    userList.getUser().stream().filter(
                            user -> user.getExpZeroToHero() != null && user.getExpZeroToHero().isNrmlEnabled()
                    ).forEach(user -> {
                        BrokerWorker brokerWorker = workerFactory.getWorker(user);
                        int qty = 1;
                        qty = user.getExpZeroToHero().getLotSize();
                        Order order = null;
                        orderParams.quantity = lotSize * qty;
                        TradeData tradeData = new TradeData();
                        String dataKey = UUID.randomUUID().toString();
                        tradeData.setDataKey(dataKey);
                        tradeData.setStockName(strikeDataEntry.getValue().getZerodhaSymbol());
                        try {
                            LOGGER.info("input:" + gson.toJson(orderParams));
                            tradeData.setStrikeId(strikeDataEntry.getValue().getDhanId());
                            order = brokerWorker.placeOrder(orderParams, user, tradeData);

                            tradeData.setEntryOrderId(order.orderId);
                            tradeData.isOrderPlaced = true;
                            tradeData.setQty(lotSize * qty);
                            tradeData.setEntryType("BUY");
                            tradeData.setUserId(user.getName());
                            tradeData.setStockId(Integer.parseInt(strikeDataEntry.getValue().getZerodhaId()));
                            tradeData.setBuyPrice(BigDecimal.valueOf(triggerPriceD));
                            tradeData.setBuyTradedPrice(BigDecimal.valueOf(triggerPriceD));
                            if (user.getBroker().equals("dhan")) {
                                tradeData.setStockName(strikeDataEntry.getValue().getDhanSymbol());
                                user.getExpZeroToHero().straddleTradeMap.put(tradeData.getStockName(), tradeData);
                            } else {
                                user.getExpZeroToHero().straddleTradeMap.put(tradeData.getStockName(), tradeData);
                            }
                            String message = "option buy limit order placed for for user:" + user.getName() + " strike: " + strikeDataEntry.getValue().getZerodhaSymbol() + ":" + getAlgoName();
                            LOGGER.info(message);
                            LOGGER.info("Trade Data:" + new Gson().toJson(tradeData));

                            try {
                                sendMessage.sendToTelegram(message, telegramToken);
                            } catch (Exception e) {
                                log.error("error:" + e);
                            }
                        } catch (KiteException e) {
                            tradeData.isErrored = true;
                            LOGGER.info("Error while placing nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + e.message + ":" + e.code + ":" + getAlgoName());
                            sendMessage.sendToTelegram("Error while placing nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + user.getName() + ",Exception:" + e.message + ":" + getAlgoName() + " Input:" + new Gson().toJson(orderParams), telegramToken);

                        } catch (IOException e) {
                            tradeData.isErrored = true;
                            LOGGER.info("Error while placing nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + e.getMessage());
                            sendMessage.sendToTelegram("Error while placing nifty buy order: " + atmNiftyStrikeMap.getKey() + ":" + user.getName() + ",Exception:" + e.getMessage() + ":" + getAlgoName(), telegramToken);
                        }
                        LOGGER.info(new Gson().toJson(user.getExpZeroToHero().straddleTradeMap));
                    });
                });

                ///  }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // });
            //}
        }
    }

    @Override
    @Scheduled(cron = "${exp.buy.sl.time}")
    public void sLMonitor() {
        userList.getUser().stream().filter(
                user -> user.getExpZeroToHero() != null && user.getExpZeroToHero().isNrmlEnabled()
        ).forEach(user -> {
            orderUtil.sLMonitorScheduler( user,this.getAlgoName(),user.getExpZeroToHero());
        });
    }

    @Override
    @Scheduled(cron = "${exp.buy.exit.time}")
    public void exit() {
        String exitTime="15:20";
        userList.getUser().stream().filter(
                user -> user.getExpZeroToHero() != null && user.getExpZeroToHero().isNrmlEnabled()
        ).forEach(user -> {
            orderUtil.exitPriceNrmlPositions( user,this.getAlgoName(),user.getExpZeroToHero(),exitTime);
        });
    }
}
