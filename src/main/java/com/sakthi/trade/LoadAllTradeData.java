package com.sakthi.trade;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.OrbTradeData;
import com.sakthi.trade.domain.TradeData;
//import com.sakthi.trade.options.banknifty.BankNiftyShortStraddle;
//import com.sakthi.trade.options.nifty.NiftyShortStraddle;
//import com.sakthi.trade.orb.OrbScheduler;
//import com.sakthi.trade.trend.TrendScheduler;
//import com.sakthi.trade.websocket.truedata.HistoricWebsocket;
//import com.sakthi.trade.websocket.truedata.RealtimeWebsocket;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.KiteConnect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class LoadAllTradeData {

  /*  @Autowired
    BankNiftyShortStraddle bankNiftyShortStraddle;*/
/*
    @Autowired
    NiftyShortStraddle niftyShortStraddle;*/

  /*  @Autowired
    HistoricWebsocket historicWebsocket;*/
   /* @Autowired
    RealtimeWebsocket realtimeWebsocket;*/
 /*   @Autowired
    TrendScheduler trendScheduler;*/
    @Value("${filepath.trend}")
    String trendPath;

    @Autowired
    ZerodhaAccount zerodhaAccount;
    @Value("${zerodha.app.key}")
    public String zerodhaAppKey;
    @Value("${zerodha.api.secret}")
    String zerodhaApiSecret;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${zerodha.username}")
    String zerodhaUsername;
    @Value("${zerodha.password}")
    String zerodhaPassword;
    @Value("${zerodha.pin}")
    String zerodhaPin;

    @Value("${zerodha.api.accesstoken}")
    String zerodhaApiaccessToken;
    @Value("${backtest.enabled}")
    boolean backtestEnabled;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
   /* @Autowired
    OrbScheduler orbScheduler;*/
    @PostConstruct
    public void loadAllData() throws ParseException {
if(backtestEnabled) {
    zerodhaAccount.kiteConnect = new KiteConnect(zerodhaAppKey);
    zerodhaAccount.kiteConnect.setUserId("RS4899");
    zerodhaAccount.kiteConnect.setAccessToken(zerodhaApiaccessToken);
    try {
        zerodhaTransactionService.getInstrument();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
     /*   DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate = LocalDate.now();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
        Date currentDate = new Date();
        final String startDate = sdf1.format(currentDate);
        String checkTime = startDate + "T09:15:00";
        String checkCloseTime = startDate + "T15:15:00";
        if (currentDate.after(sdf.parse(checkTime)) && currentDate.before(sdf.parse(checkCloseTime))) {*/
            /*try {
                CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/trend_trade_data_" + dtf1.format(localDate) + ".csv"));
                String[] line;
                int i = 0;
                while ((line = csvReader.readNext()) != null) {
                    trendScheduler.trendTradeMap.put(line[0], new Gson().fromJson(line[1], TradeData.class));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }*/
          /*  try {
                CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/nifty_straddle_trade_data_" + dtf1.format(localDate) + ".csv"));
                String[] line;
                int i = 0;
                while ((line = csvReader.readNext()) != null) {
                    niftyShortStraddle.tradeDataHashMap.put(line[0], new Gson().fromJson(line[1], TradeData.class));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }*/
            /*try {
                CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/nifty_straddle_trade_data_" + dtf1.format(localDate) + ".csv"));
                String[] line;
                int i = 0;
                while ((line = csvReader.readNext()) != null) {
                    bankNiftyShortStraddle.tradeDataHashMap.put(line[0], new Gson().fromJson(line[1], TradeData.class));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }*/
     /*       try {
                CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/orb_trade_data_" + dtf1.format(localDate) + ".csv"));
                String[] line;
                int i = 0;
                while ((line = csvReader.readNext()) != null) {
                    historicWebsocket.orbTradePriceDTOS.put(Integer.valueOf(line[0]), new Gson().fromJson(line[1], TradeData.class));
                }
                orbScheduler.ORBScheduler();
            } catch (Exception e) {
                e.printStackTrace();
            }*/
       // }
    }
}
