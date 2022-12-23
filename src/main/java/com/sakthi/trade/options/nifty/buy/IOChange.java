package com.sakthi.trade.options.nifty.buy;

import com.google.gson.Gson;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.options.Strategy;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.util.OrderUtil;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
@Service
@Slf4j
public class IOChange implements Strategy {
    public static final Logger LOGGER = Logger.getLogger(IOChange.class.getName());
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
    @Autowired
    OrderUtil orderUtil;
    @Autowired
    TransactionService transactionService;

    public String getAlgoName() {
        return "OIChange";
    }

    @Override
    public void entry() {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat weekDay = new SimpleDateFormat("EEE");
        String currentDate = format.format(date);
        String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        List<String> stockIdList = new ArrayList<>();
        stockIdList.add(niftyBank);
        String nifty = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
        stockIdList.add(nifty);
        stockIdList.stream().forEach(stockId -> {
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            Map<Double, Map.Entry<String, StrikeData>> rangeStrike = new HashMap<>();
            String status = json.getString("status");
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                historicalData.dataArrayList.forEach(historicalData1 -> {
                    try {
                        Date openDatetime = null;
                        try {
                            openDatetime = sdf.parse(historicalData1.timeStamp);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                        String openDate = format.format(openDatetime);
                        if (sdf.format(openDatetime).equals(openDate + "T" )) {/*"09:30:00"*/
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    @Override
    public void sLMonitor() {

    }

    @Override
    public void exit() {

    }
}
