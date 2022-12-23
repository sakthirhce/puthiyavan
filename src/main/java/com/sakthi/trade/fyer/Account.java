package com.sakthi.trade.fyer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.binance.client.impl.utils.JsonWrapperArray;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.FundResponseDTO;
import com.sakthi.trade.domain.ProfileResponseDTO;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.fyer.model.Candlestick;
import com.sakthi.trade.fyer.model.PivotTimeFrame;
import com.sakthi.trade.fyer.model.StandardPivot;
import com.sakthi.trade.fyer.model.StandardPivots;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.repo.*;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.util.MathUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Calendar.DAY_OF_MONTH;

@Component
@Slf4j
public class Account {
/*
    NSE – Currency Derivatives:
    https://public.fyers.in/sym_details/NSE_CD.csv
    NSE – Equity Derivatives:
    https://public.fyers.in/sym_details/NSE_FO.csv
    NSE – Capital Market:
    https://public.fyers.in/sym_details/NSE_CM.csv
    BSE – Capital Market:
    https://public.fyers.in/sym_details/BSE_CM.csv
    MCX - Commodity:
    https://public.fyers.in/sym_details/MCX_COM.csv*/
    @Value("${fyers.appId}")
    public String fyerAppId;
    Gson gson = new GsonBuilder().setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    @Value("${fyers.secretKey}")
    String fyersecretKey;
    @Value("${fyers.username}")
    String fyerUsername;
    @Value("${fyers.password}")
    String fyerPassword;
    @Value("${fyers.pancard}")
    String fyerPan;
    @Value("${fyers.authURL}")
    String authURL;
    @Value("${fyers.generateTokenURL}")
    String generateTokenURL;
    @Value("${chromedriver.path}")
    String driverPath;

    @Autowired
    TelegramMessenger sendMessage;
    public Map<String,String> lsFyerSymbols=new HashMap<>();
    public Map<String,String> lsFOSymbols=new HashMap<>();
    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${fyers.get.profile}")
    String getProfileURL;

    @Value("${fyers.get.fund}")
    String getFundURL;

    @Autowired
    TransactionService transactionService;

    @Value("${fyers.history}")
    String historyURL;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    StockDataRepository stockDataRepository;

    @Autowired
    IndexDataRepository indexDataRepository;

    @Autowired
    @Qualifier("createOkHttpClient")
    OkHttpClient okHttpClient;

    public String token=null;
  //  @Scheduled(cron="${fyer.generate.token.empty}")
    public void emptyToken() throws IOException, InterruptedException, URISyntaxException {
        log.info("setting token to null at: "+ LocalDateTime.now().toString());
        token=null;
    }
    @PostConstruct
    public void alertApplicationStartup() throws ParseException {
        try {
        sendMessage.sendToTelegram("Application started/re-started successfully",telegramToken,"-713214125");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

   // @Scheduled(cron="${fyer.password.validity.check}")
    public void passwordValidityCheck() throws IOException, InterruptedException, URISyntaxException {
        log.info("Password validity check scheduler started");
        try {
            Request request=transactionService.createGetRequest(getProfileURL, null);
            String strResponse=transactionService.callAPI(request);
            ProfileResponseDTO profileResponseDTO=new Gson().fromJson(strResponse,ProfileResponseDTO.class);
            long passwordExpDays=profileResponseDTO.getResult().getPasswordExpiryDays();
            if(passwordExpDays<=5){
                try {
                    sendMessage.sendToTelegram("Reminder: Password expires in "+passwordExpDays+" days. please reset and update password in app.properties to avoid algo execution failure",telegramToken);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    //@Scheduled(cron="${fyer.fund.check}")
    public void availableFund() throws IOException, InterruptedException, URISyntaxException {
        log.info("Fund check scheduler started");
        try {
            Request request=transactionService.createGetRequest(getFundURL, null);
            String strResponse=transactionService.callAPI(request);
            FundResponseDTO fundResponseDTO=new Gson().fromJson(strResponse,FundResponseDTO.class);
           if(fundResponseDTO.getS().equals("ok")){
               StringBuilder str=new StringBuilder();
               fundResponseDTO.getFundLimit().stream().forEach( fund-> {
                   str.append(fund.getTitle()+":"+fund.getEquityAmount()+"\n");

               });
               try {
                   System.out.println(str);
                   sendMessage.sendToTelegram(str.toString(), telegramToken);
               } catch (Exception e) {
                   e.printStackTrace();
               }
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

   // @Scheduled(cron="${fyer.generate.token}")
  //  @PostConstruct
    public String generateToken() throws IOException, InterruptedException, URISyntaxException {
        try {

            if (token == null) {
                try {
                    sendMessage.sendToTelegram("Token generation started",telegramToken);
                }catch (Exception e){
                    e.printStackTrace();
                }
                log.info("generating token at: " + LocalDateTime.now().toString());
                System.setProperty("webdriver.chrome.driver", driverPath);
                ChromeOptions ChromeOptions = new ChromeOptions();
                ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
                WebDriver webDriver = new ChromeDriver(ChromeOptions);
               /* AuthRequestDTO authRequest = new AuthRequestDTO();
                authRequest.setAppId(fyerAppId);
                authRequest.setSecretKey(fyersecretKey);
                String str = gson.toJson(authRequest);*//*
                Request request = createHttp(authURL);
                Response response = createOkHttpClient().newCall(request).execute();
                AuthResponseDTO authResponse = gson.fromJson(response.body().string(), AuthResponseDTO.class);
                generateTokenURL = generateTokenURL + authResponse.getAuthorizationCode() + "&appId=" + fyerAppId;*/
                webDriver.get(authURL);
                Thread.sleep(10000);

                webDriver.findElements(By.xpath("//*[@id=\"fy_client_id\"]")).get(0).sendKeys(fyerUsername);
                webDriver.findElements(By.xpath("//*[@id=\"clientIdSubmit\"]")).get(0).click();
                Thread.sleep(10000);
                webDriver.findElements(By.xpath("//*[@id=\"fy_client_pwd\"]")).get(0).sendKeys(fyerPassword);
                webDriver.findElements(By.xpath("//*[@id=\"loginSubmit\"]")).get(0).click();
                Thread.sleep(10000);
                webDriver.findElements(By.xpath("//*[@id=\"first\"]")).get(1).sendKeys("6");
                webDriver.findElements(By.xpath("//*[@id=\"second\"]")).get(1).sendKeys("2");
                webDriver.findElements(By.xpath("//*[@id=\"third\"]")).get(1).sendKeys("1");
                webDriver.findElements(By.xpath("//*[@id=\"fourth\"]")).get(1).sendKeys("5");
                webDriver.findElements(By.xpath("//*[@id=\"verifyPinSubmit\"]")).get(0).click();
                Thread.sleep(10000);
                List<NameValuePair> queryParams = new URIBuilder(webDriver.getCurrentUrl()).getQueryParams();
                String auth_code = queryParams.stream().filter(param -> param.getName().equals("auth_code")).map(NameValuePair::getValue).findFirst().orElse("");

                AuthTokenRequestDTO authTokenRequestDTO=new AuthTokenRequestDTO();
                authTokenRequestDTO.setCode(auth_code);
                authTokenRequestDTO.setGrantType("authorization_code");
                String sha256hex = DigestUtils.sha256Hex(fyerAppId+":"+fyersecretKey);
                authTokenRequestDTO.setAppIdHash(sha256hex);

                Request request = createPostPutDeleteRequest(HttpMethod.POST, generateTokenURL, new Gson().toJson(authTokenRequestDTO));
                String response = transactionService.callAPI(request);
                AuthTokenResponseDTO authTokenRequestDTO1=new Gson().fromJson(response,AuthTokenResponseDTO.class);
                System.out.println(response);
                token=fyerAppId+":"+authTokenRequestDTO1.getAccessToken();
                try {
                    sendMessage.sendToTelegram("Token: "+authTokenRequestDTO1.getAccessToken(),telegramToken);
                    Request requestFO = createPostPutDeleteRequest(HttpMethod.GET, "https://www1.nseindia.com/content/fo/fo_mktlots.csv", new Gson().toJson(authTokenRequestDTO));
                    String responseFO = transactionService.callAPI(requestFO);
                    CSVReader reader = new CSVReader(new StringReader(responseFO));
                    String[] line;
                    int i=0;
                    while ((line = reader.readNext()) != null) {
                        if (i>4) {
                            lsFOSymbols.put(line[1].trim(),line[1].trim());
                        }
                        i++;
                    }
            //        System.out.println(lsFOSymbols);

                    Request requestFyersCM = createPostPutDeleteRequest(HttpMethod.GET,"https://public.fyers.in/sym_details/NSE_CM.csv", new Gson().toJson(authTokenRequestDTO));
                    String responseFyersCM = transactionService.callAPI(requestFyersCM);
                    CSVReader readerFyersCM = new CSVReader(new StringReader(responseFyersCM));
                    String[] line1;
                    int i1=0;
                    List<StockEntity> stockEntityList =new ArrayList<>();
                    while ((line1 = readerFyersCM.readNext()) != null) {
                        if (lsFOSymbols.get(line1[13].trim()) !=null) {
                            lsFyerSymbols.put(line1[13].trim(),line1[9].trim());
                            StockEntity stockEntity=new StockEntity();
                            stockEntity.setSymbol(line1[13].trim());
                            stockEntity.setFyerSymbol(line1[9].trim());
                            stockEntityList.add(stockEntity);
                        }
                        i1++;
                    }
                    stockRepository.saveAll(stockEntityList);

                }catch (Exception e){
                    e.printStackTrace();
                }
                availableFund();
               /* SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date fromDate = dateTimeFormat.parse("2021-10-07 00:00:00");
                Date toDate = dateTimeFormat.parse("2021-10-07 16:00:00");
                List<Candlestick> list =getHistory("NSE:SBIN-EQ","5",String.valueOf(fromDate.getTime()/1000),String.valueOf(toDate.getTime()/1000));
                list.stream().forEach(candle->{

                    TimeZone istTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
                    Date d = new Date(candle.getTime().longValue()* 1000);
                    dateTimeFormat.setTimeZone(istTimeZone);
                    System.out.println(dateTimeFormat.format(d)+":"+candle.getClose()+":"+candle.getVolume());
                });*/
               // System.out.println(new Gson().toJson(fyerHistoryResponseDTO));

            }
        }catch (Exception e){
            e.printStackTrace();
            try {
                sendMessage.sendToTelegram("Token generation failed",telegramToken);
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }
        return token;
    }

    public void loadHistory(int day,String symbol,String fyerSymbol){
        int i=day;
        int j=day;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        while (i<0){
            j=j+100;
            if(j>0){
                j=0;
            }
            Calendar fromcalendar = Calendar.getInstance();
            fromcalendar.add(DAY_OF_MONTH, i+1);
            Calendar tocalendar = Calendar.getInstance();
            tocalendar.add(DAY_OF_MONTH, j);
            Date fromdate = fromcalendar.getTime();
            String fromDateStr=dateFormat.format(fromdate);
            String toDateStr=dateFormat.format(tocalendar.getTime());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            System.out.println(symbol);
                   try {
                       List<Candlestick> candlestickList = getHistory(fyerSymbol, "5", fromDateStr, toDateStr, "1");
                       if (candlestickList != null && candlestickList.size() > 0) {
                           List<StockDataEntity> stockDataEntities = new ArrayList<>();
                           candlestickList.stream().forEach(candlestick -> {
                               StockDataEntity stockDataEntity = new StockDataEntity();
                               String dataKey = UUID.randomUUID().toString();
                               stockDataEntity.setDataKey(dataKey);
                               Date date = new Date(candlestick.time.longValue() * 1000);
                               try {
                                   stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                               } catch (ParseException e) {
                                   e.printStackTrace();
                               }
                               stockDataEntity.setSymbol(symbol);
                               stockDataEntity.setOpen(candlestick.open.doubleValue());
                               stockDataEntity.setHigh(candlestick.high.doubleValue());
                               stockDataEntity.setLow(candlestick.low.doubleValue());
                               stockDataEntity.setClose(candlestick.close.doubleValue());
                               stockDataEntity.setVolume(candlestick.volume.intValue());
                               stockDataEntities.add(stockDataEntity);

                           });
                           stockDataRepository.saveAll(stockDataEntities);
                       }
                       } catch(URISyntaxException e){
                           e.printStackTrace();
                       } catch(Exception e){
                           e.printStackTrace();
                       }
            i=i+100;
               }




    }

    @Autowired
    StockDayDataRepository stockDayDataRepository;
    public void loadDayHistory(int day,String symbol,String fyerSymbol){
        int i=day;
        int j=day;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
        String lastDate = stockDayDataRepository.findLastDate(symbol);
        if (lastDate == null) {
            while (i < 0) {
                j = j + 300;
                if (j > 0) {
                    j = 0;
                }
                Calendar fromcalendar = Calendar.getInstance();
                fromcalendar.add(DAY_OF_MONTH, i + 1);
                Calendar tocalendar = Calendar.getInstance();
                tocalendar.add(DAY_OF_MONTH, j);
                Date fromdate = fromcalendar.getTime();
                String fromDateStr = dateFormat.format(fromdate);
                String toDateStr = dateFormat.format(tocalendar.getTime());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(symbol);
                try {
                    List<Candlestick> candlestickList = getHistory(fyerSymbol, "D", fromDateStr, toDateStr, "1");
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<StockDayDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            StockDayDataEntity stockDataEntity = new StockDayDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.time.longValue() * 1000);
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setSymbol(symbol);
                            stockDataEntity.setOpen(candlestick.open.doubleValue());
                            stockDataEntity.setHigh(candlestick.high.doubleValue());
                            stockDataEntity.setLow(candlestick.low.doubleValue());
                            stockDataEntity.setClose(candlestick.close.doubleValue());
                            stockDataEntity.setVolume(candlestick.volume.intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        System.out.println(new Gson().toJson(stockDataEntities));
                        stockDayDataRepository.saveAll(stockDataEntities);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                i = i + 300;
            }

        }
        else
        {
            try {
                Date fromDateStr= dateFormat1.parse(lastDate);
                Calendar calendar=Calendar.getInstance();
                String curDate=dateFormat.format(calendar.getTime());
                String fromDate=dateFormat.format(calendar.getTime());
                //if(curDate!=fromDate) {
                    calendar.setTime(fromDateStr);
                    calendar.add(DAY_OF_MONTH, 1);
                    Calendar toCalendar = Calendar.getInstance();
                    List<Candlestick> candlestickList = getHistory(fyerSymbol, "D", dateFormat.format(calendar.getTime()), dateFormat.format(toCalendar.getTime()), "1");
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<StockDayDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            StockDayDataEntity stockDataEntity = new StockDayDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.time.longValue() * 1000);
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setSymbol(symbol);
                            stockDataEntity.setOpen(candlestick.open.doubleValue());
                            stockDataEntity.setHigh(candlestick.high.doubleValue());
                            stockDataEntity.setLow(candlestick.low.doubleValue());
                            stockDataEntity.setClose(candlestick.close.doubleValue());
                            stockDataEntity.setVolume(candlestick.volume.intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        stockDayDataRepository.saveAll(stockDataEntities);
                    }
                //}
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



    }

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

  //  @Scheduled(cron="${fyer.load.day.data}")
    public void loadDayData(){
        List<StockEntity> stockEntityList=stockRepository.findAll();
        try {
            loadIndicesDayHistory(1400, "BANKNIFTY", "NSE:NIFTYBANK-INDEX");
        }catch (Exception e){
           e.printStackTrace();
        }
        stockEntityList.forEach(stockEntity -> {
            try {
                loadDayHistory(1400, stockEntity.getSymbol(), stockEntity.getFyerSymbol());
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }
   // @Scheduled(cron="${fyer.load.long.timeframe.data}")
    public void calculateBigTimeFrame(){
        List<StockEntity> stockEntityList=stockRepository.findAll();
/*        loadIndexWMonthlyHistory("BANKNIFTY");
        loadIndexWeekHistory("BANKNIFTY");
        loadIndexYearlyHistory("BANKNIFTY");*/
        stockEntityList.forEach(stockEntity -> {
                    try {
                        loadStockWeekHistory(stockEntity.getSymbol());
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
            loadStockMonthHistory(stockEntity.getSymbol());
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                        try {
                            loadStockYearHistory(stockEntity.getSymbol());}
                   catch (Exception e) {
                                e.printStackTrace();
                            }

        });
    }
    List<String> aboveR1Live=new ArrayList<>();
    List<String> aboveR2Live=new ArrayList<>();
    @Value("${filepath.trend}")
    String trendPath;
    Map<String,Double> last20DayMax=new HashMap<>();
    Map<String,Double> last365Max=new HashMap<>();
   // @Scheduled(cron="${fyer.schedule.pivot.alert}")
    public void findPivots() {
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
        List<StockEntity> stockEntityList=stockRepository.findAll();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar currentDate=Calendar.getInstance();
        String fromDate=dateFormat.format(currentDate.getTime());
        System.out.println("run time:"+dateFormat1.format(currentDate.getTime()));
        stockEntityList.forEach(stockEntity -> {
            List<Candlestick> candlestickList = null;
            try {
                candlestickList = getHistory(stockEntity.getFyerSymbol(), "5", fromDate, fromDate, "1");
                System.out.println(stockEntity.getSymbol());
                if(candlestickList!=null && candlestickList.size()>0) {

                    Candlestick openCandle = candlestickList.get(0);
                    Candlestick lastCandle = candlestickList.get(candlestickList.size() - 1);
                    StandardPivots standardPivots = stockPivots.get(stockEntity.getSymbol());

                    StandardPivot weekPivot = standardPivots.getWeekPivots();
                    StandardPivot monthPivot = standardPivots.getMonthPivots();
                    System.out.print(":"+lastCandle.close+":"+weekPivot.getR1());
                    double perMove = MathUtils.percentageMove(openCandle.getOpen().doubleValue(), lastCandle.close.doubleValue());
                    if (perMove > 0 && lastCandle.close.doubleValue() > weekPivot.getR1() && !aboveR1Live.contains(stockEntity.getSymbol())) {
                                        aboveR1Live.add(stockEntity.getSymbol());
                            String message="Stock Above WR1: " + stockEntity.getSymbol() + " WR1: " + weekPivot.getR1() + " Current Close: " + lastCandle.close;
                            if (lastCandle.close.doubleValue() > monthPivot.getR1()) {
                                message= message+"\n"+ "Stock Above MR1 ";
                            }
                            if(openCandle.close.doubleValue() > weekPivot.getR1()){
                                message= message+"\n"+ "Open Above WR1 ";
                            }
                            if(last20DayMax.size()>0 && last365Max.size()>0) {

                                double last20DayMaxValue = last20DayMax.get(stockEntity.getSymbol());
                                double last365MaxValue = last365Max.get(stockEntity.getSymbol());
                                if(last20DayMaxValue>0 && last365MaxValue>0) {
                                    if (lastCandle.close.doubleValue() > last20DayMaxValue) {
                                        message = message + "\n" + "Above 52 week ";
                                    }
                                    if (openCandle.close.doubleValue() > last365MaxValue) {
                                        message = message + "\n" + "Above 20 Day";
                                    }
                                }
                            }
                                try {
                                    if(lastCandle.close.doubleValue() > monthPivot.getR1() && openCandle.close.doubleValue() > weekPivot.getR1()) {
                                        sendMessage.sendToTelegram(message, telegramToken, "-713214125");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }


                        }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
  //  @Scheduled(cron = "${zerodha.find.max.data}")
    public void findMax() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        List<StockEntity> stockEntityList=stockRepository.findAll();
        Calendar oneYearDate = Calendar.getInstance();
        oneYearDate.add(Calendar.DATE, -365);
        Calendar last20Days = Calendar.getInstance();
        last20Days.add(Calendar.DATE, -20);
        stockEntityList.forEach(symbolMap -> {
            try {

                String yearMax = stockDayDataRepository.findHigh(symbolMap.getSymbol(), dateFormat.format(oneYearDate.getTime()));
                String last20Max = stockDayDataRepository.findHigh(symbolMap.getSymbol(), dateFormat.format(last20Days.getTime()));
                last20DayMax.put(symbolMap.getSymbol(), Double.parseDouble(last20Max));
                last365Max.put(symbolMap.getSymbol(), Double.parseDouble(yearMax));
            }catch (Exception e){
                e.printStackTrace();
            }
        });
    }
 //   @Scheduled(cron="${fyer.bnf.schedule.pivot.alert}")
    public void BNFPivotsFactCheck(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar currentDate=Calendar.getInstance();
        String fromDate=dateFormat.format(currentDate.getTime());

            List<Candlestick> candlestickList = null;
            try {/*
                candlestickList = getHistory("NSE:NIFTYBANK-INDEX", "5", fromDate, fromDate, "1");
                Candlestick openCandle = candlestickList.get(0);
                Candlestick lastCandle = candlestickList.get(0);*/
                StandardPivots standardPivots=indexPivots.get("BANKNIFTY");
                StandardPivot weekPivot = standardPivots.getWeekPivots();
                StandardPivot dailyPivots = standardPivots.getDayPivots();
                StandardPivot monthPivots = standardPivots.getMonthPivots();
                IndexDayDataEntity indexDayDataEntity=indexDayDataRepository.findLastRecord("BANKNIFTY");
            //    double perMove = MathUtils.percentageMove(openCandle.getOpen().doubleValue(), indexDayDataEntity.getClose());
                try {
                        sendMessage.sendToTelegram("Bank NiftyStock Week Pivots: "+new Gson().toJson(weekPivot),telegramToken);
                        sendMessage.sendToTelegram("Bank NiftyStock Daily Pivots: "+new Gson().toJson(dailyPivots),telegramToken);
                        sendMessage.sendToTelegram("Bank NiftyStock Last Close: "+indexDayDataEntity.getClose(),telegramToken);
                    }catch (Exception e){
                        e.printStackTrace();
                    }


            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    public void testPivots(String fromDate,String toDate) throws IOException {
        List<StockEntity> stockEntityList=stockRepository.findAll();
        List<String> aboveR1=new ArrayList<>();
        List<String> aboveR2=new ArrayList<>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");

        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/pp_"+fromDate+".csv"));
        stockEntityList.forEach(stockEntity -> {
            try {
                List<Candlestick> candlestickList = getHistory(stockEntity.getFyerSymbol(), "5", fromDate, toDate, "1");
                Candlestick openCandle = candlestickList.get(0);
                TradeData tradeData=new TradeData();
                AtomicInteger i=new AtomicInteger();
                i.getAndSet(0);
                AtomicInteger ii=new AtomicInteger();
                candlestickList.stream().forEach(candlestick -> {

                    double perMove=MathUtils.percentageMove(openCandle.getOpen().doubleValue(),candlestick.close.doubleValue());


                        Date date = new Date(candlestick.time.longValue() * 1000);
                        StandardPivots standardPivots=stockPivots.get(stockEntity.getSymbol());
                        StandardPivot weekPivot = standardPivots.getWeekPivots();
                        if(  perMove>0 && candlestick.close.doubleValue()>weekPivot.getR1() && candlestick.close.doubleValue()<weekPivot.getR2() && !aboveR1.contains(stockEntity.getSymbol())){

                            if(perMove>=2 && ii.get()==0) {
                                tradeData.setStockName(stockEntity.getSymbol());
                                tradeData.setBuyPrice(candlestick.close);
                                tradeData.setBuyTime(sdf1.format(date));
                                tradeData.isOrderPlaced = true;
                                int qty = 100000 / candlestick.close.intValue();
                                double targetPoints = (tradeData.getBuyPrice().doubleValue() * 1) / 100;
                                tradeData.setSlTrialPoints(new BigDecimal(targetPoints));
                                tradeData.setSlPrice(tradeData.getBuyPrice().subtract(new BigDecimal(targetPoints)));
                                tradeData.setTargetPrice(tradeData.getBuyPrice().add(new BigDecimal(targetPoints)));
                                tradeData.setQty(qty);
                                aboveR1.add(stockEntity.getSymbol());
                                System.out.println("Stock:" + stockEntity.getSymbol() + ":" + perMove + ": above WR1" + ":" + sdf1.format(date) + ":" + candlestick.close + "sl :" + tradeData.getSlPrice().doubleValue() + "Target:" + tradeData.getTargetPrice());
                            }
                            ii.getAndAdd(1);
                        }
                    try {
                        if(tradeData.isOrderPlaced && i.get()>0 &&  date.after(sdf1.parse(tradeData.getBuyTime()))) {
                            if (aboveR1.contains(stockEntity.getSymbol()) && tradeData.isOrderPlaced && candlestick.high.doubleValue() > weekPivot.getR2() && !aboveR2.contains(stockEntity.getSymbol()) && !tradeData.isExited) {
                                System.out.println("Stock:" + stockEntity.getSymbol() + ":" + perMove + ": above WR2" + ":" + sdf1.format(date) + ":" + candlestick.close);
                                aboveR2.add(stockEntity.getSymbol());
                                tradeData.isExited = true;
                                tradeData.setSellPrice(candlestick.close);
                                tradeData.setSellTime(sdf1.format(date));
                                double profitLoss = (tradeData.getSellPrice().doubleValue() - tradeData.getBuyPrice().doubleValue()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (aboveR1.contains(stockEntity.getSymbol()) && tradeData.isOrderPlaced && tradeData.getSlPrice().doubleValue() > candlestick.low.doubleValue() && !tradeData.isExited) {
                                System.out.println("Stock:" + stockEntity.getSymbol() + ":" + perMove + ": SL Hit" + ":" + sdf1.format(date) + ":" + candlestick.close);
                                aboveR2.add(stockEntity.getSymbol());
                                tradeData.isExited = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                tradeData.setSellTime(sdf1.format(date));
                                double profitLoss = (tradeData.getSellPrice().doubleValue() - tradeData.getBuyPrice().doubleValue()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (aboveR1.contains(stockEntity.getSymbol()) && tradeData.isOrderPlaced && tradeData.getTargetPrice().doubleValue() < candlestick.high.doubleValue() && !tradeData.isExited) {
                                System.out.println("Stock:" + stockEntity.getSymbol() + ":" + perMove + ": Target Hit" + ":" + sdf1.format(date) + ":" + candlestick.close);
                                aboveR2.add(stockEntity.getSymbol());
                                tradeData.isExited = true;
                                tradeData.setSellTime(sdf1.format(date));
                                tradeData.setSellPrice(candlestick.close);
                                double profitLoss = (tradeData.getSellPrice().doubleValue() - tradeData.getBuyPrice().doubleValue()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    i.getAndAdd(1);
                        /*
                        if(candlestick.close.doubleValue()>weekPivot.getR2() && !aboveR2.contains(stockEntity.getSymbol()) && aboveR1.contains(stockEntity.getSymbol()) && !tradeData.isExited){
                            System.out.println("Stock:"+stockEntity.getSymbol()+":"+perMove+": above WR2"+":"+sdf1.format(date)+":"+candlestick.close);
                            aboveR2.add(stockEntity.getSymbol());
                            tradeData.isExited=true;
                            tradeData.setSellPrice(candlestick.close);
                            tradeData.setSellTime(sdf1.format(date));
                            double profitLoss = (tradeData.getSellPrice().doubleValue() - tradeData.getBuyPrice().doubleValue()) * tradeData.getQty();
                            String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                            csvWriter.writeNext(data);
                            try {
                                csvWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }*/

                });
                if(aboveR1.contains(stockEntity.getSymbol()) && !tradeData.isExited && tradeData.isOrderPlaced){
                    Candlestick close = candlestickList.get(candlestickList.size()-1);
                    tradeData.setSellPrice(close.close);
                    double profitLoss = (tradeData.getSellPrice().doubleValue() - tradeData.getBuyPrice().doubleValue()) * tradeData.getQty();
                    String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                    csvWriter.writeNext(data);
                    try {
                        csvWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }catch (Exception e){
                System.out.println(stockEntity.getSymbol()+":"+e.getMessage());
            }

        });
    }

    public void testPivotsHistory(int n) throws IOException {
        List<StockEntity> stockEntityList = stockRepository.findAll();
        List<String> aboveR1 = new ArrayList<>();
        List<String> aboveR2 = new ArrayList<>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
        Date date = new Date();
        SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMddHHmm");
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/pp_" + fileFormat.format(date) + ".csv"));
        while (n >= 0) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
            Map<String,StandardPivots> stockPivotsHistory=new HashMap<>();
            populatePivotsHistory(today,stockPivotsHistory);
            stockEntityList.forEach(stockEntity -> {
                try {

                    StockDayDataEntity stockDayDataEntity = stockDayDataRepository.findRecord(stockEntity.getSymbol(), today + " 05:30:00.000");
                    if(stockDayDataEntity!=null) {
                        StandardPivots standardPivots = stockPivotsHistory.get(stockEntity.getSymbol());
                        StandardPivot weekPivot = standardPivots.getWeekPivots();
                        StandardPivot monthlyPivot = standardPivots.getMonthPivots();

                        if (weekPivot.getR1() < stockDayDataEntity.getHigh() && weekPivot.getR1() > stockDayDataEntity.getOpen() && monthlyPivot.getR1() < stockDayDataEntity.getHigh()) {
                            System.out.println(stockEntity.getSymbol() + ":" + today+": week pivot"+weekPivot.getR1()+": month pivot"+monthlyPivot.getR1()+": high "+stockDayDataEntity.getHigh());
                            int qty=10000/(int)(weekPivot.getR1());
                            double entry=0;
                            if(weekPivot.getR1()>monthlyPivot.getR1())
                            {
                                entry=weekPivot.getR1();
                            }else
                            {
                                entry=monthlyPivot.getR1();
                            }
                            double pl= (stockDayDataEntity.getClose()-entry)*qty;
                            double exitPrice=stockDayDataEntity.getHigh()>weekPivot.getR2()?weekPivot.getR2():stockDayDataEntity.getClose();
                            double pl1= (stockDayDataEntity.getHigh()-entry)*qty;
                            double pl2= (exitPrice-entry)*qty;
                            String[] data = {stockEntity.getSymbol(), today.toString(),String.valueOf(weekPivot.getR1()),String.valueOf(monthlyPivot.getR1()),String.valueOf(stockDayDataEntity.getHigh()),String.valueOf(pl),String.valueOf(pl1),String.valueOf(pl2)};
                            csvWriter.writeNext(data);
                            try {
                                csvWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }

            });
            n--;
        }
    }
    Map<String,StandardPivots> indexPivots=new HashMap<>();
    Map<String,StandardPivots> stockPivots=new HashMap<>();

    //@Scheduled(cron="${fyer.pivots.data}")
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
        String monthDateStr = dateFormat.format(monthCalendar.getTime());
        Calendar currentDay = Calendar.getInstance();
        currentDay.add(Calendar.DATE, -1);
        String dayPivotStartDay = dateFormat.format(currentDay.getTime());
        try {

        IndexWeekDataEntity indexWeekDataEntity=indexWeekDataRepository.findSymbolWithDate("BANKNIFTY",weekPivotStartDay);
        IndexMonthlyDataEntity indexMonthlyDataEntity=indexMonthDataRepository.findSymbolWithDate("BANKNIFTY",monthDateStr);
        IndexYearDataEntity indexYearly=indexYearDataRepository.findSymbolWithDate("BANKNIFTY",yearPivotStartDay);
        IndexDayDataEntity indexDayDataEntity=indexDayDataRepository.findLastRecord("BANKNIFTY");
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
}catch (Exception e){
    e.printStackTrace();
}
        //System.out.println(indexPivots);
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

    public  Map<String,StandardPivots> populatePivotsHistory(LocalDate today,Map<String,StandardPivots> stockPivotsH){

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar fromCalendar = Calendar.getInstance();
        Date dateHist = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        fromCalendar.setTime(dateHist);
        fromCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        fromCalendar.add(Calendar.DATE, -7);
        Calendar toCalender = Calendar.getInstance();
        toCalender.setTime(dateHist);
        toCalender.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        toCalender.add(Calendar.DATE, -1);
        String weekPivotStartDay=dateFormat.format(fromCalendar.getTime());
       // System.out.println(today+":"+dateFormat.format(fromCalendar.getTime())+":"+dateFormat.format(toCalender.getTime()));
        Calendar fromMonthCalendar = Calendar.getInstance();
        fromMonthCalendar.setTime(dateHist);
        fromMonthCalendar.add(Calendar.MONTH, -1);
        fromMonthCalendar.set(Calendar.DATE, 1);
        String fromDateStr = dateFormat.format(fromMonthCalendar.getTime());
        fromMonthCalendar.set(Calendar.DATE,     fromMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        fromMonthCalendar.add(Calendar.HOUR,6);
        String toDateStr = dateFormat.format(fromMonthCalendar.getTime());
        String dayPivotStartDay = dateFormat.format(dateHist);

        //System.out.println(indexPivots);
        List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {
            try {
                StockWeekDataEntity stockWeekDataEntity = stockWeekDataRepository.findSymbolWithDate(stockEntity.getSymbol(), weekPivotStartDay);
                StockMonthDataEntity stockMonthDataEntity = stockMonthDataRepository.findSymbolWithDate(stockEntity.getSymbol(), fromDateStr);
               // StockDayDataEntity stockDayDataEntity = stockDayDataRepository.findLastRecord(stockEntity.getSymbol());
            //    StandardPivot stockDayPivots = calculatePRS(stockDayDataEntity.getHigh(), stockDayDataEntity.getLow(), stockDayDataEntity.getClose(), PivotTimeFrame.DAY.name());
                StandardPivot stockWeekPivots = calculatePRS(stockWeekDataEntity.getHigh(), stockWeekDataEntity.getLow(), stockWeekDataEntity.getClose(), PivotTimeFrame.WEEK.name());
                StandardPivot stockMonthPivots = calculatePRS(stockMonthDataEntity.getHigh(), stockMonthDataEntity.getLow(), stockMonthDataEntity.getClose(), PivotTimeFrame.MONTH.name());
                StandardPivots stockStandardPivots = new StandardPivots();
             //   stockStandardPivots.setDayPivots(stockDayPivots);
                stockStandardPivots.setWeekPivots(stockWeekPivots);
                stockStandardPivots.setMonthPivots(stockMonthPivots);
                //   System.out.println(stockStandardPivots);
                stockPivotsH.put(stockEntity.getSymbol(), stockStandardPivots);
            }catch (Exception e){
                //e.printStackTrace();
            }
        });
      //  System.out.println(new Gson().toJson(indexPivots));
        return stockPivotsH;
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
    public void loadStockWeekHistory(String symbol){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");

            Calendar fromCalendar = Calendar.getInstance();
            fromCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            fromCalendar.add(Calendar.DATE, -7);
            Calendar toCalender = Calendar.getInstance();
            toCalender.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            toCalender.add(Calendar.DATE, -1);
            String fromDateStr = dateFormat.format(fromCalendar.getTime());
            String toDateStr = dateFormat.format(toCalender.getTime());
            System.out.println(symbol + ":" + fromDateStr + ":" + dateFormat.format(toCalender.getTime()));
            StockWeekDataEntity stockWeekDataEntity = stockWeekDataRepository.findSymbolWithDate(symbol,fromDateStr);
            if(stockWeekDataEntity==null) {
                StockWeekDataEntity stockDataEntity = new StockWeekDataEntity();
                List<StockDayDataEntity> stockWeekDataEntities = stockDayDataRepository.findSymbol(symbol, fromDateStr, toDateStr);
                Candlestick candlestick = findHighLowOfPeriod(stockWeekDataEntities);
                String dataKey = UUID.randomUUID().toString();
                stockDataEntity.setDataKey(dataKey);
                stockDataEntity.setSymbol(symbol);
                stockDataEntity.setOpen(candlestick.open.doubleValue());
                stockDataEntity.setHigh(candlestick.high.doubleValue());
                stockDataEntity.setLow(candlestick.low.doubleValue());
                stockDataEntity.setClose(candlestick.close.doubleValue());
                stockDataEntity.setTradeTime(dateFormat.parse(fromDateStr));
                stockDataEntity.setVolume(candlestick.volume.intValue());
                stockWeekDataRepository.save(stockDataEntity);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }


    public void loadIndexWeekHistory(String symbol){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");

            Calendar fromCalendar = Calendar.getInstance();
            fromCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            fromCalendar.add(Calendar.DATE, -7);
            Calendar toCalender = Calendar.getInstance();
            toCalender.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            toCalender.add(Calendar.DATE, -1);
            String fromDateStr = dateFormat.format(fromCalendar.getTime());
            String toDateStr = dateFormat.format(toCalender.getTime());
            System.out.println(symbol + ":" + fromDateStr + ":" + dateFormat.format(toCalender.getTime()));
            IndexWeekDataEntity indexWeekDataEntity = indexWeekDataRepository.findSymbolWithDate(symbol,fromDateStr);
            if(indexWeekDataEntity==null) {
                IndexWeekDataEntity stockDataEntity = new IndexWeekDataEntity();
                List<IndexDayDataEntity> stockWeekDataEntities = indexDayDataRepository.findSymbol(symbol, fromDateStr, toDateStr);
                Candlestick candlestick = findIndexHighLowOfPeriod(stockWeekDataEntities);
                String dataKey = UUID.randomUUID().toString();
                stockDataEntity.setDataKey(dataKey);
                stockDataEntity.setIndexKey(stockWeekDataEntities.get(0).getIndexKey());
                stockDataEntity.setOpen(candlestick.open.doubleValue());
                stockDataEntity.setHigh(candlestick.high.doubleValue());
                stockDataEntity.setLow(candlestick.low.doubleValue());
                stockDataEntity.setClose(candlestick.close.doubleValue());
                stockDataEntity.setTradeTime(dateFormat.parse(fromDateStr));
                indexWeekDataRepository.save(stockDataEntity);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void loadIndexWMonthlyHistory(String symbol){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");

            Calendar fromCalendar = Calendar.getInstance();
            fromCalendar.add(Calendar.MONTH, -1);
            fromCalendar.set(Calendar.DATE, 1);
            String fromDateStr = dateFormat.format(fromCalendar.getTime());
            fromCalendar.set(Calendar.DATE,     fromCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            String toDateStr = dateFormat.format(fromCalendar.getTime());
            System.out.println(symbol + ":" + fromDateStr + ":" + toDateStr);
            IndexMonthlyDataEntity indexWeekDataEntity = indexMonthDataRepository.findSymbolWithDate(symbol,fromDateStr);
            if(indexWeekDataEntity==null) {
                IndexMonthlyDataEntity stockDataEntity = new IndexMonthlyDataEntity();
                List<IndexDayDataEntity> stockWeekDataEntities = indexDayDataRepository.findSymbol(symbol, fromDateStr, toDateStr);
                Candlestick candlestick = findIndexHighLowOfPeriod(stockWeekDataEntities);
                String dataKey = UUID.randomUUID().toString();
                stockDataEntity.setDataKey(dataKey);
                stockDataEntity.setIndexKey(stockWeekDataEntities.get(0).getIndexKey());
                stockDataEntity.setOpen(candlestick.open.doubleValue());
                stockDataEntity.setHigh(candlestick.high.doubleValue());
                stockDataEntity.setLow(candlestick.low.doubleValue());
                stockDataEntity.setClose(candlestick.close.doubleValue());
                stockDataEntity.setTradeTime(dateFormat.parse(fromDateStr));
                indexMonthDataRepository.save(stockDataEntity);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void loadStockMonthHistory(String symbol){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat dateFormatTime = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Calendar fromCalendar = Calendar.getInstance();
            fromCalendar.add(Calendar.MONTH, -1);
            fromCalendar.set(Calendar.DATE, 1);
            String fromDateStr = dateFormat.format(fromCalendar.getTime());
            fromCalendar.set(Calendar.DATE,     fromCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            fromCalendar.add(Calendar.HOUR,6);
            String toDateStr = dateFormatTime.format(fromCalendar.getTime());
            System.out.println(symbol + ":" + fromDateStr + ":" + toDateStr);
            StockMonthDataEntity stockMonthDataEntity = stockMonthDataRepository.findSymbolWithDate(symbol,fromDateStr);
            if(stockMonthDataEntity==null) {
                StockMonthDataEntity stockDataEntity = new StockMonthDataEntity();
                List<StockDayDataEntity> stockWeekDataEntities = stockDayDataRepository.findSymbol(symbol, fromDateStr, toDateStr);
                Candlestick candlestick = findHighLowOfPeriod(stockWeekDataEntities);
                String dataKey = UUID.randomUUID().toString();
                stockDataEntity.setDataKey(dataKey);
                stockDataEntity.setSymbol(symbol);
                stockDataEntity.setOpen(candlestick.open.doubleValue());
                stockDataEntity.setHigh(candlestick.high.doubleValue());
                stockDataEntity.setLow(candlestick.low.doubleValue());
                stockDataEntity.setClose(candlestick.close.doubleValue());
                stockDataEntity.setTradeTime(dateFormat.parse(fromDateStr));
                stockDataEntity.setVolume(candlestick.volume.intValue());
                stockMonthDataRepository.save(stockDataEntity);

            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void loadIndexYearlyHistory(String symbol){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");

            Calendar cal=getPreviousYear();
            String fromDateStr= dateFormat.format(cal.getTime());
            cal.set(Calendar.MONTH, 11);
            cal.set(Calendar.DAY_OF_MONTH, 31);
            cal.add(Calendar.HOUR,6);
            String toDateStr = dateFormat.format(cal.getTime());
            System.out.println(symbol + ":" + fromDateStr + ":" + toDateStr);
            IndexYearDataEntity indexWeekDataEntity = indexYearDataRepository.findSymbolWithDate(symbol,fromDateStr);
            if(indexWeekDataEntity==null) {
                IndexYearDataEntity stockDataEntity = new IndexYearDataEntity();
                List<IndexDayDataEntity> stockWeekDataEntities = indexDayDataRepository.findSymbol(symbol, fromDateStr, toDateStr);
                Candlestick candlestick = findIndexHighLowOfPeriod(stockWeekDataEntities);
                String dataKey = UUID.randomUUID().toString();
                stockDataEntity.setDataKey(dataKey);
                stockDataEntity.setIndexKey(stockWeekDataEntities.get(0).getIndexKey());
                stockDataEntity.setOpen(candlestick.open.doubleValue());
                stockDataEntity.setHigh(candlestick.high.doubleValue());
                stockDataEntity.setLow(candlestick.low.doubleValue());
                stockDataEntity.setClose(candlestick.close.doubleValue());
                stockDataEntity.setTradeTime(dateFormat.parse(fromDateStr));
                indexYearDataRepository.save(stockDataEntity);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    public Calendar getPreviousYear(){
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        return cal;



    }
    public void loadStockYearHistory(String symbol){
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            Calendar cal=getPreviousYear();
            String fromDateStr= dateFormat.format(cal.getTime());
            cal.set(Calendar.MONTH, 11);
            cal.set(Calendar.DAY_OF_MONTH, 31);
            cal.add(Calendar.HOUR,6);
            String toDateStr = dateFormat.format(cal.getTime());
            System.out.println(symbol + ":" + fromDateStr + ":" + toDateStr);
            StockYearDataEntity stockYearDataEntity = stockYearDataRepository.findSymbolWithDate(symbol,fromDateStr);
            if(stockYearDataEntity==null) {
                StockYearDataEntity stockDataEntity = new StockYearDataEntity();

                List<StockDayDataEntity> stockWeekDataEntities = stockDayDataRepository.findSymbol(symbol, fromDateStr, toDateStr);
                Candlestick candlestick = findHighLowOfPeriod(stockWeekDataEntities);
                String dataKey = UUID.randomUUID().toString();
                stockDataEntity.setDataKey(dataKey);
                stockDataEntity.setSymbol(symbol);
                stockDataEntity.setOpen(candlestick.open.doubleValue());
                stockDataEntity.setHigh(candlestick.high.doubleValue());
                stockDataEntity.setLow(candlestick.low.doubleValue());
                stockDataEntity.setClose(candlestick.close.doubleValue());
                stockDataEntity.setTradeTime(dateFormat.parse(fromDateStr));
                stockDataEntity.setVolume(candlestick.volume.intValue());
                stockYearDataRepository.save(stockDataEntity);
            }

        }catch (Exception e){
            e.printStackTrace();
        }

    }
    public Candlestick findHighLowOfPeriod(List<StockDayDataEntity> stockWeekDataEntities){
        AtomicDouble low=new AtomicDouble();
        AtomicDouble high=new AtomicDouble();
        AtomicDouble close=new AtomicDouble();
        AtomicDouble open=new AtomicDouble();
        AtomicInteger volume=new AtomicInteger();
        StockDayDataEntity openStock=stockWeekDataEntities.get(0);
        StockDayDataEntity closeStock=stockWeekDataEntities.get(stockWeekDataEntities.size()-1);
        open.getAndSet(openStock.getOpen());
        low.getAndSet(openStock.getLow());
        high.getAndSet(openStock.getHigh());
        close.getAndSet(closeStock.getClose());
        stockWeekDataEntities.stream().forEach(stockDayDataEntity ->
        {
            volume.getAndAdd(stockDayDataEntity.getVolume());
            if(stockDayDataEntity.getHigh()>high.get()){
                high.getAndSet(stockDayDataEntity.getHigh());
            }
            if(stockDayDataEntity.getLow()<low.get()){
                low.getAndSet(stockDayDataEntity.getLow());
            }
        });
        Candlestick candlestick=new Candlestick();
        candlestick.setHigh(new BigDecimal(high.get()));
        candlestick.setLow(new BigDecimal(low.get()));
        candlestick.setClose(new BigDecimal(close.get()));
        candlestick.setOpen(new BigDecimal(open.get()));
        candlestick.setVolume(new BigDecimal(volume.get()));

        return candlestick;

    }

    public Candlestick findIndexHighLowOfPeriod(List<IndexDayDataEntity> stockWeekDataEntities){
        AtomicDouble low=new AtomicDouble();
        AtomicDouble high=new AtomicDouble();
        AtomicDouble close=new AtomicDouble();
        AtomicDouble open=new AtomicDouble();
        AtomicInteger volume=new AtomicInteger();
        IndexDayDataEntity openStock=stockWeekDataEntities.get(0);
        IndexDayDataEntity closeStock=stockWeekDataEntities.get(stockWeekDataEntities.size()-1);
        open.getAndSet(openStock.getOpen());
        low.getAndSet(openStock.getLow());
        high.getAndSet(openStock.getHigh());
        close.getAndSet(closeStock.getClose());
        stockWeekDataEntities.stream().forEach(stockDayDataEntity ->
        {

            if(stockDayDataEntity.getHigh()>high.get()){
                high.getAndSet(stockDayDataEntity.getHigh());
            }
            if(stockDayDataEntity.getLow()<low.get()){
                low.getAndSet(stockDayDataEntity.getLow());
            }
        });
        Candlestick candlestick=new Candlestick();
        candlestick.setHigh(new BigDecimal(high.get()));
        candlestick.setLow(new BigDecimal(low.get()));
        candlestick.setClose(new BigDecimal(close.get()));
        candlestick.setOpen(new BigDecimal(open.get()));


        return candlestick;

    }

    public void testPivotsIndices(int n) throws IOException {

        List<String> aboveR1 = new ArrayList<>();
        List<String> aboveR2 = new ArrayList<>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
        Date date = new Date();
        SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMddHHmm");
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/pp_" + fileFormat.format(date) + ".csv"));
        while (n >= 0) {

            LocalDate today = LocalDate.now(ZoneId.systemDefault()).minusDays(n);
          //  System.out.println(n+":"+today);
            Map<String,StandardPivots> stockPivotsHistory=new HashMap<>();
                try {

                    IndexDayDataEntity stockDayDataEntity = indexDayDataRepository.findHistoricLastRecord("BANKNIFTY", String.valueOf(today));

                    if(stockDayDataEntity !=null) {
                        StandardPivot stockDayPivots = calculatePRS(stockDayDataEntity.getHigh(), stockDayDataEntity.getLow(), stockDayDataEntity.getClose(), PivotTimeFrame.DAY.name());
                        List<IndexDataEntity> indexDataEntityList = indexDataRepository.findSymbol("BANKNIFTY", today + " 00:00:00", today + " 15:00:00");

                        TradeData tradeData=new TradeData();
                        if (indexDataEntityList .size()>0) {
                            IndexDataEntity indexDataEntityOpen=indexDataEntityList.get(0);
                            AtomicInteger count=new AtomicInteger();
                            indexDataEntityList.stream().forEach(indexDataEntity -> {
                                count.getAndAdd(1);
                            if (stockDayPivots.getR1() > indexDataEntityOpen.getClose() && stockDayPivots.getR1() < indexDataEntity.getClose() && indexDataEntity.getClose() <stockDayPivots.getR2() && !tradeData.isOrderPlaced) {
                              //  System.out.println(today+":"+new Gson().toJson(stockDayDataEntity) +":"+new Gson().toJson(stockDayPivots));
                                tradeData.setStockName("BANKNIFTY");
                                tradeData.setBuyPrice(new BigDecimal(indexDataEntity.getClose()));
                                tradeData.setBuyTime(indexDataEntity.getTradeTime().toString());
                                tradeData.setSlPrice(tradeData.getBuyPrice().subtract(new BigDecimal(200)));
                                tradeData.isOrderPlaced=true;
                                tradeData.setEntryType("BUY");
                            }
                                if (stockDayPivots.getS1() < indexDataEntityOpen.getClose() && stockDayPivots.getS1() > indexDataEntity.getClose() && indexDataEntity.getClose() >stockDayPivots.getS2() && !tradeData.isOrderPlaced) {
                                    //  System.out.println(today+":"+new Gson().toJson(stockDayDataEntity) +":"+new Gson().toJson(stockDayPivots));
                                    tradeData.setStockName("BANKNIFTY");
                                    tradeData.setSellPrice(new BigDecimal(indexDataEntity.getClose()));
                                    tradeData.setSellTime(indexDataEntity.getTradeTime().toString());
                                    tradeData.setSlPrice(tradeData.getSellPrice().add(new BigDecimal(200)));
                                    tradeData.isOrderPlaced=true;
                                    tradeData.setEntryType("SELL");
                                }
                            if(tradeData.isOrderPlaced && !tradeData.isExited) {
                                if(indexDataEntity.getClose()<tradeData.getSlPrice().doubleValue()&&  tradeData.getEntryType().equals("BUY")) {
                                    tradeData.isExited= true;
                                    tradeData.setSellPrice(new BigDecimal(indexDataEntity.getClose()));
                                    tradeData.setSellTime(indexDataEntity.getTradeTime().toString());
                                    BigDecimal pl1= tradeData.getSellPrice().subtract(tradeData.getBuyPrice());
                                    String[] data = {"BANKNIFTY",today.getDayOfWeek().toString(),tradeData.getBuyTime(), String.valueOf(tradeData.getBuyPrice().setScale(2, BigDecimal.ROUND_HALF_UP)),tradeData.getSellTime(), String.valueOf(tradeData.getSellPrice().setScale(2, BigDecimal.ROUND_HALF_UP)), String.valueOf(pl1),tradeData.getEntryType()};
                                    csvWriter.writeNext(data);

                                    try {
                                        csvWriter.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                if(indexDataEntity.getClose()>tradeData.getSlPrice().doubleValue()&&  tradeData.getEntryType().equals("SELL")) {
                                    tradeData.isExited= true;
                                    tradeData.setBuyPrice(new BigDecimal(indexDataEntity.getClose()));
                                    tradeData.setBuyTime(indexDataEntity.getTradeTime().toString());
                                    BigDecimal pl1= tradeData.getSellPrice().subtract(tradeData.getBuyPrice());
                                    String[] data = {"BANKNIFTY",today.getDayOfWeek().toString(),tradeData.getBuyTime(), String.valueOf(tradeData.getBuyPrice().setScale(2, BigDecimal.ROUND_HALF_UP)),tradeData.getSellTime(), String.valueOf(tradeData.getSellPrice().setScale(2, BigDecimal.ROUND_HALF_UP)), String.valueOf(pl1),tradeData.getEntryType()};
                                    csvWriter.writeNext(data);

                                    try {
                                        csvWriter.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }

                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            });
                            if(tradeData.isOrderPlaced && !tradeData.isExited){
                               IndexDataEntity indexDataEntity= indexDataEntityList.get(indexDataEntityList.size()-1);
                                tradeData.isExited= true;
                                if(tradeData.getEntryType().equals("BUY")) {
                                    tradeData.setSellPrice(new BigDecimal(indexDataEntity.getClose()));
                                    tradeData.setSellTime(indexDataEntity.getTradeTime().toString());
                                }
                                if(tradeData.getEntryType().equals("SELL")) {
                                    tradeData.setBuyPrice(new BigDecimal(indexDataEntity.getClose()));
                                    tradeData.setBuyTime(indexDataEntity.getTradeTime().toString());
                                }

                                BigDecimal pl1= tradeData.getSellPrice().subtract(tradeData.getBuyPrice());
                                String[] data = {"BANKNIFTY",today.getDayOfWeek().toString(),tradeData.getBuyTime(), String.valueOf(tradeData.getBuyPrice().setScale(2, BigDecimal.ROUND_HALF_UP)),tradeData.getSellTime(), String.valueOf(tradeData.getSellPrice().setScale(2, BigDecimal.ROUND_HALF_UP)), String.valueOf(pl1),tradeData.getEntryType()};
                                System.out.println(data);
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }


            n--;
        }
    }
    public void loadIndicesHistory(int day,String symbol,String fyerSymbol) throws ParseException {
        int i=day;
        int j=day;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
      /*  String lastDate = indexDayDataRepository.findLastDate(symbol);
        if(lastDate==null) {*/
            while (i < 0) {
                j = j + 100;
                if (j > 0) {
                    j = 0;
                }
                Calendar fromcalendar = Calendar.getInstance();
                fromcalendar.add(DAY_OF_MONTH, i + 1);
                Calendar tocalendar = Calendar.getInstance();
                tocalendar.add(DAY_OF_MONTH, j);
                Date fromdate = fromcalendar.getTime();
                String fromDateStr = dateFormat.format(fromdate);
                String toDateStr = dateFormat.format(tocalendar.getTime());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(symbol);

                try {
                    List<Candlestick> candlestickList = getHistory(fyerSymbol, "5", fromDateStr, toDateStr, "1");
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<IndexDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            IndexDataEntity stockDataEntity = new IndexDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.time.longValue() * 1000);
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setIndexKey(symbol);
                            stockDataEntity.setOpen(candlestick.open.doubleValue());
                            stockDataEntity.setHigh(candlestick.high.doubleValue());
                            stockDataEntity.setLow(candlestick.low.doubleValue());
                            stockDataEntity.setClose(candlestick.close.doubleValue());
                            //  stockDataEntity.setVolume(candlestick.volume.intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        indexDataRepository.saveAll(stockDataEntities);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                i = i + 100;
            }


      /*  }else{
            Date fromDateStr= dateFormat1.parse(lastDate);
            Calendar calendar=Calendar.getInstance();
            String curDate=dateFormat.format(calendar.getTime());
            String fromDate=dateFormat.format(calendar.getTime());
            while (i < 0) {
                j = j + 100;
                if (j > 0) {
                    j = 0;
                }
                Calendar fromcalendar = Calendar.getInstance();
                fromcalendar.add(DAY_OF_MONTH, i + 1);
                Calendar tocalendar = Calendar.getInstance();
                tocalendar.add(DAY_OF_MONTH, j);
                String toDateStr = dateFormat.format(tocalendar.getTime());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(symbol);

                try {
                    List<Candlestick> candlestickList = getHistory(fyerSymbol, "5", fromDateStr, toDateStr, "1");
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<IndexDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            IndexDataEntity stockDataEntity = new IndexDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.time.longValue() * 1000);
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setIndexKey(symbol);
                            stockDataEntity.setOpen(candlestick.open.doubleValue());
                            stockDataEntity.setHigh(candlestick.high.doubleValue());
                            stockDataEntity.setLow(candlestick.low.doubleValue());
                            stockDataEntity.setClose(candlestick.close.doubleValue());
                            //  stockDataEntity.setVolume(candlestick.volume.intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        indexDataRepository.saveAll(stockDataEntities);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                i = i + 100;
            }
        }*/

    }


    public void loadIndicesDayHistory(int day,String symbol,String fyerSymbol){
        int i=day;
        int j=day;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
        String lastDate = indexDayDataRepository.findLastDate(symbol);
        if (lastDate == null) {
            while (i < 0) {
                j = j + 300;
                if (j > 0) {
                    j = 0;
                }
                Calendar fromcalendar = Calendar.getInstance();
                fromcalendar.add(DAY_OF_MONTH, i + 1);
                Calendar tocalendar = Calendar.getInstance();
                tocalendar.add(DAY_OF_MONTH, j);
                Date fromdate = fromcalendar.getTime();
                String fromDateStr = dateFormat.format(fromdate);
                String toDateStr = dateFormat.format(tocalendar.getTime());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(symbol);

                try {
                    List<Candlestick> candlestickList = getHistory(fyerSymbol, "D", fromDateStr, toDateStr, "1");
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<IndexDayDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            IndexDayDataEntity stockDataEntity = new IndexDayDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.time.longValue() * 1000);
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setIndexKey(symbol);
                            stockDataEntity.setOpen(candlestick.open.doubleValue());
                            stockDataEntity.setHigh(candlestick.high.doubleValue());
                            stockDataEntity.setLow(candlestick.low.doubleValue());
                            stockDataEntity.setClose(candlestick.close.doubleValue());
                            //  stockDataEntity.setVolume(candlestick.volume.intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        indexDayDataRepository.saveAll(stockDataEntities);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                i = i + 300;
            }
        }
        else {

            try {
                Date fromDateStr= dateFormat1.parse(lastDate);
                Calendar calendar=Calendar.getInstance();
                String curDate=dateFormat.format(calendar.getTime());
                String fromDate=dateFormat.format(calendar.getTime());
                if(curDate!=fromDate) {
                    calendar.setTime(fromDateStr);
                    calendar.add(DAY_OF_MONTH, 1);
                    Calendar toCalendar = Calendar.getInstance();
                    List<Candlestick> candlestickList = getHistory(fyerSymbol, "D", dateFormat.format(calendar.getTime()), dateFormat.format(toCalendar.getTime()), "1");
                    if (candlestickList != null && candlestickList.size() > 0) {
                        List<IndexDayDataEntity> stockDataEntities = new ArrayList<>();
                        candlestickList.stream().forEach(candlestick -> {
                            IndexDayDataEntity stockDataEntity = new IndexDayDataEntity();
                            String dataKey = UUID.randomUUID().toString();
                            stockDataEntity.setDataKey(dataKey);
                            Date date = new Date(candlestick.time.longValue() * 1000);
                            try {
                                stockDataEntity.setTradeTime(sdf1.parse(sdf1.format(date)));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            stockDataEntity.setIndexKey(symbol);
                            stockDataEntity.setOpen(candlestick.open.doubleValue());
                            stockDataEntity.setHigh(candlestick.high.doubleValue());
                            stockDataEntity.setLow(candlestick.low.doubleValue());
                            stockDataEntity.setClose(candlestick.close.doubleValue());
                            //  stockDataEntity.setVolume(candlestick.volume.intValue());
                            stockDataEntities.add(stockDataEntity);

                        });
                        indexDayDataRepository.saveAll(stockDataEntities);
                    }
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }




    }
    public List<Candlestick> getHistory(String symbol, String timePeriod, String fromDate, String toDate,String dateFormat) throws URISyntaxException, InterruptedException {
        Thread.sleep(300);
        URI uri= new URIBuilder(historyURL).addParameter("symbol", symbol)
                .addParameter("resolution",timePeriod)
                .addParameter("date_format",dateFormat)
                .addParameter("range_from",fromDate)
                .addParameter("range_to",toDate).build();
        Request request = transactionService.createPostPutDeleteRequest(HttpMethod.GET,uri.toString(),null);
        String response=transactionService.callAPI(request);
        JSONObject json = (JSONObject) JSON.parse(response);
        JSONArray jsonArray=json.getJSONArray("candles");
        if(null != jsonArray) {
            JsonWrapperArray jsonWrapperArray = new JsonWrapperArray(jsonArray);
            List<Candlestick> candlestickList = new ArrayList<>();
            jsonWrapperArray.forEachAsArray((item) -> {
                Candlestick element = new Candlestick();
                element.setTime(item.getIntegerAt(0));
                element.setOpen(new BigDecimal(item.getObjectAt(1).toString()));
                element.setHigh(new BigDecimal(item.getObjectAt(2).toString()));
                element.setLow(new BigDecimal(item.getObjectAt(3).toString()));
                element.setClose(new BigDecimal(item.getObjectAt(4).toString()));
                element.setVolume(new BigDecimal(item.getIntegerAt(5)));
                candlestickList.add(element);
            });
            return candlestickList;
        }
        return null;




    }


    public String getBarHistory(String symbol, LocalDate fromDate, LocalDate toDate) throws URISyntaxException, InterruptedException {
        Thread.sleep(300);

        List<IndexDataEntity> indexDataEntities=indexDataRepository.findSymbol(symbol,fromDate.toString(),toDate.toString());
                indexDataEntities.stream().forEach(item -> {

            });
    //        return candlestickList;

        return new Gson().toJson(indexDataEntities);




    }
    public List<Candlestick> getHistory(String symbol, String timePeriod, String fromDate, String toDate) throws URISyntaxException, InterruptedException {
        Thread.sleep(300);
        URI uri= new URIBuilder(historyURL).addParameter("symbol", symbol)
                .addParameter("resolution",timePeriod)
                .addParameter("date_format","0")
                .addParameter("range_from",fromDate)
                .addParameter("range_to",toDate).build();
        Request request = transactionService.createPostPutDeleteRequest(HttpMethod.GET,uri.toString(),null);
        String response=transactionService.callAPI(request);
        JSONObject json = (JSONObject) JSON.parse(response);
        JSONArray jsonArray=json.getJSONArray("candles");
        if(null != jsonArray) {
            JsonWrapperArray jsonWrapperArray = new JsonWrapperArray(jsonArray);
            List<Candlestick> candlestickList = new ArrayList<>();
            jsonWrapperArray.forEachAsArray((item) -> {
                Candlestick element = new Candlestick();
                element.setTime(item.getIntegerAt(0));
                element.setOpen(new BigDecimal(item.getObjectAt(1).toString()));
                element.setHigh(new BigDecimal(item.getObjectAt(2).toString()));
                element.setLow(new BigDecimal(item.getObjectAt(3).toString()));
                element.setClose(new BigDecimal(item.getObjectAt(4).toString()));
                element.setVolume(new BigDecimal(item.getIntegerAt(5)));
                candlestickList.add(element);
            });
            return candlestickList;
        }
        return null;




    }
    public Request createPostPutDeleteRequest(HttpMethod httpMethod,String uri,String payload){
        Request.Builder requestBuilder=new Request.Builder();
        requestBuilder.addHeader("Content-Type","application/json;charset=UTF-8");
        requestBuilder.url(uri);
        MediaType JSON=MediaType.parse("application/json");
        System.out.println("payload: "+ payload);
        RequestBody body= RequestBody.create(JSON,payload);
        if(HttpMethod.POST.equals(httpMethod)){
            requestBuilder.post(body);}
        else if(HttpMethod.PUT.equals(httpMethod)){
            requestBuilder.put(body);
        }else if(HttpMethod.DELETE.equals(httpMethod)){
            requestBuilder.delete(body);
        }
        return requestBuilder.build();
    }

public OkHttpClient createOkHttpClient(){
    OkHttpClient.Builder okHttpBuilder=new OkHttpClient.Builder();
    okHttpBuilder.connectTimeout(10000, TimeUnit.MILLISECONDS);
    okHttpBuilder.readTimeout(10000, TimeUnit.MILLISECONDS);
    okHttpBuilder.connectionPool(new ConnectionPool(20,5, TimeUnit.MINUTES));
    okHttpBuilder.retryOnConnectionFailure(true);
    return okHttpBuilder.build();
}


}
