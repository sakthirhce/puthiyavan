package com.sakthi.trade.options.banknifty;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.time.DayOfWeek.THURSDAY;
import static java.time.temporal.TemporalAdjusters.lastInMonth;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class BankNiftyOptionSelling1 {

    @Value("${chromedriver.path}")
    String driverPath;

    @Autowired
    ZerodhaAccount zerodhaAccount;
    @Value("${filepath.trend}")
    String trendPath;

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat dateTimeFormatT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    SimpleDateFormat dateTimeFileFormat = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss");
    SimpleDateFormat monthformatter = new SimpleDateFormat("MMMM");
    SimpleDateFormat monthformat = new SimpleDateFormat("MMM");
    SimpleDateFormat mmformatter = new SimpleDateFormat("MM");
    SimpleDateFormat yearformatter = new SimpleDateFormat("yyyy");
    SimpleDateFormat dayformatter = new SimpleDateFormat("dd");
    SimpleDateFormat expformatter = new SimpleDateFormat("dd-MMM-yyy");
    SimpleDateFormat weekDay=new SimpleDateFormat("EEE");

    @Autowired
    CommonUtil commonUtil;

    @Value("${straddle.banknifty.lot}")
    String bankniftyLot;
    public void optionSelling(int day, String trialPercent,boolean isPCREnabled) throws IOException, InterruptedException {
        String filePath = "";
       /* System.setProperty("webdriver.chrome.driver", driverPath);
        ChromeOptions ChromeOptions = new ChromeOptions();
        ChromeOptions.addArguments("user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Safari/605.1.15");
        ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
        WebDriver webDriver = new ChromeDriver(ChromeOptions);
        webDriver.manage().deleteAllCookies();
        webDriver.get("https://www1.nseindia.com/products/content/derivatives/equities/archieve_fo.htm");*/
        Date date = new Date();
        BigDecimal previousPCR = new BigDecimal(0);
        while (day < 0) {
            try {
                Calendar calendar = Calendar.getInstance();
                System.out.println(calendar.getFirstDayOfWeek());
                System.out.println(calendar.get(DAY_OF_WEEK));
                Map<String, TradeData> tradeMap = new HashMap<>();

          /*      //TODO: add logic to determine wednesday exp date if thursday is trade holiday
            //    System.out.println(webDriver.getPageSource());
                Select se=new Select(webDriver.findElement(By.xpath("/html/body/div[2]/div[3]/div[2]/div[1]/div[3]/div/div[1]/div/div[2]/select")));
                se.selectByIndex(1);
                Thread.sleep(1000);
                webDriver.findElement(By.xpath("//*[@id=\"date\"]")).click();

                System.out.println(webDriver.getPageSource());
                Select yearSel= new Select(webDriver.findElement(By.className("ui-datepicker-year")));
                yearSel.selectByIndex(27);

                //select the given month
                Select monthSel= new Select(webDriver.findElement(By.className("ui-datepicker-month")));
                monthSel.selectByIndex(1); //value start @ 0 so we need the -1

                //get the table
                WebElement calTable= webDriver.findElement(By.className("ui-datepicker-calendar"));
                //click on the correct/given cell/date
                WebElement dateWidgetFrom = webDriver.findElement(By.xpath("//*[@id=\"ui-datepicker-div\"]/table/tbody"));
                List<WebElement> columns = dateWidgetFrom.findElements(By.tagName("td"));
                for (WebElement cell: columns) {
            *//*
            //If you want to click 18th Date
            if (cell.getText().equals("18")) {
            *//*
                    //Select Today's Date
                    if (cell.getText().equals("1")) {
                        cell.click();
                        break;
                    }
                }

         *//*       WebElement calel=calTable.findElement(By.linkText(String.valueOf(1)));
                calel.click();*//*
                this.takeSnapShot(webDriver, "/home/hasvanth/test1.png") ;
                Thread.sleep(5000);

                WebDriverWait wait = new WebDriverWait(webDriver, 10);

      *//*          WebElement elementb1 = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"wrapper_btm\"]/div[1]/div[3]/div/div[1]/div/div[4]/input[3]")));
                elementb1.click();*//*
                WebElement elementb1 =  webDriver.findElement(By.className("getdata-button"));
                Actions act = new Actions(webDriver);
                act.moveToElement(elementb1).click().perform();
               // webDriver.findElement(By.xpath("//input[contains(@src, '/common/images/btn-get-data.gif')]")).click();
            *//*    WebElement elementb = webDriver.findElement(By.className("getdata-button"));
                JavascriptExecutor executor = (JavascriptExecutor)webDriver;
                executor.executeScript("arguments[0].click();", elementb);
                Thread.sleep(5000);*//*
                this.takeSnapShot(webDriver, "/home/hasvanth/test3.png") ;
                //*[@id="ui-datepicker-div"]/table/tbody/tr[4]/td[3]/a
          //      webDriver.navigate().refresh();webDriver.findElement(By.className("getdata-button")).click();
                this.takeSnapShot(webDriver, "/home/hasvanth/test4.png") ;
                // accepting javascript alert
                Alert alert = webDriver.switchTo().alert();
                alert.accept();
                WebElement element = webDriver.findElement(By.id("spanDisplayBox"));
                List<WebElement> webEleList = element.findElements(By.xpath(".//*"));
                System.out.println(webEleList.size());*/
                calendar.add(DAY_OF_MONTH, day);
                Date cdate = calendar.getTime();
                String weekDayStr = weekDay.format(cdate);
                String currentDate = dateFormat.format(cdate);
                /*     HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(currentDate + " 09:15:00"), dateTimeFormat.parse(currentDate + " 15:15:00"), "260105", "5minute", false, false);
                 */
                log.info("Date:" + dateFormat.format(calendar.getTime()));
                Calendar cthurs = Calendar.getInstance();
                cthurs.add(DAY_OF_MONTH, day);
                int dayadd = 5 - cthurs.get(DAY_OF_WEEK);
                if (dayadd > 0) {
                    cthurs.add(DAY_OF_MONTH, dayadd);
                } else if (dayadd == -2) {
                    cthurs.add(DAY_OF_MONTH, 5);
                } else if (dayadd < 0) {
                    cthurs.add(DAY_OF_MONTH, 6);
                }

                String yeard = yearformatter.format(cdate);
                String monthd = monthformat.format(cdate).toUpperCase();
                String dated = dayformatter.format(cdate);
           /*     Runtime.getRuntime().exec(new String[]{"bash", "-c", "google-chrome https://www1.nseindia.com/content/historical/DERIVATIVES/" + yeard + "/" + monthd + "/fo" + dated + monthd + yeard + "bhav.csv.zip"});
                Thread.sleep(1000);*/



                CSVReader csvReaderst = new CSVReader(new FileReader("/home/hasvanth/Downloads/short_straddle_s4_25.csv"));
                String[] lineSt;
                int j = 0;
                Map<String,Map<String,TradeData>> mapTrade=new HashMap<>();
                while ((lineSt = csvReaderst.readNext()) != null) {
                    if (j >= 0) {
                        try {


                            if (!lineSt[0].trim().equals("Date") && !lineSt[0].trim().equals("")) {
                                SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy");
                                Date trDate = format.parse(lineSt[0].substring(0, lineSt[0].length() - 6));
                                String[] data = lineSt[5].split("\\(");
                                String[] data1 = data[1].split("=");
                                String[] pEOpen = data1[0].split("-");
                                String[] data2 = data1[1].split("\\)");
                                String[] stName = data2[1].split(" ");

                                TradeData trPE = new TradeData();
                                trPE.setStockName(stName[1].trim());
                                trPE.setQty(25);
                                trPE.setSellPrice(new BigDecimal(pEOpen[0].trim()));
                                trPE.setBuyPrice(new BigDecimal(pEOpen[1].trim()));
                                Map<String, TradeData> tradeDataMap = new HashMap<>();
                                if(!lineSt[6].trim().equals("NA")) {
                                    String[] data6 = lineSt[6].split("\\(");
                                    String[] data7 = data6[1].split("=");
                                    String[] cEOpen = data7[0].split("-");
                                    String[] data8 = data7[1].split("\\)");
                                    String[] stNameCE = data8[1].split(" ");
                                    TradeData trCE = new TradeData();
                                    trCE.setStockName(stNameCE[1].trim());
                                    trCE.setQty(25);
                                    trCE.setSellPrice(new BigDecimal(cEOpen[0].trim()));
                                    trCE.setBuyPrice(new BigDecimal(cEOpen[1].trim()));
                                    tradeDataMap.put(stNameCE[1].trim(), trCE);
                                }

                                tradeDataMap.put(stName[1].trim(), trPE);
                                mapTrade.put(dateFormat.format(trDate), tradeDataMap);

                            }
                        }catch (Exception e){
                            log.info(lineSt.toString()+":"+e.getMessage());
                        }

                    }

                    j++;


                }
                filePath = "/home/hasvanth/Downloads/fo" + dated + monthd + yeard + "bhav.csv.zip";
                //  System.out.println(filePath);

                ZipInputStream zipIn = new ZipInputStream(new FileInputStream(filePath));
                //get the zipped file list entry
                ZipEntry ze = zipIn.getNextEntry();
                byte[] buffer = new byte[1024];
                File folder = new File("/home/hasvanth/Downloads" + File.separator + ze.getName());
                FileOutputStream fos = new FileOutputStream(folder);

                int len;
                while ((len = zipIn.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                CSVReader csvReader = new CSVReader(new FileReader("/home/hasvanth/Downloads" + File.separator + ze.getName()));
                String[] lineS;
                try {
                    int i = 0;
                    long ceOpenInterest = 0L;
                    long peOpenInterest = 0L;
                    while ((lineS = csvReader.readNext()) != null) {
                        if (lineS[1].trim().equals("BANKNIFTY")) {
                            if (lineS[0].trim().equals("OPTIDX") && lineS[2].trim().equals(expformatter.format(cthurs.getTime())) && lineS[4].trim().equals("PE")) {
                                peOpenInterest = peOpenInterest + Long.parseLong(lineS[12]);
                            }
                            if (lineS[0].trim().equals("OPTIDX") && lineS[2].trim().equals(expformatter.format(cthurs.getTime())) && lineS[4].trim().equals("CE")) {
                                ceOpenInterest = ceOpenInterest + Long.parseLong(lineS[12]);
                            }
                        }
                        i++;

                    }
                    log.info("ceOpenInterest:" + ceOpenInterest);
                    log.info("peOpenInterest:" + peOpenInterest);
                    long ceOpenInterestFl = ceOpenInterest;
                    long peOpenInterestFl = peOpenInterest;
                    final BigDecimal pcrfinal = previousPCR;
                    Map<String ,TradeData> tradeDataMap=mapTrade.get(dateFormat.format(cdate));
                    if (pcrfinal.compareTo(new BigDecimal("1.50")) > 0 && isPCREnabled) {

                        tradeDataMap.entrySet().stream().filter(mapTrad->mapTrad.getKey().contains("PE")).findFirst().ifPresent( mapTrad->{
                            TradeData tradeData=mapTrad.getValue();
                            Brokerage brokerage=commonUtil.calculateBrokerage(mapTrad.getValue(),true,false,false,"0.25");
                            CSVWriter csvWriterOp = null;
                            try {
                                csvWriterOp = new CSVWriter(new FileWriter(trendPath + "/BankNifty_Backtest/straddle_pcr/banknifty_straddle_pcr" + dateTimeFileFormat.format(date) + ".csv", true));

                            BigDecimal profitLoss=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                            BigDecimal profitLossAfterChargeSlippage=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                            String[] data1 = {dateFormat.format(cdate),weekDayStr, tradeData.getStockName(), String.valueOf(ceOpenInterestFl), String.valueOf(peOpenInterestFl),String.valueOf(pcrfinal),tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()),String.valueOf(brokerage.getTotalCharges()),String.valueOf(brokerage.getSlipageCost()),String.valueOf(profitLoss),String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(),String.valueOf(tradeData.isSLHit)};
                            csvWriterOp.writeNext(data1);
                            csvWriterOp.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        });
                    } else if (new BigDecimal("0.50").compareTo(pcrfinal) > 0 && isPCREnabled) {
                        tradeDataMap.entrySet().stream().filter(mapTrad->mapTrad.getKey().contains("CE")).findFirst().ifPresent( mapTrad-> {
                            TradeData tradeData = mapTrad.getValue();
                            Brokerage brokerage = commonUtil.calculateBrokerage(mapTrad.getValue(), true, false, false, "0.25");
                            CSVWriter csvWriterOp = null;
                            try {
                                csvWriterOp = new CSVWriter(new FileWriter(trendPath + "/BankNifty_Backtest/straddle_pcr/banknifty_straddle_pcr" + dateTimeFileFormat.format(date) + ".csv", true));

                                BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                String[] data1 = {dateFormat.format(cdate), weekDayStr, tradeData.getStockName(), String.valueOf(ceOpenInterestFl), String.valueOf(peOpenInterestFl), String.valueOf(pcrfinal), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(), String.valueOf(tradeData.isSLHit)};
                                csvWriterOp.writeNext(data1);
                                csvWriterOp.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    } else {

                            tradeDataMap.entrySet().stream().forEach( mapTrad->{
                                        TradeData tradeData=mapTrad.getValue();
                                        Brokerage brokerage=commonUtil.calculateBrokerage(mapTrad.getValue(),true,false,false,"0.25");
                                        CSVWriter csvWriterOp = null;
                                        try {
                                            csvWriterOp = new CSVWriter(new FileWriter(trendPath + "/BankNifty_Backtest/straddle_pcr/banknifty_straddle_pcr" + dateTimeFileFormat.format(date) + ".csv", true));

                                            BigDecimal profitLoss=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                            BigDecimal profitLossAfterChargeSlippage=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                            String[] data1 = {dateFormat.format(cdate),weekDayStr, tradeData.getStockName(), String.valueOf(ceOpenInterestFl), String.valueOf(peOpenInterestFl),String.valueOf(pcrfinal),tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()),String.valueOf(brokerage.getTotalCharges()),String.valueOf(brokerage.getSlipageCost()),String.valueOf(profitLoss),String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(),String.valueOf(tradeData.isSLHit)};
                                            csvWriterOp.writeNext(data1);
                                            csvWriterOp.flush();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                            });

                    }
                    BigDecimal pcrB = new BigDecimal(String.valueOf(peOpenInterest)).divide(new BigDecimal(String.valueOf(ceOpenInterest)), 2, BigDecimal.ROUND_UP);
                    previousPCR = pcrB;
                    log.info("pcrB:" + pcrB.toString());
                  /*  CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/BankNifty_Backtest/straddle_pcr/banknifty_straddle_pcr" + dateTimeFileFormat.format(date) + "_oi.csv", true));
                    String[] data = {expformatter.format(cthurs.getTime()), String.valueOf(ceOpenInterest), String.valueOf(peOpenInterest), pcrB.toString()};
                    csvWriter.writeNext(data);
                    csvWriter.flush();*/
/*
                    if (historicalData.dataArrayList.size() > 0) {
                        Map<String, Date> dateMap = new HashMap<>();
                        Date openDatetime = dateTimeFormatT.parse(historicalData.dataArrayList.get(0).timeStamp);
                        String openDate = dateFormat.format(openDatetime);
                        historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                            try {

                                Date closeDatetime = dateTimeFormatT.parse(historicalData1.timeStamp);
                                String closeDate = dateFormat.format(closeDatetime);
                                if (dateTimeFormatT.format(closeDatetime).equals(openDate + "T09:15:00")) {
                                    int atmStrike = commonUtil.findATM((int) historicalData1.close);
                                    log.info("Bank Nifty:" + atmStrike);
                                    List<String> strikeList = new ArrayList<>();
                                    if (pcrfinal.compareTo(new BigDecimal("1.50")) > 0 && isPCREnabled) {
                                        strikeList.add("BANKNIFTYWK" + atmStrike + "PE");
                                    } else if (new BigDecimal("0.50").compareTo(pcrfinal) > 0 && isPCREnabled) {
                                        strikeList.add("BANKNIFTYWK" + atmStrike + "CE");
                                    } else {
                                        strikeList.add("BANKNIFTYWK" + atmStrike + "PE");
                                        strikeList.add("BANKNIFTYWK" + atmStrike + "CE");
                                    }

                                    strikeList.stream().forEach(strike -> {
                                        try {
                                            String month = monthformatter.format(cthurs.getTime());
                                            String year = yearformatter.format(cdate);
                                            String expDay = dayformatter.format(cthurs.getTime());
                                            String expDayWithS = dayformatter.format(cthurs.getTime()) + commonUtil.suffixes[Integer.parseInt(expDay)];
                                            String fileName;
                                            LocalDate lastThursday = LocalDate.of(Integer.parseInt(year), Integer.parseInt(mmformatter.format(cdate)), 1).with(lastInMonth(THURSDAY));
                                            if(expDayWithS.contains(String.valueOf(lastThursday.getDayOfMonth()))){
                                                fileName="BANKNIFTY"+strike.substring(11,strike.length()-2)+strike.substring(strike.length()-2);
                                            }else{
                                                fileName=strike;
                                            }
                                            String fileOpPath = "/home/hasvanth/Downloads/BankNiftyWk/" + year + "_5min/" + month + "/Expiry " + expDayWithS + " " + month + "/5min/" + fileName + ".csv";
                                            System.out.println(fileOpPath);
                                            CSVReader csvReaderOP = new CSVReader(new FileReader(fileOpPath));
                                            HistoricalDataExtended historicalDataEx = commonUtil.mapCSVtoHistoric(csvReaderOP);
                                            historicalDataEx.dataArrayList.stream().forEach(historicalDataExtended -> {
                                                try {

                                                    Date closTime = dateTimeFormat.parse(historicalDataExtended.timeStamp);
                                                    if (closTime.equals(dateTimeFileFormat.parse(openDate + " 09:15:00"))) {
                                                        TradeData tradeData = new TradeData();
                                                        tradeData.setSellTime(historicalDataExtended.timeStamp);
                                                        tradeData.isOrderPlaced = true;
                                                        tradeData.setStockName(strike);
                                                        BigDecimal qty=new BigDecimal("20000").divide(new BigDecimal(historicalDataExtended.close).multiply(new BigDecimal("25")),0,RoundingMode.DOWN);
                                                        tradeData.setQty(qty.intValue()*25);
                                                        tradeData.setSellPrice(new BigDecimal(historicalDataExtended.close));
                                                        BigDecimal slPoints = (tradeData.getSellPrice().multiply(new BigDecimal(trialPercent))).divide(new BigDecimal(100)).setScale(0, BigDecimal.ROUND_DOWN);
                                                        BigDecimal slPrice = (tradeData.getSellPrice().add(slPoints)).setScale(0, RoundingMode.HALF_UP);
                                                        tradeData.setSlPrice(slPrice);
                                                        tradeMap.put(strike,tradeData);
                                                    }

                                                    if (tradeMap.get(strike) != null && tradeMap.get(strike).isOrderPlaced && !tradeMap.get(strike).isExited && closTime.after(dateTimeFileFormat.parse(openDate + " 09:15:00"))) {
                                                        TradeData tradeData = tradeMap.get(strike);
                                                        if (!tradeData.isExited && closTime.equals(dateTimeFormat.parse(dateFormat.format(closTime)+" 15:05:00"))) {
                                                            tradeData.isExited = true;
                                                            tradeData.setBuyPrice(new BigDecimal(historicalDataExtended.close));
                                                            tradeData.setBuyTime(historicalDataExtended.timeStamp);
                                                            Brokerage brokerage=commonUtil.calculateBrokerage(tradeData,true,false,false,"0.25");

                                                        //    dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                                            CSVWriter csvWriterOp = new CSVWriter(new FileWriter(trendPath + "/BankNifty_Backtest/straddle_pcr/banknifty_straddle_pcr" + dateTimeFileFormat.format(date) + "_oi.csv", true));
                                                            BigDecimal profitLoss=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                                            BigDecimal profitLossAfterChargeSlippage=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                                            String[] data1 = {openDate,weekDayStr, tradeData.getStockName(), String.valueOf(ceOpenInterestFl), String.valueOf(peOpenInterestFl),String.valueOf(pcrfinal),tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()),String.valueOf(brokerage.getTotalCharges()),String.valueOf(brokerage.getSlipageCost()),String.valueOf(profitLoss),String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(),String.valueOf(tradeData.isSLHit)};
                                                            csvWriterOp.writeNext(data1);
                                                            csvWriterOp.flush();
                                                        }
                                                        if (new BigDecimal(historicalDataExtended.high).compareTo(tradeData.getSlPrice()) > 0 && !tradeData.isExited) {
                                                            tradeData.isExited = true;
                                                            tradeData.isSLHit = true;
                                                            tradeData.setBuyPrice(tradeData.getSlPrice());
                                                            tradeData.setBuyTime(historicalDataExtended.timeStamp);
                                                            Brokerage brokerage=commonUtil.calculateBrokerage(tradeData,true,false,false,"0.25");

                                                         //   dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                                            CSVWriter csvWriterOp = new CSVWriter(new FileWriter(trendPath + "/BankNifty_Backtest/straddle_pcr/banknifty_straddle_pcr" + dateTimeFileFormat.format(date) + "_oi.csv", true));
                                                            BigDecimal profitLoss=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                                            BigDecimal profitLossAfterChargeSlippage=(tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                                            String[] data1 = {openDate,weekDayStr, tradeData.getStockName(), String.valueOf(ceOpenInterestFl), String.valueOf(peOpenInterestFl),String.valueOf(pcrfinal),tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()),String.valueOf(brokerage.getTotalCharges()),String.valueOf(brokerage.getSlipageCost()),String.valueOf(profitLoss),String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime(),String.valueOf(tradeData.isSLHit)};
                                                            csvWriterOp.writeNext(data1);
                                                            csvWriterOp.flush();

                                                        }
                                                    }

                                                    } catch (Exception e) {

                                                }
                                            });

                                        } catch (Exception e) {

                                        }
                                    });
                                }

                            }catch (Exception e)
                            {

                            }}
                        );}
                } catch (FileNotFoundException fe){
                 //   log.info("file not found");
                } catch(Exception e) {
                    e.printStackTrace();

                }
            } catch (FileNotFoundException fe){
          //      log.info("file not found");
            } catch (Exception e) {
                e.printStackTrace();
            } catch (KiteException e) {
                e.printStackTrace();
            }*/
                }catch (FileNotFoundException e) {
                    log.info("file not found");

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CsvValidationException e) {
                    e.printStackTrace();
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }catch (FileNotFoundException e) {
                log.info("file not found");

            } catch (Exception e) {
                e.printStackTrace();
            }
            day++;
        }
    }
            public static void selectDateFromJqueryDatepicker(WebElement datePickerDiv, int expYear, int expMonth, int expDay) {
        //handle 2 digit years
        if(expYear < 100)
            expYear += 2000;

        //select the given year

    }

    public static void takeSnapShot(WebDriver webdriver,String fileWithPath) throws Exception{

        //Convert web driver object to TakeScreenshot

        TakesScreenshot scrShot =((TakesScreenshot)webdriver);

        //Call getScreenshotAs method to create image file

        File SrcFile=scrShot.getScreenshotAs(OutputType.FILE);

        //Move image file to new destination

        File DestFile=new File(fileWithPath);

        //Copy file at destination

        FileUtils.copyFile(SrcFile, DestFile);

    }
}
