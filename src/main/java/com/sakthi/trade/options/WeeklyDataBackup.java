package com.sakthi.trade.options;

import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.ZipUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
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
    SendMessage sendMessage;

    @Autowired
    ZipUtils zipUtils;

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    public Map<String,String> lsHoliday=new HashMap<>();

    public String currentExp(){
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
        if (lsHoliday.containsKey(weekExp)){
            calendar.add(DAY_OF_MONTH, -1);
            weekExp=format.format(calendar.getTime());
            log.info("Thursday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        System.out.printf(weekExp);
        return  weekExp;
    }

    @Scheduled(cron="${zerodha.data.backup}")
    public void dataBackUp() throws IOException, CsvValidationException {
        log.info("Expiry export started");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat year = new SimpleDateFormat("yyyy");
        SimpleDateFormat month = new SimpleDateFormat("MMM");
        Date currentDate=new Date();
        String currentExp=currentExp();
        Calendar calendarCurrent = Calendar.getInstance();
        calendarCurrent.add(DAY_OF_MONTH, -8);
        Date startDate=calendarCurrent.getTime();
        String path=trendPath+"/BANKNIFTY/"+year.format(currentDate);
        File f=new File(path);
        if(!f.exists()){
            f.mkdir();
        }
        String monthpath=path+"/"+month.format(currentDate);
        File monthFile=new File(monthpath);
        if(!monthFile.exists()){
            monthFile.mkdir();
        }
        String expPath=monthpath+"/"+currentExp;
        File expFolder=new File(expPath);
        if(!expFolder.exists()){
            expFolder.mkdir();
        }
        expFolder.setReadable(true); //read
        expFolder.setWritable(true); //write
        if(currentExp.equals(format.format(currentDate))){
            log.info("Expiry export date:"+currentExp);
            zerodhaTransactionService.bankNiftyWeeklyOptions.entrySet().stream().forEach( exp->{
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
                        writer = new PrintWriter(new File(expFolder+"/"+fileName+".json"));

                    writer.write(response);
                    writer.flush();
                    writer.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                });
            });
      /*  zipUtils.zipExpData(expFolder.getPath(),currentExp);
        File zipPath=new File(expFolder.getPath()+"/"+currentExp+".zip");*/
      //  sendMessage.sendDocumentToTelegram(zipPath,"1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o","-713214125");

        }
    }
}
