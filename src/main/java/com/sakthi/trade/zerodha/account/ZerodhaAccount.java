package com.sakthi.trade.zerodha.account;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.fyer.AuthRequestDTO;
import com.sakthi.trade.fyer.model.Candlestick;
import com.sakthi.trade.fyer.model.PivotTimeFrame;
import com.sakthi.trade.fyer.model.StandardPivot;
import com.sakthi.trade.fyer.model.StandardPivots;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.repo.*;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
import com.zerodhatech.models.User;
import de.taimos.totp.TOTP;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class ZerodhaAccount {

    @Value("${zerodha.app.key}")
    public String zerodhaAppKey;
    @Value("${zerodha.api.secret}")
    String zerodhaApiSecret;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${zerodha.username}")
    String zerodhaUsername;
    @Value("${zerodha.password}")
    String zerodhaPassword;
    @Value("${zerodha.totp.key}")
    String zerodhaTotp;
    @Value("${zerodha.pin}")
    String zerodhaPin;
    @Autowired
    SendMessage sendMessage;
    @Value("${chromedriver.path}")
    String driverPath;
    @Autowired
    TransactionService transactionService;

    public com.zerodhatech.models.User user;
    public String token = null;
    public KiteConnect kiteSdk;

    //  @Scheduled(cron="${zerodha.login.schedule}")
    public String generateToken() throws IOException, InterruptedException, URISyntaxException {

        try {

            try {
                sendMessage.sendToTelegram("Token generation started", telegramToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log.info("generating token at: " + LocalDateTime.now().toString());
            System.setProperty("webdriver.chrome.driver", driverPath);
            ChromeOptions ChromeOptions = new ChromeOptions();
            ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
            WebDriver webDriver = new ChromeDriver(ChromeOptions);
            AuthRequestDTO authRequest = new AuthRequestDTO();
            webDriver.get("https://kite.zerodha.com/");

            Thread.sleep(3000);
            webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaUsername);
            webDriver.findElements(By.xpath("//input")).get(1).sendKeys(zerodhaPassword);

            webDriver.findElements(By.xpath("//button")).get(0).click();
            Thread.sleep(3000);
            webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaPin);
            webDriver.findElements(By.xpath("//button")).get(0).click();
            Thread.sleep(3000);

            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[1]/div/div[2]/div[1]/a[2]/span")).click();
            Thread.sleep(2000);
            //search
            webDriver.findElement(By.xpath("//*[@id=\"search-input\"]")).sendKeys("BANKNIFTY20O2222800CE");
            Thread.sleep(1000);
            //move over to search
            WebElement webElement = webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[2]/div[1]/div/div[1]/div/div[2]/ul/div/li[1]"));
            Actions actions = new Actions(webDriver);
            actions.moveToElement(webElement).perform();
            takeSnapShot(webDriver, "/home/hasvanth/test1.png");
            //click buy
            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[2]/div[1]/div/div[1]/div/div[2]/ul/div/li/span[3]/button[2]")).click();
            Thread.sleep(1000);
            takeSnapShot(webDriver, "/home/hasvanth/test2.png");
//short

            //mis
            webDriver.findElement(By.xpath("//html/body/div[1]/form/section/div[2]/div[1]/div/div[1]/label")).click();
            takeSnapShot(webDriver, "/home/hasvanth/test4.png");

            //market
            webDriver.findElement(By.xpath("/html/body/div[1]/form/section/div[2]/div[2]/div[2]/div[2]/div/div[1]/label")).click();
            takeSnapShot(webDriver, "/home/hasvanth/test4.png");
            //more options
/*            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[3]/div/form/div[3]/div[3]/a")).click();
            this.takeSnapShot(webDriver, "/home/hasvan



            th/test5.png") ;
*/
            webDriver.findElement(By.xpath("/html/body/div[1]/form/section/footer/div/div[2]/button[1]")).click();
            takeSnapShot(webDriver, "/home/hasvanth/test3.png");

            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[1]/div/div[2]/div[1]/a[3]/span")).click();
            Thread.sleep(2000);
            takeSnapShot(webDriver, "/home/hasvanth/test5.png");
            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[1]/div/div[2]/div[1]/a[4]/span")).click();
            Thread.sleep(2000);
            takeSnapShot(webDriver, "/home/hasvanth/test6.png");
            try {
                sendMessage.sendToTelegram("Zerodha Token: ", telegramToken);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
            try {
                sendMessage.sendToTelegram("Token generation failed", telegramToken);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    private String getTotp(String totp) throws Exception {

        try {
            Base32 base32 = new Base32();
            byte[] bytes = base32.decode(totp);
            String hexKey = Hex.encodeHexString(bytes);
            String previousKey = TOTP.getOTP(hexKey);

            while (previousKey.equals(TOTP.getOTP(hexKey))) {
                Thread.sleep(1000);
                log.info("sleeping for a second to wait for next key . current is : " + TOTP.getOTP(hexKey));

            }
            System.out.println("The new key is :" + TOTP.getOTP(hexKey));

            return TOTP.getOTP(hexKey);

        } catch (Exception e) {
            log.error("Exception occured --> ", e);
            throw e;
        }

    }

    //Scheduled(cron = "${zerodha.generate.token}")
    public String generateAccessToken() {

        try {


            log.info("generating token at: " + LocalDateTime.now().toString());

            if (token == null) {
                kiteSdk = new KiteConnect(zerodhaAppKey);
                kiteSdk.setUserId("RS4899");
                String url = kiteSdk.getLoginURL();
                System.setProperty("webdriver.chrome.driver", driverPath);
                ChromeOptions ChromeOptions = new ChromeOptions();
                ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
                WebDriver webDriver = new ChromeDriver(ChromeOptions);
                AuthRequestDTO authRequest = new AuthRequestDTO();
                webDriver.get(url);

                Thread.sleep(1000);
                webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaUsername);
                webDriver.findElements(By.xpath("//input")).get(1).sendKeys(zerodhaPassword);
                webDriver.findElements(By.xpath("//button")).get(0).click();
                Thread.sleep(1000);
                String totp=getTotp(zerodhaTotp);
                webDriver.findElements(By.xpath("//input")).get(0).sendKeys(totp);
                webDriver.findElements(By.xpath("//button")).get(0).click();
                Thread.sleep(1000);
               /* webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaPin);
                this.takeSnapShot(webDriver, "/home/hasvanth/test3.png");
                webDriver.findElements(By.xpath("//button")).get(0).click();*/
                Thread.sleep(1000);
                System.out.println(webDriver.getCurrentUrl());
                List<NameValuePair> queryParams = new URIBuilder(webDriver.getCurrentUrl()).getQueryParams();
                String requestToken = queryParams.stream().filter(param -> param.getName().equals("request_token")).map(NameValuePair::getValue).findFirst().orElse("");

                user = kiteSdk.generateSession(requestToken, zerodhaApiSecret);
                System.out.println(user.accessToken);
                kiteSdk.setAccessToken(user.accessToken);
                token = user.accessToken;
                kiteSdk.setPublicToken(user.publicToken);
                Margin margins = kiteSdk.getMargins("equity");
                System.out.println(margins.available.cash);
                System.out.println(margins.utilised.debits);
                sendMessage.sendToTelegram("Token :" + kiteSdk.getAccessToken(), telegramToken);
                sendMessage.sendToTelegram("Available Cash :" + margins.available.cash, telegramToken);

                webDriver.quit();
            }
        } catch (Exception | KiteException e) {
            e.printStackTrace();
            try {
                sendMessage.sendToTelegram("Token generation failed", telegramToken);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }


    @Autowired
    UserList userList;
    @Scheduled(cron = "${zerodha.generate.token}")
    @PostConstruct
    public String generateMultiUserAccessToken() throws IOException, InterruptedException, URISyntaxException {
        userList.getUser().stream().filter(user1 -> user1.enabled).forEach(user1->{
            KiteConnect kiteConnect;
        try {
            kiteConnect = new KiteConnect(user1.appkey);
            kiteConnect.setUserId(user1.name);
            String url = kiteConnect.getLoginURL();
            System.setProperty("webdriver.chrome.driver", driverPath);
            ChromeOptions ChromeOptions = new ChromeOptions();
            ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
            WebDriver webDriver = new ChromeDriver(ChromeOptions);
            AuthRequestDTO authRequest = new AuthRequestDTO();
            webDriver.get(url);

            Thread.sleep(1000);

            webDriver.findElements(By.xpath("//input")).get(0).sendKeys(user1.name);
            webDriver.findElements(By.xpath("//input")).get(1).sendKeys(user1.password);
            webDriver.findElements(By.xpath("//button")).get(0).click();
           // takeSnapShot(webDriver, "/home/ubuntu/test3_"+user1.name+".png");
            Thread.sleep(1000);
            String totp=getTotp(user1.totp);
            webDriver.findElements(By.xpath("//input")).get(0).sendKeys(totp);
           // takeSnapShot(webDriver, "/home/ubuntu/test2_"+user1.name+".png");
            webDriver.findElements(By.xpath("//button")).get(0).click();

            Thread.sleep(1000);
           // takeSnapShot(webDriver, "/home/ubuntu/test1_"+user1.name+".png");
               /* webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaPin);
                this.takeSnapShot(webDriver, "/home/hasvanth/test3.png");
                webDriver.findElements(By.xpath("//button")).get(0).click();*/
            Thread.sleep(1000);
            System.out.println(webDriver.getCurrentUrl());
            List<NameValuePair> queryParams = new URIBuilder(webDriver.getCurrentUrl()).getQueryParams();
            String requestToken = queryParams.stream().filter(param -> param.getName().equals("request_token")).map(NameValuePair::getValue).findFirst().orElse("");

            User user = kiteConnect.generateSession(requestToken, user1.secret);
            System.out.println(user.accessToken);
            kiteConnect.setAccessToken(user.accessToken);
            token = user.accessToken;
            kiteConnect.setPublicToken(user.publicToken);
            Margin margins = kiteConnect.getMargins("equity");
            System.out.println(margins.available.cash);
            System.out.println(margins.utilised.debits);
            sendMessage.sendToTelegram("Token for user:"+user.userName+":" + kiteConnect.getAccessToken(), telegramToken,"-713214125");
            sendMessage.sendToTelegram("Available Cash :" + margins.available.cash, telegramToken,"-713214125");
            user1.kiteConnect=kiteConnect;
            if (user1.admin){
                transactionService.setup();
                kiteSdk=kiteConnect;
            }
            webDriver.quit();
            } catch (URISyntaxException | IOException | KiteException | InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                sendMessage.sendToTelegram("Token generation failed", telegramToken,"-713214125");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

    });
        return null;
    }


    public static void takeSnapShot(WebDriver webdriver, String fileWithPath) throws Exception {

        //Convert web driver object to TakeScreenshot

        TakesScreenshot scrShot = ((TakesScreenshot) webdriver);

        //Call getScreenshotAs method to create image file

        File SrcFile = scrShot.getScreenshotAs(OutputType.FILE);

        //Move image file to new destination

        File DestFile = new File(fileWithPath);

        //Copy file at destination

        FileUtils.copyFile(SrcFile, DestFile);

    }

    @Autowired
    ZerodhaAccount zerodhaAccount;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    List<String> aboveR1Live=new ArrayList<>();

    Map<String,StandardPivots> indexPivots=new HashMap<>();
    Map<String,StandardPivots> stockPivots=new HashMap<>();
    @Autowired
    StockDayDataRepository stockDayDataRepository;

    @Autowired
    StockWeekDataRepository stockWeekDataRepository;
    @Autowired
    IndexDayDataRepository indexDayDataRepository;
    @Autowired
    IndexWeekDataRepository indexWeekDataRepository;
    @Autowired
    IndexMonthDataRepository indexMonthDataRepository;
    @Autowired
    IndexYearDataRepository indexYearDataRepository;
    @Autowired
    StockYearDataRepository stockYearDataRepository;
    @Autowired
    StockMonthDataRepository stockMonthDataRepository;
    @Autowired
    StockRepository stockRepository;
    public Calendar getPreviousYear(){
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        return cal;

    }
    @Scheduled(cron="${zerodha.pivots.data}")
    public void populatePivots(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar fromCalendar = Calendar.getInstance();
        fromCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        fromCalendar.add(Calendar.DATE, -7);
        Calendar toCalender = Calendar.getInstance();
        toCalender.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        toCalender.add(Calendar.DATE, -1);
        String weekPivotStartDay = dateFormat.format(fromCalendar.getTime());
        Calendar calPreYear=getPreviousYear();
        String yearPivotStartDay = dateFormat.format(calPreYear.getTime());
        Calendar monthCalendar = Calendar.getInstance();
        monthCalendar.add(Calendar.MONTH, -1);
        monthCalendar.set(Calendar.DATE, 1);
        String monthDateStr = dateFormat.format(monthCalendar.getTime());/*
        Calendar currentDay = Calendar.getInstance();
        currentDay.add(Calendar.DATE, -1);
        String dayPivotStartDay = dateFormat.format(currentDay.getTime());*/

        IndexWeekDataEntity indexWeekDataEntity=indexWeekDataRepository.findSymbolWithDate("BANKNIFTY",weekPivotStartDay);
        IndexMonthlyDataEntity indexMonthlyDataEntity=indexMonthDataRepository.findSymbolWithDate("BANKNIFTY",monthDateStr);
        IndexYearDataEntity indexYearly=indexYearDataRepository.findSymbolWithDate("BANKNIFTY",yearPivotStartDay);
        IndexDayDataEntity indexDayDataEntity=indexDayDataRepository.findLastRecord("BANKNIFTY");
        try{
        StandardPivot dayPivots=calculatePRS(indexDayDataEntity.getHigh(),indexDayDataEntity.getLow(),indexDayDataEntity.getClose(), PivotTimeFrame.DAY.name());
        StandardPivot weekPivots=calculatePRS(indexWeekDataEntity.getHigh(),indexWeekDataEntity.getLow(),indexWeekDataEntity.getClose(), PivotTimeFrame.WEEK.name());
        StandardPivot monthPivots=calculatePRS(indexMonthlyDataEntity.getHigh(),indexMonthlyDataEntity.getLow(),indexMonthlyDataEntity.getClose(), PivotTimeFrame.MONTH.name());
        StandardPivot yearPivots=calculatePRS(indexYearly.getHigh(),indexYearly.getLow(),indexYearly.getClose(), PivotTimeFrame.YEAR.name());
        StandardPivots standardPivots=new StandardPivots();
        standardPivots.setDayPivots(dayPivots);
        standardPivots.setWeekPivots(weekPivots);
        standardPivots.setYearPivots(yearPivots);
        standardPivots.setMonthPivots(monthPivots);
        indexPivots.put("BANKNIFTY",standardPivots);
        }catch (Exception e){e.printStackTrace();
        }
        List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {
            try {
                StockWeekDataEntity stockWeekDataEntity = stockWeekDataRepository.findSymbolWithDate(stockEntity.getSymbol(), weekPivotStartDay);
                StockMonthDataEntity stockMonthDataEntity = stockMonthDataRepository.findSymbolWithDate(stockEntity.getSymbol(), monthDateStr);
                StockYearDataEntity stockYearDataEntity = stockYearDataRepository.findSymbolWithDate(stockEntity.getSymbol(), yearPivotStartDay);
                StockDayDataEntity stockDayDataEntity = stockDayDataRepository.findLastRecord(stockEntity.getSymbol());
                StandardPivot stockDayPivots = calculatePRS(stockDayDataEntity.getHigh(), stockDayDataEntity.getLow(), stockDayDataEntity.getClose(), PivotTimeFrame.DAY.name());
                StandardPivot stockWeekPivots = calculatePRS(stockWeekDataEntity.getHigh(), stockWeekDataEntity.getLow(), stockWeekDataEntity.getClose(), PivotTimeFrame.WEEK.name());
                StandardPivot stockMonthPivots = calculatePRS(stockMonthDataEntity.getHigh(), stockMonthDataEntity.getLow(), stockMonthDataEntity.getClose(), PivotTimeFrame.MONTH.name());
                StandardPivot stockYearPivots = calculatePRS(stockYearDataEntity.getHigh(), stockYearDataEntity.getLow(), stockYearDataEntity.getClose(), PivotTimeFrame.YEAR.name());
                StandardPivots stockStandardPivots = new StandardPivots();
                stockStandardPivots.setDayPivots(stockDayPivots);
                stockStandardPivots.setWeekPivots(stockWeekPivots);
                stockStandardPivots.setYearPivots(stockYearPivots);
                stockStandardPivots.setMonthPivots(stockMonthPivots);
                //   System.out.println(stockStandardPivots);
                stockPivots.put(stockEntity.getSymbol(), stockStandardPivots);
            }catch (Exception e){
                e.printStackTrace();
            }
        });
        System.out.println(new Gson().toJson(indexPivots));
        System.out.println(new Gson().toJson(stockPivots));
    }

    public StandardPivot calculatePRS(double HIGHprev, double LOWprev, double CLOSEprev, String timeFrame){
        NumberFormat formatter = new DecimalFormat("#0.00");
        double PP;
        double R1;
        double S1;
        double R2;
        double S2;
        double R3;
        double S3;
        double R4;
        double S4;
        double R5;
        double S5;
        PP = (HIGHprev + LOWprev + CLOSEprev) / 3;
        R1 = PP * 2 - LOWprev;
        S1 = PP * 2 - HIGHprev;
        R2 = PP + (HIGHprev - LOWprev);
        S2 = PP - (HIGHprev - LOWprev);
        R3 = PP * 2 + (HIGHprev - 2 * LOWprev);
        S3 = PP * 2 - (2 * HIGHprev - LOWprev);
        R4 = PP * 3 + (HIGHprev - 3 * LOWprev);
        S4 = PP * 3 - (3 * HIGHprev - LOWprev);
        R5 = PP * 4 + (HIGHprev - 4 * LOWprev);
        S5 = PP * 4 - (4 * HIGHprev - LOWprev);
        StandardPivot standardPivots=new StandardPivot();
        standardPivots.setPP(Double.parseDouble(formatter.format(PP)));
        standardPivots.setPivotTimeFrame(timeFrame);
        standardPivots.setR1(Double.parseDouble(formatter.format(R1)));
        standardPivots.setR2(Double.parseDouble(formatter.format(R2)));
        standardPivots.setR3(Double.parseDouble(formatter.format(R3)));
        standardPivots.setR5(Double.parseDouble(formatter.format(R5)));
        standardPivots.setR4(Double.parseDouble(formatter.format(R4)));
        standardPivots.setS1(Double.parseDouble(formatter.format(S1)));
        standardPivots.setS2(Double.parseDouble(formatter.format(S2)));
        standardPivots.setS3(Double.parseDouble(formatter.format(S3)));
        standardPivots.setS4(Double.parseDouble(formatter.format(S4)));
        standardPivots.setS5(Double.parseDouble(formatter.format(S5)));
        return standardPivots;

    }
    Map<String,Double> last20DayMax=new HashMap<>();
    Map<String,Double> last365Max=new HashMap<>();

    @Scheduled(cron = "${zerodha.find.max.data}")
    public void findMax() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar oneYearDate = Calendar.getInstance();
        oneYearDate.add(Calendar.DATE, -365);
        Calendar last20Days = Calendar.getInstance();
        last20Days.add(Calendar.DATE, -20);
        zerodhaTransactionService.lsSymbols.forEach((key, value) -> {
            try {
                String yearMax = stockDayDataRepository.findHigh(key, dateFormat.format(oneYearDate.getTime()));
                String last20Max = stockDayDataRepository.findHigh(key, dateFormat.format(last20Days.getTime()));
                last20DayMax.put(key, Double.parseDouble(last20Max));
                last365Max.put(key, Double.parseDouble(yearMax));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
  //  @Scheduled(cron = "${zerodha.schedule.pivot.alert}")
    public void findPivots() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar currentDate = Calendar.getInstance();
        String fromDate = dateFormat.format(currentDate.getTime());

        zerodhaTransactionService.lsSymbols.entrySet().forEach(symbolMap -> {
            try {
                System.out.println(symbolMap+":"+dateTimeFormat.format(new Date()));
                HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(fromDate + " 09:15:00"), dateTimeFormat.parse(fromDate + " 15:05:00"), symbolMap.getValue(), "5minute", false, false);
                Thread.sleep(400);
                if (historicalData != null && historicalData.dataArrayList.size() > 0) {
                    HistoricalData historicalDataOpen = historicalData.dataArrayList.get(0);
                    HistoricalData lastCandle = historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1);
                    StandardPivots standardPivots = stockPivots.get(symbolMap.getKey());
                    if(standardPivots!=null) {
                        StandardPivot weekPivot = standardPivots.getWeekPivots();
                        StandardPivot monthPivot = standardPivots.getMonthPivots();
                        double perMove = MathUtils.percentageMove(historicalDataOpen.open, lastCandle.close);
                        if (perMove > 0 && lastCandle.close > weekPivot.getR1() && !aboveR1Live.contains(symbolMap.getKey())) {
                            aboveR1Live.add(symbolMap.getKey());
                            String message="Stock Above WR1: " + symbolMap.getKey() + " WR1: " + weekPivot.getR1() + " Current Close: " + lastCandle.close;
                            if (lastCandle.close > monthPivot.getR1()) {
                                message= message+"\n"+ "Stock Above MR1 ";
                            }
                            if(historicalDataOpen.close > weekPivot.getR1()){
                                message= message+"\n"+ "Open Above WR1 ";
                            }
                       double last20DayMaxValue=last20DayMax.get(symbolMap.getKey());
                            double last365MaxValue=last365Max.get(symbolMap.getKey());
                            if (lastCandle.close >last20DayMaxValue) {
                                message= message+"\n"+ "Above 52 week ";
                            }
                            if(historicalDataOpen.close > last365MaxValue){
                                message= message+"\n"+ "Above 20 Day";
                            }
                            try {
                                sendMessage.sendToTelegram(message, telegramToken);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }catch (KiteException e) {
                System.out.println(symbolMap.getKey()+":"+String.valueOf(e.code));
                e.printStackTrace();
            }catch (Exception e){
                System.out.println(symbolMap.getKey()+":");
                e.printStackTrace();
            }
        });
    }/*
    @Scheduled(cron = "${over.position.monitor.scheduler}")
   public void monitorPositionSize() throws IOException, KiteException {
       List<Position> positions = zerodhaAccount.kiteSdk.getPositions().get("net");

       positions.stream().forEach(position ->
       {
           int qty = 25 * (Integer.valueOf(bankniftyLot));
           int niftyqty = 50 * (Integer.valueOf(niftyLot));
           if( position.tradingSymbol.startsWith("BANKNIFTY")) {
               if(position.netQuantity > qty ) {
                   OrderParams orderParams = new OrderParams();
                   orderParams.tradingsymbol = position.tradingSymbol;
                   orderParams.exchange = "NFO";
                   orderParams.quantity = Math.abs(position.netQuantity);
                   orderParams.orderType = "MARKET";
                   orderParams.product = "MIS";
                   //orderParams.price=price.doubleValue();
                   if (position.netQuantity > 0) {
                       orderParams.transactionType = "SELL";
                   } else {
                       orderParams.transactionType = "BUY";
                   }
                   orderParams.validity = "DAY";
                   com.zerodhatech.models.Order orderResponse = null;
                   try {
                       orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");

                       String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
                       log.info(message);
                       sendMessage.sendToTelegram(message, telegramToken);

                   } catch (IOException e) {
                       e.printStackTrace();
                   } catch (KiteException e) {
                       e.printStackTrace();
                   }
               }else if(position.pnl<-2500 && position.netQuantity !=0) {
                   OrderParams orderParams = new OrderParams();
                   orderParams.tradingsymbol = position.tradingSymbol;
                   orderParams.exchange = "NFO";
                   orderParams.quantity = Math.abs(position.netQuantity);
                   orderParams.orderType = "MARKET";
                   orderParams.product = "MIS";
                   //orderParams.price=price.doubleValue();
                   if (position.netQuantity > 0) {
                       orderParams.transactionType = "SELL";
                   } else {
                       orderParams.transactionType = "BUY";
                   }
                   orderParams.validity = "DAY";
                   com.zerodhatech.models.Order orderResponse = null;
                   try {
                       orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");

                       String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
                       log.info(message);
                       sendMessage.sendToTelegram(message, telegramToken);

                   } catch (IOException e) {
                       e.printStackTrace();
                   } catch (KiteException e) {
                       e.printStackTrace();
                   }
               }
           }else if( position.tradingSymbol.startsWith("NIFTY"))

               if(position.netQuantity > niftyqty ) {
                   OrderParams orderParams = new OrderParams();
                   orderParams.tradingsymbol = position.tradingSymbol;
                   orderParams.exchange = "NFO";
                   orderParams.quantity = Math.abs(position.netQuantity);
                   orderParams.orderType = "MARKET";
                   orderParams.product = "MIS";
                   //orderParams.price=price.doubleValue();
                   if (position.netQuantity > 0) {
                       orderParams.transactionType = "SELL";
                   } else {
                       orderParams.transactionType = "BUY";
                   }
                   orderParams.validity = "DAY";
                   com.zerodhatech.models.Order orderResponse = null;
                   try {
                       orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");

                       String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
                       log.info(message);
                       sendMessage.sendToTelegram(message, telegramToken);

                   } catch (IOException e) {
                       e.printStackTrace();
                   } catch (KiteException e) {
                       e.printStackTrace();
                   }
               }else if(position.pnl<-2500) {
           OrderParams orderParams = new OrderParams();
           orderParams.tradingsymbol = position.tradingSymbol;
           orderParams.exchange = "NFO";
           orderParams.quantity = Math.abs(position.netQuantity);
           orderParams.orderType = "MARKET";
           orderParams.product = "MIS";
           //orderParams.price=price.doubleValue();
           if (position.netQuantity > 0) {
               orderParams.transactionType = "SELL";
           } else {
               orderParams.transactionType = "BUY";
           }
           orderParams.validity = "DAY";
           com.zerodhatech.models.Order orderResponse;
           try {
               orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");

               String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
               log.info(message);
               sendMessage.sendToTelegram(message, telegramToken);

           } catch (IOException e) {
               e.printStackTrace();
           } catch (KiteException e) {
               e.printStackTrace();
           }
       }

       });
   }*/

}
