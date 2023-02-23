package com.sakthi.trade.zerodha;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.zerodha.account.Expiry;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static java.time.DayOfWeek.THURSDAY;
import static java.time.temporal.TemporalAdjusters.lastInMonth;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Service
@Slf4j
public class ZerodhaTransactionService {

    @Autowired
    ZerodhaAccount account;

    @Value("${filepath.trend}")
    String trendPath;
    @Autowired
    TransactionService transactionService;
    @Value("${zerodha.url.quote}")
    String quoteURL;
    @Value("${zerodha.url.base}")
    String baseURL;
    @Value("${zerodha.url.ohlc}")
    String ohlcURL;
    @Value("${zerodha.url.historical}")
    String historicalURL;
    @Value("${zerodha.url.instrument}")
    String instrumentURL;
    @Value("${dhan.url.instrument}")
    String dhanInstrumentURL;
    public Map<String,String> lsSymbols=new HashMap<>();
    public Map<String,String> lsHoliday=new HashMap<>();
    public Map<String,String> lsFinHoliday=new HashMap<>();
    public Map<String,String> niftyIndics=new HashMap<>();

    public Map<String,Map<String,String>> currentFutures=new HashMap<>();
    public Map<String,String> nearFutures=new HashMap<>();
    public Map<String,String> niftyVix  =new HashMap<>();
    public Map<String,Map<String,String>> bankNiftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> bankNiftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> niftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> niftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> finNiftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> finNiftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> dhanBankNiftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> dhanFNiftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> dhanBankNiftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> dhanNiftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>>dhanNiftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String, Map<String, StrikeData>>>globalOptionsInfo=new HashMap<>();
    public String expDate;
    public String finExpDate;
    @Autowired
    TelegramMessenger sendMessage;

    @Autowired
    TradeSedaQueue tradeSedaQueue;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;
    public static final Logger LOGGER = LoggerFactory.getLogger(ZerodhaTransactionService.class.getName());
    public void olhc(){

        String historicURL = "https://api.kite.trade/instruments/historical/5633/5minute?from=2020-12-17+09:00:00&to=2020-12-17+10:30:00";
        String response=transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.println(response);
        try {
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/PreOpen_FO_17Dec2020.csv"));
            String[] line;
            int i = 0;
            String uri="";
            while ((line = csvReader.readNext()) != null) {
                if(i==1) {
                    uri = uri+"i=NSE:" + line[0];
                }
                else if(i>1){
                    uri = uri+"&i=NSE:" + line[0];
                }
                i++;
            }
            String ohlcQuotesURI ="https://api.kite.trade/quote/ohlc?"+uri;
            String response1=transactionService.callAPI(transactionService.createZerodhaGetRequest(ohlcQuotesURI));
            System.out.println(response1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Date nextWeekExpDate(Calendar currentWeekExpCal,boolean currentWeekExpOff,Map<String,String> lsHoliday){
        if(currentWeekExpOff){
            currentWeekExpCal.add(DAY_OF_MONTH, 1);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        currentWeekExpCal.add(DAY_OF_MONTH, 7);
        String weekExp=format.format(currentWeekExpCal.getTime());
        boolean nextWeekExpOff=false;
        if (lsHoliday.containsKey(weekExp)) {
            nextWeekExpOff=true;
        }
        if(nextWeekExpOff){
            currentWeekExpCal.add(DAY_OF_MONTH, -1);
            weekExp=format.format(currentWeekExpCal.getTime());
            LOGGER.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        return currentWeekExpCal.getTime();
    }
    @Value("${test.profile:false}")
    Boolean testProfile;
    @Scheduled(cron="${zerodha.get.instrument}")
    public void getInstrument() throws IOException, CsvValidationException {
        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;

            CSVReader csvHolidayReader = new CSVReader(new FileReader(trendPath + "/trading_thursday_holiday_2021.csv"));
        String[] lineHoliday;
        while ((lineHoliday = csvHolidayReader.readNext()) != null) {
            lsHoliday.put(lineHoliday[0].trim(),lineHoliday[0].trim());
        }
        CSVReader csvHolidayFinReader = new CSVReader(new FileReader(trendPath + "/trading_tuesday_holiday_2021.csv"));
        String[] lineFinHoliday;
        while ((lineFinHoliday = csvHolidayFinReader.readNext()) != null) {
            lsFinHoliday.put(lineFinHoliday[0].trim(),lineFinHoliday[0].trim());
        }
        int i=0;

        while ((line = csvReader.readNext()) != null) {
            if (i>4) {
                lsSymbols.put(line[1].trim(),line[1].trim());
            }
            i++;
        }
        Calendar calendar = Calendar.getInstance();
        Calendar finniftycalendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat formatMM = new SimpleDateFormat("MM-dd");
        SimpleDateFormat formatddMMM = new SimpleDateFormat("dd-MMM");
        if (formatMM.format(calendar.getTime()).equals("01-02") || formatMM.format(calendar.getTime()).equals("01-03")) {
            try {
                sendMessage.sendToTelegram("UPDATE HOLIDAY CALENDER FOR WEEKLY EXP DATE CALCULATION",telegramToken);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday
        int dayadd = 5 - calendar.get(DAY_OF_WEEK);
        int findayadd = 3 - finniftycalendar.get(DAY_OF_WEEK);
        if (dayadd > 0) {
            calendar.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd < 0) {
            int dayadd1 =dayadd+7;
            calendar.add(DAY_OF_MONTH, dayadd1);
        }
        if (findayadd > 0) {
            finniftycalendar.add(DAY_OF_MONTH, findayadd);
        }else if (findayadd < 0) {
            int findayadd1 =findayadd+7;
            finniftycalendar.add(DAY_OF_MONTH, findayadd1);
        }
        Date date = calendar.getTime();
        Date findate = finniftycalendar.getTime();
        String weekExp=format.format(date);
        String finWeekExp=format.format(findate);
        boolean currentWeekExpOff=false;
        if (lsHoliday.containsKey(weekExp)) {
            currentWeekExpOff=true;
        }
        if(currentWeekExpOff){
            calendar.add(DAY_OF_MONTH, -1);
            date=calendar.getTime();
            weekExp=format.format(date);
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        boolean currentFinWeekExpOff=false;
        if (lsFinHoliday.containsKey(finWeekExp)) {
            currentFinWeekExpOff=true;
        }
        if(currentFinWeekExpOff){
            finniftycalendar.add(DAY_OF_MONTH, -1);
            findate=finniftycalendar.getTime();
            finWeekExp=format.format(findate);
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        expDate=weekExp;
        finExpDate=finWeekExp;
        Date currentWeekExpDate=date;
        Date currentFinWeekExpDate=findate;
        Date monthlyExpDate=getMonthExpDay();
        boolean currentMonthlyExpOff=false;
        String monthlyExp=format.format(monthlyExpDate);
        if (lsHoliday.containsKey(monthlyExp)) {
            currentMonthlyExpOff=true;
        }
        if(currentMonthlyExpOff){
            Calendar monthlyExpCal = Calendar.getInstance();
            monthlyExpCal.setTime(monthlyExpDate);
            monthlyExpCal.add(DAY_OF_MONTH, -1);
            monthlyExpDate=monthlyExpCal.getTime();
            monthlyExp=format.format(monthlyExpDate);
            log.info("Monthly Exp falling on holiday. recalculated weekly exp date is:"+monthlyExp);
        }

        Date nextWeekExpDateRes=nextWeekExpDate(calendar,currentWeekExpOff,lsHoliday);
        String nextWeekExpDate=format.format(nextWeekExpDateRes);
        String instrumentURI = baseURL+instrumentURL;
        String response=null;
        if(!testProfile) {
            response = transactionService.callAPI(transactionService.createZerodhaGetRequest(instrumentURI));
        }else {
             response = transactionService.callAPI(transactionService.createZerodhaGetRequestTest(instrumentURI));
        }
        String[] lines = response.split("\\r?\\n");
        System.out.println("output:"+ lines.length);
        for ( int j=0; j< lines.length;j++){
            String[] data =lines[j].split(",");

            if(lsSymbols.get(data[2].trim())!=null && data[9].equals("EQ") && data[11].equals("NSE")){
               // System.out.println(lines[j]);
                lsSymbols.put(data[2].trim(),data[0].trim());
            }
            if( data[9].equals("EQ") && data[10].equals("INDICES") && data[11].equals("NSE") && data[2].contains("VIX")){
                // System.out.println(lines[j]);
                niftyVix.put(data[2].trim(),data[0].trim());
            }
            if( data[9].equals("EQ") && data[10].equals("INDICES") && data[11].equals("NSE")){
                  niftyIndics.put(data[2].trim(),data[0].trim());
            }
            String index = data[3].replace("\"", "");
            if( index.equals("BANKNIFTY") &&  data[5].equals(weekExp) &&  data[10].equals("NFO-OPT") && data[11].equals("NFO")){
                if(bankNiftyWeeklyOptions.get(data[6])!=null){
                    Map<String,String> map=bankNiftyWeeklyOptions.get(data[6]);
                    map.put(data[2],data[0]);
                    bankNiftyWeeklyOptions.put(data[6], map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    bankNiftyWeeklyOptions.put(data[6], map);
                }
            }
            if( index.equals("FINNIFTY") &&  data[5].equals(finWeekExp) &&  data[10].equals("NFO-OPT") && data[11].equals("NFO")){
                if(finNiftyWeeklyOptions.get(data[6])!=null){
                    Map<String,String> map=finNiftyWeeklyOptions.get(data[6]);
                    map.put(data[2],data[0]);
                    finNiftyWeeklyOptions.put(data[6], map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    finNiftyWeeklyOptions.put(data[6], map);
                }
            }
            if( index.equals("FINNIFTY") &&  data[5].equals(nextWeekExpDate) &&  data[10].equals("NFO-OPT") && data[11].equals("NFO")){
                if(finNiftyNextWeeklyOptions.get(data[6])!=null){
                    Map<String,String> map=finNiftyNextWeeklyOptions.get(data[6]);
                    map.put(data[2],data[0]);
                    finNiftyNextWeeklyOptions.put(data[6], map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    finNiftyNextWeeklyOptions.put(data[6], map);
                }
            }
            if( index.equals("BANKNIFTY") &&  data[5].equals(nextWeekExpDate) &&  data[10].equals("NFO-OPT") && data[11].equals("NFO")){
                if(bankNiftyNextWeeklyOptions.get(data[6])!=null){
                    Map<String,String> map=bankNiftyNextWeeklyOptions.get(data[6]);
                    map.put(data[2],data[0]);
                    bankNiftyNextWeeklyOptions.put(data[6], map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    bankNiftyNextWeeklyOptions.put(data[6], map);
                }
            }
            if(data[5].equals(monthlyExp) &&  data[10].equals("NFO-FUT") && data[11].equals("NFO")){
                if(currentFutures.get(index)!=null){
                    Map<String,String> map=currentFutures.get(index);
                    map.put(data[2],data[0]);
                    currentFutures.put(index, map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    currentFutures.put(index, map);
                }
            }
            if( index.equals("NIFTY") &&  data[5].equals(weekExp) &&  data[10].equals("NFO-OPT") && data[11].equals("NFO")){
                if(niftyWeeklyOptions.get(data[6])!=null){
                    Map<String,String> map=niftyWeeklyOptions.get(data[6]);
                    map.put(data[2],data[0]);
                    niftyWeeklyOptions.put(data[6], map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    niftyWeeklyOptions.put(data[6], map);
                }
            }
            if( index.equals("NIFTY") &&  data[5].equals(nextWeekExpDate) &&  data[10].equals("NFO-OPT") && data[11].equals("NFO")){
                if(niftyNextWeeklyOptions.get(data[6])!=null){
                    Map<String,String> map=niftyNextWeeklyOptions.get(data[6]);
                    map.put(data[2],data[0]);
                    niftyNextWeeklyOptions.put(data[6], map);
                }else {
                    Map<String,String> map=new HashMap<>();
                    map.put(data[2],data[0]);
                    niftyNextWeeklyOptions.put(data[6], map);
                }
            }

        }

     /*lsSymbols.entrySet().parallelStream().forEach(map->{
            System.out.println(map.getKey()+":"+map.getValue());
        });*/
        System.out.println(bankNiftyWeeklyOptions.size());
        sendMessage.sendToTelegram("Total BNF current week expiry strike count :" + bankNiftyWeeklyOptions.size(), telegramToken,"-646157933");
        sendMessage.sendToTelegram("Total BNF Next Week expiry strike count :" + bankNiftyNextWeeklyOptions.size(), telegramToken,"-646157933");
        sendMessage.sendToTelegram("Total Fin nifty expiry strike count:" + finNiftyWeeklyOptions.size(), telegramToken,"-646157933");
        sendMessage.sendToTelegram("Total BNF Futures strike Count for monthly exp :" +monthlyExp+":"+ currentFutures.size(), telegramToken,"-646157933");
        try {
            tradeSedaQueue.sendTelemgramSeda("Total BNF current week expiry strike count :" + bankNiftyWeeklyOptions.size());
        }catch (Exception e){
            e.printStackTrace();
        }
        try{
       String dhanResponse=transactionService.downloadInstrumentData(dhanInstrumentURL);
       String[] dhanlines = dhanResponse.split("\\r?\\n");
       System.out.println("dhan output:"+ dhanResponse.length());
       for ( int j=0; j< dhanlines.length;j++){
           String[] data =dhanlines[j].split(",");
           String index = data[0].replace("\"", "");
           String instrumentName = data[3].replace("\"", "");
           String securityId = data[2].replace("\"", "");
           String securitySymbol = data[5].replace("\"", "");
           String securityCustomSymbol = data[7].replace("\"", "");
           String[] splited = securityCustomSymbol.split("\\s+");
           String dhanCurrentWeekExpDate=formatddMMM.format(currentWeekExpDate).toUpperCase();
           String dhanFinCurrentWeekExpDate=formatddMMM.format(currentFinWeekExpDate).toUpperCase();
           String dhanNextWeekExpDate=formatddMMM.format(nextWeekExpDateRes).toUpperCase();
           if(index.equals("NSE") && instrumentName.equals("OPTIDX")){
               String strikeExpDate=splited[1]+"-"+splited[2];
               if(dhanFinCurrentWeekExpDate.equals(strikeExpDate)){
                   if ("FINNIFTY".equals(splited[0])) {
                       if(dhanFNiftyWeeklyOptions.get(splited[3])!=null){
                           Map<String,String> map=dhanFNiftyWeeklyOptions.get(splited[3]);
                           map.put(data[5],data[2]);
                           dhanFNiftyWeeklyOptions.put(splited[3], map);
                       }else {
                           Map<String,String> map=new HashMap<>();
                           map.put(data[5],data[2]);
                           dhanFNiftyWeeklyOptions.put(splited[3], map);
                       }
                   }
               }
                   if (dhanCurrentWeekExpDate.equals(strikeExpDate)) {
                       if ("BANKNIFTY".equals(splited[0])) {
                           if(dhanBankNiftyWeeklyOptions.get(splited[3])!=null){
                               Map<String,String> map=dhanBankNiftyWeeklyOptions.get(splited[3]);
                               map.put(data[5],data[2]);
                               dhanBankNiftyWeeklyOptions.put(splited[3], map);
                           }else {
                               Map<String,String> map=new HashMap<>();
                               map.put(data[5],data[2]);
                               dhanBankNiftyWeeklyOptions.put(splited[3], map);
                           }
                       }
                       if("NIFTY".equals(splited[0])) {
                           if(dhanNiftyWeeklyOptions.get(splited[3])!=null){
                               Map<String,String> map=dhanNiftyWeeklyOptions.get(splited[3]);
                               map.put(data[5],data[2]);
                               dhanNiftyWeeklyOptions.put(splited[3], map);
                           }else {
                               Map<String,String> map=new HashMap<>();
                               map.put(data[5],data[2]);
                               dhanNiftyWeeklyOptions.put(splited[3], map);
                           }
                       }
                   }else if(dhanNextWeekExpDate.equals(strikeExpDate)) {
                       if ("BANKNIFTY".equals(splited[0])) {
                           if(dhanBankNiftyNextWeeklyOptions.get(splited[3])!=null){
                               Map<String,String> map=dhanBankNiftyNextWeeklyOptions.get(splited[3]);
                               map.put(data[5],data[2]);
                               dhanBankNiftyNextWeeklyOptions.put(splited[3], map);
                           }else {
                               Map<String,String> map=new HashMap<>();
                               map.put(data[5],data[2]);
                               dhanBankNiftyNextWeeklyOptions.put(splited[3], map);
                           }
                       }
                       if("NIFTY".equals(splited[0])) {
                           if(dhanNiftyNextWeeklyOptions.get(splited[3])!=null){
                               Map<String,String> map=dhanNiftyNextWeeklyOptions.get(splited[3]);
                               map.put(data[5],data[2]);
                               dhanNiftyNextWeeklyOptions.put(splited[3], map);
                           }else {
                               Map<String,String> map=new HashMap<>();
                               map.put(data[5],data[2]);
                               dhanNiftyNextWeeklyOptions.put(splited[3], map);
                           }
                       }
                   }
           }

       }
       // System.out.println(bankNiftyWeeklyOptions.size());
       sendMessage.sendToTelegram("Total Dhan BNF current week expiry strike count :" + dhanBankNiftyWeeklyOptions.size(), telegramToken,"-646157933");
       sendMessage.sendToTelegram("Total Dhan BNF Next Week expiry strike count :" + dhanBankNiftyNextWeeklyOptions.size(), telegramToken,"-646157933");
       sendMessage.sendToTelegram("Total Dhan FN Week expiry strike count :" + dhanFNiftyWeeklyOptions.size(), telegramToken,"-646157933");
       //  sendMessage.sendToTelegram("Total Dhan BNF Futures strike Count for monthly exp :" +monthlyExp+":"+ currentFutures.size(), telegramToken,"-713214125");
       sendMessage.sendToTelegram("Total Dhan NF current week expiry strike count :" + dhanNiftyWeeklyOptions.size(), telegramToken,"-646157933");
       sendMessage.sendToTelegram("Total Dhan NF Next Week expiry strike count :" + dhanNiftyNextWeeklyOptions.size(), telegramToken,"-646157933");
       //   sendMessage.sendToTelegram("Total Dhan NF Futures strike Count for monthly exp :" +monthlyExp+":"+ currentFutures.size(), telegramToken,"-713214125");
       Map<String,Map<String,StrikeData>> strikeFNLevelMap=new HashMap<>();

       finNiftyWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
           Map<String,StrikeData> strikeTypeMap=new HashMap<>();
           Map<String,String> dhanData=dhanFNiftyWeeklyOptions.get(stringMapEntry.getKey());
           stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
               StrikeData strikeData=new StrikeData();
               strikeData.setStrike(stringMapEntry.getKey());
               strikeData.setZerodhaSymbol(stringStringEntry.getKey());
               strikeData.setZerodhaId(stringStringEntry.getValue());
               if(stringStringEntry.getKey().contains("CE")) {
                   strikeData.setStrikeType("CE");
                   Map.Entry<String,String> dhanCE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("CE")).findFirst().get();
                   strikeData.setDhanId(dhanCE.getValue());
                   strikeData.setDhanSymbol(dhanCE.getKey());
               }
               else {
                   strikeData.setStrikeType("PE");
                   Map.Entry<String,String> dhanPE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("PE")).findFirst().get();
                   strikeData.setDhanId(dhanPE.getValue());
                   strikeData.setDhanSymbol(dhanPE.getKey());

               }
               strikeTypeMap.put(strikeData.getStrikeType(),strikeData);
           });
           strikeFNLevelMap.put(stringMapEntry.getKey(),strikeTypeMap);
       });
       globalOptionsInfo.put(Expiry.FN_CURRENT.expiryName,strikeFNLevelMap);
       Map<String,Map<String,StrikeData>> strikeLevelMap=new HashMap<>();
       globalOptionsInfo.put(Expiry.BNF_CURRENT.expiryName,strikeLevelMap);
       bankNiftyWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
           Map<String,StrikeData> strikeTypeMap=new HashMap<>();
           Map<String,String> dhanData=dhanBankNiftyWeeklyOptions.get(stringMapEntry.getKey());
           stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
               StrikeData strikeData=new StrikeData();
               strikeData.setStrike(stringMapEntry.getKey());
               strikeData.setZerodhaSymbol(stringStringEntry.getKey());
               strikeData.setZerodhaId(stringStringEntry.getValue());
               if(stringStringEntry.getKey().contains("CE")) {
                   strikeData.setStrikeType("CE");
                   Map.Entry<String,String> dhanCE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("CE")).findFirst().get();
                   strikeData.setDhanId(dhanCE.getValue());
                   strikeData.setDhanSymbol(dhanCE.getKey());
               }
               else {
                   strikeData.setStrikeType("PE");
                   Map.Entry<String,String> dhanPE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("PE")).findFirst().get();
                   strikeData.setDhanId(dhanPE.getValue());
                   strikeData.setDhanSymbol(dhanPE.getKey());

               }
                   strikeTypeMap.put(strikeData.getStrikeType(),strikeData);
               });
           strikeLevelMap.put(stringMapEntry.getKey(),strikeTypeMap);
           });
       globalOptionsInfo.put(Expiry.BNF_CURRENT.expiryName,strikeLevelMap);
       Map<String,Map<String,StrikeData>> bnfStrikeNextWeekLevelMap=new HashMap<>();
       bankNiftyNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
           Map<String,StrikeData> strikeTypeMap=new HashMap<>();
           Map<String,String> dhanData=dhanBankNiftyNextWeeklyOptions.get(stringMapEntry.getKey());
           stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
               StrikeData strikeData=new StrikeData();
               strikeData.setStrike(stringMapEntry.getKey());
               strikeData.setZerodhaSymbol(stringStringEntry.getKey());
               strikeData.setZerodhaId(stringStringEntry.getValue());
               if(stringStringEntry.getKey().contains("CE")) {
                   strikeData.setStrikeType("CE");
                   Map.Entry<String,String> dhanCE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("CE")).findFirst().get();
                   strikeData.setDhanId(dhanCE.getValue());
                   strikeData.setDhanSymbol(dhanCE.getKey());
               }
               else {
                   strikeData.setStrikeType("PE");
                   Map.Entry<String,String> dhanPE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("PE")).findFirst().get();
                   strikeData.setDhanId(dhanPE.getValue());
                   strikeData.setDhanSymbol(dhanPE.getKey());

               }
               strikeTypeMap.put(strikeData.getStrikeType(),strikeData);
           });
           bnfStrikeNextWeekLevelMap.put(stringMapEntry.getKey(),strikeTypeMap);
       });
       globalOptionsInfo.put(Expiry.BNF_NEXT.expiryName,bnfStrikeNextWeekLevelMap);
       Map<String,Map<String,StrikeData>> nfstrikeLevelMap=new HashMap<>();
       niftyWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
           Map<String,StrikeData> strikeTypeMap=new HashMap<>();
           Map<String,String> dhanData=dhanNiftyWeeklyOptions.get(stringMapEntry.getKey());
           stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
               StrikeData strikeData=new StrikeData();
               strikeData.setStrike(stringMapEntry.getKey());
               strikeData.setZerodhaSymbol(stringStringEntry.getKey());
               strikeData.setZerodhaId(stringStringEntry.getValue());
               if(stringStringEntry.getKey().contains("CE")) {
                   strikeData.setStrikeType("CE");
                   Map.Entry<String,String> dhanCE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("CE")).findFirst().get();
                   strikeData.setDhanId(dhanCE.getValue());
                   strikeData.setDhanSymbol(dhanCE.getKey());
               }
               else {
                   strikeData.setStrikeType("PE");
                   Map.Entry<String,String> dhanPE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("PE")).findFirst().get();
                   strikeData.setDhanId(dhanPE.getValue());
                   strikeData.setDhanSymbol(dhanPE.getKey());

               }
               strikeTypeMap.put(strikeData.getStrikeType(),strikeData);
           });
           nfstrikeLevelMap.put(stringMapEntry.getKey(),strikeTypeMap);
       });
       globalOptionsInfo.put(Expiry.NF_CURRENT.expiryName,nfstrikeLevelMap);
       Map<String,Map<String,StrikeData>> nfStrikeNextWeekLevelMap=new HashMap<>();
       niftyNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
           Map<String,StrikeData> strikeTypeMap=new HashMap<>();
           Map<String,String> dhanData=dhanNiftyNextWeeklyOptions.get(stringMapEntry.getKey());
           stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
               StrikeData strikeData=new StrikeData();
               strikeData.setStrike(stringMapEntry.getKey());
               strikeData.setZerodhaSymbol(stringStringEntry.getKey());
               strikeData.setZerodhaId(stringStringEntry.getValue());
               if(stringStringEntry.getKey().contains("CE")) {
                   strikeData.setStrikeType("CE");
                   Map.Entry<String,String> dhanCE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("CE")).findFirst().get();
                   strikeData.setDhanId(dhanCE.getValue());
                   strikeData.setDhanSymbol(dhanCE.getKey());
               }
               else {
                   strikeData.setStrikeType("PE");
                   Map.Entry<String,String> dhanPE= dhanData.entrySet().stream().filter(key-> key.getKey().contains("PE")).findFirst().get();
                   strikeData.setDhanId(dhanPE.getValue());
                   strikeData.setDhanSymbol(dhanPE.getKey());

               }
               strikeTypeMap.put(strikeData.getStrikeType(),strikeData);
           });
           nfStrikeNextWeekLevelMap.put(stringMapEntry.getKey(),strikeTypeMap);
       });
       globalOptionsInfo.put(Expiry.NF_NEXT.expiryName,nfStrikeNextWeekLevelMap);
    //   System.out.println("global data:"+new Gson().toJson(globalOptionsInfo));
   }catch (Exception e){
       e.printStackTrace();
   }
    }
   /* public static void main(String args[]){
        ZerodhaTransactionService zerodhaTransactionService=new ZerodhaTransactionService();
        zerodhaTransactionService.getMonthExpDay();
    }*/
    public Date getMonthExpDay() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM");// 01-12
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");// 01-12
        LocalDate expDateTemp=getLastThursday(Integer.parseInt(monthFormat.format(cal.getTime())),Integer.parseInt(yearFormat.format(cal.getTime())));
       // String monthlyExpTemp1=format.format(expDateTemp);
        int expDayTemp=expDateTemp.getDayOfMonth();
       int curDayTemp=LocalDate.now().getDayOfMonth();
        //Calendar calCurTemp = Calendar.getInstance();
        if(curDayTemp>=expDayTemp){
            int month=Integer.parseInt(monthFormat.format(cal.getTime()));
            int year=Integer.parseInt(yearFormat.format(cal.getTime()));
            if(month==12){
                month=1;
                year=year+1;
            }else {
                month=month+1;
            }
            LocalDate date= getLastThursday(month,year);
            return Date.from(date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        }
        return Date.from(expDateTemp.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    public LocalDate getLastThursday(int month, int year){
        LocalDate lastThursday = LocalDate.of(year, month, 1).with(lastInMonth(THURSDAY));
        if (lsHoliday.containsKey(lastThursday)) {
            lastThursday.minusDays(1);
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+lastThursday);
        }
        return lastThursday;
    }
}
