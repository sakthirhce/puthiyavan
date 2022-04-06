package com.sakthi.trade;

import com.binance.client.RequestOptions;
import com.binance.client.impl.RestApiRequestImpl;
import com.binance.client.impl.SyncRequestImpl;
import com.binance.client.model.market.ExchangeInfoEntry;
import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.sakthi.trade.aliceblue.AliceAccount;
//import com.sakthi.trade.binance.BinanceAccount;
import com.sakthi.trade.binance.*;
import com.sakthi.trade.domain.*;
import com.sakthi.trade.domain.Order;
import com.sakthi.trade.domain.ExpResponse;
import com.sakthi.trade.entity.CryptoFuturesEntity;
import com.sakthi.trade.entity.IndexEntity;
import com.sakthi.trade.entity.StockDataEntity;
import com.sakthi.trade.entity.StockEntity;
import com.sakthi.trade.fyer.Account;
import com.sakthi.trade.fyer.FyerTrendTest;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.fyer.transactions.OrderStatusResponseDTO;
import com.sakthi.trade.fyer.transactions.PlaceOrderRequestDTO;
import com.sakthi.trade.index.option.data.load.BankNiftyDataLoad;
import com.sakthi.trade.options.WeeklyDataBackup;
import com.sakthi.trade.options.banknifty.*;
//import com.sakthi.trade.options.banknifty.BankNiftyShortStraddle;
//import com.sakthi.trade.options.buy.banknifty.VwapRsiOiVolumeBuy;
import com.sakthi.trade.options.banknifty.backtest.*;
import com.sakthi.trade.options.buy.banknifty.VwapRsiOiVolumeBuyBacktest;
import com.sakthi.trade.options.buy.banknifty.VwapRsiOiVolumeBuyBacktestTrueData;
import com.sakthi.trade.options.nifty.NIftyStraddleLongBackTest;
//import com.sakthi.trade.options.nifty.NiftyShortStraddle;
import com.sakthi.trade.options.nifty.NiftyShortStraddleOI;
import com.sakthi.trade.options.nifty.buy.NiftyVwapRsiOiVolumeBuy;
//import com.sakthi.trade.orb.OrbScheduler;
import com.sakthi.trade.options.nifty.buy.NiftyVwapRsiOiVolumeBuyBacktest;
import com.sakthi.trade.repo.BankNiftyOptionRepository;
import com.sakthi.trade.repo.CryptoRepository;
import com.sakthi.trade.repo.IndexRepository;
import com.sakthi.trade.repo.StockRepository;
import com.sakthi.trade.telegram.SendMessage;
//import com.sakthi.trade.trend.TrendScheduler;
//import com.sakthi.trade.trend.ZerodhaTrendScheduler;
import com.sakthi.trade.trend.ZerodhaTrendScheduler;
import com.sakthi.trade.trend.ZerodhaTrendSchedulerTest;
import com.sakthi.trade.truedata.HistoricRequestDTO;
import com.sakthi.trade.util.MathUtils;
//import com.sakthi.trade.websocket.truedata.HistoricWebsocket;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
//import com.sakthi.trade.zerodha.instrument.InstrumentService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.websocket.Session;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@Slf4j
public class AutomationController {
    @Autowired
    Account account;
    @Value("${filepath.trend}")
    String trendPath;
    /* @Autowired
     OrbScheduler orbScheduler;*/
    @Value("${truedata.wss}")
    String truedataWss;
    @Value("${truedata.username}")
    String truedataUsername;
    @Value("${truedata.password}")
    String truedataPassword;
    @Value("${truedata.realtime.port}")
    String truedataRealTimeDataPort;
    @Value("${fyers.order.place.api}")
    String orderPlaceURL;
    public Session session = null;
    String truedataURL = null;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;
    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;
    @Autowired
    FyerTransactionMapper fyerTransactionMapper;
    @Autowired
    ZerodhaTransactionService instrumentService;


    @Autowired
    ZerodhaTrendScheduler zerodhaTrendScheduler;


    @Autowired
    TransactionService transactionService;
    @Autowired
    SendMessage sendMessage;

    @Autowired
    NIftyStraddleLongBackTest nIftyStraddleLongBackTest;/*
    @Autowired
    BankNIftyStraddleLongBackTest bankNIftyStraddleLongBackTest;*/
    /*@Autowired
    HistoricWebsocket historicWebsocket;*/

    @Autowired
    ZerodhaBankNiftyShortStraddle zerodhaBankNiftyShortStraddle;

    @Value("${preoopen.filepath:/home/hasvanth/Downloads/PreOpen_FO_}")
    String preOpenFile;
    @Value("${secban.filepath:/home/hasvanth/Downloads/}")
    String secBan;
/*

    @Autowired
    TrendScheduler trendScheduler;
*/

   /* @Autowired
    BankNiftyShortStraddle bankNiftyShortStraddle;*/

   /* @Autowired
    NiftyShortStraddle niftyShortStraddle;*/

    public Boolean trendCompleted = false;

    @Autowired
    ZerodhaAccount zerodhaAccount;

    @Autowired
    AliceAccount aliceaccount;

    /*@Autowired
    VwapRsiOiVolumeBuy vwapRsiOiVolumeBuy;*/

    @Autowired
    VwapRsiOiVolumeSelling vwapRsiOiVolumeSelling;
    @Autowired
    NiftyVwapRsiOiVolumeBuy niftyVwapRsiOiVolumeBuy;

    @Autowired
    VwapRsiOiVolumeBuyBacktest vwapRsiOiVolumeBuyBacktest;

    @Autowired
    ZerodhaTransactionService ztransactionService;

    @Autowired
    NiftyVwapRsiOiVolumeBuyBacktest niftyVwapRsiOiVolumeBuyBacktest;

    @Autowired
    ZerodhaTrendSchedulerTest zerodhaTrendSchedulerTest;

    @GetMapping("/aliceTokenTest")
    public void aliceToken() throws Exception {
        aliceaccount.aliceToken();
        aliceaccount.aliceWebsocket();
    }

    @PostMapping("/postAliceData")
    public void postAliceData(@RequestBody String body) throws Exception {
        aliceaccount.session.getAsyncRemote().sendText(body);
    }

    @GetMapping("/zerodhatest")
    public void zerodhaGenerateToken() throws Exception {
        zerodhaAccount.generateToken();
    }

    @GetMapping("/zerodhaTradeGet")
    public void zerodhaTradeGet(@RequestParam String orderId) throws Exception, KiteException {
        List<Trade> trades = zerodhaAccount.kiteSdk.getOrderTrades(orderId); //oly for executed
        trades.stream().findFirst();
        log.info("getOrderTrades:" + new Gson().toJson(trades));
    }

    @GetMapping("/zerodhaTrades")
    public void zerodhaTrades() throws Exception, KiteException {
        List<Trade> trades = zerodhaAccount.kiteSdk.getTrades(); //all executed trades
        trades.stream().findFirst();
        log.info("zerodhaTrades:" + new Gson().toJson(trades));
    }

    @GetMapping("/zerodhaOrders")
    public void zerodhaOrders() throws Exception, KiteException {
        List<com.zerodhatech.models.Order> trades = zerodhaAccount.kiteSdk.getOrders(); //all orders including pending, completed
        trades.stream().findFirst();
        log.info("zerodhaOrders:" + new Gson().toJson(trades));
    }

  /*  @GetMapping("/zerodhaOrdersAndAdd")
    public void zerodhaOrdersAndAdd(@RequestParam String orderId, @RequestParam boolean isSLPlaced, @RequestParam String slOrderId) throws Exception, KiteException {
        List<com.zerodhatech.models.Order> trades = zerodhaAccount.kiteSdk.getOrders(); //all orders including pending, completed
        log.info("zerodhaOrders:" + new Gson().toJson(trades));
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
        log.info("zerodhaOrders:" + new Gson().toJson(trades));

    }
*/
    @GetMapping("/zerodhaGetPositions")
    public void zerodhaGetPositions() throws Exception, KiteException {
        Map<String, List<Position>> trades = zerodhaAccount.kiteSdk.getPositions(); //all orders including pending, completed
        log.info("zerodhaOrders:" + new Gson().toJson(trades));
    }

    @GetMapping("/zerodhaOrdersId")
    public void zerodhaOrdersId(@RequestParam String orderId) throws Exception, KiteException {
        List<com.zerodhatech.models.Order> trades = zerodhaAccount.kiteSdk.getOrderHistory(orderId);
        log.info("zerodhaOrdersId:" + new Gson().toJson(trades));
    }

    @GetMapping("/zerodhalogintest")
    public void generateAccessToken() throws Exception {
        zerodhaAccount.generateAccessToken();
    }
    @GetMapping("/zerodhaloginmtest")
    public void zerodhaloginmtest() throws Exception {
        zerodhaAccount.generateMultiUserAccessToken();
    }


    @GetMapping("/zerodha_instrument")
    public void generateInstrument() throws Exception {
        instrumentService.getInstrument();
    }

    @Autowired
    WeeklyDataBackup weeklyDataBackup;


    @GetMapping("/zerodha_backup")
    public void weeklyDataBackup() throws Exception {
        weeklyDataBackup.dataBackUp();
    }

    @GetMapping("/zerodhaNewTrend")
        public void newTrend(@RequestParam int day, @RequestParam boolean isOpenPriceSL, @RequestParam String slPer, @RequestParam String gainPer, @RequestParam String margin, @RequestParam int topNumber, @RequestParam boolean isPyramid, @RequestParam boolean shortTest) throws Exception, KiteException {
        zerodhaTrendSchedulerTest.trendScheduler(day, isOpenPriceSL, slPer, gainPer, margin, topNumber, isPyramid, shortTest);

    }
    @Autowired
    FyerTrendTest fyerTrendTest;
    @GetMapping("/newFyersTrend")
    public void newFyersTrend(@RequestParam int day, @RequestParam boolean isOpenPriceSL, @RequestParam String slPer, @RequestParam String gainPer, @RequestParam String margin, @RequestParam int topNumber, @RequestParam boolean isPyramid, @RequestParam boolean shortTest) throws Exception, KiteException {
        fyerTrendTest.trendScheduler(day, isOpenPriceSL, slPer, gainPer, margin, topNumber, isPyramid, shortTest);

    }

    @Autowired
    StockRepository stockRepository;
    @GetMapping("/loadHistory")
    public void loadHistory(@RequestParam int day) throws Exception, KiteException {

        List<StockEntity> stockEntityList=stockRepository.findMissingStockData();

        stockEntityList.forEach(stockEntity -> {
            account.loadHistory(day,stockEntity.getSymbol(),stockEntity.getFyerSymbol());
        });

    }
    @GetMapping("/loadStockDayHistory")
    public void loadStockDayHistory(@RequestParam int day) throws Exception, KiteException {

        List<StockEntity> stockEntityList=stockRepository.findAll();

        stockEntityList.forEach(stockEntity -> {
            account.loadDayHistory(day,stockEntity.getSymbol(),stockEntity.getFyerSymbol());
        });

    }
    @GetMapping("/loadStockWeekHistory")
    public void loadStockWeekHistory(@RequestParam int day) throws Exception, KiteException {

        List<StockEntity> stockEntityList=stockRepository.findAll();

        stockEntityList.forEach(stockEntity -> {
            account.loadStockWeekHistory(stockEntity.getSymbol());
        });

    }

    @GetMapping("/calculateBigTimeFrame")
    public void calculateBigTimeFrame(@RequestParam int day) throws Exception, KiteException {

       account.calculateBigTimeFrame();
    }
    @GetMapping("/populatePivots")
    public void populatePivots() throws Exception, KiteException {

       /* List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {*/
            account.populatePivots();
      /*  });*/

    }
    @GetMapping("/findMax")
    public void findMax() throws Exception, KiteException {

       /* List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {*/
        account.findMax();
        /*  });*/

    }
    @GetMapping("/testPivots")
    public void testPivots(@RequestParam String fromDate,@RequestParam String toDate) throws Exception, KiteException {

       /* List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {*/
        account.testPivots(fromDate,toDate);
        /*  });*/

    }
    @GetMapping("/testPivotsHistory")
    public void testPivotsHistory(@RequestParam int day) throws Exception, KiteException {

       /* List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {*/
        account.testPivotsHistory(day);
        /*  });*/

    }


    @GetMapping("/loadStockYearHistory")
    public void loadStockYearHistory(@RequestParam int day) throws Exception, KiteException {
        List<StockEntity> stockEntityList=stockRepository.findAll();
        stockEntityList.forEach(stockEntity -> {
            account.loadStockYearHistory(stockEntity.getSymbol());
        });
    }
    @GetMapping("/loadIndicesDayHistory")
    public void loadIndicesDayHistory(@RequestParam int day) throws Exception, KiteException {

        account.loadIndicesDayHistory(day,"BANKNIFTY","NSE:NIFTYBANK-INDEX");

    }
    @Autowired
    IndexRepository indexRepository;
    @GetMapping("/loadIndicesHistory")
    public void loadIndicesHistory(@RequestParam int day) throws Exception, KiteException {
            account.loadIndicesHistory(day,"BANKNIFTY","NSE:NIFTYBANK-INDEX");


    }
    @GetMapping("/testPivotsIndices")
    public void testPivotsIndices(@RequestParam int day) throws Exception, KiteException {
        account.testPivotsIndices(day);


    }
    /*  @GetMapping("/vwapBuy")
      public void vwapBuy() throws Exception, KiteException {
          zerodhaAccount.generateAccessToken();
          instrumentService.getInstrument();
          vwapRsiOiVolumeBuy.buy();

      }*/
    @GetMapping("/vwapBuyTest")
    public void vwapBuyTest(@RequestParam int day, @RequestParam String tf, @RequestParam String tailSL, @RequestParam String target, @RequestParam String slipage) throws Exception, KiteException {
        vwapRsiOiVolumeBuyBacktest.buy(day, tf, tailSL, target, slipage);

    }

    @Autowired
    BankNiftyOiSellingBackTest bankNiftyOiSellingBackTest;

    @GetMapping("/OiSelling")
    public void OiSelling(@RequestParam int day, @RequestParam String tf, @RequestParam String tailSL, @RequestParam String target, @RequestParam String slipage) throws Exception, KiteException {
        bankNiftyOiSellingBackTest.sell(day, tf, tailSL, target, slipage);

    }

    @GetMapping("/vwapSellTest")
    public void vwapSellTest(@RequestParam int day, @RequestParam String tf, @RequestParam String tailSL, @RequestParam String target, @RequestParam String slipage) throws Exception, KiteException {
        vwapRsiOiVolumeSelling.sell(day, tf, tailSL, target, slipage);

    }

    @GetMapping("/enableScheduler")
    public void enableScheduler(@RequestParam boolean isEnabled) throws Exception, KiteException {
        niftyVwapRsiOiVolumeBuy.isEnabled = isEnabled;

    }

    @GetMapping("/niftyvwapBuyTest")
    public void niftyvwapBuyTest(@RequestParam int day, @RequestParam String tf, @RequestParam String tailSL, @RequestParam String target, @RequestParam String slipage, @RequestParam boolean targetEnabled) throws Exception, KiteException {
        niftyVwapRsiOiVolumeBuyBacktest.buy(day, tf, tailSL, target, slipage, targetEnabled);
    }

    @GetMapping("/niftyVwapBuy")
    public void niftyVwapBuy() throws Exception, KiteException {
        niftyVwapRsiOiVolumeBuy.buy();

    }

    @GetMapping("/testRSI")
    public void testRSI() throws Exception, KiteException {
        double[] list = {44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64, 46.21, 46.25, 45.71, 46.45, 45.78, 45.35, 44.03, 44.18, 44.22, 44.57, 43.42, 42.66, 43.13};

        MathUtils.RSI rsi = new MathUtils.RSI(14);
        for (double a : list
        ) {
            rsi.compute(a);
        }

    }

    @GetMapping("/zerodhaBN")
    public void zerodhaBN() throws Exception {
        zerodhaAccount.generateAccessToken();
        instrumentService.getInstrument();
        zerodhaBankNiftyShortStraddle.zerodhaBankNifty();

    }
  /*  @GetMapping("/zerodhatrend")
    public void trendLive() throws Exception, KiteException {
        zerodhaAccount.generateAccessToken();
        instrumentService.getInstrument();
        zerodhaTrendScheduler.trendLive();

    }*/

    @GetMapping("/fundCheck")
    public void fundCheck() throws Exception {
        account.availableFund();
    }

    @GetMapping("/fyersToken")
    public void fyersToken() throws Exception {
        account.generateToken();
    }

   /* @GetMapping("/live")
    public void startORBLive() throws Exception {
        orbScheduler.ORBScheduler();
    }
    @GetMapping("/data15min")
    public void startORB15min() throws Exception {
        orbScheduler.ORB15MinDataScheduler();
    }*/


    // @GetMapping("/placeOrder")
    public void placeOrder() throws Exception {
        PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO("SBIN", Order.BUY, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, 1, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
        Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
        String response = transactionService.callAPI(request);
        System.out.println("buy response: " + response);
    }

    // @GetMapping("/emptyToken")
    public void emptyToken() throws Exception {
        account.emptyToken();
    }

    //  @GetMapping("/sellOrder")
    public void sellOrder() throws Exception {
        PlaceOrderRequestDTO placeOrderRequestDTO = fyerTransactionMapper.placeOrderRequestDTO("SBIN", Order.SELL, OrderType.MARKET_ORDER, ProductType.INTRADAY, Validity.DAY, 1, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
        Request request = transactionService.createPostPutDeleteRequest(HttpMethod.POST, orderPlaceURL, new Gson().toJson(placeOrderRequestDTO));
        String response = transactionService.callAPI(request);
        System.out.println("sell response: " + response);
    }

    @Autowired
    BankNiftyShortStraddleOIBacktest bankNiftyShortStraddleOI;

    @GetMapping("/trendTest")
    public void trendTest(@RequestParam int day, @RequestParam String[] stocks) throws Exception {
        int n = day;
        while (n >= 0) {
            LocalDate tradeDate = LocalDate.now().minusDays(n);
            bankNiftyShortStraddleOI.triggerNIFTYBANK(tradeDate);
            n--;
        }

    }

    @Autowired
    VwapRsiOiVolumeBuyBacktestTrueData vwapRsiOiVolumeBuyBacktestTrueData;

    @GetMapping("/trendTestV")
    public void trendTestV(@RequestParam int day, @RequestParam String tf, @RequestParam String tailSL, @RequestParam String target, @RequestParam String slipage) throws Exception, KiteException {
        int n = day;
        vwapRsiOiVolumeBuyBacktestTrueData.buy(day, tf, tailSL, target, slipage);

    }

    @Autowired
    BankNiftyDataLoad bankNiftyDataLoad;

    @GetMapping("/bankNiftyDataLoad")
    public void bankNiftyDataLoad() throws Exception, KiteException {

        bankNiftyDataLoad.loadData();

    }

    @GetMapping("/loadDataLatest")
    public void loadDataLatest() throws Exception, KiteException {

        bankNiftyDataLoad.loadDataLatest();

    }

    @Autowired
    BankNiftyShortStraddleOIBuyBacktest bankNiftyShortStraddleOIBuyBacktest;

    @GetMapping("/trendTestBuy")
    public void trendTestBuy(@RequestParam int day, @RequestParam String[] stocks) throws Exception {
        int n = day;
        while (n >= 0) {
            LocalDate tradeDate = LocalDate.now().minusDays(n);
            bankNiftyShortStraddleOIBuyBacktest.triggerNIFTYBANK(tradeDate);
            n--;
        }

    }

    @Autowired
    NiftyShortStraddleOI niftyShortStraddleOI;

    @GetMapping("/niftyStaddleTrend")
    public void niftyStaddleTrend(@RequestParam int day, @RequestParam String[] stocks) throws Exception {
        int n = day;
        while (n >= 0) {
            LocalDate tradeDate = LocalDate.now().minusDays(n);
            niftyShortStraddleOI.triggerNIFTY(tradeDate);
            n--;
        }

    }

    @Autowired
    BankNiftyShortStraddleOIDBBacktest bankNiftyShortStraddleOIDBBacktest;

    @GetMapping("/bankNiftyShortStraddleOIDB")
    public void bankNiftyShortStraddleOIDBBacktest(@RequestParam int day, @RequestParam String[] stocks) throws Exception {
        int n = day;
        while (n >= 0) {
            LocalDate tradeDate = LocalDate.now().minusDays(n);
            bankNiftyShortStraddleOIDBBacktest.triggerNIFTYBANK(tradeDate);
            n--;
        }

    }

    @Autowired
    BankNiftyShortStraddleOIDBBuyBacktest bankNiftyShortStraddleOIDBBuyBacktest;

    @GetMapping("/bankNiftyShortStraddleOIDBBuyBacktest")
    public void bankNiftyShortStraddleOIDBBuyBacktest(@RequestParam int day, @RequestParam String[] stocks) throws Exception {
        int n = day;
        while (n >= 0) {
            LocalDate tradeDate = LocalDate.now().minusDays(n);
            bankNiftyShortStraddleOIDBBuyBacktest.triggerNIFTYBANK(tradeDate);
            n--;
        }

    }

    @Autowired
    BankNiftyOptionRepository bankNiftyOptionRepository;

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

    @GetMapping("/optionStrikeData")
    public ResponseEntity<String> optionStrikeData(@RequestParam String tradeDate, @RequestParam String expDate, @RequestParam String strikeType) throws Exception {
        HttpHeaders responseHeaders = new HttpHeaders();
        SimpleDateFormat format1 = new SimpleDateFormat("dd-MM-yyyy");
        LocalDate expDateD = LocalDate.parse(expDate);
        LocalDate tradeDateD = LocalDate.parse(tradeDate);
        OptionCandleChartData optionCandleChartData = bankNiftyShortStraddleOIDBBacktest.straddleOI(tradeDateD, expDateD, strikeType);
        return new ResponseEntity<>(new Gson().toJson(optionCandleChartData), responseHeaders, HttpStatus.OK);
    }


    @GetMapping("/getIndexData")
    public ResponseEntity<String> getIndexData(@RequestParam String tradeDate, @RequestParam String indexName,int i) throws Exception {
        HttpHeaders responseHeaders = new HttpHeaders();
        DateTimeFormatter format1 =  DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate fromDate = LocalDate.parse(tradeDate,format1).minusDays(2);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDate tradeDateD = LocalDate.parse(tradeDate,format1);
        String optionCandleChartData = account.getBarHistory(indexName, fromDate, tradeDateD);
        return new ResponseEntity<>(new Gson().toJson(optionCandleChartData), responseHeaders, HttpStatus.OK);
    }

    /*
    @GetMapping("/crudeTest")
    public void crudeTest(@RequestParam int day) throws Exception {
        int n=day;
        Session session=historicWebsocket.session;
        if (session==null || !session.isOpen()){
            session = historicWebsocket.createHistoricWebSocket();
        }
        while(n>0) {
            LocalDate currentdate = LocalDate.now().minusDays(n);

            LocalDate finalDate = currentdate;
            DateTimeFormatter df = DateTimeFormatter.ofPattern("MMM");
            DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (currentdate.getDayOfMonth() >= 20) {
                finalDate = currentdate.plusMonths(1);
            } else {
                df.format(currentdate);
            }
            String payload = orbIntraday15minHistoricInput(df1.format(currentdate), "5min", "CRUDEOIL20"+df.format(finalDate).toUpperCase()+"FUT");

            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(payload);
                } else {
                    session = historicWebsocket.createHistoricWebSocket();
                    session.getBasicRemote().sendText(payload);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            n--;
        }

    }*/
    // @GetMapping("/trendTestReport")
    public void trendTestReport(@RequestParam String date) throws Exception {

        CSVReader csvReader = new CSVReader(new FileReader("/home/hasvanth/Downloads/trend.csv"));

    }

    /* @GetMapping("/trendScheduler")
     public void trendScheduler(@RequestParam String date) throws Exception {

        trendScheduler.trendScheduler();

     }*/
   /* @GetMapping("/historyHeartBeat")
    public void historyHeartBeat(@RequestParam String date) throws Exception {
        historicWebsocket.heartBeat();

    }*/
    @GetMapping("/telegramtest")
    public void telegramtest() throws Exception {
        sendMessage.sendToTelegram("System Cancelled SL HEROMOTOCORP", telegramToken);

    }

    /*  @GetMapping("/shortStraddleTest")
      public void shortStraddleTest() throws Exception {
          bankNiftyShortStraddle.getWeeklyExpiryOptionsDetails();

      }*/
    @GetMapping("/niftyshortStraddleLongTest")
    public void niftyshortStraddleLongTest() throws Exception {
        nIftyStraddleLongBackTest.niftyStraddleLongTest();

    }
   /* @GetMapping("/shortStraddleScheduleTest")
    public void shortStraddleScheduleTest() throws Exception {
        bankNiftyShortStraddle.shortStraddleTradeSchedule();

    }
*//*
    //use it for stock mock based historic test
    @GetMapping("/bankNIftyStraddleLongBackTest")
    public void bankNIftyStraddleLongBackTest() throws Exception {
        bankNIftyStraddleLongBackTest.bankNiftyStraddleLongTest();

    }
    @GetMapping("/summaryReport")
    public void summaryReport() throws Exception {
        bankNIftyStraddleLongBackTest.summaryReport();

    }*/

   /* @GetMapping("/eodTest")
    public void eodTest() throws Exception {
        System.out.println("eodTest");
        historicWebsocket.eodData();

    }*/
  /*  @GetMapping("/preOpenTest")
    public void preOpenTest() throws Exception {
        System.out.println("preOpenTest");
        historicWebsocket.preOpenSchedule();
    }*/

    /*  @GetMapping("/preOpenPopulate")
      public void preOpenPopulate() throws Exception {
          System.out.println("preOpenTest preOpenPopulate");
          historicWebsocket.preOpenPopulate();
      }*/
   /* @GetMapping("/shortStraddleBackTest")
    public void shortStraddleBackTest(@RequestParam int day,@RequestParam int toDay) throws Exception {
        while(day>toDay) {
            LocalDate currentdate = LocalDate.now().minusDays(day);
            DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            bankNiftyShortStraddle.getWeeklyExpiryOptionsDetailsBackTest(df1.format(currentdate));

            bankNiftyShortStraddle.shortStraddleTradeScheduleBacktest(df1.format(currentdate));
            Thread.sleep(2000);
            day--;
        }
    }*/
 /*   @GetMapping("/niftyShortStraddleBackTest")
    public void niftyShortStraddleBackTest(@RequestParam int day,@RequestParam int toDay) throws Exception {
        while(day>toDay) {
            LocalDate currentdate = LocalDate.now().minusDays(day);
            DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            niftyShortStraddle.getWeeklyExpiryOptionsDetailsBackTest(df1.format(currentdate));

            niftyShortStraddle.shortStraddleTradeScheduleBacktest(df1.format(currentdate));
            Thread.sleep(2000);
            day--;
        }
    }*/
  /*  @GetMapping("/shortStraddleScheduleBBTest")
    public void shortStraddleScheduleBackTest() throws Exception {
        bankNiftyShortStraddle.shortStraddleTradeSchedule();

    }*/
  /*  @GetMapping("/niftyshortStraddleScheduleBBTest")
    public void niftyShortStraddleScheduleBackTest() throws Exception {
        niftyShortStraddle.getWeeklyExpiryOptionsDetails();
        Thread.sleep(5000);
        niftyShortStraddle.shortStraddleTradeSchedule();

    }*/
    @GetMapping("/passwordValidityCheck")
    public void passwordValidityCheck() throws Exception {
        account.passwordValidityCheck();

    }

    @GetMapping("/getOrderStatus")
    public void getOrderStatus(@RequestParam String orderId) throws Exception {

        Request request = transactionService.createGetRequest(orderStatusAPIUrl, orderId);
        String response = transactionService.callAPI(request);
        System.out.println(response);
        //  String response ="{\"s\":\"ok\",\"message\":\"\",\"orderDetails\":{\"status\":2,\"symbol\":\"NSE:IGL-EQ\",\"qty\":49,\"orderNumStatus\":\"120082021161:2\",\"dqQtyRem\":0,\"orderDateTime\":\"20-Aug-2020 09:33:30\",\"orderValidity\":\"DAY\",\"fyToken\":\"101000000011262\",\"slNo\":13,\"message\":\"TRADE CONFIRMED\",\"segment\":\"E\",\"id\":\"120082021161\",\"stopPrice\":0.0,\"instrument\":\"EQUITY\",\"exchOrdId\":\"1100000001815348\",\"remainingQuantity\":0,\"filledQty\":49,\"limitPrice\":0.0,\"offlineOrder\":false,\"source\":\"ITS\",\"productType\":\"INTRADAY\",\"type\":2,\"side\":1,\"tradedPrice\":404.95,\"discloseQty\":0}}";
        OrderStatusResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OrderStatusResponseDTO.class);
        System.out.println(response);

    }

    @GetMapping("/getPositions")
    public void getPositions() throws Exception {
        String response = "{\"s\":\"ok\",\"netPositions\":[{\"crossCurrency\":\"N\",\"qty\":16,\"realized_profit\":0.0,\"id\":\"NSE:JPASSOCIAT-EQ-CNC\",\"unrealized_profit\":0.32,\"buyQty\":16,\"sellAvg\":0.0,\"sellQty\":0,\"buyAvg\":4.78,\"symbol\":\"NSE:JPASSOCIAT-EQ\",\"fyToken\":\"101000000011460\",\"slNo\":0,\"avgPrice\":4.78,\"segment\":\"E\",\"dummy\":\" \",\"rbiRefRate\":1.0,\"side\":1,\"netQty\":16,\"pl\":0.32,\"productType\":\"CNC\",\"netAvg\":4.78,\"qtyMulti_com\":1.0},{\"crossCurrency\":\"N\",\"qty\":90,\"realized_profit\":0.0,\"id\":\"NSE:RCOM-EQ-CNC\",\"unrealized_profit\":1.8,\"buyQty\":90,\"sellAvg\":0.0,\"sellQty\":0,\"buyAvg\":2.03,\"symbol\":\"NSE:RCOM-EQ\",\"fyToken\":\"101000000013187\",\"slNo\":1,\"avgPrice\":2.03,\"segment\":\"E\",\"dummy\":\" \",\"rbiRefRate\":1.0,\"side\":1,\"netQty\":90,\"pl\":1.8,\"productType\":\"CNC\",\"netAvg\":2.03,\"qtyMulti_com\":1.0}],message:\"\"}";
        System.out.println(response);
        //  String response ="{\"s\":\"ok\",\"message\":\"\",\"orderDetails\":{\"status\":2,\"symbol\":\"NSE:IGL-EQ\",\"qty\":49,\"orderNumStatus\":\"120082021161:2\",\"dqQtyRem\":0,\"orderDateTime\":\"20-Aug-2020 09:33:30\",\"orderValidity\":\"DAY\",\"fyToken\":\"101000000011262\",\"slNo\":13,\"message\":\"TRADE CONFIRMED\",\"segment\":\"E\",\"id\":\"120082021161\",\"stopPrice\":0.0,\"instrument\":\"EQUITY\",\"exchOrdId\":\"1100000001815348\",\"remainingQuantity\":0,\"filledQty\":49,\"limitPrice\":0.0,\"offlineOrder\":false,\"source\":\"ITS\",\"productType\":\"INTRADAY\",\"type\":2,\"side\":1,\"tradedPrice\":404.95,\"discloseQty\":0}}";
        OpenPositionsResponseDTO orderStatusResponseDTO = new Gson().fromJson(response, OpenPositionsResponseDTO.class);
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
        return new Gson().toJson(historicRequestDTO);
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

    @Autowired
    BankNiftyOptionSelling bankNiftyOptionSelling;

    @GetMapping("/testOptionSelling")
    public void testOptionSelling(@RequestParam int days, @RequestParam String trialPercent, @RequestParam boolean isPCREnabled) throws KiteException, Exception {
        bankNiftyOptionSelling.optionSelling(days, trialPercent, isPCREnabled);
    }

    @Autowired
    BankNiftyOptionSelling1 bankNiftyOptionSelling1;

    @GetMapping("/testOptionSelling1")
    public void testOptionSelling1(@RequestParam int days, @RequestParam String trialPercent, @RequestParam boolean isPCREnabled) throws KiteException, Exception {
        bankNiftyOptionSelling1.optionSelling(days, trialPercent, isPCREnabled);
    }

    @GetMapping("/getTrend")
    public void getTrend() throws KiteException, Exception {
        zerodhaTrendScheduler.trendScheduler();
    }

    @Autowired
    BinanceAccount binanceAccount;
    @GetMapping("/getBinanceCandle")
    public void getBinanceCandle() throws IOException {
        binanceAccount.binanceTest();
    }
    @GetMapping("/getBinanceAccount")
    public void getBinanceAccount() throws BinanceApiException {
        binanceAccount.binanceAccountInfo();
    }
    @Autowired
    BinanceTrendBacktest binanceTrend;
    @GetMapping("/binanceTrend")
    public void binanceTrend() throws Exception {
        binanceTrend.binanceTrend();
    }


    @Value("${binance.sathiyaseelanrhce.v11.secret}")
    private String binanceSecretKey;
    @Value("${binance.sathiyaseelanrhce.v11.apikey}")
    private String binanceApiKey;
    RestApiRequestImpl restApiRequest = new RestApiRequestImpl(binanceApiKey, binanceSecretKey, new RequestOptions());
    SyncRequestImpl syncRequest = new SyncRequestImpl(restApiRequest);

    @Autowired
    BinanceTrendBacktestNew binanceTrendNew;
    @GetMapping("/binanceTrendNew")
    public void binanceTrendNew(@RequestParam int days,@RequestParam String perGain) throws Exception {
        binanceTrendNew.binanceTrend(days,perGain);
    }
    @GetMapping("/binanceBullTrend")
    public void binanceBullTrend(@RequestParam int days,@RequestParam String perGain) throws Exception {
        binanceTrendNew.binanceBullTrend(days,perGain);
    }

    @Autowired
    CryptoRepository cryptoRepository;
    @GetMapping("/loadBHistory")
    public void loadBHistory(@RequestParam int days) throws Exception {
        List<CryptoFuturesEntity> cryptoFuturesEntities=cryptoRepository.findAll();

        for (ExchangeInfoEntry symbol : syncRequest.getExchangeInformation().getSymbols()) {
          /*  boolean ispresent=false;
            for (CryptoFuturesEntity cryptoFuturesEntitie: cryptoFuturesEntities){
               if( cryptoFuturesEntitie.getSymbol().equals(symbol.getSymbol())){
                   ispresent=true;
               }
            }

            if(!ispresent) {*/
                CryptoFuturesEntity cryptoFuturesEntity = new CryptoFuturesEntity();
                cryptoFuturesEntity.setSymbol(symbol.getSymbol());
                cryptoRepository.save(cryptoFuturesEntity);
                binanceTrendNew.loadHistory(days, symbol.getSymbol());
         //   }
        }

    }

    @GetMapping("/loadDayHistory")
    public void loadDayHistory(@RequestParam int days) throws Exception {
        List<CryptoFuturesEntity> cryptoFuturesEntities=cryptoRepository.findAll();

        for (ExchangeInfoEntry symbol : syncRequest.getExchangeInformation().getSymbols()) {
          /*  boolean ispresent=false;
            for (CryptoFuturesEntity cryptoFuturesEntitie: cryptoFuturesEntities){
               if( cryptoFuturesEntitie.getSymbol().equals(symbol.getSymbol())){
                   ispresent=true;
               }
            }

            if(!ispresent) {*/
            CryptoFuturesEntity cryptoFuturesEntity = new CryptoFuturesEntity();
            cryptoFuturesEntity.setSymbol(symbol.getSymbol());
            cryptoRepository.save(cryptoFuturesEntity);
            binanceTrendNew.loadDayHistory(days, symbol.getSymbol());
            //   }
        }

    }
    @Autowired
    BinanceTrendLive binanceTrendlive;
    @GetMapping("/loadDayHistoryTest")
    public void loadDayHistoryTest(@RequestParam int days) throws Exception {

        binanceTrendlive.loadDayHistory();
            //   }


    }
    @GetMapping("/binanceTrendTop")
    public void binanceTrendTop() throws Exception {
        binanceTrendNew.binanceTrendTop();
    }

        @GetMapping("/binanceTrendSell")
    public void binanceTrendSell() throws Exception {
        binanceTrendNew.binanceTrendSell();
    }

    @GetMapping("/binanceTrendRSI")
    public void binanceTrendRSI() throws Exception {
        binanceTrend.binanceRSITrendBacktest();
        binanceTrend.summaryReport();
    }

    @Autowired
    BinanceTrendBacktest binanceTrendBacktest;

    @GetMapping("/binanceVolumeTrend")
    public void binanceVolumeTrend() throws Exception {
        binanceTrend.binanceVolumeTrend();
    }
    @GetMapping("/binanceRSITrendBacktestShort")
    public void binanceRSITrendBacktestShort() throws Exception {
        binanceTrendBacktest.binanceRSITrendBacktestShort();
    }
    @GetMapping("/binanceRSITrendBacktest")
    public void binanceRSITrendBacktest() throws Exception {
        binanceTrendBacktest.binanceRSITrendBacktest();
    }

    @GetMapping("/binanceCloseAll")
    public void closeAll() {
        binanceTrendlive.closeAll();
    }
    @GetMapping("/binanceRSITrigger")
    public void binanceRSITrigger() throws BinanceApiException {
        binanceTrendlive.binanceRSITrendLive();
    }
    @GetMapping("/sendDocumentTest")
    public void testTelegram(){
        File file= new File("/home/hasvanth/Downloads/BANKNIFTY/2022/Feb/2022-02-24/34500PE.json");
//        sendMessage.sendDocumentToTelegram(file,"1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o","-713214125");

    }
}
