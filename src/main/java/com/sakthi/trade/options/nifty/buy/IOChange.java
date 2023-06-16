package com.sakthi.trade.options.nifty.buy;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.OIChangeData;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.options.Strategy;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.util.OrderUtil;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.Expiry;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    Map<String,Map<String, StrikeData>> atmStrikeList=new ConcurrentHashMap<>();
    Map<String,Map<String, OIChangeData>> previousOI=new ConcurrentHashMap<>();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    @Autowired
    CommonUtil commonUtil;
    public String getAlgoName() {
        return "OIChange";
    }
    @Autowired
    TelegramMessenger telegramClient;
    @Value("${telegram.orb.bot.token}")
    String telegramTokenGroup;
 //   @Scheduled(cron = "${exp.oi.atm.get.time}")
    public void getAtm(){
        Date date = new Date();
        String currentDate = format.format(date);
        if(zerodhaTransactionService.expDate.equals(currentDate) || zerodhaTransactionService.finExpDate.equals(currentDate)){
        LOGGER.info("IOChange get atm");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat weekDay = new SimpleDateFormat("EEE");

       // String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        List<String> stockIdList = new ArrayList<>();
        if(zerodhaTransactionService.expDate.equals(currentDate)) {
            stockIdList.add("BNF");
            stockIdList.add("NF");
        }else  if(zerodhaTransactionService.finExpDate.equals(currentDate)){
            stockIdList.add("FN");
        }

        stockIdList.stream().forEach(index-> {
            LOGGER.info("index get atm:"+index);
            Map<String,Map<String, StrikeData>> strikeMasterMap1=new HashMap<>();
            // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
            String stockId=null;
        if("BNF".equals(index)) {
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            //  if (zerodhaTransactionService.expDate.equals(currentDate)) {
            strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_CURRENT.expiryName);
            //   }
        }else if("NF".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
            //if (zerodhaTransactionService.expDate.equals(currentDate)) {
            strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_CURRENT.expiryName);
            //  }
        }
        else if("FN".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
            //  if (zerodhaTransactionService.finExpDate.equals(currentDate)) {
            strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.FN_CURRENT.expiryName);
            //  }
        }
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            Map<Double, Map.Entry<String, StrikeData>> rangeStrike = new HashMap<>();
            String status = json.getString("status");
           // LOGGER.info("IOChange 2:45 response:"+response);
            Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                HistoricalData lastElement =historicalData.dataArrayList.get(historicalData.dataArrayList.size()-1);
               // historicalData.dataArrayList.forEach(historicalData1 -> {
                    try {
                        Date openDatetime = null;
                        try {
                            openDatetime = sdf.parse(lastElement.timeStamp);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    //    LOGGER.info("IOChange lastElement:"+new Gson().toJson(lastElement));
                        String openDate = format.format(openDatetime);
                        if (sdf.format(openDatetime).equals(openDate + "T14:44:00")) {/*"09:30:00"*/
                     //       LOGGER.info("IOChange inside if");
                            int atmStrike = commonUtil.findATM((int) lastElement.close);
                      //      LOGGER.info("IOChange atmStrike:"+atmStrike);
                            final Map<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(atmStrike));
                            atmStrikeList.put(index,atmStrikesStraddle);
                     //       LOGGER.info("IOChange atmStrikeList:"+new Gson().toJson(atmStrikeList));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
             //   });
            }
        });}
    }
//    @Scheduled(cron = "${exp.oi.atm.monitor.time}")
    public void expOIMonitorSchedule(){
        Date date = new Date();
        String currentDate = format.format(date);

        if(zerodhaTransactionService.expDate.equals(currentDate) || zerodhaTransactionService.finExpDate.equals(currentDate)) {
      //      LOGGER.info("IOChange 2:45 response:" + new Gson().toJson(atmStrikeList));

            if (!atmStrikeList.isEmpty()) {
                atmStrikeList.entrySet().stream().forEach(indexMapEntry -> {
                    Map<String, StrikeData> strikeDataMap = indexMapEntry.getValue();
                    strikeDataMap.entrySet().stream().forEach(strikeDataEntry -> {
                        String historicURLStrike = "https://api.kite.trade/instruments/historical/" + strikeDataEntry.getValue().getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:34:00";
                        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURLStrike));
                        //  LOGGER.info("API response:"+strikeName+":" + priceResponse);
                        HistoricalData historicalPriceData = new HistoricalData();
                        JSONObject priceJson = new JSONObject(priceResponse);
                        String responseStatus = priceJson.getString("status");
                        AtomicDouble closePrice = new AtomicDouble(0);
                        if (!responseStatus.equals("error")) {
                            historicalPriceData.parseResponse(priceJson);
                            HistoricalData lasthistoricalPriceData = historicalPriceData.dataArrayList.get(historicalPriceData.dataArrayList.size() - 1);

                            Map<String, OIChangeData> indexOI = previousOI.get(indexMapEntry.getKey());
                            if (indexOI != null) {
                                if(indexOI.containsKey(strikeDataEntry.getKey())){
                                OIChangeData previosOI = indexOI.get(strikeDataEntry.getKey());
                                double OIchange = MathUtils.percentageMove(previosOI.getCurrentOI(), lasthistoricalPriceData.oi);
                                previosOI.setPreviousOI(previosOI.getCurrentOI());
                                previosOI.setCurrentOI(lasthistoricalPriceData.oi);
                                previosOI.setOiPercentChange(OIchange);

                                if (OIchange > 10) {
                                    previosOI.setPositiveChange(true);
                                }
                                if (OIchange < -10) {
                                    previosOI.setNegativeChange(true);
                                }}
                                else{
                                    OIChangeData oiChangeData = new OIChangeData();
                                    oiChangeData.setStrikeName(strikeDataEntry.getValue().getZerodhaSymbol());
                                    oiChangeData.setCurrentOI(lasthistoricalPriceData.oi);
                                    indexOI.put(strikeDataEntry.getKey(), oiChangeData);
                                }
                            } else {
                                Map<String, OIChangeData> strikeOI = new ConcurrentHashMap<>();
                                OIChangeData oiChangeData = new OIChangeData();
                                oiChangeData.setStrikeName(strikeDataEntry.getValue().getZerodhaSymbol());
                                oiChangeData.setCurrentOI(lasthistoricalPriceData.oi);
                                strikeOI.put(strikeDataEntry.getKey(), oiChangeData);
                                previousOI.put(indexMapEntry.getKey(), strikeOI);
                            }
                        }
                    });
                    LOGGER.info("IOChange previousOI:" + new Gson().toJson(previousOI));
                    Map<String, OIChangeData> oiChangeDataMap = previousOI.get(indexMapEntry);
                    OIChangeData ceData = oiChangeDataMap.get("CE");
                    OIChangeData peData = oiChangeDataMap.get("PE");
                    if (ceData.getCurrentOI() > 0 && peData.getCurrentOI() > 0 && ceData.getPreviousOI() > 0 && peData.getPreviousOI() > 0 && !peData.isOrderPlaced() && !ceData.isOrderPlaced()) {
                        if (ceData.isPositiveChange() && peData.isNegativeChange()) {
                            String message = "Take PE position:" + peData.getStrikeName() + ". PE oi declined by:" + peData.getOiPercentChange() + " CE oi increased by:" + ceData.getOiPercentChange();
                            telegramClient.sendToTelegram(message, telegramTokenGroup, "-646157933");
                            peData.setOrderPlaced(true);
                        }
                        if (ceData.isNegativeChange() && peData.isPositiveChange()) {
                            String message = "Take CE position:" + ceData.getStrikeName() + ". PE oi increased by:" + peData.getOiPercentChange() + " CE oi declined by:" + ceData.getOiPercentChange();
                            telegramClient.sendToTelegram(message, telegramTokenGroup, "-646157933");
                            ceData.setOrderPlaced(true);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void entry() {

    }

    @Override
    public void sLMonitor() {

    }

    @Override
    public void exit() {

    }
}
