package com.sakthi.trade.util;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.Expiry;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.zerodhatech.models.HistoricalData;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Math Utilities.
 */
@Component
public class MathUtils {
    public static final Logger LOGGER = Logger.getLogger(MathUtils.class.getName());
    @Autowired
    TransactionService transactionService;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    @Autowired
    CommonUtil commonUtil;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Simple Moving Average
     */

    public static BigDecimal percentageValueOfAmount(BigDecimal percent, BigDecimal amount) {
        BigDecimal value = amount.divide(new BigDecimal("100"), 2, BigDecimal.ROUND_UP).multiply(percent).setScale(0, RoundingMode.HALF_UP);
        return value;
    }
    public static BigDecimal percentageValueOfAmountWithoutRund(BigDecimal percent, BigDecimal amount) {
        BigDecimal value = amount.divide(new BigDecimal("100")).multiply(percent);
        return value;
    }
    public static BigDecimal percentageMove(BigDecimal open, BigDecimal close) {
        BigDecimal value = ((close.subtract(open)).divide(open, RoundingMode.HALF_EVEN)).multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_UP);
        return value;
    }

    public static double percentageMove(double open, double close) {
        double value = ((close - open) / open) * 100;
        return value;
    }

/*
    public static void main(String args[]) {
        TradeData tradeData = new TradeData();
        tradeData.setStockName("abc");
        tradeData.setBuyTradedPrice(new BigDecimal("448.96"));
        tradeData.setSellTradedPrice(new BigDecimal("629.4"));
        tradeData.setQty(150);
        calculateBrokerage(tradeData, true, false, false, "0");
    }
*/

    public static Brokerage calculateBrokerage(TradeData tradeData, boolean isOptions, boolean isEquityIntraday, boolean isFutures, String slipage) {

        Brokerage brokerage = new Brokerage();
        BigDecimal brokerC = new BigDecimal("0");
        if (isOptions) {
            brokerC = new BigDecimal("40");
            brokerage.setBrokerCharge(brokerC);
        } else if (isEquityIntraday) {
            brokerC = MathUtils.percentageValueOfAmount(new BigDecimal("0.03"), tradeData.getSellTradedPrice().add(tradeData.getBuyTradedPrice())).multiply(new BigDecimal(tradeData.getQty()));
        }
        brokerage.setQty(tradeData.getQty());
        //STT
        BigDecimal stt = new BigDecimal("40");

        if (isOptions) {
            //   stt=tradeData.getSellPrice().multiply(new BigDecimal("0.05")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));
            stt = MathUtils.percentageValueOfAmount(new BigDecimal("0.05"), (tradeData.getSellTradedPrice().multiply(new BigDecimal(tradeData.getQty()))));

        } else if (isEquityIntraday) {
            // stt=tradeData.getSellPrice().multiply(new BigDecimal("0.025")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));
            stt = MathUtils.percentageValueOfAmount(new BigDecimal("0.025"), tradeData.getSellTradedPrice().multiply(new BigDecimal(tradeData.getQty())));
        }

        brokerage.setStt(stt);
        //transaction charges
        BigDecimal transactionCharges = new BigDecimal("0");
        if (isOptions) {
            //  transactionCharges=tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.053"));
            transactionCharges = MathUtils.percentageValueOfAmount(new BigDecimal("0.053"), tradeData.getSellTradedPrice().add(tradeData.getBuyTradedPrice()).multiply(new BigDecimal(tradeData.getQty())));
        } else if (isEquityIntraday) {
            //  transactionCharges= tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.00345"));
            transactionCharges = MathUtils.percentageValueOfAmount(new BigDecimal("0.00345"), tradeData.getSellTradedPrice().add(tradeData.getBuyTradedPrice()).multiply(new BigDecimal(tradeData.getQty())));


        }
        //   BigDecimal slippagePoints=new BigDecimal(slipage).divide(new BigDecimal("100"));
        BigDecimal buyslippageCost = MathUtils.percentageValueOfAmount(new BigDecimal(slipage), tradeData.getSellTradedPrice()).multiply(new BigDecimal(tradeData.getQty()));
        BigDecimal sellSlippageCost = MathUtils.percentageValueOfAmount(new BigDecimal(slipage), tradeData.getBuyTradedPrice()).multiply(new BigDecimal(tradeData.getQty()));
        brokerage.setSlipageCost(new BigDecimal("0"));
        brokerage.setTransactionCharges(transactionCharges);
        //GST: 18%
        BigDecimal gst = MathUtils.percentageValueOfAmount(new BigDecimal("18"), transactionCharges.add(brokerC));
        brokerage.setGst(gst);
        BigDecimal stampDuty = new BigDecimal(.003).multiply(tradeData.getBuyTradedPrice());
        brokerage.setGst(stampDuty);
        BigDecimal totalcharges = brokerC.add(stt).add(transactionCharges).add(gst).add(stampDuty);
        brokerage.setTotalCharges(totalcharges);
        tradeData.setCharges(totalcharges.setScale(2, RoundingMode.HALF_UP));
        return brokerage;
    }

    public Map<String, String> getPriceRange( String currentDate, int upperRange, int lowerRange, String checkTime, String index) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        Map<String,Map<String,String>> strikeMasterMap1=new HashMap<>();
        String stockId=null;
        if("BNF".equals(index)) {
             stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1 = zerodhaTransactionService.bankNiftyNextWeeklyOptions;
            } else {
                strikeMasterMap1 = zerodhaTransactionService.bankNiftyWeeklyOptions;
            }
        }else if("NF".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1= zerodhaTransactionService.niftyNextWeeklyOptions;
            } else {
                strikeMasterMap1 = zerodhaTransactionService.niftyWeeklyOptions;
            }
        }
        Map<String,Map<String,String>> strikeMasterMap=strikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        Map<String, String> rangeStrike = new HashMap<>();
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            historicalData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T" + checkTime)) {/*"09:30:00"*/
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        int tempStrike = atmStrike;
                        int i=0;
                        while (tempStrike > 0 && i<10) {
                                final Map.Entry<String, String> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                                if (atmStrikesStraddle.getKey().contains("CE")) {
                                    double closePrice = callStrike(atmStrikesStraddle.getValue(), currentDate,checkTime);
                                    Thread.sleep(200);
                                    int tempStrike1 = assessRange("CE", closePrice, upperRange, lowerRange, tempStrike);
                                    if (tempStrike1 == tempStrike) {
                                        final Map<String, String> stringStringMap = strikeMasterMap.get(String.valueOf(tempStrike));
                                        stringStringMap.forEach((key1, value1) -> {
                                          //  LOGGER.info(key1 + ":" + value1);
                                            if (key1.contains("CE")) {
                                                rangeStrike.put(key1, value1);
                                                System.out.println(currentDate+":"+key1);
                                            }
                                        });
                                        break;
                                    }
                                    tempStrike = tempStrike1;
                                }
                                i++;
                            }
                            int tempStrike2 = atmStrike;
                        int j=0;
                            while (tempStrike2 > 0 && j<10) {
                                final Map.Entry<String, String> atmStrikesStraddle =strikeMasterMap.get(String.valueOf(tempStrike2)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                                if (atmStrikesStraddle.getKey().contains("PE")) {
                                    double closePrice = callStrike(atmStrikesStraddle.getValue(), currentDate,checkTime);
                                    Thread.sleep(200);
                                    int tempStrike1 = assessRange("PE", closePrice, upperRange, lowerRange, tempStrike2);
                                    if (tempStrike1 == tempStrike2) {
                                        final Map<String, String> stringStringMap = strikeMasterMap.get(String.valueOf(tempStrike2));
                                        stringStringMap.forEach((key1, value1) -> {
                                            LOGGER.info(key1 + ":" + value1);
                                            if (key1.contains("PE")) {
                                                rangeStrike.put(key1, value1);
                                                System.out.println(currentDate+":"+key1);
                                            }
                                        });
                                        break;
                                    }
                                    tempStrike2 = tempStrike1;
                                }
                            j++;
                            }



                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        return rangeStrike;
    }


    public  Map<String, StrikeData> getPriceRangeSortedWithLowRange( String currentDate, int upperRange, int lowerRange, String checkTime, String index) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        Map<String,Map<String, StrikeData>>  strikeMasterMap1=new HashMap<>();
        String stockId=null;
        if("BNF".equals(index)) {
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_NEXT.expiryName);
            } else {
                strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_CURRENT.expiryName);
            }
        }else if("NF".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1= zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_NEXT.expiryName);
            } else {
                strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_CURRENT.expiryName);
            }
        }
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:15:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        Map<String, StrikeData> rangeStrike = new HashMap<>();
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
          // HistoricalData lastElement= historicalData.dataArrayList.get(historicalData.dataArrayList.size()-1);
            historicalData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                  //  String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(currentDate + "T" + checkTime)) {/*"09:30:00"*/
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        int tempStrike = atmStrike;
                        int i=0;
                        while (tempStrike > 0 && i<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("CE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(200);
                                int tempStrike1 = assessRangeWithRange("CE", closePrice, upperRange, lowerRange, tempStrike,atmStrike,atmStrikesStraddle,ce);
                                if (tempStrike1 == tempStrike) {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(ce);
                                    if(stringStringMap!=null){
                                        //  LOGGER.info(key1 + ":" + value1);
                                            rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                            System.out.println(currentDate+":"+stringStringMap.getKey());
                                        }
                                    break;
                                }
                                tempStrike = tempStrike1;
                            }
                            i++;
                        }
                        int tempStrike2 = atmStrike;
                        int j=0;
                        while (tempStrike2 > 0 && j<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle =strikeMasterMap.get(String.valueOf(tempStrike2)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("PE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(200);
                                int tempStrike1 = assessRangeWithRange("PE", closePrice, upperRange, lowerRange, tempStrike2,atmStrike,atmStrikesStraddle,pe);
                                if (tempStrike1 == tempStrike2) {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(pe);
                                    if(stringStringMap!=null){
                                        rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                        System.out.println(currentDate+":"+stringStringMap.getKey());
                                    }

                                    break;
                                }
                                tempStrike2 = tempStrike1;
                            }
                            j++;
                        }



                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        return rangeStrike;
    }
//use only for exp for now
    public Map<Double,Map<String, StrikeData>> getPriceCloseToPremium( String currentDate, int closePremium, String checkTime, String index) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
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
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
      //  Map<Double,Map.Entry<String, StrikeData>> cepe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
        //   Map<String,Map<String,String>> dhanStrikeMasterMap=dhanStrikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        Map<Double,Map<String, StrikeData>> rangeStrike = new HashMap<>();
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            historicalData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T" + checkTime)) {/*"09:30:00"*/
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        int tempStrike = atmStrike-300;
                        int i=0;
                        while (tempStrike > 0 && i<12) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                            //  final Map.Entry<String, StrikeData> dhanAtmStrikesStraddle = dhanStrikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("CE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(200);
                                tempStrike = tempStrike+50; //TODO: handle BNF, increase BNF 100
                                ce.put(closePrice,atmStrikesStraddle);

                            }
                            i++;
                        }
                        selectClosestStrikePrice(ce,closePremium,rangeStrike);
                        int tempStrike2 = atmStrike+300;
                        int j=0;
                        while (tempStrike2 > 0 && j<12) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle =strikeMasterMap.get(String.valueOf(tempStrike2)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("PE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(200);
                                tempStrike2 = tempStrike2-50;
                                Map<String, StrikeData> atmStrikesStraddle1 =new HashMap<>();
                              //  atmStrikesStraddle1.put(atmStrikesStraddle.getKey(),atmStrikesStraddle.getValue())
                                pe.put(closePrice,atmStrikesStraddle);
                            }
                            j++;
                        }
                        selectClosestStrikePrice(pe,closePremium,rangeStrike);



                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        return rangeStrike;
    }
    public Map<String, StrikeData> getPriceRangeSortedWithLowRangeNifty( String currentDate, int upperRange, int lowerRange, String checkTime, String index) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        Map<String,Map<String, StrikeData>> strikeMasterMap1=new HashMap<>();
       // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
        String stockId=null;
        if("BNF".equals(index)) {
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_NEXT.expiryName);
            } else {
                strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_CURRENT.expiryName);
            }
        }else if("NF".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1= zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_NEXT.expiryName);
            } else {
                strikeMasterMap1 = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_CURRENT.expiryName);
            }
        }
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
     //   Map<String,Map<String,String>> dhanStrikeMasterMap=dhanStrikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(response);
        Map<String, StrikeData> rangeStrike = new HashMap<>();
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            historicalData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String openDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(openDate + "T" + checkTime)) {/*"09:30:00"*/
                        int atmStrike = commonUtil.findATM((int) historicalData1.close);
                        int tempStrike = atmStrike;
                        int i=0;
                        while (tempStrike > 0 && i<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                          //  final Map.Entry<String, StrikeData> dhanAtmStrikesStraddle = dhanStrikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("CE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(200);
                                int tempStrike1 = assessRangeWithRange50("CE", closePrice, upperRange, lowerRange, tempStrike,atmStrike,atmStrikesStraddle,ce);
                                if (tempStrike1 == tempStrike) {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(ce);
                                    if(stringStringMap!=null){
                                        //  LOGGER.info(key1 + ":" + value1);
                                        rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                        System.out.println(currentDate+":"+stringStringMap.getKey());
                                    }
                                    break;
                                }
                                tempStrike = tempStrike1;
                            }
                            i++;
                        }
                        int tempStrike2 = atmStrike;
                        int j=0;
                        while (tempStrike2 > 0 && j<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle =strikeMasterMap.get(String.valueOf(tempStrike2)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("PE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(200);
                                int tempStrike1 = assessRangeWithRange50("PE", closePrice, upperRange, lowerRange, tempStrike2,atmStrike,atmStrikesStraddle,pe);
                                if (tempStrike1 == tempStrike2) {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(pe);
                                    if(stringStringMap!=null){
                                        rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                        System.out.println(currentDate+":"+stringStringMap.getKey());
                                    }

                                    break;
                                }
                                tempStrike2 = tempStrike1;
                            }
                            j++;
                        }



                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        return rangeStrike;
    }
    Map.Entry<String, String> getMinPremiumStrikeStr(Map<Double,Map.Entry<String, String>> nonSorted){
        Map.Entry<Double,Map.Entry<String, String>> min = null;
        for (Map.Entry<Double,Map.Entry<String, String>> entry : nonSorted.entrySet()) {
            if (min == null || min.getKey() > entry.getKey()) {
                min = entry;
            }
        }
        return min.getValue();
    }
    Map.Entry<String, StrikeData> getMinPremiumStrike(Map<Double,Map.Entry<String, StrikeData>> nonSorted){
        Map.Entry<Double,Map.Entry<String, StrikeData>> min = null;
        for (Map.Entry<Double,Map.Entry<String, StrikeData>> entry : nonSorted.entrySet()) {
            if (min == null || min.getKey() > entry.getKey()) {
                min = entry;
            }
        }
        return min.getValue();
    }
    public double callStrike(String strikeId, String currentDate,String checkTime) {
        String historicURLStrike = "https://api.kite.trade/instruments/historical/" + strikeId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURLStrike));
        LOGGER.info("API response:" + priceResponse);
        HistoricalData historicalPriceData = new HistoricalData();
        JSONObject priceJson = new JSONObject(priceResponse);
        String responseStatus = priceJson.getString("status");
        AtomicDouble closePrice = new AtomicDouble(0);
        if (!responseStatus.equals("error")) {
            historicalPriceData.parseResponse(priceJson);
            historicalPriceData.dataArrayList.forEach(historicalDataPrice -> {
                try {
                    Date priceDatetime = sdf.parse(historicalDataPrice.timeStamp);
                    String priceDate = format.format(priceDatetime);
                    if (sdf.format(priceDatetime).equals(priceDate + "T"+checkTime)) {
                        closePrice.getAndSet(historicalDataPrice.close);
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                }
            });
        }
        return closePrice.get();
    }
    public double callStrikeWithName(String strikeId, String currentDate,String checkTime,String strikeName) {
        String historicURLStrike = "https://api.kite.trade/instruments/historical/" + strikeId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURLStrike));
     //   LOGGER.info("API response:"+strikeName+":" + priceResponse);
        HistoricalData historicalPriceData = new HistoricalData();
        JSONObject priceJson = new JSONObject(priceResponse);
        String responseStatus = priceJson.getString("status");
        AtomicDouble closePrice = new AtomicDouble(0);
        if (!responseStatus.equals("error")) {
            historicalPriceData.parseResponse(priceJson);
            historicalPriceData.dataArrayList.forEach(historicalDataPrice -> {
                try {
                    Date priceDatetime = sdf.parse(historicalDataPrice.timeStamp);
                    String priceDate = format.format(priceDatetime);
                    if (sdf.format(priceDatetime).equals(priceDate + "T"+checkTime)) {
                        closePrice.getAndSet(historicalDataPrice.close);
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                }
            });
        }
        return closePrice.get();
    }
    public double callStrikeWithName(StrikeData strikeData, String currentDate,String checkTime,String strikeName) {
        String historicURLStrike = "https://api.kite.trade/instruments/historical/" + strikeData.getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURLStrike));
        LOGGER.info("URL"+historicURLStrike+" API response:"+strikeName+":" + priceResponse);
        HistoricalData historicalPriceData = new HistoricalData();
        JSONObject priceJson = new JSONObject(priceResponse);
        String responseStatus = priceJson.getString("status");
        AtomicDouble closePrice = new AtomicDouble(0);
        if (!responseStatus.equals("error")) {
            historicalPriceData.parseResponse(priceJson);
            //   HistoricalData lastElement=historicalPriceData.dataArrayList.get(historicalPriceData.dataArrayList.size()-1);
            historicalPriceData.dataArrayList.forEach(historicalData1 -> {
                try {
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String priceDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(priceDate + "T" + checkTime)) {
                        LOGGER.info("API last element price :" + strikeName + ":" + historicalData1.close);
                        closePrice.getAndSet(historicalData1.close);
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                }

            });
        }
        return closePrice.get();
    }
    public static int assessRange(String strikeType, double closePrice, int upperRange, int lowerRange, int currentStrike) {
        if (strikeType.equals("CE")) {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                return currentStrike;
            } else if (closePrice < lowerRange) {
                return currentStrike - 100;
            } else if (closePrice > upperRange) {
                return currentStrike + 100;
            }
        } else {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                return currentStrike;
            } else if (closePrice < lowerRange) {
                return currentStrike + 100;
            } else if (closePrice > upperRange) {
                return currentStrike - 100;
            }
        }
        return 0;
    }

    public static int assessRangeWithRange(String strikeType, double closePrice, int upperRange, int lowerRange, int currentStrike,int atmStrike,Map.Entry<String, StrikeData> straddle,Map<Double,Map.Entry<String, StrikeData>> mapst) {
        if (strikeType.equals("CE")) {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                mapst.put(closePrice,straddle);
                if(atmStrike>currentStrike){
                    return currentStrike;
                }
                return currentStrike+100;
            } else if (closePrice < lowerRange) {
                if (mapst.size()>0){
                    return currentStrike;
                }
                return currentStrike - 100;
            } else if (closePrice > upperRange) {
                return currentStrike + 100;
            }
        } else {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                mapst.put(closePrice,straddle);
                if(atmStrike<currentStrike){
                    return currentStrike;
                }
                return currentStrike-100;
            } else if (closePrice < lowerRange) {
                if (mapst.size()>0){
                    return currentStrike;
                }
                return currentStrike + 100;
            } else if (closePrice > upperRange) {
                return currentStrike - 100;
            }
        }
        return 0;
    }
    public static void selectClosestStrikePrice(Map<Double, Map.Entry<String, StrikeData>> strikePrices, double targetPrice,Map<Double, Map<String, StrikeData>> range) {
        Map<Double, Map<String, StrikeData>>  closestStrikePrice = new HashMap<>();
        double minDifference = Double.MAX_VALUE;
        for (Map.Entry<Double, Map.Entry<String, StrikeData>> entry : strikePrices.entrySet()) {
            double strikePrice = entry.getKey();
            double difference = Math.abs(strikePrice - targetPrice);
            if (difference < minDifference) {
                closestStrikePrice=new HashMap<>();
                Map<String, StrikeData> closestStrikePrice1=new HashMap<>();
                closestStrikePrice1.put(entry.getValue().getKey(),entry.getValue().getValue());
                closestStrikePrice.put(entry.getKey(), closestStrikePrice1);
                minDifference = difference;
            }
        }
        for (Map.Entry<Double, Map<String, StrikeData>> entry : closestStrikePrice.entrySet()) {
            range.put(entry.getKey(), entry.getValue());
        }
    }
    public static int assessRangeWithRange50(String strikeType, double closePrice, int upperRange, int lowerRange, int currentStrike,int atmStrike,Map.Entry<String, StrikeData> straddle,Map<Double,Map.Entry<String, StrikeData>> mapst) {
        if (strikeType.equals("CE")) {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                mapst.put(closePrice,straddle);
                if(atmStrike>currentStrike){
                    return currentStrike;
                }
                return currentStrike+50;
            } else if (closePrice < lowerRange) {
                if (mapst.size()>0){
                    return currentStrike;
                }
                return currentStrike - 50;
            } else if (closePrice > upperRange) {
                return currentStrike + 50;
            }
        } else {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                mapst.put(closePrice,straddle);
                if(atmStrike<currentStrike){
                    return currentStrike;
                }
                return currentStrike-50;
            } else if (closePrice < lowerRange) {
                if (mapst.size()>0){
                    return currentStrike;
                }
                return currentStrike + 50;
            } else if (closePrice > upperRange) {
                return currentStrike - 50;
            }
        }
        return 0;
    }
    public static class SMA {
        private LinkedList values = new LinkedList();

        private int length;

        private double sum = 0;

        private double average = 0;

        /**
         * @param length the maximum length
         */
        public SMA(int length) {
            if (length <= 0) {
                throw new IllegalArgumentException("length must be greater than zero");
            }
            this.length = length;
        }

        public double currentAverage() {
            return average;
        }

        /**
         * Compute the moving average.
         * Synchronised so that no changes in the underlying data is made during calculation.
         *
         * @param value The value
         * @return The average
         */
        public synchronized double compute(double value) {
            if (values.size() == length && length > 0) {
                sum -= ((Double) values.getFirst()).doubleValue();
                values.removeFirst();
            }
            sum += value;
            values.addLast(new Double(value));
            average = sum / values.size();
            return average;
        }
    }

    public static class RSI {
        private LinkedList<Double> gains = new LinkedList<>();
        private LinkedList<Double> loss = new LinkedList<>();
        private LinkedList<Double> closeList = new LinkedList<>();
        private LinkedList<Double> averageGains = new LinkedList<>();
        private LinkedList<Double> averageLosses = new LinkedList<>();
        private int periodLength;

        private double sum = 0;
        private int counter = 0;
        private double average = 0;

        /**
         * @param periodLength the maximum length
         */
        public RSI(int periodLength) {
            if (periodLength <= 0) {
                throw new IllegalArgumentException("length must be greater than zero");
            }
            this.periodLength = periodLength;
        }

        public double currentAverage() {
            return average;
        }

        /**
         * Compute the moving average.
         * Synchronised so that no changes in the underlying data is made during calculation.
         *
         * @param value The value
         * @return The average
         */
        public synchronized double compute(double value) {
            double rsi = 0;
            if (counter == 0) {
                closeList.addLast(value);
            } else if (counter > 0) {
                if (closeList.size() == 1) {
                    double change = value - closeList.getFirst();
                    if (change > 0) {
                        gains.addLast(change);
                        loss.addLast(0.0);
                    } else if (change < 0) {
                        gains.addLast(0.0);
                        loss.addLast(Math.abs(change));
                    } else {
                        gains.addLast(0.0);
                        loss.addLast(0.0);
                    }
                }
                closeList.removeFirst();
                closeList.addLast(value);
            }

            if (counter >= 14) {
                double averageGain = 0;
                double averageLoss = 0;
                if (counter == 14) {
                    double gainsSum = gains.stream()
                            .filter(a -> a != null)
                            .mapToDouble(a -> a)
                            .sum();
                    averageGain = gainsSum / 14;
                    averageGains.addLast(averageGain);
                    double lossSum = loss.stream()
                            .filter(a -> a != null)
                            .mapToDouble(a -> a)
                            .sum();
                    averageLoss = lossSum / 14;
                    averageLosses.addLast(averageLoss);
                } else {
                    double averageG = averageGains.getFirst();
                    averageGain = ((averageG * 13) + gains.getLast()) / 14;
                    averageGains.removeFirst();
                    averageGains.addLast(averageGain);
                    double averageL = averageLosses.getFirst();
                    averageLoss = ((averageL * 13) + loss.getLast()) / 14;
                    averageLosses.removeFirst();
                    averageLosses.addLast(averageLoss);

                }

                double rs = averageGain / averageLoss;
                rsi = 100 - (100 / (1 + rs));

            }
            counter++;
            return rsi;
        }
    }
}