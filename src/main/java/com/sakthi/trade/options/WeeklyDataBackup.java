package com.sakthi.trade.options;

import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.telegram.TelegramMessenger;

import com.sakthi.trade.util.ZippingDirectory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Value("${data.export}")
    boolean dataExport;

    @Autowired
    TelegramMessenger sendMessage;
    public static final Logger LOGGER = LoggerFactory.getLogger(WeeklyDataBackup.class.getName());

 //   SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
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
        if (zerodhaTransactionService.lsFinHoliday.containsKey(weekExp)){
            finniftycalendar.add(DAY_OF_MONTH, -1);
            weekExp=format.format(finniftycalendar.getTime());
            log.info("Tuesday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        System.out.printf(weekExp);
        return  weekExp;
    }

    public String currentBSEExp(){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Calendar finniftycalendar = Calendar.getInstance();
        int findayadd = 6 - finniftycalendar.get(DAY_OF_WEEK);
        if (findayadd > 0) {
            finniftycalendar.add(DAY_OF_MONTH, findayadd);
        }else if (findayadd < 0) {
            int findayadd1 =findayadd+7;
            finniftycalendar.add(DAY_OF_MONTH, findayadd1);
        }
        Date date = finniftycalendar.getTime();
        String weekExp=format.format(date);
        if (zerodhaTransactionService.lsSensexHoliday.containsKey(weekExp)){
            finniftycalendar.add(DAY_OF_MONTH, -1);
            weekExp=format.format(finniftycalendar.getTime());
            log.info("friday falling on holiday. recalculated weekly exp date is:"+weekExp);
        }
        System.out.printf(weekExp);
        return  weekExp;
    }
    @Autowired
    ZippingDirectory zippingDirectory;

    @Autowired
    TelegramMessenger telegramClient;
    @Value("${telegram.orb.bot.token}")
    String telegramTokenGroup;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    @Scheduled(cron="${app.log.backup}")
    public void logBackUp() throws Exception {
        Date currentDate=new Date();
        String message="log export date:" + format.format(currentDate);
        LOGGER.info(message);
        telegramClient.sendToTelegram(message,telegramTokenGroup);
        telegramClient.sendLogToTelegram("/home/ubuntu/application1.log",format.format(currentDate));
    }
    @Scheduled(cron="${zerodha.data.backup}")
    public void dataBackUp() throws Exception {
        if (dataExport) {
            LOGGER.info("Expiry export started");
            SimpleDateFormat year = new SimpleDateFormat("yyyy");
            SimpleDateFormat month = new SimpleDateFormat("MMM");
            Date currentDate = new Date();
            String currentExp = currentExp();
            String currentFNExp = currentFNExp();
            String currentBNFExp = zerodhaTransactionService.bankBiftyExpDate;
            String currentMidcpExp = zerodhaTransactionService.midCpExpDate;
            String currentBSEExp = currentBSEExp();
            Calendar calendarCurrent = Calendar.getInstance();
            calendarCurrent.add(DAY_OF_MONTH, -8);
            Date startDate = calendarCurrent.getTime();
            String path = trendPath + "/BANKNIFTY/" + year.format(currentDate);
            String nPath = trendPath + "/NIFTY/" + year.format(currentDate);
            String bsePath = trendPath + "/BSE/" + year.format(currentDate);
            String midcpPath = trendPath + "/MIDCP/" + year.format(currentDate);
            File f = new File(path);
            if (!f.exists()) {
                f.mkdir();
            }
            File nf = new File(nPath);
            if (!nf.exists()) {
                nf.mkdir();
            }
            File sensexf = new File(bsePath);
            if (!sensexf.exists()) {
                sensexf.mkdir();
            }
            File midCpf = new File(midcpPath);
            if (!midCpf.exists()) {
                midCpf.mkdir();
            }

            String monthpath = path + "/" + month.format(currentDate);
            String nMonthpath = nPath + "/" + month.format(currentDate);
            String sMonthpath = sensexf + "/" + month.format(currentDate);
            String mMonthpath = midCpf + "/" + month.format(currentDate);
            File monthFile = new File(monthpath);
            File nmonthFile = new File(nMonthpath);
            File smonthFile = new File(sMonthpath);
            File mmonthFile = new File(mMonthpath);
            if (!monthFile.exists()) {
                monthFile.mkdir();
            }
            if (!smonthFile.exists()) {
                smonthFile.mkdir();
            }
            if (!nmonthFile.exists()) {
                nmonthFile.mkdir();
            }
            if (!mmonthFile.exists()) {
                mmonthFile.mkdir();
            }
            String expPath = monthpath + "/" + currentBNFExp;
            File expFolder = new File(expPath);
            if (!expFolder.exists()) {
                expFolder.mkdir();
            }
            String mexpPath = mmonthFile + "/" + currentMidcpExp;
            File mexpFolder = new File(mexpPath);
            if (!mexpFolder.exists()) {
                mexpFolder.mkdir();
            }
            String nexpPath = nmonthFile + "/" + currentExp;
            File nexpFolder = new File(nexpPath);
            if (!nexpFolder.exists()) {
                nexpFolder.mkdir();
            }
            String sensexpPath = smonthFile + "/" + currentBSEExp;
            File sensexexpFolder = new File(sensexpPath);
            if (!sensexexpFolder.exists()) {
                System.out.println("inside folder creation:");
                boolean b = sensexexpFolder.mkdir();
                System.out.println("inside folder creation:" + b);
            }
            System.out.println(sensexexpFolder);
            expFolder.setReadable(true); //read
            expFolder.setWritable(true); //write
            nexpFolder.setReadable(true); //read
            nexpFolder.setWritable(true); //write
            sensexexpFolder.setReadable(true); //read
            sensexexpFolder.setWritable(true); //write
            mexpFolder.setReadable(true); //read
            mexpFolder.setWritable(true); //write
            try {
                String message = "midcap Expiry export date:" + currentMidcpExp;
                if (currentMidcpExp.equals(format.format(currentDate))) {
                    // String message = "midcap Expiry export date:" + currentMidcpExp;
                    telegramClient.sendToTelegram(message, telegramTokenGroup, "-646157933");
                    zerodhaTransactionService.midcpWeeklyOptions.entrySet().stream().forEach(exp -> {

                        Map<String, String> map = exp.getValue();
                        map.entrySet().stream().forEach(optionExp -> {
                            String strikeNo = optionExp.getValue();
                            String strikeKey = optionExp.getKey();
                            String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentMidcpExp + "+15:30:00&oi=1";
                            System.out.println(historicURL);
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequestWithoutLog(historicURL));
                            System.out.println(response);
                            String fileName;
                            if (strikeKey.contains("CE")) {
                                fileName = exp.getKey() + "CE";
                            } else {
                                fileName = exp.getKey() + "PE";
                            }

                            PrintWriter writer = null;
                            try {
                                writer = new PrintWriter(new File(mexpFolder + "/" + fileName + ".json"));

                                writer.write(response);
                                writer.flush();
                                writer.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        });
                    });
                    zippingDirectory.test(mexpPath, "MIDCP_" + format.format(currentDate));
                    telegramClient.sendDocumentToTelegram(mexpPath + "/MIDCP_" + format.format(currentDate) + ".zip", "MIDCP_" + format.format(currentDate));
                    // FileUtils.deleteDirectory(new File(sensexpPath));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (currentBSEExp.equals(format.format(currentDate))) {
                    String message = "sensex Expiry export date:" + currentBSEExp;
                    telegramClient.sendToTelegram(message, telegramTokenGroup, "-646157933");
                    zerodhaTransactionService.sensexWeeklyOptions.entrySet().stream().forEach(exp -> {

                        Map<String, String> map = exp.getValue();
                        map.entrySet().stream().forEach(optionExp -> {
                            String strikeNo = optionExp.getValue();
                            String strikeKey = optionExp.getKey();
                            String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentBSEExp + "+15:30:00&oi=1";
                            System.out.println(historicURL);
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequestWithoutLog(historicURL));
                            System.out.println(response);
                            String fileName;
                            if (strikeKey.contains("CE")) {
                                fileName = exp.getKey() + "CE";
                            } else {
                                fileName = exp.getKey() + "PE";
                            }

                            PrintWriter writer = null;
                            try {
                                writer = new PrintWriter(new File(sensexexpFolder + "/" + fileName + ".json"));

                                writer.write(response);
                                writer.flush();
                                writer.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        });
                    });
                    zippingDirectory.test(sensexpPath, "SENSEX_" + format.format(currentDate));
                    telegramClient.sendDocumentToTelegram(sensexpPath + "/SENSEX_" + format.format(currentDate) + ".zip", "SENSEX_" + format.format(currentDate));
                    // FileUtils.deleteDirectory(new File(sensexpPath));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


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
                if (!fnexpFolder.exists()) {
                    fnexpFolder.mkdir();
                }
                fnexpFolder.setReadable(true); //read
                fnexpFolder.setWritable(true); //write

                if (currentFNExp.equals(format.format(currentDate))) {
                    String message = "FN Expiry export date:" + currentFNExp;
                    telegramClient.sendToTelegram(message, telegramTokenGroup, "-646157933");
                    zerodhaTransactionService.finNiftyWeeklyOptions.entrySet().stream().forEach(exp -> {

                        Map<String, String> map = exp.getValue();
                        map.entrySet().stream().forEach(optionExp -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            String strikeNo = optionExp.getValue();
                            String strikeKey = optionExp.getKey();
                            String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentFNExp + "+15:30:00&oi=1";
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequestWithoutLog(historicURL));
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
                    zippingDirectory.test(fnexpPath, "FINNIFTY_" + format.format(currentDate));
                    telegramClient.sendDocumentToTelegram(fnexpPath + "/FINNIFTY_" + format.format(currentDate) + ".zip", "FINNIFTY_" + format.format(currentDate));
                    FileUtils.deleteDirectory(new File(fnexpPath));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            if (currentBNFExp.equals(format.format(currentDate))) {
                // log.info("Expiry export date:"+currentExp);
                try {
                    String message = "BNifty Expiry export date:" + currentExp;
                    telegramClient.sendToTelegram(message, telegramTokenGroup, "-646157933");
                    zerodhaTransactionService.bankNiftyWeeklyOptions.entrySet().stream().forEach(exp -> {
                        Map<String, String> map = exp.getValue();
                        map.entrySet().stream().forEach(optionExp -> {
                            String strikeNo = optionExp.getValue();
                            String strikeKey = optionExp.getKey();
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentExp + "+15:30:00&oi=1";
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequestWithoutLog(historicURL));
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
                    zippingDirectory.test(expPath, "BNIFTY_" + format.format(currentDate));
                    telegramClient.sendDocumentToTelegram(expPath + "/BNIFTY_" + format.format(currentDate) + ".zip", "BNF");
                    FileUtils.deleteDirectory(new File(expPath));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (currentExp.equals(format.format(currentDate))) {
                try {
                    zerodhaTransactionService.niftyWeeklyOptions.entrySet().stream().forEach(exp -> {
                        Map<String, String> map = exp.getValue();
                        map.entrySet().stream().forEach(optionExp -> {
                            String strikeNo = optionExp.getValue();
                            String strikeKey = optionExp.getKey();
                            String historicURL = "https://api.kite.trade/instruments/historical/" + strikeNo + "/minute?from=" + format.format(startDate) + "+09:00:00&to=" + currentExp + "+15:30:00&oi=1";
                            String response = transactionService.callAPI(transactionService.createZerodhaGetRequestWithoutLog(historicURL));
                            String fileName;
                            if (strikeKey.contains("CE")) {
                                fileName = exp.getKey() + "CE";
                            } else {
                                fileName = exp.getKey() + "PE";
                            }

                            PrintWriter writer = null;
                            try {
                                writer = new PrintWriter(new File(nexpFolder + "/" + fileName + ".json"));

                                writer.write(response);
                                writer.flush();
                                writer.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }

                        });
                    });
                    zippingDirectory.test(nexpPath, "NIFTY_" + format.format(currentDate));
                    telegramClient.sendDocumentToTelegram(nexpPath + "/NIFTY_" + format.format(currentDate) + ".zip", "NIFTY");
                    FileUtils.deleteDirectory(new File(nexpPath));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                /*  zipUtils.zipExpData(expFolder.getPath(),currentExp);
        File zipPath=new File(expFolder.getPath()+"/"+currentExp+".zip");*/
                //  sendMessage.sendDocumentToTelegram(zipPath,"1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o","-646157933");

            }
        }
    }
}
