package com.sakthi.trade.options;

import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.TelegramMessenger;

import com.sakthi.trade.util.ZippingDirectory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Slf4j
@Service
public class WeeklyDataBackup {
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    Calendar calendar = Calendar.getInstance();
    @Autowired
    TransactionService transactionService;

    @Value("${filepath.trend}")
    String trendPath;

    @Autowired
    TelegramMessenger sendMessage;


    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    public Map<String,String> lsHoliday=new HashMap<>();

    public String currentExp(){
        int dayadd = 5 - calendar.get(DAY_OF_WEEK);
        if (dayadd > 0) {
            calendar.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd < 0) {
            int dayadd1 =dayadd+7;
            calendar.add(DAY_OF_MONTH, dayadd1);
        }
        Date date = calendar.getTime();
        String weekExp=format.format(date);
        if (zerodhaTransactionService.lsHoliday.containsKey(weekExp)){
            calendar.add(DAY_OF_MONTH, -1);
            weekExp=format.format(calendar.getTime());
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        System.out.printf(weekExp);
        return  weekExp;
    }

    public String currentFNExp(){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Calendar finniftycalendar = Calendar.getInstance();
        int findayadd = 3 - finniftycalendar.get(DAY_OF_WEEK);
        if (findayadd > 0) {
            finniftycalendar.add(DAY_OF_MONTH, findayadd);
        }else if (findayadd < 0) {
            int findayadd1 =findayadd+7;
            finniftycalendar.add(DAY_OF_MONTH, findayadd1);
        }
        Date date = finniftycalendar.getTime();
        String weekExp=format.format(date);
        if (zerodhaTransactionService.lsHoliday.containsKey(weekExp)){
            finniftycalendar.add(DAY_OF_MONTH, -1);
            weekExp=format.format(finniftycalendar.getTime());
            log.info("Tuesday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        System.out.printf(weekExp);
        return  weekExp;
    }
    @Autowired
    ZippingDirectory zippingDirectory;

    @Autowired
    TelegramMessenger telegramClient;
    @Value("${telegram.straddle.bot.token}")
    String telegramToken;
    @Value("${telegram.orb.bot.token}")
    String telegramTokenGroup;
    @Scheduled(cron="${zerodha.data.backup}")
    public void dataBackUp() throws Exception {
        log.info("Expiry export started");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat year = new SimpleDateFormat("yyyy");
        SimpleDateFormat month = new SimpleDateFormat("MMM");
        Date currentDate=new Date();
        String currentExp=currentExp();
        String currentFNExp=currentFNExp();
        Calendar calendarCurrent = Calendar.getInstance();
        calendarCurrent.add(DAY_OF_MONTH, -8);
        Date startDate=calendarCurrent.getTime();
        String path=trendPath+"/BANKNIFTY/"+year.format(currentDate);
        String nPath=trendPath+"/NIFTY/"+year.format(currentDate);
        File f=new File(path);
        if(!f.exists()){
            f.mkdir();
        }
        File nf=new File(nPath);
        if(!nf.exists()){
            nf.mkdir();
        }
        String monthpath=path+"/"+month.format(currentDate);
        String nMonthpath=nPath+"/"+month.format(currentDate);
        File monthFile=new File(monthpath);
        File nmonthFile=new File(nMonthpath);

        if(!monthFile.exists()){
            monthFile.mkdir();
        }
        if(!nmonthFile.exists()){
            nmonthFile.mkdir();
        }
        String expPath=monthpath+"/"+currentExp;
        File expFolder=new File(expPath);
        if(!expFolder.exists()){
            expFolder.mkdir();
        }
        String nexpPath=nmonthFile+"/"+currentExp;
        File nexpFolder=new File(nexpPath);
        if(!nexpFolder.exists()){
            nexpFolder.mkdir();
        }
        expFolder.setReadable(true); //read
        expFolder.setWritable(true); //write
        nexpFolder.setReadable(true); //read
        nexpFolder.setWritable(true); //write
try {
    String fnPath = trendPath + "/FINNIFTY/" + year.format(currentDate);
    File fn = new File(fnPath);
    if (!fn.exists()) {
        fn.mkdir();
    }
    String fnmonthpath = fnPath + "/" + month.format(currentDate);
    File fnmonthFile = new File(fnmonthpath);
    if (!fnmonthFile.exists()) {
        fnmonthFile.mkdir();
    }
    String fnexpPath = fnmonthFile + "/" + currentFNExp;
    File fnexpFolder = new File(fnexpPath);
    if (!nexpFolder.exists()) {
        fnexpFolder.mkdir();
    }
    fnexpFolder.setReadable(true); //read
    fnexpFolder.setWritable(true); //write

    if (currentFNExp.equals(format.format(currentDate))) {
        String message="FN Expiry export date:" + currentExp;
        telegramClient.sendToTelegram(message,telegramTokenGroup, "-713214125");
        zerodhaTransactionService.finNiftyWeeklyOptions.entrySet().stream().forEach(exp -> {

            Map<String, String> map = exp.getValue();
            map.entrySet().stream().forEach(optionExp -> {
                String strikeNo = optionExp.getValue();
                String strikeKey = optionExp.getKey();
                String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentExp + "+15:30:00&oi=1";
                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                String fileName;
                if (strikeKey.contains("CE")) {
                    fileName = exp.getKey() + "CE";
                } else {
                    fileName = exp.getKey() + "PE";
                }

                PrintWriter writer = null;
                try {
                    writer = new PrintWriter(new File(fnexpFolder + "/" + fileName + ".json"));

                    writer.write(response);
                    writer.flush();
                    writer.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            });
        });
        zippingDirectory.test(fnexpPath, "FINNIFTY_"+format.format(currentDate));
        telegramClient.sendDocumentToTelegram(fnexpPath+"/FINNIFTY_"+format.format(currentDate)+".zip","FINNIFTY");
        FileUtils.deleteDirectory(new File(fnexpPath));
    }

}catch (Exception e){
    e.printStackTrace();
}
        if(currentExp.equals(format.format(currentDate))){
           // log.info("Expiry export date:"+currentExp);
            try {
                String message = "BNifty Expiry export date:" + currentExp;
                telegramClient.sendToTelegram(message, telegramTokenGroup, "-713214125");
                zerodhaTransactionService.bankNiftyWeeklyOptions.entrySet().stream().forEach(exp -> {
                    Map<String, String> map = exp.getValue();
                    map.entrySet().stream().forEach(optionExp -> {
                        String strikeNo = optionExp.getValue();
                        String strikeKey = optionExp.getKey();
                        String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentExp + "+15:30:00&oi=1";
                        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                        String fileName;
                        if (strikeKey.contains("CE")) {
                            fileName = exp.getKey() + "CE";
                        } else {
                            fileName = exp.getKey() + "PE";
                        }

                        PrintWriter writer = null;
                        try {
                            writer = new PrintWriter(new File(expFolder + "/" + fileName + ".json"));

                            writer.write(response);
                            writer.flush();
                            writer.close();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }

                    });
                });
                zippingDirectory.test(expPath,"BNIFTY_"+format.format(currentDate));
                telegramClient.sendDocumentToTelegram(expPath+"/BNIFTY_"+format.format(currentDate)+".zip","BNF");
                FileUtils.deleteDirectory(new File(expPath));

            }catch (Exception e){
                e.printStackTrace();
            }
               try{
            zerodhaTransactionService.niftyWeeklyOptions.entrySet().stream().forEach( exp->{
                Map<String,String> map=exp.getValue();
                map.entrySet().stream().forEach(optionExp -> {
                    String strikeNo = optionExp.getValue();
                    String strikeKey = optionExp.getKey();
                    String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentExp + "+15:30:00&oi=1";
                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                    String fileName;
                    if(strikeKey.contains("CE"))
                    {
                        fileName=exp.getKey()+"CE";
                    }else{
                        fileName=exp.getKey()+"PE";
                    }

                    PrintWriter writer =null;
                    try {
                        writer = new PrintWriter(new File(nexpFolder+"/"+fileName+".json"));

                        writer.write(response);
                        writer.flush();
                        writer.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                });
            });
                zippingDirectory.test(nexpPath,"NIFTY_"+format.format(currentDate));
                telegramClient.sendDocumentToTelegram(nexpPath+"/NIFTY_"+format.format(currentDate)+".zip","NIFTY");
                FileUtils.deleteDirectory(new File(nexpPath));
            }catch (Exception e){
                e.printStackTrace();
            }
                /*  zipUtils.zipExpData(expFolder.getPath(),currentExp);
        File zipPath=new File(expFolder.getPath()+"/"+currentExp+".zip");*/
      //  sendMessage.sendDocumentToTelegram(zipPath,"1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o","-713214125");

        }
    }
}
