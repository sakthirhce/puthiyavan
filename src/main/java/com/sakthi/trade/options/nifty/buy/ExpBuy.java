/*
package com.sakthi.trade.options.nifty.buy;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
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
  //  @Scheduled(cron = "${exp.buy.entry.time}")

    @Override
  //  @Scheduled(cron = "${exp.buy.sl.time}")
    public void sLMonitor() {
        userList.getUser().stream().filter(
                user -> user.getExpZeroToHero() != null && user.getExpZeroToHero().isNrmlEnabled()
        ).forEach(user -> {
            orderUtil.sLMonitorScheduler( user,this.getAlgoName(),user.getExpZeroToHero());
        });
    }

    @Override
   // @Scheduled(cron = "${exp.buy.exit.time}")
    public void exit() {
        String exitTime="15:20";
        userList.getUser().stream().filter(
                user -> user.getExpZeroToHero() != null && user.getExpZeroToHero().isNrmlEnabled()
        ).forEach(user -> {
            orderUtil.exitPriceNrmlPositions( user,this.getAlgoName(),user.getExpZeroToHero(),exitTime);
        });
    }


}
*/
