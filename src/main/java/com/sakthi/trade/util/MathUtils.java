package com.sakthi.trade.util;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.strategy.StrikeSelectionType;
import com.sakthi.trade.domain.strategy.TradeValidity;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.options.Strategy;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

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
        String stockId=strikeId(index);
        if("BNF".equals(index)) {
            if (zerodhaTransactionService.bankBiftyExpDate.equals(currentDate)) {
                strikeMasterMap1 = zerodhaTransactionService.bankNiftyNextWeeklyOptions;
            } else {
                strikeMasterMap1 = zerodhaTransactionService.bankNiftyWeeklyOptions;
            }
        }else if("NF".equals(index)){
            if (zerodhaTransactionService.expDate.equals(currentDate)) {
                strikeMasterMap1= zerodhaTransactionService.niftyNextWeeklyOptions;
            } else {
                strikeMasterMap1 = zerodhaTransactionService.niftyWeeklyOptions;
            }
        }

        if ("FN".equals(index)) {
            if (zerodhaTransactionService.finExpDate.equals(currentDate)) {
                strikeMasterMap1= zerodhaTransactionService.finNiftyWeeklyOptions;
            } else {
                strikeMasterMap1 = zerodhaTransactionService.finNiftyNextWeeklyOptions;
            }
        }
        if ("SS".equals(index)) {
            if (zerodhaTransactionService.sensexExpDate.equals(currentDate)) {
                strikeMasterMap1= zerodhaTransactionService.sensexWeeklyOptions;
            } else {
                strikeMasterMap1 = zerodhaTransactionService.sensexNextWeeklyOptions;
            }
        }
        if ("MC".equals(index)) {
            if (zerodhaTransactionService.midCpExpDate.equals(currentDate)) {
                strikeMasterMap1 = zerodhaTransactionService.midcpWeeklyOptions;
            }
            else {
                strikeMasterMap1 = zerodhaTransactionService.midcpNextWeeklyOptions;
            }
        }
        Map<String,Map<String,String>> strikeMasterMap=strikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,checkTime);
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
        Map<String, Map<String, StrikeData>> strikeMasterMap1 = strikeData(index,null,null);
        // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
        String stockId = strikeId(index);
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:15:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,checkTime);
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
                        int tempStrikeCE = atmStrike;
                        int tempStrikePE = atmStrike;
                        int i=0;
                        while (tempStrikeCE > 0 && i<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrikeCE)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("CE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(100);
                                int tempStrike1 =0;
                                    tempStrike1 = assessRangeWithRange25(index,"CE", closePrice, upperRange, lowerRange, tempStrikeCE, atmStrike, atmStrikesStraddle, ce);

                                if (tempStrike1 == tempStrikeCE) {
                                    try {
                                        final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(ce);
                                        if (stringStringMap != null) {
                                            //  LOGGER.info(key1 + ":" + value1);
                                            rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                            System.out.println(currentDate + ":" + stringStringMap.getKey());
                                        }
                                    }catch (Exception e){
                                        LOGGER.info("error:"+new Gson().toJson(ce)+":"+atmStrikesStraddle);
                                    }
                                    break;
                                }
                                tempStrikeCE = tempStrike1;
                            }
                            i++;
                        }
                    //    int tempStrike2 = atmStrike;
                        int j=0;
                        while (tempStrikePE > 0 && j<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle =strikeMasterMap.get(String.valueOf(tempStrikePE)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("PE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(100);
                                int tempStrike1 =0;
                                tempStrike1 = assessRangeWithRange25(index,"PE", closePrice, upperRange, lowerRange, tempStrikePE, atmStrike, atmStrikesStraddle, pe);

                                if (tempStrike1 == tempStrikePE) {
                                    try {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(pe);
                                    if(stringStringMap!=null){
                                        rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                        System.out.println(currentDate+":"+stringStringMap.getKey());
                                    }
                                    }catch (Exception e){
                                        LOGGER.info("error:"+new Gson().toJson(pe)+":"+atmStrikesStraddle);
                                    }

                                    break;
                                }
                                tempStrikePE = tempStrike1;
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

    public  Map<String, StrikeData> getPriceRangeSortedWithLowRange(String currentDate, int upperRange, int lowerRange, String checkTime, String index, TradeStrategy strategy) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        Map<String, Map<String, StrikeData>> strikeMasterMap1 = strikeData(index,null,null);
        // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
        String stockId = strikeId(index);
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:15:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,checkTime);
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
                        int tempStrikeCE = atmStrike;
                        int tempStrikePE = atmStrike;
                        int i=0;
                        while (tempStrikeCE > 0 && i<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrikeCE)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("CE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(100);
                                int tempStrike1 =0;
                                    tempStrike1 = assessRangeWithRange25(index,"CE", closePrice, upperRange, lowerRange, tempStrikeCE, atmStrike, atmStrikesStraddle, ce);

                                if (tempStrike1 == tempStrikeCE) {
                                    try {
                                        final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(ce);
                                        if (stringStringMap != null) {
                                            //  LOGGER.info(key1 + ":" + value1);
                                            rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                            System.out.println(currentDate + ":" + stringStringMap.getKey());
                                        }
                                    }catch (Exception e){
                                        LOGGER.info("error:"+new Gson().toJson(ce)+":"+atmStrikesStraddle);
                                    }
                                    break;
                                }
                                tempStrikeCE = tempStrike1;
                            }
                            i++;
                        }
                        //    int tempStrike2 = atmStrike;
                        int j=0;
                        while (tempStrikePE > 0 && j<10) {
                            final Map.Entry<String, StrikeData> atmStrikesStraddle =strikeMasterMap.get(String.valueOf(tempStrikePE)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                            if (atmStrikesStraddle.getKey().contains("PE")) {
                                double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate,checkTime,atmStrikesStraddle.getKey());
                                Thread.sleep(100);
                                int tempStrike1 = assessRangeWithRange25(index,"CE", closePrice, upperRange, lowerRange, tempStrikeCE, atmStrike, atmStrikesStraddle, ce);
                                if (tempStrike1 == tempStrikePE) {
                                    try {
                                        final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrike(pe);
                                        if(stringStringMap!=null){
                                            rangeStrike.put(stringStringMap.getKey(), stringStringMap.getValue());
                                            System.out.println(currentDate+":"+stringStringMap.getKey());
                                        }
                                    }catch (Exception e){
                                        LOGGER.info("error:"+new Gson().toJson(pe)+":"+atmStrikesStraddle);
                                    }

                                    break;
                                }
                                tempStrikePE = tempStrike1;
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
        Map<Double, Map<String, StrikeData>> rangeStrike = new HashMap<>();
        try {
            //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
            Map<String, Map<String, StrikeData>> strikeMasterMap1 = strikeData(index,null,null);
            // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
            String stockId = strikeId(index);
            Map<Double, Map.Entry<String, StrikeData>> ce = new HashMap<>();
            Map<Double, Map.Entry<String, StrikeData>> pe = new HashMap<>();
            //  Map<Double,Map.Entry<String, StrikeData>> cepe=new HashMap<>();
            Map<String, Map<String, StrikeData>> strikeMasterMap = strikeMasterMap1;
            //   Map<String,Map<String,String>> dhanStrikeMasterMap=dhanStrikeMasterMap1;
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), stockId, checkTime);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);

            String status = json.getString("status");
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                historicalData.dataArrayList.forEach(historicalData1 -> {
                    try {
                        Date openDatetime = sdf.parse(historicalData1.timeStamp);
                        String openDate = format.format(openDatetime);
                        if (sdf.format(openDatetime).equals(openDate + "T" + checkTime)) {/*"09:30:00"*/
                            int atmStrike = commonUtil.findATM((int) historicalData1.close);
                            int tempStrike = atmStrike - 300;
                            int i = 0;
                            while (tempStrike > 0 && i < 12) {
                                final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                                if (atmStrikesStraddle.getKey().contains("CE")) {
                                    double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate, checkTime, atmStrikesStraddle.getKey());
                                    closePrice = Math.round(closePrice * 20.0) / 20.0;
                                    Thread.sleep(100);
                                    int increment =100;
                                    if ("NF".equals(index) || "FN".equals(index)) {
                                        increment= 50;
                                    } else if ("MC".equals(index)) {
                                        increment= 25;
                                    }
                                    tempStrike = tempStrike + increment;
                                    ce.put(closePrice, atmStrikesStraddle);

                                }
                                i++;
                            }
                            selectClosestStrikePrice(ce, closePremium, rangeStrike);
                            int tempStrike2 = atmStrike +300;
                            int j = 0;
                            while (tempStrike2 > 0 && j < 12) {
                                final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike2)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                                if (atmStrikesStraddle.getKey().contains("PE")) {
                                    double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate, checkTime, atmStrikesStraddle.getKey());
                                    closePrice = Math.round(closePrice * 20.0) / 20.0;
                                    Thread.sleep(100);
                                    int increment =100;
                                    if ("NF".equals(index) || "FN".equals(index)) {
                                        increment= 50;
                                    } else if ("MC".equals(index)) {
                                        increment= 25;
                                    }
                                    tempStrike = tempStrike + increment;
                                    pe.put(closePrice, atmStrikesStraddle);
                                }
                                j++;
                            }
                            selectClosestStrikePrice(pe, closePremium, rangeStrike);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rangeStrike;
    }

    //use only for exp for now
    public Map<Double,Map<String, StrikeData>> getPriceCloseToPremium( String currentDate, int closePremium, String checkTime, String index, String byOptionStrikeType) {
        System.out.println("inside getPriceCloseToPremium");
        Map<Double, Map<String, StrikeData>> rangeStrike = new HashMap<>();
        try {
            //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
            Map<String, Map<String, StrikeData>> strikeMasterMap1 = strikeData(index,null,null);
            // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
            String stockId = strikeId(index);

            Map<Double, Map.Entry<String, StrikeData>> ce = new HashMap<>();
            Map<Double, Map.Entry<String, StrikeData>> pe = new HashMap<>();
            //  Map<Double,Map.Entry<String, StrikeData>> cepe=new HashMap<>();
            Map<String, Map<String, StrikeData>> strikeMasterMap = strikeMasterMap1;
            //   Map<String,Map<String,String>> dhanStrikeMasterMap=dhanStrikeMasterMap1;
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL), stockId, checkTime);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);

            String status = json.getString("status");
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                historicalData.dataArrayList.forEach(historicalData1 -> {
                    try {
                        Date openDatetime = sdf.parse(historicalData1.timeStamp);
                        String openDate = format.format(openDatetime);
                        if (sdf.format(openDatetime).equals(openDate + "T" + checkTime)) {/*"09:30:00"*/
                            int atmStrike = commonUtil.findATM((int) historicalData1.close);
                            System.out.println("closePremium:atm:"+atmStrike);
                            if ("CE".equals(byOptionStrikeType)) {
                                int tempStrike = atmStrike - 300;
                                int i = 0;
                                while (tempStrike > 0 && i < 12) {
                                    final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst().get();
                                    if (atmStrikesStraddle.getKey().contains("CE")) {
                                        double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate, checkTime, atmStrikesStraddle.getKey());
                                        closePrice = Math.round(closePrice * 20.0) / 20.0;
                                        Thread.sleep(100);
                                        int increment =100;
                                        if ("NF".equals(index) || "FN".equals(index)) {
                                            increment= 50;
                                        } else if ("MC".equals(index)) {
                                            increment= 25;
                                        }
                                        tempStrike = tempStrike + increment;
                                        ce.put(closePrice, atmStrikesStraddle);

                                    }
                                    i++;
                                }
                                selectClosestStrikePrice(ce, closePremium, rangeStrike);
                            }
                            if ("PE".equals(byOptionStrikeType)) {
                                int tempStrike2 = atmStrike + 300;
                                int j = 0;
                                while (tempStrike2 > 0 && j < 12) {
                                    final Map.Entry<String, StrikeData> atmStrikesStraddle = strikeMasterMap.get(String.valueOf(tempStrike2)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst().get();
                                    if (atmStrikesStraddle.getKey().contains("PE")) {
                                        double closePrice = callStrikeWithName(atmStrikesStraddle.getValue(), currentDate, checkTime, atmStrikesStraddle.getKey());
                                        closePrice = Math.round(closePrice * 20.0) / 20.0;
                                        Thread.sleep(100);
                                        int increment =100;
                                        if ("NF".equals(index) || "FN".equals(index)) {
                                            increment= 50;
                                        } else if ("MC".equals(index)) {
                                            increment= 25;
                                        }
                                        tempStrike2 = tempStrike2 + increment;
                                        pe.put(closePrice, atmStrikesStraddle);
                                    }
                                    j++;
                                }
                                selectClosestStrikePrice(pe, closePremium, rangeStrike);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("rangeStrike:"+new Gson().toJson(rangeStrike));
        return rangeStrike;
    }
    public Map<String, StrikeData>  getPriceRangeSortedWithLowRangeNifty( String currentDate, int upperRange, int lowerRange, String checkTime, String index) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        Map<String,Map<String, StrikeData>> strikeMasterMap1=strikeData(index,null,null);
        // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
        String stockId=strikeId(index);
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
     //   Map<String,Map<String,String>> dhanStrikeMasterMap=dhanStrikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,checkTime);
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
                                Thread.sleep(100);
                                int tempStrike1 = assessRangeWithRange25(index,"CE", closePrice, upperRange, lowerRange, tempStrike,atmStrike,atmStrikesStraddle,ce);
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
                                Thread.sleep(100);
                                int tempStrike1 = assessRangeWithRange25(index,"PE", closePrice, upperRange, lowerRange, tempStrike2,atmStrike,atmStrikesStraddle,pe);
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
    public String strikeId(String index){
        String stockId = null;
        if("BNF".equals(index)) {
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        }else if("NF".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
        }
        else if("FN".equals(index)){
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
        }

        if ("MC".equals(index)) {
            stockId = zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
        }
        if ("SS".equals(index)) {
            stockId = zerodhaTransactionService.niftyIndics.get("SENSEX");
        }
        return stockId;
    }
    public Map<String,Map<String, StrikeData>> strikeData(String index,String currentDate,String tradeValidity){
        Map<String,Map<String, StrikeData>> strikeMasterMap=new HashMap<>();
        if("BNF".equals(index)) {
            if (zerodhaTransactionService.bankBiftyExpDate.equals(currentDate) && TradeValidity.BTST.getValidity().equals(tradeValidity)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_NEXT.expiryName);
            }else {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.BNF_CURRENT.expiryName);
            }

        }else if("NF".equals(index)){
            if (zerodhaTransactionService.expDate.equals(currentDate) && TradeValidity.BTST.getValidity().equals(tradeValidity)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_NEXT.expiryName);
            }else {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.NF_CURRENT.expiryName);
            }
        }
        else if("FN".equals(index)) {
            if (zerodhaTransactionService.finExpDate.equals(currentDate) &&  TradeValidity.BTST.getValidity().equals(tradeValidity)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.FN_NEXT.expiryName);
            } else{
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.FN_CURRENT.expiryName);
        }
        }

        if ("MC".equals(index)) {
            if (zerodhaTransactionService.midCpExpDate.equals(currentDate) && TradeValidity.BTST.getValidity().equals(tradeValidity)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.MC_NEXT.expiryName);
            }else {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.MC_CURRENT.expiryName);
            }
        }
        if ("SS".equals(index)) {
            if (zerodhaTransactionService.sensexExpDate.equals(currentDate) && TradeValidity.BTST.getValidity().equals(tradeValidity)) {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.SS_NEXT.expiryName);
            }else {
                strikeMasterMap = zerodhaTransactionService.globalOptionsInfo.get(Expiry.SS_CURRENT.expiryName);
            }
        }
        return strikeMasterMap;
    }
    public Map<String, StrikeData>  getPriceRangeSortedWithLowRangeNifty( String currentDate, int upperRange, int lowerRange, String checkTime, String index,String breakSide,String cEorPE) {
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        Map<String,Map<String, StrikeData>> strikeMasterMap1=strikeData(index,null,null);
        // Map<String,Map<String,String>> dhanStrikeMasterMap1=new HashMap<>();
        String stockId=strikeId(index);
        Map<Double,Map.Entry<String, StrikeData>> ce=new HashMap<>();
        Map<Double,Map.Entry<String, StrikeData>> pe=new HashMap<>();
        Map<String,Map<String,StrikeData>> strikeMasterMap=strikeMasterMap1;
        //   Map<String,Map<String,String>> dhanStrikeMasterMap=dhanStrikeMasterMap1;
        String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL),stockId,checkTime);
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
                                Thread.sleep(100);
                                int tempStrike1 = assessRangeWithRange25(index,"CE", closePrice, upperRange, lowerRange, tempStrike,atmStrike,atmStrikesStraddle,ce);
                                if (tempStrike1 == tempStrike) {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrikeWithHigh(ce,breakSide);
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
                                Thread.sleep(100);
                                int tempStrike1 = assessRangeWithRange25(index,"PE", closePrice, upperRange, lowerRange, tempStrike2,atmStrike,atmStrikesStraddle,pe);
                                if (tempStrike1 == tempStrike2) {
                                    final Map.Entry<String, StrikeData> stringStringMap = getMinPremiumStrikeWithHigh(pe,breakSide);
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
    Map.Entry<String, StrikeData> getMinPremiumStrikeWithHigh(Map<Double,Map.Entry<String, StrikeData>> nonSorted,String entryType){
        Map.Entry<Double,Map.Entry<String, StrikeData>> min = null;
        for (Map.Entry<Double,Map.Entry<String, StrikeData>> entry : nonSorted.entrySet()) {
            if("BUY".equals(entryType)) {
                if (min == null || min.getKey() > entry.getKey()) {
                    min = entry;
                }
            }else {
                if (min == null || min.getKey() < entry.getKey()) {
                    min = entry;
                }
            }
        }
        return min.getValue();
    }

    public Map<Double, Map<String, StrikeData>> strikeSelection(String currentDate, TradeStrategy strategy, double close, String checkTime) {
        String strikeSelectionType = strategy.getStrikeSelectionType();
        String checkTime1 = strategy.getCandleCheckTime();
        System.out.println(checkTime1);
        String index = strategy.getIndex();
        Map<String, Map<String, StrikeData>> strikeMasterMap = strikeData(index,currentDate,strategy.getTradeValidity());
        int atmStrike = commonUtil.findATM((int) close);
        System.out.println("atmStrike:"+atmStrike);
        if (strikeSelectionType.equals(StrikeSelectionType.ATM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            Map<String, StrikeData> strikeDataMap1 = strikeMasterMap.get(String.valueOf(atmStrike));
            stringMapMap.put(close, strikeDataMap1);
            return stringMapMap;
        }
        if (strikeSelectionType.contains(StrikeSelectionType.OTM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale = strikeSelectionType.substring(3);
            // String sub=otmStrikeVale.substring(3);
            int otmValue = Integer.valueOf(otmStrikeVale) * 100;
            int ceValue = atmStrike + otmValue;
            int peValue = atmStrike - otmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapCe = strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(), strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapPe = strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(), strikeDataMapPe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(peValue)), stringMapPE);
            }
            return stringMapMap;
        }
        if (strikeSelectionType.contains(StrikeSelectionType.ITM.getType())) {
            System.out.println("ITM: check");
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale = strikeSelectionType.substring(3);
            // String sub=otmStrikeVale.substring(3);
            int itmValue = Integer.valueOf(otmStrikeVale) * 100;
            int ceValue = atmStrike - itmValue;
            int peValue = atmStrike + itmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapCe = strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(), strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapPe = strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(), strikeDataMapPe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(peValue)), stringMapPE);
            }
            return stringMapMap;
        }
        if (strikeSelectionType.equals(StrikeSelectionType.CLOSE_PREMUIM.getType())) {
            int closePremium = strategy.getStrikeClosestPremium().intValue();
            Map<Double, Map<String, StrikeData>> strikes = getPriceCloseToPremium(currentDate, closePremium, checkTime, index);
            return strikes;
        }
        if (strikeSelectionType.equals(StrikeSelectionType.PRICE_RANGE.getType())) {
            int high = strategy.getStrikePriceRangeHigh().intValue();
            int low = strategy.getStrikePriceRangeLow().intValue();
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            Map<String, StrikeData> rangeSelected;
            rangeSelected = getPriceRangeSortedWithLowRange(currentDate, high, low, checkTime, index, strategy);
            rangeSelected.entrySet().stream().forEach(atmNiftyStrikeMap -> {
                String historicPriceURL = "https://api.kite.trade/instruments/historical/" + atmNiftyStrikeMap.getValue().getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
                String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicPriceURL), atmNiftyStrikeMap.getValue().getZerodhaId(), checkTime);
                HistoricalData historicalPriceData = new HistoricalData();
                JSONObject priceJson = new JSONObject(priceResponse);
                String responseStatus = priceJson.getString("status");

                if (!responseStatus.equals("error")) {
                    historicalPriceData.parseResponse(priceJson);
                    historicalPriceData.dataArrayList.forEach(historicalDataPrice -> {

                        Date priceDatetime = null;
                        try {
                            priceDatetime = candleDateTimeFormat.parse(historicalDataPrice.timeStamp);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                        String priceDate = dateFormat.format(priceDatetime);
                        if (candleDateTimeFormat.format(priceDatetime).equals(priceDate + "T" + checkTime)) {
                            Map<String, StrikeData> strikeDataMap = new HashMap<>();
                            strikeDataMap.put(atmNiftyStrikeMap.getKey(), atmNiftyStrikeMap.getValue());
                            stringMapMap.put(historicalDataPrice.close, strikeDataMap);
                        }
                    });
                }
            });
            return stringMapMap;
        }
        return null;
    }
    public Map<Double, Map<String, StrikeData>> strikeSelection(String currentDate, TradeStrategy strategy, double close, String checkTime,String byOptionStrike) {
        String strikeSelectionType = strategy.getStrikeSelectionType();
        String checkTime1 = strategy.getCandleCheckTime();
      //  System.out.println(checkTime1);
        String index = strategy.getIndex();
        Map<String, Map<String, StrikeData>> strikeMasterMap = strikeData(index,currentDate,strategy.getTradeValidity());
        int atmStrike = commonUtil.findATM((int) close);
        System.out.println("atmStrike:"+atmStrike);
        if (strikeSelectionType.equals(StrikeSelectionType.ATM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            Map<String, StrikeData> strikeDataMap1 = strikeMasterMap.get(String.valueOf(atmStrike));
            stringMapMap.put(close, strikeDataMap1);
            return stringMapMap;
        }
        if (strikeSelectionType.contains(StrikeSelectionType.OTM.getType())) {
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale = strikeSelectionType.substring(3);
            // String sub=otmStrikeVale.substring(3);
            int otmValue = Integer.valueOf(otmStrikeVale) * 100;
            int ceValue = atmStrike + otmValue;
            int peValue = atmStrike - otmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapCe = strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(), strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapPe = strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(), strikeDataMapPe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(peValue)), stringMapPE);
            }
            return stringMapMap;
        }
        if (strikeSelectionType.contains(StrikeSelectionType.ITM.getType())) {
            System.out.println("ITM: check");
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            String otmStrikeVale = strikeSelectionType.substring(3);
            // String sub=otmStrikeVale.substring(3);
            int itmValue = Integer.valueOf(otmStrikeVale) * 100;
            int ceValue = atmStrike - itmValue;
            int peValue = atmStrike + itmValue;
            Optional<Map.Entry<String, StrikeData>> strikeDataMapCeOp = strikeMasterMap.get(String.valueOf(ceValue)).entrySet().stream().filter(map -> map.getKey().contains("CE")).findFirst();
            if (strikeDataMapCeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapCe = strikeDataMapCeOp.get();
                Map<String, StrikeData> stringMapCE = new HashMap<>();
                stringMapCE.put(strikeDataMapCe.getKey(), strikeDataMapCe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(ceValue)), stringMapCE);
            }
            Optional<Map.Entry<String, StrikeData>> strikeDataMapPeOp = strikeMasterMap.get(String.valueOf(peValue)).entrySet().stream().filter(map -> map.getKey().contains("PE")).findFirst();
            if (strikeDataMapPeOp.isPresent()) {
                Map.Entry<String, StrikeData> strikeDataMapPe = strikeDataMapPeOp.get();
                Map<String, StrikeData> stringMapPE = new HashMap<>();
                stringMapPE.put(strikeDataMapPe.getKey(), strikeDataMapPe.getValue());
                stringMapMap.put(Double.parseDouble(String.valueOf(peValue)), stringMapPE);
            }
            return stringMapMap;
        }
        if (strikeSelectionType.equals(StrikeSelectionType.CLOSE_PREMUIM.getType())) {
            int closePremium = strategy.getStrikeClosestPremium().intValue();
            System.out.println("CLOSE_PREMUIM: check:"+closePremium);
            Map<Double, Map<String, StrikeData>> strikes = getPriceCloseToPremium(currentDate, closePremium, checkTime, index,byOptionStrike);
            return strikes;
        }
        if (strikeSelectionType.equals(StrikeSelectionType.PRICE_RANGE.getType())) {
            int high = strategy.getStrikePriceRangeHigh().intValue();
            int low = strategy.getStrikePriceRangeLow().intValue();
            Map<Double, Map<String, StrikeData>> stringMapMap = new HashMap<>();
            Map<String, StrikeData> rangeSelected;
            rangeSelected = getPriceRangeSortedWithLowRange(currentDate, high, low, checkTime, index, strategy);
            rangeSelected.entrySet().stream().forEach(atmNiftyStrikeMap -> {
                String historicPriceURL = "https://api.kite.trade/instruments/historical/" + atmNiftyStrikeMap.getValue().getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
                String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicPriceURL), atmNiftyStrikeMap.getValue().getZerodhaId(), checkTime);
                HistoricalData historicalPriceData = new HistoricalData();
                JSONObject priceJson = new JSONObject(priceResponse);
                String responseStatus = priceJson.getString("status");

                if (!responseStatus.equals("error")) {
                    historicalPriceData.parseResponse(priceJson);
                    historicalPriceData.dataArrayList.forEach(historicalDataPrice -> {

                        Date priceDatetime = null;
                        try {
                            priceDatetime = candleDateTimeFormat.parse(historicalDataPrice.timeStamp);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                        String priceDate = dateFormat.format(priceDatetime);
                        if (candleDateTimeFormat.format(priceDatetime).equals(priceDate + "T" + checkTime)) {
                            Map<String, StrikeData> strikeDataMap = new HashMap<>();
                            strikeDataMap.put(atmNiftyStrikeMap.getKey(), atmNiftyStrikeMap.getValue());
                            stringMapMap.put(historicalDataPrice.close, strikeDataMap);
                        }
                    });
                }
            });
            return stringMapMap;
        }
        return null;
    }

    public static double calculateSMA(LinkedList<Double> closingPrices) {
        return closingPrices.stream()
                .collect(Collectors.averagingDouble(Double::doubleValue));
    }

    public static double calculateStandardDeviation(LinkedList<Double> closingPrices, double mean) {
        double sumDiffsSquared = closingPrices.stream()
                .mapToDouble(price -> Math.pow(price - mean, 2))
                .sum();
        return Math.sqrt(sumDiffsSquared / (closingPrices.size() - 1));
    }

    public static double calculatePercentile(double[] values, double percentile) {
        Arrays.sort(values);
        int index = (int) Math.ceil(percentile / 100.0 * values.length) - 1;
        return values[index];
    }
    public double callStrike(String strikeId, String currentDate,String checkTime) {
        String historicURLStrike = "https://api.kite.trade/instruments/historical/" + strikeId + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURLStrike),strikeId,checkTime);
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
    public double callStrikeWithName(StrikeData strikeData, String currentDate,String checkTime,String strikeName) {
        String historicURLStrike = "https://api.kite.trade/instruments/historical/" + strikeData.getZerodhaId() + "/minute?from=" + currentDate + "+09:00:00&to=" + currentDate + "+15:30:00";
        String priceResponse = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURLStrike),strikeData.getZerodhaId(),checkTime);
        LOGGER.info("URL"+historicURLStrike+" API response:"+strikeData.getZerodhaId()+":" + priceResponse);
        HistoricalData historicalPriceData = new HistoricalData();
        JSONObject priceJson = new JSONObject(priceResponse);
        String responseStatus = priceJson.getString("status");
        AtomicDouble closePrice = new AtomicDouble(0);
        if (!responseStatus.equals("error")) {
            historicalPriceData.parseResponse(priceJson);
            //   HistoricalData lastElement=historicalPriceData.dataArrayList.get(historicalPriceData.dataArrayList.size()-1);
            historicalPriceData.dataArrayList.forEach(historicalData1 -> {
                try {
                   // System.out.println(strikeName+":"+historicalData1.timeStamp);
                    Date openDatetime = sdf.parse(historicalData1.timeStamp);
                    String priceDate = format.format(openDatetime);
                    if (sdf.format(openDatetime).equals(priceDate + "T" + checkTime)) {
                        LOGGER.info("API last element price :" + strikeName + ":" + historicalData1.close);
                        closePrice.getAndSet(historicalData1.close);
                    }
                } catch (Exception e) {
                 //   System.out.println(strikeData.getZerodhaId()+":"+historicalData1.timeStamp);
                 //   System.out.println(strikeData.getZerodhaId()+":"+new Gson().toJson(historicalData1));
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

    public static int assessRangeWithRange25(String index,String strikeType, double closePrice, int upperRange, int lowerRange, int currentStrike,int atmStrike,Map.Entry<String, StrikeData> straddle,Map<Double,Map.Entry<String, StrikeData>> mapst) {
        int increment=100;
         if (index.equals("NF") || index.equals("FN")) {
             increment=50;
        }
        else if (index.equals("MC")) {
             increment=25;
        }
        if (strikeType.equals("CE")) {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                mapst.put(closePrice,straddle);
                if(atmStrike>currentStrike){
                    return currentStrike;
                }
                return currentStrike+increment;
            } else if (closePrice < lowerRange) {
                if (mapst.size()>0){
                    return currentStrike;
                }
                return currentStrike - increment;
            } else if (closePrice > upperRange) {
                return currentStrike + increment;
            }
        } else {
            if (closePrice <= upperRange && lowerRange <= closePrice) {
                mapst.put(closePrice,straddle);
                if(atmStrike<currentStrike){
                    return currentStrike;
                }
                return currentStrike-increment;
            } else if (closePrice < lowerRange) {
                if (mapst.size()>0){
                    return currentStrike;
                }
                return currentStrike + increment;
            } else if (closePrice > upperRange) {
                return currentStrike - increment;
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