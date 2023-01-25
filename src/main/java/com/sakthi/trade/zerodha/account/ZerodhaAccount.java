package com.sakthi.trade.zerodha.account;

import com.google.gson.Gson;
import com.sakthi.trade.dhan.DhanRoutes;
import com.sakthi.trade.dhan.schema.FundResponseDTO;
import com.sakthi.trade.domain.OpenPositionData;
import com.sakthi.trade.fyer.AuthRequestDTO;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.repo.*;
import com.sakthi.trade.telegram.TelegramMessenger;

import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
import com.zerodhatech.models.User;
import de.taimos.totp.TOTP;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;

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
    TelegramMessenger sendMessage;
    @Value("${chromedriver.path}")
    String driverPath;
    @Value("${test.profile:false}")
    Boolean testProfile;
    @Autowired
    DhanRoutes dhanRoutes;
    @Autowired
    TransactionService transactionService;

    public com.zerodhatech.models.User user;
    public String token = null;
    public KiteConnect kiteSdk;
    public int spaceCheck=0;
    SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

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
        //    takeSnapShot(webDriver, "/home/hasvanth/test1.png");
            //click buy
            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[2]/div[1]/div/div[1]/div/div[2]/ul/div/li/span[3]/button[2]")).click();
            Thread.sleep(1000);
         //   takeSnapShot(webDriver, "/home/hasvanth/test2.png");
//short

            //mis
            webDriver.findElement(By.xpath("//html/body/div[1]/form/section/div[2]/div[1]/div/div[1]/label")).click();
      //      takeSnapShot(webDriver, "/home/hasvanth/test4.png");

            //market
            webDriver.findElement(By.xpath("/html/body/div[1]/form/section/div[2]/div[2]/div[2]/div[2]/div/div[1]/label")).click();
       //     takeSnapShot(webDriver, "/home/hasvanth/test4.png");
            //more options
/*            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[3]/div/form/div[3]/div[3]/a")).click();
            this.takeSnapShot(webDriver, "/home/hasvan



            th/test5.png") ;
*/
            webDriver.findElement(By.xpath("/html/body/div[1]/form/section/footer/div/div[2]/button[1]")).click();
     //       takeSnapShot(webDriver, "/home/hasvanth/test3.png");

            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[1]/div/div[2]/div[1]/a[3]/span")).click();
            Thread.sleep(2000);
     //       takeSnapShot(webDriver, "/home/hasvanth/test5.png");
            webDriver.findElement(By.xpath("//*[@id=\"app\"]/div[1]/div/div[2]/div[1]/a[4]/span")).click();
            Thread.sleep(2000);
     //       takeSnapShot(webDriver, "/home/hasvanth/test6.png");
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

    public String getTotp(String totp) throws Exception {

        try {
            Base32 base32 = new Base32();
            byte[] bytes = base32.decode(totp);
            String hexKey = Hex.encodeHexString(bytes);
            String previousKey = TOTP.getOTP(hexKey);
            int i=0;
            while (previousKey.equals(TOTP.getOTP(hexKey))) {
                Thread.sleep(1000);
                if (i == 0) {
                log.info("sleeping for a second to wait for next key . current is : " + TOTP.getOTP(hexKey));
                }
                i++;

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


    Logger logger = LoggerFactory.getLogger(ZerodhaAccount.class);
    @Autowired
    UserList userList;
    @Scheduled(cron = "${zerodha.generate.token}")
    @PostConstruct
    public String generateMultiUserAccessToken() throws IOException, InterruptedException, URISyntaxException {
        Date date=new Date();
        MDC.put("run_time",candleDateTimeFormat.format(date));
        if(!testProfile) {
            userList.getUser().stream().filter(user1 -> user1.enabled).forEach(user1 -> {
                if (user1.tokenCount < 2) {
                if (user1.broker.equals("zerodha")) {
                        KiteConnect kiteConnect;
                        System.setProperty("webdriver.chrome.driver", driverPath);
                        ChromeOptions ChromeOptions = new ChromeOptions();
                        ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
                        WebDriver webDriver = new ChromeDriver(ChromeOptions);
                        try {
                            kiteConnect = new KiteConnect(user1.appkey);
                            kiteConnect.setUserId(user1.name);
                            String url = kiteConnect.getLoginURL();


                            AuthRequestDTO authRequest = new AuthRequestDTO();
                            webDriver.get(url);

                            Thread.sleep(2000);

                            webDriver.findElements(By.xpath("//input")).get(0).sendKeys(user1.name);
                            webDriver.findElements(By.xpath("//input")).get(1).sendKeys(user1.password);
                            webDriver.findElements(By.xpath("//button")).get(0).click();
                            //       takeSnapShot(webDriver, "/home/hasvanth/test3_"+user1.name+".png");
                            Thread.sleep(2000);
                            String totp = getTotp(user1.totp);
                            webDriver.findElements(By.xpath("//input")).get(0).sendKeys(totp);
                            //      takeSnapShot(webDriver, "/home/hasvanth/test2_"+user1.name+".png");
                     //       webDriver.findElements(By.xpath("//button")).get(0).click();

                       //     Thread.sleep(1000);
                            //         takeSnapShot(webDriver, "/home/hasvanth/test1_"+user1.name+".png");
               /* webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaPin);
                this.takeSnapShot(webDriver, "/home/hasvanth/test3.png");
                webDriver.findElements(By.xpath("//button")).get(0).click();*/
                            Thread.sleep(2000);
                            System.out.println(webDriver.getCurrentUrl());
                            List<NameValuePair> queryParams = new URIBuilder(webDriver.getCurrentUrl()).getQueryParams();
                            String requestToken = queryParams.stream().filter(param -> param.getName().equals("request_token")).map(NameValuePair::getValue).findFirst().orElse("");

                            User user = kiteConnect.generateSession(requestToken, user1.secret);
                            System.out.println(user.accessToken);
                            kiteConnect.setAccessToken(user.accessToken);
                            token = user.accessToken;
                            kiteConnect.setPublicToken(user.publicToken);
                            Margin margins = kiteConnect.getMargins("equity");
                            logger.info(margins.available.cash);
                            logger.info(margins.utilised.debits);
                            String botId = "";
                            TelegramBot telegramBot = user1.getTelegramBot();
                            if (telegramBot != null) {
                                botId = telegramBot.getGroupId();
                            }
                            String botIdFinal = botId;

                            user1.kiteConnect = kiteConnect;
                            user1.tokenGenerated = true;
                            if (user1.admin) {
                                transactionService.setup();
                                kiteSdk = kiteConnect;
                                sendMessage.sendToTelegram("Token for user:" + user.userName + ":" + kiteConnect.getAccessToken(), telegramToken, botIdFinal);
                                try {
                                    Double amount = Double.parseDouble(margins.available.cash) - 1000000;
                                    sendMessage.sendToTelegram("Available Cash :" + new BigDecimal(amount).setScale(0, RoundingMode.HALF_UP).doubleValue(), telegramToken, botIdFinal);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                sendMessage.sendToTelegram("Token for user:" + user.userName + ":" + kiteConnect.getAccessToken(), telegramToken, botIdFinal);
                                sendMessage.sendToTelegram("Available Cash :" + +new BigDecimal(margins.available.cash).setScale(0, RoundingMode.HALF_UP).doubleValue(), telegramToken, botIdFinal);
                            }
                            user1.tokenCount = user1.tokenCount + 1;
                            webDriver.quit();
                        } catch (URISyntaxException | IOException | KiteException | InterruptedException e) {
                            sendMessage.sendToTelegram("Token generation failed" + e.getMessage(), telegramToken, "-713214125");
                            try {
                                //takeSnapShot(webDriver, "/home/ubuntu/test1_"+user1.name+".png");
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }

                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                            try {
                                if (user1.getStraddleConfigOld().enabled) {
                                    sendMessage.sendToTelegram("Token generation failed", telegramToken);
                                } else {
                                    sendMessage.sendToTelegram("Token generation failed", telegramToken, "-713214125");
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                else if (user1.broker.equals("dhan")) {
                    try {
                        String botId = "";
                        TelegramBot telegramBot = user1.getTelegramBot();
                        if (telegramBot != null) {
                            botId = telegramBot.getGroupId();
                        }
                        String botIdFinal = botId;
                        user1.tokenCount = user1.tokenCount + 1;
                        Request request = transactionService.createGetRequests(dhanRoutes.get("funds"), user1.getAccessToken());
                        String response = transactionService.callAPI(request);
                        System.out.println(response);
                        com.sakthi.trade.dhan.schema.FundResponseDTO fundResponseDTO = new Gson().fromJson(response, FundResponseDTO.class);
                        sendMessage.sendToTelegram("Dhan client ID :" + fundResponseDTO.getDhanClientId() + ":" + user1.clientName + " : Available cash: " + fundResponseDTO.getAvailabelBalance().setScale(0, RoundingMode.HALF_UP).doubleValue(), telegramToken, botIdFinal);
                    } catch (Exception e) {
                        sendMessage.sendToTelegram("Dhan client ID :" + user1.getClientId() + "Token generation failed" + e.getMessage(), telegramToken, "-713214125");
                        e.printStackTrace();
                    }
                }
                }
            });
            if(spaceCheck<2) {
                try {
                    // Execute the "df" command
                    Process p = Runtime.getRuntime().exec("df -h");
                    p.waitFor();

                    // Read the output of the command
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    int i = 0;
                    while ((line = reader.readLine()) != null) {
                        if (i > 0) {

                            // Split the output line by spaces
                            String[] elements = line.split("\\s+");
                            // The third element is the total disk space, and the fourth element is the used disk space
                            if (elements[5].equals("/")) {
                                String used = elements[2];
                                String free = elements[3];
                                // Calculate the free disk space
                                String usedPerf = elements[4];
                                String total = elements[1];
                                sendMessage.sendToTelegram("Total: " + total + " Used:" + used + " Free:" + free + " Used Percent:" + usedPerf, telegramToken, "-713214125");
                            }

                        }
                        i++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                spaceCheck=spaceCheck+1;
            }
        }
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

    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    ZerodhaAccount zerodhaAccount;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    List<String> aboveR1Live=new ArrayList<>();
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
    Map<String,Double> last20DayMax=new HashMap<>();
    Map<String,Double> last365Max=new HashMap<>();

  //  @Scheduled(cron = "${zerodha.find.max.data}")
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
    @Autowired
    OpenTradeDataRepo openTradeDataRepo;
    private Map<String,Integer> getTradeOpenQty(){
        Map<String,Integer> openTradeData = new HashMap<>();
            List<OpenPositionData> openTradeDataEntities;
            String userId=zerodhaAccount.kiteSdk.getUserId();
        openTradeDataEntities = openTradeDataRepo.getTradeOpenData(userId);
        openTradeDataEntities.stream().forEach(openTradeDataEntity -> {
            openTradeData.put(openTradeDataEntity.getStockName(),openTradeDataEntity.getQty());
        });
        return openTradeData;
    }
  //  @Scheduled(cron = "${over.position.monitor.scheduler}")
   public void monitorPositionSize() throws IOException, KiteException {

        Map<String,Integer> openTradeData =getTradeOpenQty();
        openTradeData.entrySet().stream().forEach(map->{
            System.out.println("over position:"+map.getKey()+": qty:"+map.getValue());
        });
       List<Position> positions = zerodhaAccount.kiteSdk.getPositions().get("net");
       positions.stream().filter(position -> position.netQuantity > 0).forEach(position ->
       {
           executorService.submit(() -> {
           if (openTradeData.containsKey(position.tradingSymbol)) {
               Integer openQty = openTradeData.get(position.tradingSymbol);
               Integer overPositionQty = position.netQuantity - openQty;
               if (overPositionQty > 0) {
                   OrderParams orderParams = new OrderParams();
                   orderParams.tradingsymbol = position.tradingSymbol;
                   orderParams.exchange = "NFO";
                   orderParams.quantity = Math.abs(position.netQuantity);
                   orderParams.orderType = "MARKET";
                   orderParams.product = position.product;
                   //orderParams.price=price.doubleValue();
                   if (position.netQuantity > 0) {
                       orderParams.transactionType = "SELL";
                   } else {
                       orderParams.transactionType = "BUY";
                   }
                   orderParams.validity = "DAY";
                   com.zerodhatech.models.Order orderResponse = null;
                   try {
                      // orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");

                       String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
                       log.info(message);
                       sendMessage.sendToTelegram(message, telegramToken);

                   } catch (Exception e) {
                       e.printStackTrace();
                   }
                    /*else if (position.pnl < -2500) {
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
               }*/
               }
           } else {
               try {
                   Thread.sleep(// minutes to sleep. for 1 min its not required.
                           60 *    // seconds to a minute
                                   1000);
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
               Map<String, Integer> openTradeData1 = getTradeOpenQty();
               if (openTradeData1.containsKey(position.tradingSymbol)) {
                   Integer openQty = openTradeData1.get(position.tradingSymbol);
                   Integer overPositionQty = position.netQuantity - openQty;
                   if (overPositionQty > 0) {
                       OrderParams orderParams = new OrderParams();
                       orderParams.tradingsymbol = position.tradingSymbol;
                       orderParams.exchange = "NFO";
                       orderParams.quantity = Math.abs(position.netQuantity);
                       orderParams.orderType = "MARKET";
                       orderParams.product = position.product;
                       //orderParams.price=price.doubleValue();
                       if (position.netQuantity > 0) {
                           orderParams.transactionType = "SELL";
                       } else {
                           orderParams.transactionType = "BUY";
                       }
                       orderParams.validity = "DAY";
                       com.zerodhatech.models.Order orderResponse = null;
                       try {
                           //orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");

                           String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
                           log.info(message);
                           sendMessage.sendToTelegram(message, telegramToken);

                       } catch (Exception e) {
                           e.printStackTrace();
                       }
                   }
               } else {
                   OrderParams orderParams = new OrderParams();
                   orderParams.tradingsymbol = position.tradingSymbol;
                   orderParams.exchange = "NFO";
                   orderParams.quantity = Math.abs(position.netQuantity);
                   orderParams.orderType = "MARKET";
                   orderParams.product = position.product;
                   //orderParams.price=price.doubleValue();
                   if (position.netQuantity > 0) {
                       orderParams.transactionType = "SELL";
                   } else {
                       orderParams.transactionType = "BUY";
                   }
                   orderParams.validity = "DAY";
                   com.zerodhatech.models.Order orderResponse = null;
                   try {
                    //   orderResponse = zerodhaAccount.kiteSdk.placeOrder(orderParams, "regular");
                       String message = MessageFormat.format("Closed Over Leveraged Position {0}", orderParams.tradingsymbol);
                       log.info(message);
                       sendMessage.sendToTelegram(message, telegramToken);

                   } catch (Exception e) {
                       e.printStackTrace();
                   }
               }
           }
       });
       });
   }

}
