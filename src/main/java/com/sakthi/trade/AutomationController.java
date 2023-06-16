package com.sakthi.trade;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sakthi.trade.algotest.backtest.data.Algotest;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.entity.*;
import com.sakthi.trade.futures.banknifty.BNFFuturesTrendFollowing;
//import com.sakthi.trade.fyer.FyerTrendTest;
import com.sakthi.trade.options.OptionDayViceTest;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.options.WeeklyDataBackup;
import com.sakthi.trade.options.banknifty.*;
import com.sakthi.trade.options.banknifty.buy.BNiftyOptionBuy917;
import com.sakthi.trade.options.banknifty.buy.BNiftyOptionBuy935;
import com.sakthi.trade.options.nifty.buy.*;
import com.sakthi.trade.repo.*;
import com.sakthi.trade.telegram.DataBot;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.truedata.HistoricRequestDTO;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.util.ZippingDirectory;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.UserList;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Position;
import com.zerodhatech.models.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.websocket.Session;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.logging.Logger;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@Slf4j
public class AutomationController {
    Gson gson=new Gson();
    public Session session = null;
    public Boolean trendCompleted = false;
    @Value("${filepath.trend}")
    String trendPath;
    /* @Autowired
     OrbScheduler orbScheduler;*/
    @Value("${fyers.order.place.api}")
    String orderPlaceURL;
    String truedataURL = null;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;
    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;
    @Autowired
    ZerodhaTransactionService instrumentService;
    @Autowired
    TransactionService transactionService;
    @Autowired
    TelegramMessenger sendMessage;

    @Autowired
    DataBot dataBot;
    /*@Autowired
    HistoricWebsocket historicWebsocket;*/
 /*
    @Autowired
    BankNIftyStraddleLongBackTest bankNIftyStraddleLongBackTest;*/
    @Autowired
    ZerodhaBankNiftyShortStraddle zerodhaBankNiftyShortStraddle;
    @Value("${preoopen.filepath:/home/hasvanth/Downloads/PreOpen_FO_}")
    String preOpenFile;
/*

    @Autowired
    TrendScheduler trendScheduler;
*/

   /* @Autowired
    BankNiftyShortStraddle bankNiftyShortStraddle;*/

   /* @Autowired
    NiftyShortStraddle niftyShortStraddle;*/
    @Value("${secban.filepath:/home/hasvanth/Downloads/}")
    String secBan;
    @Autowired
    ZerodhaAccount zerodhaAccount;


    /*@Autowired
    VwapRsiOiVolumeBuy vwapRsiOiVolumeBuy;*/

    @Autowired
    ZerodhaTransactionService ztransactionService;


    @Autowired
    BNFFuturesTrendFollowing bnfFuturesTrendFollowing;
    @Autowired
    ZerodhaBankNiftyShortStraddleWithLong zerodhaBankNiftyShortStraddleWithLong;
    @Autowired
    WeeklyDataBackup weeklyDataBackup;
 /*   @Autowired
    FyerTrendTest fyerTrendTest;*/
    @Autowired
    StockRepository stockRepository;
    @Autowired
    IndexRepository indexRepository;
    @Autowired
    BankNiftyOptionRepository bankNiftyOptionRepository;
    @Autowired
    UserLoginRepository userLoginRepository;


    @GetMapping("/zerodhaTradeGet")
    public void zerodhaTradeGet(@RequestParam String orderId) throws Exception, KiteException {
        List<Trade> trades = zerodhaAccount.kiteSdk.getOrderTrades(orderId); //oly for executed
        trades.stream().findFirst();
        log.info("getOrderTrades:" + gson.toJson(trades));
    }
    @GetMapping("/currentDate")
    public void niftyBuyTest(@RequestParam String orderId) throws Exception, KiteException {
        String currentDate="2023-02-14";
        mathUtils.getPriceRangeSortedWithLowRangeNifty(currentDate,200,150,"09:34:00","NF");
        //log.info("getOrderTrades:" + gson.toJson(trades));
    }

    @GetMapping("/zerodhaTrades")
    public void zerodhaTrades() throws Exception, KiteException {
        List<Trade> trades = zerodhaAccount.kiteSdk.getTrades(); //all executed trades
        trades.stream().findFirst();
        log.info("zerodhaTrades:" + gson.toJson(trades));
    }

    @GetMapping("/zerodhaOrders")
    public void zerodhaOrders() throws Exception, KiteException {
        List<com.zerodhatech.models.Order> trades = zerodhaAccount.kiteSdk.getOrders(); //all orders including pending, completed
        trades.stream().findFirst();
        log.info("zerodhaOrders:" + gson.toJson(trades));
    }

    /*  @GetMapping("/zerodhaOrdersAndAdd")
      public void zerodhaOrdersAndAdd(@RequestParam String orderId, @RequestParam boolean isSLPlaced, @RequestParam String slOrderId) throws Exception, KiteException {
          List<com.zerodhatech.models.Order> trades = zerodhaAccount.kiteSdk.getOrders(); //all orders including pending, completed
          log.info("zerodhaOrders:" + gson.toJson(trades));
          trades.stream().forEach(order -> {
              if (orderId.equals(order.orderId) && "COMPLETE".equals(order.status)) {
                  TradeData tradeData = new TradeData();
                  tradeData.setStockName(order.tradingSymbol);
                  tradeData.setEntryOrderId(order.orderId);
                  tradeData.isOrderPlaced = true;
                  tradeData.setQty(Integer.valueOf(order.filledQuantity));
                  tradeData.setEntryType(order.transactionType);
                  if (isSLPlaced) {
                      tradeData.isSlPlaced = isSLPlaced;
                      tradeData.setSlOrderId(slOrderId);
                  }
                  zerodhaBankNiftyShortStraddle.straddleTradeMap.put(order.tradingSymbol, tradeData);
              }
          });
          log.info("zerodhaOrders:" + gson.toJson(trades));

      }
  */
    @GetMapping("/zerodhaGetPositions")
    public void zerodhaGetPositions() throws Exception, KiteException {
        Map<String, List<Position>> trades = zerodhaAccount.kiteSdk.getPositions(); //all orders including pending, completed
        log.info("zerodhaOrders:" + gson.toJson(trades));
    }

    @GetMapping("/zerodhaOrdersId")
    public void zerodhaOrdersId(@RequestParam String orderId) throws Exception, KiteException {
        List<com.zerodhatech.models.Order> trades = zerodhaAccount.kiteSdk.getOrderHistory(orderId);
        log.info("zerodhaOrdersId:" + gson.toJson(trades));
    }

    @GetMapping("/bnfFutures")
    public void bnfFutures() throws Exception, KiteException {
        bnfFuturesTrendFollowing.bnfFutures();
    }
@Autowired
MathUtils mathUtils;

    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @GetMapping("/testRange")
    public ResponseEntity<String> testRange(@RequestParam String index,String date,int upperRange,int lowerRange) throws Exception, KiteException {
        Map<String,Map<String,String>> strikeMasterMap;
String stockId;
        if ("BNF".equals(index)) {
            strikeMasterMap=zerodhaTransactionService.bankNiftyWeeklyOptions;
            stockId="260105";
        }else {
            strikeMasterMap=zerodhaTransactionService.niftyWeeklyOptions;
            stockId="256265";
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        Map<String,String> strike=mathUtils.getPriceRange(date,upperRange,lowerRange,"09:34:00",index);
        strike.entrySet().stream().forEach(map->{
            System.out.println(date+":"+map.getKey());
        });
        return new ResponseEntity<>(gson.toJson(strike), responseHeaders, HttpStatus.OK);
    }
    @Autowired
    BNiftyOptionBuy917 bNiftyOptionBuy917;
    @GetMapping("/bNiftyOptionBuy917")
    public void bNiftyOptionBuy917() throws Exception, KiteException {
        bNiftyOptionBuy917.buy();
    }

    @Autowired
    BNiftyOptionBuy935 bNiftyOptionBuy935;


    @GetMapping("/bNiftyOptionBuy935")
    public void bNiftyOptionBuy935() throws Exception, KiteException {
        bNiftyOptionBuy935.buy();
    }
    @Autowired
    UserList userList;
    @GetMapping("/addTrades")
    public void addTrades(@RequestBody String payload) throws Exception, KiteException {
       // bNiftyOptionBuy935.buy();
        LocalDate localDate = LocalDate.now();
        DayOfWeek dow = localDate.getDayOfWeek();
        String today = dow.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.ENGLISH);
        String todayCaps = today.toUpperCase();
        AddTrade addTrade=gson.fromJson(payload,AddTrade.class);
        userList.getUser().stream().filter(
                user -> user.getName().equals(addTrade.getUserId())
        ).forEach(user -> {
            List<com.zerodhatech.models.Order> orderList= null;
            try {
                orderList = user.getKiteConnect().getOrders();
                com.zerodhatech.models.Order order= orderList.stream().filter(order1 -> order1.orderId.equals(addTrade.getOrderId())).findFirst().get();
                TradeData tradeData=new TradeData();
                tradeData.setEntryOrderId(order.orderId);
                tradeData.setStockName(order.tradingSymbol);
                String dataKey = UUID.randomUUID().toString();
                tradeData.setDataKey(dataKey);
                tradeData.setUserId(addTrade.getUserId());
                tradeData.setEntryType(order.transactionType);
                tradeData.setOrderPlaced(true);
                tradeData.setQty(Integer.parseInt(order.quantity));
                tradeData.setStockId(Integer.parseInt(addTrade.getStockId()));
                if("BUY".equals(order.transactionType)){
                    if("SL".equals(order.orderType)){
                        tradeData.setBuyPrice(new BigDecimal(order.price));
                    }else {

                    }
                }else {
                    if("SL".equals(order.orderType)){

                    }
                }

            if("BNIFTY_BUY_935".equals(addTrade.getStrategyName())){
            if(user.getBniftyBuy935() != null && user.getBniftyBuy935().isNrmlEnabled() && user.getBniftyBuy935().getLotConfig().containsKey(todayCaps)){
                try {    tradeData.setAlgoName("BNIFTY_BUY_935");
                    user.getBniftyBuy935().straddleTradeMap.put(order.symbol,tradeData);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            }
            if("BNIFTY_BUY_917".equals(addTrade.getStrategyName())){
                    if(user.getBniftyBuy917() != null && user.getBniftyBuy917().isNrmlEnabled() && user.getBniftyBuy917().getLotConfig().containsKey(todayCaps)){
                        try {
                            tradeData.setAlgoName("BNIFTY_BUY_917");
                            user.getBniftyBuy917().straddleTradeMap.put(order.symbol,tradeData);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
            if("BNIFTY_BUY_925".equals(addTrade.getStrategyName())){
                    if(user.getBniftyBuy925() != null && user.getBniftyBuy925().isNrmlEnabled() && user.getBniftyBuy925().getLotConfig().containsKey(todayCaps)){
                        try {
                            tradeData.setAlgoName("BNIFTY_BUY_925");
                            user.getBniftyBuy925().straddleTradeMap.put(order.symbol,tradeData);

                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
                if("NIFTY_BUY_935".equals(addTrade.getStrategyName())){
                    if(user.getNiftyBuy935() != null && user.getNiftyBuy935().isNrmlEnabled() && user.getNiftyBuy935().getLotConfig().containsKey(todayCaps)){
                        try {
                            tradeData.setAlgoName("NIFTY_BUY_935");
                            user.getNiftyBuy935().straddleTradeMap.put(order.symbol,tradeData);

                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
                if("NIFTY_BUY_1035".equals(addTrade.getStrategyName())){
                    if(user.getNiftyBuy1035() != null && user.getNiftyBuy1035().isNrmlEnabled() && user.getNiftyBuy1035().getLotConfig().containsKey(todayCaps)){
                        try {
                            tradeData.setAlgoName("NIFTY_BUY_1035");
                            user.getNiftyBuy1035().straddleTradeMap.put(order.symbol,tradeData);

                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
                mapTradeDataToSaveOpenTradeDataEntity(tradeData,tradeData.isOrderPlaced);
            } catch (KiteException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    @Autowired
    TradeDataMapper tradeDataMapper;
    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData,boolean orderPlaced) {
        try {/*
            OpenTradeDataEntity openTradeDataEntity = new OpenTradeDataEntity();
            openTradeDataEntity.setDataKey(tradeData.getDataKey());
            openTradeDataEntity.setAlgoName(this.getAlgoName());
            openTradeDataEntity.setStockName(tradeData.getStockName());
            openTradeDataEntity.setEntryType(tradeData.getEntryType());
            openTradeDataEntity.setUserId(tradeData.getUserId());
            openTradeDataEntity.isOrderPlaced = tradeData.isOrderPlaced;
            openTradeDataEntity.isSlPlaced = tradeData.isSlPlaced();
            openTradeDataEntity.isExited = tradeData.isExited();
            openTradeDataEntity.isErrored = tradeData.isErrored;
            openTradeDataEntity.isSLHit = tradeData.isSLHit;
            openTradeDataEntity.setBuyTradedPrice(tradeData.getBuyTradedPrice());
            openTradeDataEntity.setSellTradedPrice(tradeData.getSellTradedPrice());
            openTradeDataEntity.setExitOrderId(tradeData.getExitOrderId());
            openTradeDataEntity.setBuyPrice(tradeData.getBuyPrice());
            openTradeDataEntity.setSellPrice(tradeData.getSellPrice());
            openTradeDataEntity.setSlPrice(tradeData.getSlPrice());
            openTradeDataEntity.setQty(tradeData.getQty());
            openTradeDataEntity.setSlPercentage(tradeData.getSlPercentage());
            openTradeDataEntity.setEntryOrderId(tradeData.getEntryOrderId());
            openTradeDataEntity.setSlOrderId(tradeData.getSlOrderId());
            openTradeDataEntity.setStockId(tradeData.getStockId());
            Date date = new Date();
            if(orderPlaced) {
                String tradeDate = format.format(date);
                openTradeDataEntity.setTradeDate(tradeDate);
                tradeData.setTradeDate(tradeDate);
            }else{
                openTradeDataEntity.setTradeDate(tradeData.getTradeDate());
            }
            saveTradeData(openTradeDataEntity);*/
            tradeDataMapper.mapTradeDataToSaveOpenTradeDataEntity(tradeData,orderPlaced,tradeData.getAlgoName());
            //LOGGER.info("sucessfully saved trade data");
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }
    @GetMapping("/bNiftyOptionBuy935Test")
    public void bNiftyOptionBuy935Test() throws Exception, KiteException {
        bNiftyOptionBuy935.buy();
    }

    @Autowired
    Algotest algotest;

    @GetMapping("/loadAlgoTestData")
    public void loadAlgoTestData() throws Exception {
        algotest.loadBacktestData();
    }
    @GetMapping("/getAlgoTestData")
    public ResponseEntity<String> getAlgoTestData() throws Exception {
        SummaryDataList tradeData= algotest.getAlgoTestData();
        HttpHeaders responseHeaders = new HttpHeaders();
        return new ResponseEntity<>(gson.toJson(tradeData), responseHeaders, HttpStatus.OK);
    }
    @GetMapping("/zerodhaloginmtest")
    public void zerodhaloginmtest() throws Exception {
        zerodhaAccount.generateMultiUserAccessToken();
    }
@Autowired
NiftyOptionBuy935 niftyOptionBuy935;

    @GetMapping("/zerodhaloadmtest")
    public void zerodhaloadmtest() throws Exception {
        zerodhaBankNiftyShortStraddleWithLong.loadNrmlPositions();
    }
    @GetMapping("/niftyOptionBuy935loadmtest")
    public void niftyOptionBuy935loadmtest() throws Exception {
        niftyOptionBuy935.loadNrmlPositions();
    }
    @GetMapping("/zerodha_instrument")
    public void generateInstrument() throws Exception {
        instrumentService.getInstrument();
    }
    @Autowired
    ZippingDirectory zippingDirectory;

    @GetMapping("/zerodha_backup")
    public void weeklyDataBackup() throws Exception {
        weeklyDataBackup.dataBackUp();
        //       zippingDirectory.test();

    }
    @GetMapping("/sendLog")
    public void sendLog() throws Exception {
        weeklyDataBackup.logBackUp();
        //       zippingDirectory.test();

    }

    @GetMapping("/monitorPositionSize")
    public void monitorPositionSize() throws Exception, KiteException {
        zerodhaAccount.monitorPositionSize();
    }



    @GetMapping("/loadTradeEngineData")
    public void loadTradeEngineData() throws Exception, KiteException {

       /* List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {*/
        tradeEngine.loadNrmlPositions();
        /*  });*/

    }

    @Autowired
    TradeEngine oneTradeExecutor;
    @GetMapping("/oneTradeExecutor")
    public void oneTradeExecutor() throws Exception, KiteException {

       /* List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {*/
        oneTradeExecutor.loadStrategy();
        oneTradeExecutor.executeStrategy();
        /*  });*/

    }

    @GetMapping("/zerodhaBN")
    public void zerodhaBN() throws Exception {
        instrumentService.getInstrument();
        zerodhaBankNiftyShortStraddle.zerodhaBankNifty();

    }


    @GetMapping("/optionExpDate")
    public ResponseEntity<?> optionExpDate() throws Exception {
        HttpHeaders responseHeaders = new HttpHeaders();
        List<String> stringList = bankNiftyOptionRepository.getExpDate();
        List<ExpResponse> entities = new ArrayList<>();
        ExpResponse entity = new ExpResponse();
        entity.setName(stringList);
        return new ResponseEntity<>(entity, responseHeaders, HttpStatus.OK);
    }

    @GetMapping("/optionExpOption")
    public ResponseEntity<?> optionExpOption(@RequestParam String expDate) throws Exception {
        HttpHeaders responseHeaders = new HttpHeaders();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date expDateD = format.parse(expDate);
        List<String> stringList = bankNiftyOptionRepository.getExpStikeAll(expDateD);
        List<ExpResponse> entities = new ArrayList<>();
        ExpResponse entity = new ExpResponse();
        System.out.println(stringList);
        entity.setName(stringList);
        return new ResponseEntity<>(entity, responseHeaders, HttpStatus.OK);
    }

    @Autowired
    ExpBuy expBuy;
    @GetMapping("/expBuy")
    public void expBuy() throws Exception {
       // expBuy.buy();

    }
    @GetMapping("/getPositions")
    public void getPositions() throws Exception {
        String response = "{\"s\":\"ok\",\"netPositions\":[{\"crossCurrency\":\"N\",\"qty\":16,\"realized_profit\":0.0,\"id\":\"NSE:JPASSOCIAT-EQ-CNC\",\"unrealized_profit\":0.32,\"buyQty\":16,\"sellAvg\":0.0,\"sellQty\":0,\"buyAvg\":4.78,\"symbol\":\"NSE:JPASSOCIAT-EQ\",\"fyToken\":\"101000000011460\",\"slNo\":0,\"avgPrice\":4.78,\"segment\":\"E\",\"dummy\":\" \",\"rbiRefRate\":1.0,\"side\":1,\"netQty\":16,\"pl\":0.32,\"productType\":\"CNC\",\"netAvg\":4.78,\"qtyMulti_com\":1.0},{\"crossCurrency\":\"N\",\"qty\":90,\"realized_profit\":0.0,\"id\":\"NSE:RCOM-EQ-CNC\",\"unrealized_profit\":1.8,\"buyQty\":90,\"sellAvg\":0.0,\"sellQty\":0,\"buyAvg\":2.03,\"symbol\":\"NSE:RCOM-EQ\",\"fyToken\":\"101000000013187\",\"slNo\":1,\"avgPrice\":2.03,\"segment\":\"E\",\"dummy\":\" \",\"rbiRefRate\":1.0,\"side\":1,\"netQty\":90,\"pl\":1.8,\"productType\":\"CNC\",\"netAvg\":2.03,\"qtyMulti_com\":1.0}],message:\"\"}";
        System.out.println(response);
        //  String response ="{\"s\":\"ok\",\"message\":\"\",\"orderDetails\":{\"status\":2,\"symbol\":\"NSE:IGL-EQ\",\"qty\":49,\"orderNumStatus\":\"120082021161:2\",\"dqQtyRem\":0,\"orderDateTime\":\"20-Aug-2020 09:33:30\",\"orderValidity\":\"DAY\",\"fyToken\":\"101000000011262\",\"slNo\":13,\"message\":\"TRADE CONFIRMED\",\"segment\":\"E\",\"id\":\"120082021161\",\"stopPrice\":0.0,\"instrument\":\"EQUITY\",\"exchOrdId\":\"1100000001815348\",\"remainingQuantity\":0,\"filledQty\":49,\"limitPrice\":0.0,\"offlineOrder\":false,\"source\":\"ITS\",\"productType\":\"INTRADAY\",\"type\":2,\"side\":1,\"tradedPrice\":404.95,\"discloseQty\":0}}";
        OpenPositionsResponseDTO orderStatusResponseDTO = gson.fromJson(response, OpenPositionsResponseDTO.class);
        System.out.println(response);

    }

    public String orbIntraday15minHistoricInput(String date, String interval, String stock) {
        HistoricRequestDTO historicRequestDTO = new HistoricRequestDTO();
        String fromDate = date + "T09:00:00";
        String todate = date + "T23:30:00";
        historicRequestDTO.setFrom(fromDate);
        historicRequestDTO.setTo(todate);
        historicRequestDTO.setSymbol(stock);
        historicRequestDTO.setInterval(interval);
        historicRequestDTO.setMethod("gethistory");
        return gson.toJson(historicRequestDTO);
    }

    public Map<String, Double> getOrbStockList(String strdate) throws Exception {
        long startTime = System.nanoTime();

        LocalDate localDate = LocalDate.now();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = sdf.parse(strdate);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("ddMMMyyyy");
        SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat("ddMMyyyy");
        FileReader fileSecBan = new FileReader(secBan + simpleDateFormat1.format(date) + ".csv");
        BufferedReader readerSecBan = new BufferedReader(fileSecBan);
        String lineSecBan = "";
        String csvSplitBy = ",";
        List<String> secBanList = new ArrayList<>();
        int k = 0;
        while ((lineSecBan = readerSecBan.readLine()) != null) {
            if (k > 0) {
                String[] data = lineSecBan.split(csvSplitBy);
                secBanList.add(data[1]);
            }
            k++;

        }
        FileReader file = new FileReader(preOpenFile + simpleDateFormat.format(date) + ".csv");
        BufferedReader reader = new BufferedReader(file);
        String line = "";

        int j = 0;
        int m = 0;
        Map<String, Double> preopenDataMap = new HashMap<>();
        Map<String, Double> preopenData = new HashMap<>();
        while ((line = reader.readLine()) != null) {
            String[] data = line.split(csvSplitBy);
            if (j > 0 && !secBanList.contains(data[0])) {
                Double stockPrice = Double.valueOf(data[4].replace(",", ""));
                Double perCh = Double.valueOf(data[6]);
                if (stockPrice > 50) {
                    preopenDataMap.put(data[0], perCh);
                    preopenData.put(data[0], stockPrice);
                    m++;
                }
            }
            j++;
        }
        //orbScheduler.preOpenData=preopenData;

        long endTime = System.nanoTime();
        long processDuration = (endTime - startTime) / 1000000;
        log.info("Successfully retrived pre open fo data from nse with time: " + processDuration);
        return preopenData;
    }

    @GetMapping("/getQuote")
    public void getQuote() throws Exception, KiteException {
        String[] str = {"NFO:BANKNIFTY21JAN31500CE"};
        zerodhaAccount.kiteSdk.getLTP(str);
        Map<String, LTPQuote> map = zerodhaAccount.kiteSdk.getLTP(str);
        map.entrySet().stream().findFirst().isPresent();
    }



    @PostMapping("/authenticate")
    public ResponseEntity<?> userAuthentication(@RequestBody UserInput payload) throws Exception {
        String userName= payload.getUserName();
        String password=payload.getPassword();
        Optional<UserLoginEntity> userLoginEntity = userLoginRepository.findById(userName);
        if (userLoginEntity.isPresent()) {
            UserLoginEntity userLoginEntity1=userLoginEntity.get();
            if(userName.equals(userLoginEntity1.getUserName()) && password.equals(userLoginEntity1.getPassword())){
                com.sakthi.trade.domain.User user=new com.sakthi.trade.domain.User();
                user.setName(userLoginEntity1.getName());
                user.setUserId(userLoginEntity1.getUserId());
                user.setUserName(userLoginEntity1.getUserName());
                return new ResponseEntity<>(user,HttpStatus.OK);
            }
        }
        return new ResponseEntity<>(null,HttpStatus.NOT_FOUND);
    }
    public static final Logger LOGGER = Logger.getLogger(AutomationController.class.getName());
    @Autowired
    OpenTradeDataRepo openTradeDataRepo;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

    @GetMapping("/getVixData")
    public ResponseEntity<?> getVixData(@RequestBody String payload) throws Exception {
        String historicURL = "https://api.kite.trade/instruments/historical/264969/day?from=2014-01-01+09:00:00&to=2017-12-31+06:15:00";
        //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
        String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
        System.out.print(response);
        return new ResponseEntity<>(null,HttpStatus.OK);
    }

    @Autowired
    TradeUserRepository tradeUserRepository;
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    @GetMapping("/saveStrategy")
    public ResponseEntity<?> saveStrategy(@RequestBody String payload) throws Exception {
        List<TradeStrategy> tradeStrategies=gson.fromJson(payload, new TypeToken<List<TradeStrategy>>(){}.getType());
        System.out.println(gson.toJson(tradeStrategies));
        tradeStrategyRepo.saveAll(tradeStrategies);
        tradeStrategyRepo.flush();
        return new ResponseEntity<>(null,HttpStatus.OK);
    }

    @Autowired
    OptionDayViceTest optionDayViceTest;
    @GetMapping("/dayTrade")
    public ResponseEntity<?> dayTrade(@RequestParam int day) throws Exception, KiteException {
        optionDayViceTest.test(day);
        return new ResponseEntity<>(null,HttpStatus.OK);
    }
    @Autowired
    TradeEngine tradeEngine;
/*    @GetMapping("/oneTradeExecutor")
    public ResponseEntity<?> oneTradeExecutor(@RequestBody String payload) throws Exception {
        List<TradeStrategy> tradeStrategies=gson.fromJson(payload, new TypeToken<List<TradeStrategy>>(){}.getType());
        System.out.println(gson.toJson(tradeStrategies));
      tradeEngine.loadStrategy();
      tradeEngine.executeStrategy();
        return new ResponseEntity<>(null,HttpStatus.OK);
    }*/

    @GetMapping("/saveTradeUser")
    public ResponseEntity<?> saveTradeUser(@RequestBody String payload) throws Exception {
        List<TradeUserEntity> tradeUserEntities=gson.fromJson(payload, new TypeToken<List<TradeUserEntity>>(){}.getType());
        System.out.println(gson.toJson(tradeUserEntities));
        tradeUserEntities.forEach(tradeUserEntity -> {
            tradeUserRepository.saveAndFlush(tradeUserEntity);
       //     tradeUserRepository.flush();
        });

        return new ResponseEntity<>(null,HttpStatus.OK);
    }
    @PostMapping("/getTradeDetails")
    public ResponseEntity<?> getTradeDetails(@RequestBody String payload) throws Exception {
        Users users=gson.fromJson(payload,Users.class);
        User user=users.getUser();
        Date date = new Date();
        List<OpenTradeDataEntity> orderDetails = openTradeDataRepo.getOrderDetails(user.getUserId(),format.format(date));
        List<TradeData> tradeDataList = new ArrayList<>();
        if (orderDetails.size() > 0){
            tradeDataList=mapOpenTradeDataEntityToTradeData(orderDetails);
            return new ResponseEntity<>(gson.toJson(tradeDataList),HttpStatus.OK);
            }else {
            orderDetails = openTradeDataRepo.getOpenPositionDetails(user.getUserId());
            if (orderDetails.size() > 0){
                tradeDataList=mapOpenTradeDataEntityToTradeData(orderDetails);
                return new ResponseEntity<>(gson.toJson(tradeDataList),HttpStatus.OK);
            }
        }
        return new ResponseEntity<>(null,HttpStatus.NOT_FOUND);
    }
    @PostMapping("/getOpenOrderDetails")
    public ResponseEntity<?> getOpenOrderDetails(@RequestBody String payload) throws Exception {
        Users users = gson.fromJson(payload, Users.class);
        User user = users.getUser();
        com.sakthi.trade.zerodha.account.User zerodhaUser = userList.getUser().stream().filter(user1 -> user1.getName().equals(user.getUserId())).findFirst().get();

        if (user != null) {
            List<com.zerodhatech.models.Order> orderList = null;
            try {
                orderList = zerodhaUser.getKiteConnect().getOrders();
                //   LOGGER.info("get trade response:"+gson.toJson(orderList));
            } catch (KiteException | IOException e) {
                e.printStackTrace();
            }
            if (orderList.size() > 0) {
                return new ResponseEntity<>(gson.toJson(orderList), HttpStatus.OK);
            }}
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

    }
    @PostMapping("/getPositionDetails")
    public ResponseEntity<?> getPositionDetails(@RequestBody String payload) throws Exception {
        Users users = gson.fromJson(payload, Users.class);
        User user = users.getUser();
        com.sakthi.trade.zerodha.account.User zerodhaUser = userList.getUser().stream().filter(user1 -> user1.getName().equals(user.getUserId())).findFirst().get();

        if (user != null) {
            List<Position> positions = null;
            try {
                 positions = zerodhaUser.getKiteConnect().getPositions().get("net");
                   LOGGER.info("get trade response:"+gson.toJson(positions));
            } catch (KiteException | IOException e) {
                e.printStackTrace();
            }
            if (positions.size() > 0) {
                return new ResponseEntity<>(gson.toJson(positions), HttpStatus.OK);
            }

        }
        return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

    }

    public List<TradeData> mapOpenTradeDataEntityToTradeData( List<OpenTradeDataEntity> tradeDataEntities) {
        try {
            List<TradeData> tradeDataList = new ArrayList<>();
            tradeDataEntities.stream().forEach(tradeDataEntity -> {
                try {
                    TradeData tradeData = new TradeData();
                    tradeData.setDataKey(tradeDataEntity.getDataKey());
                    tradeData.setAlgoName(tradeDataEntity.getAlgoName());
                    tradeData.setStockName(tradeDataEntity.getStockName());
                    tradeData.setEntryType(tradeDataEntity.getEntryType());
                    tradeData.setUserId(tradeDataEntity.getUserId());
                    tradeData.isOrderPlaced = tradeDataEntity.isOrderPlaced;
                    tradeData.isSlPlaced = tradeDataEntity.isSlPlaced();
                    tradeData.isExited = tradeDataEntity.isExited();
                    tradeData.isErrored = tradeDataEntity.isErrored;
                    tradeData.isSLHit = tradeDataEntity.isSLHit;
                    tradeData.setBuyTradedPrice(tradeDataEntity.getBuyTradedPrice());
                    tradeData.setSellTradedPrice(tradeDataEntity.getSellTradedPrice());
                    tradeData.setExitOrderId(tradeDataEntity.getExitOrderId());
                    tradeData.setBuyPrice(tradeDataEntity.getBuyPrice());
                    tradeData.setSellPrice(tradeDataEntity.getSellPrice());
                    tradeData.setSlPrice(tradeDataEntity.getSlPrice());
                    tradeData.setQty(tradeDataEntity.getQty());
                    tradeData.setSlPercentage(tradeDataEntity.getSlPercentage());
                    tradeData.setEntryOrderId(tradeDataEntity.getEntryOrderId());
                    tradeData.setSlOrderId(tradeDataEntity.getSlOrderId());
                    tradeData.setStockId(tradeDataEntity.getStockId());
                    tradeData.setTradeDate(tradeDataEntity.getTradeDate());
                    tradeDataList.add(tradeData);
                } catch (Exception e) {
                    LOGGER.info(e.getMessage());
                }
            });
            return tradeDataList;
            } catch (Exception e) {
                LOGGER.info(e.getMessage());
            }
        return null;
    }
}
