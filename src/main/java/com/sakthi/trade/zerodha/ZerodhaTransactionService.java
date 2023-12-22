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

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.time.DayOfWeek.THURSDAY;
import static java.time.temporal.TemporalAdjusters.lastInMonth;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Service
@Slf4j
public class ZerodhaTransactionService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ZerodhaTransactionService.class.getName());
    public Map<String, String> lsSymbols = new HashMap<>();
    public Map<String, String> lsHoliday = new HashMap<>();
    public Map<String, String> lsFinHoliday = new HashMap<>();
    public Map<String, String> lsSensexHoliday = new HashMap<>();
    public Map<String, String> lsBankNiftyHoliday = new HashMap<>();
    public Map<String, String> midcpHoliday = new HashMap<>();
    public Map<String, String> niftyIndics = new HashMap<>();
    public Map<String, Map<String, String>> currentFutures = new HashMap<>();
    public Map<String, String> nearFutures = new HashMap<>();
    public Map<String, String> niftyVix = new HashMap<>();
    public Map<String, Map<String, String>> bankNiftyWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> bankNiftyNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> sensexWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> sensexNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> niftyNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> niftyWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> finNiftyNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> finNiftyWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> midcpNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> midcpWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanBankNiftyWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanFNiftyWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanFNiftyNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanBankNiftyNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanMidcpWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanMidCpNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanSensexWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanSensexNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanNiftyNextWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, String>> dhanNiftyWeeklyOptions = new HashMap<>();
    public Map<String, Map<String, Map<String, StrikeData>>> globalOptionsInfo = new HashMap<>();
    public String expDate;
    public String finExpDate;
    public String sensexExpDate;
    public String bankBiftyExpDate;
    public String midCpExpDate;
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
    @Autowired
    TelegramMessenger sendMessage;
    @Autowired
    TradeSedaQueue tradeSedaQueue;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;
    @Value("${test.profile:false}")
    Boolean testProfile;

    public void olhc() {

        String historicURL = "https://api.kite.trade/instruments/historical/5633/5minute?from=2020-12-17+09:00:00&to=2020-12-17+10:30:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.println(response);
        try {
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/PreOpen_FO_17Dec2020.csv"));
            String[] line;
            int i = 0;
            String uri = "";
            while ((line = csvReader.readNext()) != null) {
                if (i == 1) {
                    uri = uri + "i=NSE:" + line[0];
                } else if (i > 1) {
                    uri = uri + "&i=NSE:" + line[0];
                }
                i++;
            }
            String ohlcQuotesURI = "https://api.kite.trade/quote/ohlc?" + uri;
            String response1 = transactionService.callAPI(transactionService.createZerodhaGetRequest(ohlcQuotesURI));
            System.out.println(response1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Date nextWeekExpDate(Calendar currentWeekExpCal, boolean currentWeekExpOff, Map<String, String> lsHoliday) {
        if (currentWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, 1);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        currentWeekExpCal.add(DAY_OF_MONTH, 7);
        String weekExp = format.format(currentWeekExpCal.getTime());
        boolean nextWeekExpOff = false;
        if (lsHoliday.containsKey(weekExp)) {
            nextWeekExpOff = true;
        }
        if (nextWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, -1);
            weekExp = format.format(currentWeekExpCal.getTime());
            LOGGER.info("exp falling on holiday. recalculated weekly exp date is:" + weekExp);
        }
        return currentWeekExpCal.getTime();
    }

    public Date nextWeekFinExpDate(Calendar currentWeekExpCal, boolean currentWeekExpOff, Map<String, String> lsHoliday) {
        if (currentWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, 1);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        currentWeekExpCal.add(DAY_OF_MONTH, 7);
        String weekExp = format.format(currentWeekExpCal.getTime());
        boolean nextWeekExpOff = false;
        if (lsHoliday.containsKey(weekExp)) {
            nextWeekExpOff = true;
        }
        if (nextWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, -1);
            weekExp = format.format(currentWeekExpCal.getTime());
            LOGGER.info("Next Tuesday falling on holiday. recalculated fin weekly exp date is:" + weekExp);
        }
        return currentWeekExpCal.getTime();
    }

    public Date nextWeekMpExpDate(Calendar currentWeekExpCal, boolean currentWeekExpOff, Map<String, String> lsHoliday) {
        if (currentWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, 3);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        currentWeekExpCal.add(DAY_OF_MONTH, 7);
        String weekExp = format.format(currentWeekExpCal.getTime());
        boolean nextWeekExpOff = false;
        if (lsHoliday.containsKey(weekExp)) {
            nextWeekExpOff = true;
        }
        if (nextWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, -1);
            weekExp = format.format(currentWeekExpCal.getTime());
            LOGGER.info("Next Tuesday falling on holiday. recalculated fin weekly exp date is:" + weekExp);
        }
        return currentWeekExpCal.getTime();
    }

    public Date nextWeekSensexExpDate(Calendar currentWeekExpCal, boolean currentWeekExpOff, Map<String, String> lsHoliday) {
        if (currentWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, 1);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        currentWeekExpCal.add(DAY_OF_MONTH, 7);
        String weekExp = format.format(currentWeekExpCal.getTime());
        boolean nextWeekExpOff = false;
        if (lsHoliday.containsKey(weekExp)) {
            nextWeekExpOff = true;
        }
        if (nextWeekExpOff) {
            currentWeekExpCal.add(DAY_OF_MONTH, -1);
            weekExp = format.format(currentWeekExpCal.getTime());
            LOGGER.info("Next Tuesday falling on holiday. recalculated fin weekly exp date is:" + weekExp);
        }
        return currentWeekExpCal.getTime();
    }

    @Scheduled(cron = "${zerodha.get.instrument}")
    @PostConstruct
    public void getInstrument() throws IOException, CsvValidationException, InterruptedException {
        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/fo_mktlots.csv"));
        String[] line;

        CSVReader csvHolidayReader = new CSVReader(new FileReader(trendPath + "/trading_thursday_holiday_2021.csv"));
        String[] lineHoliday;
        while ((lineHoliday = csvHolidayReader.readNext()) != null) {
            lsHoliday.put(lineHoliday[0].trim(), lineHoliday[0].trim());
        }
        CSVReader bnfcsvHolidayReader = new CSVReader(new FileReader(trendPath + "/trading_wednesday_holiday_2021.csv"));
        String[] bnflineHoliday;
        while ((bnflineHoliday = bnfcsvHolidayReader.readNext()) != null) {
            lsBankNiftyHoliday.put(bnflineHoliday[0].trim(), bnflineHoliday[0].trim());
        }
        CSVReader mondaycsvHolidayReader = new CSVReader(new FileReader(trendPath + "/trading_monday_holiday.csv"));
        String[] mondaycsvHoliday;
        while ((mondaycsvHoliday = mondaycsvHolidayReader.readNext()) != null) {
            midcpHoliday.put(mondaycsvHoliday[0].trim(), mondaycsvHoliday[0].trim());
        }
        CSVReader csvHolidayFinReader = new CSVReader(new FileReader(trendPath + "/trading_tuesday_holiday_2021.csv"));
        String[] lineFinHoliday;
        while ((lineFinHoliday = csvHolidayFinReader.readNext()) != null) {
            lsFinHoliday.put(lineFinHoliday[0].trim(), lineFinHoliday[0].trim());
        }
        CSVReader csvHolidaySensexReader = new CSVReader(new FileReader(trendPath + "/trading_friday_holiday.csv"));
        String[] lineSensexHoliday;
        while ((lineSensexHoliday = csvHolidaySensexReader.readNext()) != null) {
            lsSensexHoliday.put(lineSensexHoliday[0].trim(), lineSensexHoliday[0].trim());
        }
        int i = 0;
        while ((line = csvReader.readNext()) != null) {
            if (i > 4) {
                lsSymbols.put(line[1].trim(), line[1].trim());
            }
            i++;
        }
        Calendar calendar = Calendar.getInstance();
        Calendar todayDate = Calendar.getInstance();
        Calendar finniftycalendar = Calendar.getInstance();
        Calendar bankNiftycalendar = Calendar.getInstance();
        Calendar sensexCalendar = Calendar.getInstance();
        Calendar midCpCalendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String todayDateStr = format.format(todayDate.getTime());
        SimpleDateFormat formatMM = new SimpleDateFormat("MM-dd");
        SimpleDateFormat formatddMMM = new SimpleDateFormat("dd-MMM");
        if (formatMM.format(calendar.getTime()).equals("01-02") || formatMM.format(calendar.getTime()).equals("01-03")) {
            try {
                sendMessage.sendToTelegram("UPDATE HOLIDAY CALENDER FOR WEEKLY EXP DATE CALCULATION", telegramToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday
        int dayadd = 5 - calendar.get(DAY_OF_WEEK);
        int bankniftyadd = 4 - calendar.get(DAY_OF_WEEK);
        int daysensexadd = 6 - calendar.get(DAY_OF_WEEK);
        int findayadd = 3 - finniftycalendar.get(DAY_OF_WEEK);
        int midcpdayadd = 2 - midCpCalendar.get(DAY_OF_WEEK);
        if (dayadd > 0) {
            calendar.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd < 0) {
            int dayadd1 = dayadd + 7;
            calendar.add(DAY_OF_MONTH, dayadd1);
        }
        if (bankniftyadd > 0) {
            bankNiftycalendar.add(DAY_OF_MONTH, bankniftyadd);
        } else if (bankniftyadd < 0) {
            int dayadd1 = bankniftyadd + 7;
            bankNiftycalendar.add(DAY_OF_MONTH, dayadd1);
        }
        if (daysensexadd > 0) {
            sensexCalendar.add(DAY_OF_MONTH, daysensexadd);
        } else if (daysensexadd < 0) {
            int sensexadd1 = daysensexadd + 7;
            sensexCalendar.add(DAY_OF_MONTH, sensexadd1);
        }
        if (findayadd > 0) {
            finniftycalendar.add(DAY_OF_MONTH, findayadd);
        } else if (findayadd < 0) {
            int findayadd1 = findayadd + 7;
            finniftycalendar.add(DAY_OF_MONTH, findayadd1);
        }
        if (midcpdayadd > 0) {
            midCpCalendar.add(DAY_OF_MONTH, midcpdayadd);
        } else if (midcpdayadd < 0) {
            int midcpdayadd1 = midcpdayadd + 7;
            midCpCalendar.add(DAY_OF_MONTH, midcpdayadd1);
        }
        Date date = calendar.getTime();
        Date findate = finniftycalendar.getTime();
        Date midcpdate = midCpCalendar.getTime();
        Date sensexDate = sensexCalendar.getTime();
        Date bnfDate = bankNiftycalendar.getTime();
        String weekExp = format.format(date);
        String finWeekExp = format.format(findate);
        String midcpWeekExp = format.format(midcpdate);
        midCpExpDate = midcpWeekExp;
        String sensexWeekExp = format.format(sensexDate);
        String bnfWeekExp = format.format(bnfDate);
        boolean currentWeekExpOff = false;
        boolean bnfWeekExpOff = false;
        boolean currentSensexWeekExpOff = false;
        boolean mondayOff = false;
        if (lsHoliday.containsKey(weekExp)) {
            currentWeekExpOff = true;
        }
        if (currentWeekExpOff) {
            calendar.add(DAY_OF_MONTH, -1);
            date = calendar.getTime();
            weekExp = format.format(date);
            log.info("Thursday falling on holiday. recalculated weekly exp date is:" + weekExp);
            tradeSedaQueue.sendTelemgramSeda("Thursday falling on holiday. recalculated weekly exp date is:" + weekExp);
        }
        if (lsBankNiftyHoliday.containsKey(bnfWeekExp)) {
            bnfWeekExpOff = true;
        }
        if (bnfWeekExpOff) {
            bankNiftycalendar.add(DAY_OF_MONTH, -1);
            bnfDate = bankNiftycalendar.getTime();
            bnfWeekExp = format.format(bnfDate);
            log.info("wednesday falling on holiday. recalculated weekly exp date is:" + bnfWeekExp);
            tradeSedaQueue.sendTelemgramSeda("wednesday falling on holiday. recalculated bnf weekly exp date is:" + bnfWeekExp);
        }
        if (lsSensexHoliday.containsKey(sensexWeekExp)) {
            currentSensexWeekExpOff = true;
        }
        if (currentSensexWeekExpOff) {
            sensexCalendar.add(DAY_OF_MONTH, -1);
            sensexDate = sensexCalendar.getTime();
            sensexWeekExp = format.format(sensexDate);
            log.info("Friday falling on holiday. recalculated weekly exp date is:" + sensexWeekExp);
            tradeSedaQueue.sendTelemgramSeda("Friday falling on holiday. recalculated weekly exp date is:" + sensexWeekExp);
        }
        boolean currentFinWeekExpOff = false;
        if (lsFinHoliday.containsKey(finWeekExp)) {
            currentFinWeekExpOff = true;
        }
        if (currentFinWeekExpOff) {
            finniftycalendar.add(DAY_OF_MONTH, -1);
            findate = finniftycalendar.getTime();
            finWeekExp = format.format(findate);
            log.info("tuesday falling on holiday. recalculated fin weekly exp date is:" + finWeekExp);
            tradeSedaQueue.sendTelemgramSeda("tuesday falling on holiday. recalculated fin weekly exp date is:" + finWeekExp);
        }
        boolean currentMCWeekExpOff = false;
        if (midcpHoliday.containsKey(midcpWeekExp)) {
            currentMCWeekExpOff = true;
        }
        if (currentMCWeekExpOff) {
            midCpCalendar.add(DAY_OF_MONTH, -3);
            midcpdate = midCpCalendar.getTime();
            midcpWeekExp = format.format(midcpdate);
            midCpExpDate=midcpWeekExp;
            log.info("monday falling on holiday. recalculated mcap weekly exp date is:" + midcpWeekExp);
            tradeSedaQueue.sendTelemgramSeda("monday falling on holiday. recalculated mcap weekly exp date is:" + midcpWeekExp);
        }
        expDate = weekExp;
        finExpDate = finWeekExp;
        sensexExpDate = sensexWeekExp;
        Date monthlyExpDate = getMonthExpDay();
        boolean currentMonthlyExpOff = false;
        String monthlyExp = format.format(monthlyExpDate);
        if (lsHoliday.containsKey(monthlyExp)) {
            currentMonthlyExpOff = true;
        }
        if (currentMonthlyExpOff) {
            Calendar monthlyExpCal = Calendar.getInstance();
            monthlyExpCal.setTime(monthlyExpDate);
            monthlyExpCal.add(DAY_OF_MONTH, -1);
            monthlyExpDate = monthlyExpCal.getTime();
            monthlyExp = format.format(monthlyExpDate);
            log.info("Monthly Exp falling on holiday. recalculated weekly exp date is:" + monthlyExp);
            tradeSedaQueue.sendTelemgramSeda("Monthly Exp falling on holiday. recalculated weekly exp date is:" + monthlyExp);
        }
        Date bnfnextWeekExpDateRes = null;
        if (monthlyExp.equals(expDate)) {
            bnfnextWeekExpDateRes = bnfDate;
            bnfWeekExp = expDate;
            bnfDate = date;

        } else {
            bnfnextWeekExpDateRes = nextWeekExpDate(bankNiftycalendar, bnfWeekExpOff, lsBankNiftyHoliday);
        }
        bankBiftyExpDate = bnfWeekExp;
        Date currentWeekExpDate = date;
        Date currentFinWeekExpDate = findate;
        Date nextWeekFinExpDateRes = nextWeekExpDate(finniftycalendar, currentFinWeekExpOff, lsFinHoliday);
        String nextWeekFinExpDate = format.format(nextWeekFinExpDateRes);
        Date nextWeekSensexExpDateRes = nextWeekExpDate(sensexCalendar, currentSensexWeekExpOff, lsSensexHoliday);
        Date nextWeekMidcapExpDateRes = nextWeekMpExpDate(midCpCalendar, currentMCWeekExpOff, midcpHoliday);
        String nextWeekMidcpExpDate = format.format(nextWeekMidcapExpDateRes);
        String nextWeekSensexExpDate = format.format(nextWeekSensexExpDateRes);
        Date nextWeekExpDateRes = nextWeekExpDate(calendar, currentWeekExpOff, lsHoliday);
        String nextWeekExpDate = format.format(nextWeekExpDateRes);
        String bnfnextWeekExpDate = format.format(bnfnextWeekExpDateRes);
        String instrumentURI = baseURL + instrumentURL;
        if (monthlyExp.equals(nextWeekExpDate) && todayDateStr.equals(bankBiftyExpDate)) {
            bnfnextWeekExpDate = nextWeekExpDate;
            bnfnextWeekExpDateRes = nextWeekExpDateRes;
        }
        if (monthlyExp.equals(expDate)) {
            bnfWeekExp = expDate;
            bnfDate = date;
            bankBiftyExpDate = bnfWeekExp;

        }
        if (monthlyExp.equals(nextWeekExpDate) && todayDateStr.equals(expDate)) {
            bnfnextWeekExpDate = nextWeekExpDate;
            bnfnextWeekExpDateRes = nextWeekExpDateRes;
            bnfWeekExp = nextWeekExpDate;
            bnfDate = nextWeekExpDateRes;
            bankBiftyExpDate = bnfWeekExp;

        }
        String response = null;
        if (!testProfile) {
            response = transactionService.callAPI(transactionService.createZerodhaGetRequest(instrumentURI));
        } else {
            response = transactionService.callAPI(transactionService.createZerodhaGetRequestTest(instrumentURI));
        }
        String[] lines = response.split("\\r?\\n");
        System.out.println("output:" + lines.length);
        for (int j = 0; j < lines.length; j++) {
            String[] data = lines[j].split(",");

            if (lsSymbols.get(data[2].trim()) != null && data[9].equals("EQ") && data[11].equals("NSE")) {
                // System.out.println(lines[j]);
                lsSymbols.put(data[2].trim(), data[0].trim());
            }
            if (data[9].equals("EQ") && data[10].equals("INDICES") && data[11].equals("NSE") && data[2].contains("VIX")) {
                // System.out.println(lines[j]);
                niftyVix.put(data[2].trim(), data[0].trim());
            }
            if (data[9].equals("EQ") && data[10].equals("INDICES") && data[11].equals("NSE")) {
                niftyIndics.put(data[2].trim(), data[0].trim());
            }
            if (data[9].equals("EQ") && data[10].equals("INDICES") && data[11].equals("BSE")) {
                niftyIndics.put(data[2].trim(), data[0].trim());
            }
            String index = data[3].replace("\"", "");
            // String indexMidcap = data[2].replace("\"", "");
            if (index.equals("BANKNIFTY") && data[5].equals(bnfWeekExp) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (bankNiftyWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = bankNiftyWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    bankNiftyWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    bankNiftyWeeklyOptions.put(data[6], map);
                }
            }
            if (index.equals("SENSEX") && data[5].equals(sensexWeekExp) && data[10].equals("BFO-OPT") && data[11].equals("BFO")) {
                if (sensexWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = sensexWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    sensexWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    sensexWeeklyOptions.put(data[6], map);
                }
            }
            if (index.equals("SENSEX") && data[5].equals(nextWeekSensexExpDate) && data[10].equals("BFO-OPT") && data[11].equals("BFO")) {
                if (sensexNextWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = sensexNextWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    sensexNextWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    sensexNextWeeklyOptions.put(data[6], map);
                }
            }
            if (index.equals("FINNIFTY") && data[5].equals(finWeekExp) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (finNiftyWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = finNiftyWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    finNiftyWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    finNiftyWeeklyOptions.put(data[6], map);
                }
            }
            if (index.equals("FINNIFTY") && data[5].equals(nextWeekFinExpDate) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (finNiftyNextWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = finNiftyNextWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    finNiftyNextWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    finNiftyNextWeeklyOptions.put(data[6], map);
                }
            }
            //midcap:start

            if (index.equals("MIDCPNIFTY") && data[5].equals(midcpWeekExp) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (midcpWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = midcpWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    midcpWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    midcpWeeklyOptions.put(data[6], map);
                }
            }
            if (index.equals("MIDCPNIFTY") && data[5].equals(nextWeekMidcpExpDate) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (midcpNextWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = midcpNextWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    midcpNextWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    midcpNextWeeklyOptions.put(data[6], map);
                }
            }
            //midcap:end
            if (index.equals("BANKNIFTY") && data[5].equals(bnfnextWeekExpDate) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (bankNiftyNextWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = bankNiftyNextWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    bankNiftyNextWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    bankNiftyNextWeeklyOptions.put(data[6], map);
                }
            }
            if (data[5].equals(monthlyExp) && data[10].equals("NFO-FUT") && data[11].equals("NFO")) {
                if (currentFutures.get(index) != null) {
                    Map<String, String> map = currentFutures.get(index);
                    map.put(data[2], data[0]);
                    currentFutures.put(index, map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    currentFutures.put(index, map);
                }
            }
            if (index.equals("NIFTY") && data[5].equals(weekExp) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (niftyWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = niftyWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    niftyWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    niftyWeeklyOptions.put(data[6], map);
                }
            }
            if (index.equals("NIFTY") && data[5].equals(nextWeekExpDate) && data[10].equals("NFO-OPT") && data[11].equals("NFO")) {
                if (niftyNextWeeklyOptions.get(data[6]) != null) {
                    Map<String, String> map = niftyNextWeeklyOptions.get(data[6]);
                    map.put(data[2], data[0]);
                    niftyNextWeeklyOptions.put(data[6], map);
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(data[2], data[0]);
                    niftyNextWeeklyOptions.put(data[6], map);
                }
            }

        }

     /*lsSymbols.entrySet().parallelStream().forEach(map->{
            System.out.println(map.getKey()+":"+map.getValue());
        });*/
        System.out.println(bankNiftyWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Nifty current week expiry:" + weekExp + " strike count :" + niftyWeeklyOptions.size());
        Thread.sleep(10000);
        tradeSedaQueue.sendTelemgramSeda("Total Nifty Next Week expiry expiry:" + nextWeekExpDate + " strike count :" + niftyNextWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total BNF current week expiry:" + bnfWeekExp + " strike count :" + bankNiftyWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total BNF Next Week expiry expiry:" + bnfnextWeekExpDate + " strike count :" + bankNiftyNextWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Fin nifty expiry:" + finWeekExp + " strike count:" + finNiftyWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Fin nifty next week expiry:" + nextWeekFinExpDate + " strike count:" + finNiftyNextWeeklyOptions.size());
        Thread.sleep(2000);
        //log.info("Total Fin nifty next week expiry strike count:" + new Gson().toJson(finNiftyNextWeeklyOptions));
        tradeSedaQueue.sendTelemgramSeda("Total Sensex week expiry:" + sensexWeekExp + " strike count:" + sensexWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Sensex next week expiry:" + nextWeekSensexExpDate + " strike count:" + sensexNextWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total BNF Futures strike Count for monthly exp :" + monthlyExp + ":" + currentFutures.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Midcap week expiry:" + midcpWeekExp + " strike count:" + midcpWeeklyOptions.size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Midcap next week expiry:" + nextWeekMidcpExpDate + " strike count:" + midcpNextWeeklyOptions.size());
        Thread.sleep(2000);
        try {
            tradeSedaQueue.sendTelemgramSeda("Total BNF current week expiry strike count :" + bankNiftyWeeklyOptions.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            String dhanResponse = transactionService.downloadInstrumentData(dhanInstrumentURL);
            String[] dhanlines = dhanResponse.split("\\r?\\n");
            String dhanCurrentWeekExpDate = formatddMMM.format(currentWeekExpDate).toUpperCase();
            String dhanBNFCurrentWeekExpDate = formatddMMM.format(bnfDate).toUpperCase();
            String dhanMidcpCurrentWeekExpDate = formatddMMM.format(midcpdate).toUpperCase();
            String dhanMidcpNextWeekExpDate = formatddMMM.format(nextWeekMidcapExpDateRes).toUpperCase();
            String dhanSensexCurrentWeekExpDate = formatddMMM.format(sensexDate).toUpperCase();
            String dhanSensexNextWeekExpDate = formatddMMM.format(nextWeekSensexExpDateRes).toUpperCase();
            if (monthlyExp.equals(expDate)) {
                dhanBNFCurrentWeekExpDate = dhanCurrentWeekExpDate;
            }

            String dhanFinCurrentWeekExpDate = formatddMMM.format(currentFinWeekExpDate).toUpperCase();
            String dhanFinNextWeekExpDate = formatddMMM.format(nextWeekFinExpDateRes).toUpperCase();
            String dhanNextWeekExpDate = formatddMMM.format(nextWeekExpDateRes).toUpperCase();
            String bnfdhanNextWeekExpDate = formatddMMM.format(bnfnextWeekExpDateRes).toUpperCase();
            if (monthlyExp.equals(nextWeekExpDate) && todayDateStr.equals(bankBiftyExpDate)) {
                bnfdhanNextWeekExpDate = dhanNextWeekExpDate;
            }
            if (todayDateStr.equals(expDate) && monthlyExp.equals(nextWeekExpDate)) {
                dhanBNFCurrentWeekExpDate = dhanNextWeekExpDate;
            }
            System.out.println("dhan output:" + dhanResponse.length());
            for (int j = 0; j < dhanlines.length; j++) {
                String[] data = dhanlines[j].split(",");
                String index = data[0].replace("\"", "");
                String instrumentName = data[3].replace("\"", "");
                String securityId = data[2].replace("\"", "");
                String securitySymbol = data[5].replace("\"", "");
                String securityCustomSymbol = data[7].replace("\"", "");
                String[] splited = securityCustomSymbol.split("\\s+");

                if (index.equals("NSE") && instrumentName.equals("OPTIDX")) {
                    String strikeExpDate = splited[1] + "-" + splited[2];
                    if (dhanFinCurrentWeekExpDate.equals(strikeExpDate)) {
                        if ("FINNIFTY".equals(splited[0])) {
                            if (dhanFNiftyWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanFNiftyWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanFNiftyWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanFNiftyWeeklyOptions.put(splited[3], map);
                            }
                        }
                    } else if (dhanFinNextWeekExpDate.equals(strikeExpDate)) {
                        if ("FINNIFTY".equals(splited[0])) {
                            if (dhanFNiftyNextWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanFNiftyNextWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanFNiftyNextWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanFNiftyNextWeeklyOptions.put(splited[3], map);
                            }
                        }
                    }
                    if (dhanCurrentWeekExpDate.equals(strikeExpDate)) {
                        if ("NIFTY".equals(splited[0])) {
                            if (dhanNiftyWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanNiftyWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanNiftyWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanNiftyWeeklyOptions.put(splited[3], map);
                            }
                        }
                    } else if (dhanNextWeekExpDate.equals(strikeExpDate)) {
                        if ("NIFTY".equals(splited[0])) {
                            if (dhanNiftyNextWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanNiftyNextWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanNiftyNextWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanNiftyNextWeeklyOptions.put(splited[3], map);
                            }
                        }
                    }
                    if (dhanBNFCurrentWeekExpDate.equals(strikeExpDate)) {
                        if ("BANKNIFTY".equals(splited[0])) {
                            if (dhanBankNiftyWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanBankNiftyWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanBankNiftyWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanBankNiftyWeeklyOptions.put(splited[3], map);
                            }
                        }
                    }
                    if (bnfdhanNextWeekExpDate.equals(strikeExpDate)) {
                        if ("BANKNIFTY".equals(splited[0])) {
                            if (dhanBankNiftyNextWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanBankNiftyNextWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanBankNiftyNextWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanBankNiftyNextWeeklyOptions.put(splited[3], map);
                            }
                        }
                    }
                    if (dhanMidcpCurrentWeekExpDate.equals(strikeExpDate)) {
                        if ("MIDCPNIFTY".equals(splited[0])) {
                            if (dhanMidcpWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanMidcpWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanMidcpWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanMidcpWeeklyOptions.put(splited[3], map);
                            }
                        }
                    } else if (dhanMidcpNextWeekExpDate.equals(strikeExpDate)) {
                        if ("MIDCPNIFTY".equals(splited[0])) {
                            if (dhanMidCpNextWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanMidCpNextWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanMidCpNextWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanMidCpNextWeeklyOptions.put(splited[3], map);
                            }
                        }
                    }
                }
                if (index.equals("BSE") && instrumentName.equals("OPTIDX")) {
                    String strikeExpDate = splited[1] + "-" + splited[2];
                    if (dhanSensexCurrentWeekExpDate.equals(strikeExpDate)) {
                        if ("SENSEX".equals(splited[0])) {
                            if (dhanSensexWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanSensexWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanSensexWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanSensexWeeklyOptions.put(splited[3], map);
                            }
                        }
                    } else if (dhanSensexNextWeekExpDate.equals(strikeExpDate)) {
                        if ("SENSEX".equals(splited[0])) {
                            if (dhanSensexNextWeeklyOptions.get(splited[3]) != null) {
                                Map<String, String> map = dhanSensexNextWeeklyOptions.get(splited[3]);
                                map.put(data[5], data[2]);
                                dhanSensexNextWeeklyOptions.put(splited[3], map);
                            } else {
                                Map<String, String> map = new HashMap<>();
                                map.put(data[5], data[2]);
                                dhanSensexNextWeeklyOptions.put(splited[3], map);
                            }
                        }
                    }
                }

            }
            Thread.sleep(10000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan BNF current week expiry  :" + dhanBNFCurrentWeekExpDate + " strike count :" + dhanBankNiftyWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan BNF Next Week expiry:" + bnfdhanNextWeekExpDate + " strike count :" + dhanBankNiftyNextWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan FN Week expiry strike count :" + dhanFNiftyWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan FN Next Week expiry strike count :" + dhanFNiftyNextWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan NF current week expiry strike count :" + dhanNiftyWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan NF Next Week expiry strike count :" + dhanNiftyNextWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan Midcap current week expiry strike count :" + dhanMidcpWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan Midcap Next Week expiry strike count :" + dhanMidCpNextWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan Sensex current week expiry strike count :" + dhanSensexWeeklyOptions.size());
            Thread.sleep(2000);
            tradeSedaQueue.sendTelemgramSeda("Total Dhan Sensex Next Week expiry strike count :" + dhanSensexNextWeeklyOptions.size());
            //   sendMessage.sendToTelegram("Total Dhan NF Futures strike Count for monthly exp :" +monthlyExp+":"+ currentFutures.size(), telegramToken,"-713214125");
            Map<String, Map<String, StrikeData>> strikeFNLevelMap = new HashMap<>();
            try {
                Map<String, Map<String, StrikeData>> nfstrikeLevelMap = new HashMap<>();
                niftyWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanNiftyWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    nfstrikeLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.NF_CURRENT.expiryName, nfstrikeLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Map<String, Map<String, StrikeData>> nfStrikeNextWeekLevelMap = new HashMap<>();
                niftyNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanNiftyNextWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    nfStrikeNextWeekLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.NF_NEXT.expiryName, nfStrikeNextWeekLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                finNiftyWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanFNiftyWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    strikeFNLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.FN_CURRENT.expiryName, strikeFNLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                finNiftyNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanFNiftyNextWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    strikeFNLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.FN_NEXT.expiryName, strikeFNLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Map<String, Map<String, StrikeData>> strikeLevelMap = new HashMap<>();
            globalOptionsInfo.put(Expiry.BNF_CURRENT.expiryName, strikeLevelMap);
            try {
                bankNiftyWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanBankNiftyWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    strikeLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.BNF_CURRENT.expiryName, strikeLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Map<String, Map<String, StrikeData>> bnfStrikeNextWeekLevelMap = new HashMap<>();
            try {
                bankNiftyNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanBankNiftyNextWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    bnfStrikeNextWeekLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            globalOptionsInfo.put(Expiry.BNF_NEXT.expiryName, bnfStrikeNextWeekLevelMap);
            //Midcap: Start
            try {
                Map<String, Map<String, StrikeData>> mcStrikeLevelMap = new HashMap<>();
                midcpWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanMidcpWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    mcStrikeLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.MC_CURRENT.expiryName, mcStrikeLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Map<String, Map<String, StrikeData>> mcStrikeNextWeekLevelMap = new HashMap<>();
            try {
                midcpNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanMidCpNextWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    mcStrikeNextWeekLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.MC_NEXT.expiryName, mcStrikeNextWeekLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //midcap: End
            //Sensex: start
            try {
                Map<String, Map<String, StrikeData>> nfstrikeLevelMap = new HashMap<>();
                sensexWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanSensexWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    nfstrikeLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.SS_CURRENT.expiryName, nfstrikeLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Map<String, Map<String, StrikeData>> nfStrikeNextWeekLevelMap = new HashMap<>();
                sensexNextWeeklyOptions.entrySet().stream().forEach(stringMapEntry -> {
                    Map<String, StrikeData> strikeTypeMap = new HashMap<>();
                    Map<String, String> dhanData = dhanSensexNextWeeklyOptions.get(stringMapEntry.getKey());
                    stringMapEntry.getValue().entrySet().forEach(stringStringEntry -> {
                        StrikeData strikeData = new StrikeData();
                        strikeData.setStrike(stringMapEntry.getKey());
                        strikeData.setZerodhaSymbol(stringStringEntry.getKey());
                        strikeData.setZerodhaId(stringStringEntry.getValue());
                        try {
                            if (stringStringEntry.getKey().contains("CE")) {
                                strikeData.setStrikeType("CE");
                                Map.Entry<String, String> dhanCE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("CE")).findFirst().get();
                                strikeData.setDhanId(dhanCE.getValue());
                                strikeData.setDhanSymbol(dhanCE.getKey());
                            } else {
                                strikeData.setStrikeType("PE");
                                Map.Entry<String, String> dhanPE = dhanData.entrySet().stream().filter(key -> key.getKey().contains("PE")).findFirst().get();
                                strikeData.setDhanId(dhanPE.getValue());
                                strikeData.setDhanSymbol(dhanPE.getKey());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        strikeTypeMap.put(strikeData.getStrikeType(), strikeData);
                    });
                    nfStrikeNextWeekLevelMap.put(stringMapEntry.getKey(), strikeTypeMap);
                });
                globalOptionsInfo.put(Expiry.SS_NEXT.expiryName, nfStrikeNextWeekLevelMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //Sensex: End
            // System.out.println("global data:"+new Gson().toJson(globalOptionsInfo.get(Expiry.MC_CURRENT.expiryName)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.sleep(10000);
        tradeSedaQueue.sendTelemgramSeda("Total Global BNF current week expiry strike count :" + globalOptionsInfo.get(Expiry.BNF_CURRENT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global BNF next week expiry strike count :" + globalOptionsInfo.get(Expiry.BNF_NEXT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global NF current week expiry strike count :" + globalOptionsInfo.get(Expiry.NF_CURRENT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global NF next week expiry strike count :" + globalOptionsInfo.get(Expiry.NF_NEXT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global FN current week expiry strike count :" + globalOptionsInfo.get(Expiry.FN_CURRENT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global FN next week expiry strike count :" + globalOptionsInfo.get(Expiry.FN_NEXT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global Mcap current week expiry strike count :" + globalOptionsInfo.get(Expiry.MC_CURRENT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global Mcap next week expiry strike count :" + globalOptionsInfo.get(Expiry.MC_NEXT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global Sensex current week expiry strike count :" + globalOptionsInfo.get(Expiry.SS_CURRENT.expiryName).size());
        Thread.sleep(2000);
        tradeSedaQueue.sendTelemgramSeda("Total Global Sensex next week expiry strike count :" + globalOptionsInfo.get(Expiry.SS_NEXT.expiryName).size());

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
        LocalDate expDateTemp = getLastThursday(Integer.parseInt(monthFormat.format(cal.getTime())), Integer.parseInt(yearFormat.format(cal.getTime())));
        // String monthlyExpTemp1=format.format(expDateTemp);
        int expDayTemp = expDateTemp.getDayOfMonth();
        int curDayTemp = LocalDate.now().getDayOfMonth();
        //Calendar calCurTemp = Calendar.getInstance();
        if (curDayTemp > expDayTemp) {
            int month = Integer.parseInt(monthFormat.format(cal.getTime()));
            int year = Integer.parseInt(yearFormat.format(cal.getTime()));
            if (month == 12) {
                month = 1;
                year = year + 1;
            } else {
                month = month + 1;
            }
            LocalDate date = getLastThursday(month, year);
            return Date.from(date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        }
        return Date.from(expDateTemp.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    public LocalDate getLastThursday(int month, int year) {
        LocalDate lastThursday = LocalDate.of(year, month, 1).with(lastInMonth(THURSDAY));
        if (lsHoliday.containsKey(lastThursday)) {
            lastThursday.minusDays(1);
            log.info("Thursday falling on holiday. recalculated weekly exp date is:" + lastThursday);
        }
        return lastThursday;
    }
}
