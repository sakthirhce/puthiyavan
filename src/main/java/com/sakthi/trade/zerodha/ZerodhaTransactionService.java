package com.sakthi.trade.zerodha;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

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
    public Map<String,String> lsSymbols=new HashMap<>();
    public Map<String,String> lsHoliday=new HashMap<>();
    public Map<String,String> niftyIndics=new HashMap<>();
    public Map<String,String> niftyVix  =new HashMap<>();
    public Map<String,Map<String,String>> bankNiftyWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> bankNiftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> niftyNextWeeklyOptions=new HashMap<>();
    public Map<String,Map<String,String>> niftyWeeklyOptions=new HashMap<>();
    public String expDate;
    @Autowired
    SendMessage sendMessage;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;
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

    public String nextWeekExpDate(Calendar currentWeekExpCal,boolean currentWeekExpOff,Map<String,String> lsHoliday){
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
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        return weekExp;
    }
    @Scheduled(cron="${zerodha.get.instrument}")
    public void getInstrument() throws IOException, CsvValidationException {
        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;

            CSVReader csvHolidayReader = new CSVReader(new FileReader(trendPath + "/trading_thursday_holiday_2021.csv"));
        String[] lineHoliday;
        while ((lineHoliday = csvHolidayReader.readNext()) != null) {
            lsHoliday.put(lineHoliday[0].trim(),lineHoliday[0].trim());
        }
        int i=0;

        while ((line = csvReader.readNext()) != null) {
            if (i>4) {
                lsSymbols.put(line[1].trim(),line[1].trim());
            }
            i++;
        }
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat formatMM = new SimpleDateFormat("MM-dd");
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
        if (dayadd > 0) {
            calendar.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd ==-2) {
            calendar.add(DAY_OF_MONTH, 5);
        } else if (dayadd < 0) {
            calendar.add(DAY_OF_MONTH, 6);
        }
        Date date = calendar.getTime();
        String weekExp=format.format(date);
        boolean currentWeekExpOff=false;
        if (lsHoliday.containsKey(weekExp)) {
            currentWeekExpOff=true;
        }
        if(currentWeekExpOff){
            calendar.add(DAY_OF_MONTH, -1);
            weekExp=format.format(calendar.getTime());
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        expDate=weekExp;
        String nextWeekExpDate=nextWeekExpDate(calendar,currentWeekExpOff,lsHoliday);
        String instrumentURI = baseURL+instrumentURL;
        String response=transactionService.callAPI(transactionService.createZerodhaGetRequest(instrumentURI));
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
        sendMessage.sendToTelegram("Total BNF current week expiry strike count :" + bankNiftyWeeklyOptions.size(), telegramToken,"-713214125");
        sendMessage.sendToTelegram("Total BNF Next Week expiry strike count :" + bankNiftyNextWeeklyOptions.size(), telegramToken,"-713214125");
    }
}
