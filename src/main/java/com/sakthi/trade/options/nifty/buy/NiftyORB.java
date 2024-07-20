/*
package com.sakthi.trade.options.nifty.buy;

import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.zerodhatech.models.HistoricalData;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NiftyORB {
    @Value("${filepath.trend}")
    String trendPath;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    @Autowired
    TransactionService transactionService;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    Map<String,Double> orbHighLow=new HashMap<>();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;

    public static final Logger LOGGER = LoggerFactory.getLogger(NiftyORB.class.getName());
    @Autowired
    TelegramMessenger sendMessage;
    boolean cePlaced=false;
    boolean pePlaced=false;
    public Map<String, TradeData> straddleTradeMap = new ConcurrentHashMap<>();
    public String getAlgoName() {
        return "NIFTY_BUY_935";
    }
   // @Scheduled(cron = "${niftyORB.orb.range.schedule}")
    public void ORB() throws IOException {
        Date date = new Date();
            String currentDate = format.format(date);
            String historicURL = "https://api.kite.trade/instruments/historical/256265/minute?from=" + currentDate + "+09:16:00&to=" + currentDate + "+09:22:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            String status = json.getString("status");
            if (!status.equals("error")) {
                historicalData.parseResponse(json);

                if (historicalData.dataArrayList.size() > 0) {

                    orbHighLow(historicalData.dataArrayList);
                }
            }
        try {
            double low = orbHighLow.get("LOW");
            double high = orbHighLow.get("HIGH");
            String message="option orb range low:"+low+" high:"+high;
            sendMessage.sendToTelegram(message, telegramToken);
        }catch (Exception e){
            LOGGER.info("error:"+e);
        }
    }
    @Autowired
    MathUtils mathUtils;
 //   @Scheduled(cron = "${niftyORB.orb.range.entry.check}")
    public void ORBEntryCheck() throws IOException, ParseException {
        Date date = new Date();
        String currentDate = format.format(date);

        String historicURL = "https://api.kite.trade/instruments/historical/256265/minute?from=" + currentDate + "+09:15:00&to=" + currentDate + "+15:27:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequestWithoutLog(historicURL));
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        Map<String, StrikeData> rangeStrike = new HashMap<>();
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            if (historicalData.dataArrayList.size() > 0) {
                HistoricalData lastElement = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                Date lastTradingDatetime = sdf.parse(lastElement.timeStamp);
                String openDate = format.format(lastTradingDatetime);
                if (lastTradingDatetime.after(sdf.parse(openDate + "T" + "09:23:00"))) {
                    double low = orbHighLow.get("LOW");
                    double high = orbHighLow.get("HIGH");

                   */
/* We will start by tracking the Selected Strike price between 9:16 and 09:23:59 .
                   We will take entry after 09:23:59 once the High price of the Selected Strike in the range is breached. *//*

                    if (lastElement.close < low && !pePlaced) {
                        pePlaced=true;
                        Calendar calendar = Calendar.getInstance();
                        SimpleDateFormat sdf1 = new SimpleDateFormat("HH:mm");
                        calendar.add(Calendar.MINUTE, -1);
                        Date currentMinDate=calendar.getTime();
                        String currentMin=sdf1.format(currentMinDate);
                        Map<String, StrikeData> rangeSelected;
                        rangeSelected=mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDate,200,150,currentMin+":00","NF");
                        Map.Entry<String, StrikeData> finalSelected=rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("PE")).findFirst().get();
                        TradeData tradeData = new TradeData();
                        tradeData.setEntryType("SELL");
                        tradeData.isOrderPlaced = true;
                        tradeData.setQty(50);
                        tradeData.setSellTime(sdf.format(lastTradingDatetime));
                        tradeData.setSellPrice(new BigDecimal(lastElement.close));
                        tradeData.setZerodhaStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                        straddleTradeMap.put(finalSelected.getKey(),tradeData);
                        String message="option orb range low broke, strike selected :"+finalSelected.getValue().getZerodhaSymbol();
                        sendMessage.sendToTelegram(message, telegramToken);
                    }
                    if (lastElement.close > high && !cePlaced) {
                        cePlaced=true;
                        Calendar calendar = Calendar.getInstance();
                        SimpleDateFormat sdf1 = new SimpleDateFormat("HH:mm");
                        calendar.add(Calendar.MINUTE, -1);
                        Date currentMinDate=calendar.getTime();
                        String currentMin=sdf1.format(currentMinDate);
                        Map<String, StrikeData> rangeSelected;
                        rangeSelected=mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDate,200,150,currentMin+":00","NF");
                        Map.Entry<String, StrikeData> finalSelected=rangeSelected.entrySet().stream().filter(stringStrikeDataEntry -> stringStrikeDataEntry.getKey().equals("CE")).findFirst().get();
                        TradeData tradeData = new TradeData();
                        tradeData.setEntryType("BUY");
                        tradeData.isOrderPlaced = true;
                        tradeData.setQty(50);
                        tradeData.setBuyTime(sdf.format(lastTradingDatetime));
                        tradeData.setBuyPrice(new BigDecimal(lastElement.close));
                        tradeData.setZerodhaStockId(Integer.parseInt(finalSelected.getValue().getZerodhaId()));
                        straddleTradeMap.put(finalSelected.getKey(),tradeData);
                        String message="option orb range high broke, strike selected :"+finalSelected.getValue().getZerodhaSymbol();
                        sendMessage.sendToTelegram(message, telegramToken);
                    }
                */
/*
                historicalData.dataArrayList.forEach(historicalData1 -> {
                    try {
                        Date openDatetime = sdf.parse(historicalData1.timeStamp);
                        String openDate = format.format(openDatetime);
                        double low = orbHighLow.get("LOW");
                        double high = orbHighLow.get("HIGH");
                        if (openDatetime.after(sdf.parse(openDate + "T" + "09:21:00"))) {
                            if (historicalData1.close < low) {
                                tradeData.setEntryType("SELL");
                                tradeData.isOrderPlaced = true;
                                tradeData.setSellTime(sdf.format(openDatetime));
                                tradeData.setSellPrice(new BigDecimal(historicalData1.close));
                            }
                            if (historicalData1.close > high) {
                                tradeData.setEntryType("BUY");
                                tradeData.isOrderPlaced = true;
                                tradeData.setBuyTime(sdf.format(openDatetime));
                                tradeData.setBuyPrice(new BigDecimal(historicalData1.close));
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.info("error:"+e);
                        String message="option orb range error:"+e.getMessage();
                        sendMessage.sendToTelegram(message, telegramToken);
                    }
                });*//*

                }
            }
        }
    }
    @Scheduled(cron = "${niftyORB.orb.range.sl.monitor}")
    public void ORBSLMonitor() throws IOException {

    }
    @Scheduled(cron = "${niftyORB.orb.range.exit}")
    public void ORBExit() throws IOException {

    }
    public void orbHighLow( List<HistoricalData> orb){
        double low=Double.MAX_VALUE;
        double high=Double.MIN_VALUE;
        for (HistoricalData candle : orb) {
            if (candle.high > high) {
                high = candle.high;
            }
            if (candle.low < low) {
                low = candle.low;
            }
        }
        orbHighLow.put("LOW",low);
        orbHighLow.put("HIGH",high);
    }


}
*/
