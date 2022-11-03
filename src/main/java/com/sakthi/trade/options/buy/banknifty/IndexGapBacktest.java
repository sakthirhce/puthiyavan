
package com.sakthi.trade.options.buy.banknifty;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainCategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.*;

import static java.time.DayOfWeek.THURSDAY;
import static java.time.temporal.TemporalAdjusters.lastInMonth;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class IndexGapBacktest {
    @Autowired
    ZerodhaAccount zerodhaAccount;
    @Autowired
    CommonUtil commonUtil;
    @Value("${filepath.trend}")
    String trendPath;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${fyers.get.order.status.api}")
    String orderStatusAPIUrl;
    @Autowired
    TransactionService transactionService;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;

    @Autowired
    SendMessage sendMessage;
    @Autowired
    FyerTransactionMapper fyerTransactionMapper;
    @Value("${vwap.rsi.oi.volume.lot}")
    String lotSize;/*
    @Value("${vwap.rsi.oi.volume.sl.trail.percentage}")
    String trialPercent;*/

    @Value("${fyers.order.place.api}")
    String orderPlaceAPIUrl;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat dateTimeFormatT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    SimpleDateFormat monthformatter = new SimpleDateFormat("MMMM");
    SimpleDateFormat mmformatter = new SimpleDateFormat("MM");
    SimpleDateFormat yearformatter = new SimpleDateFormat("yyyy");
    SimpleDateFormat dayformatter = new SimpleDateFormat("dd");
    boolean testSch=false;

    static String[] suffixes =
            //    0     1     2     3     4     5     6     7     8     9
            {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    10    11    12    13    14    15    16    17    18    19
                    "th", "th", "th", "th", "th", "th", "th", "th", "th", "th",
                    //    20    21    22    23    24    25    26    27    28    29
                    "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    30    31
                    "th", "st"};

   /* public void testScheduler(boolean testFlag){

            testSch=testFlag;


    }

    @Scheduled(cron = "* * * * * *")
    public void testTry() throws InterruptedException {
        if (!testSch){
            Thread.sleep(1000);
            log.info(new Date().toString());
        }
    }*/

    public void buy(int day,String stockId) throws ParseException, KiteException, IOException, CsvValidationException {


        String[] lineS;
        //   String currentDate=format.format(date);
        //start:test
        Map<String,Map<String,TradeData>> optionData=new HashMap<>();
        CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/entry_324exit_916.csv"));
        while ((lineS = csvReader.readNext()) != null) {
            if(lineS[1]!="" && lineS[1]!=null) {
                try {
                    Map<String, TradeData> tradeDataMap = optionData.get(lineS[1]);
                    if (Optional.ofNullable(tradeDataMap).isPresent() && tradeDataMap.size() > 0) {
                        TradeData tradeData = new TradeData();
                        tradeData.setBuyPrice(new BigDecimal(lineS[4]));
                        tradeData.setSellPrice(new BigDecimal(lineS[12]));
                        tradeData.setProfitLoss(new BigDecimal(lineS[13]));
                        tradeDataMap.put(lineS[6], tradeData);
                    } else {
                        Map<String, TradeData> tradeDataMap1 = new HashMap<>();
                        TradeData tradeData = new TradeData();
                        tradeData.setBuyPrice(new BigDecimal(lineS[4]));
                        tradeData.setSellPrice(new BigDecimal(lineS[12]));
                        tradeData.setProfitLoss(new BigDecimal(lineS[13]));
                        tradeDataMap1.put(lineS[6], tradeData);
                        optionData.put(lineS[1], tradeDataMap1);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        System.out.println(new Gson().toJson(optionData));
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/index_gap_"+UUID.randomUUID()+".csv"));
        while (day<0) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            Calendar calendar = Calendar.getInstance();
            System.out.println(calendar.getFirstDayOfWeek());
            System.out.println(calendar.get(DAY_OF_WEEK));
            //TODO: add logic to determine wednesday exp date if thursday is trade holiday

            calendar.add(DAY_OF_MONTH, day);
            Calendar calendar1 = Calendar.getInstance();
            calendar1.add(DAY_OF_MONTH, day + 1);
            log.info("Date:" + dateFormat.format(calendar.getTime()));

            String startDate = dateFormat.format(calendar.getTime());
            if(calendar1.get(DAY_OF_WEEK)==7){
                calendar1.add(DAY_OF_MONTH, 2);
            }
            String startDate1 = dateFormat.format(calendar1.getTime());
            String currentDate = startDate;
            // String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            Map<String, TradeData> tradeMap = new HashMap<>();
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/day?from=" + currentDate + "+00:00:00&to=" + startDate + "+23:00:00";
            //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            // HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(currentDate + " 00:00:00"), dateTimeFormat.parse(startDate1 + " 00:00:00"), stockId, "day", false, false);
            System.out.println(new Gson().toJson(response));
            JSONObject json = new JSONObject(response);
            HistoricalData historicalData = new HistoricalData();
            historicalData.parseResponse(json);
            if (historicalData.dataArrayList.size() > 0) {
                HistoricalData dayCandle = historicalData.dataArrayList.get(0);
                double openPrice = dayCandle.open;
                double closePrice = dayCandle.close;
                double diff = closePrice - openPrice;
                if (historicalData.dataArrayList.size() > 0 && (diff >= 100 || diff <= -100)) {
                    // HistoricalData historicalDataIntra = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(currentDate + " 09:15:00"), dateTimeFormat.parse(currentDate + " 15:30:00"), stockId, "minute", false, false);
                    //    HistoricalData historicalData=zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse("2021-01-05 09:00:00"),sdfformat.parse("2021-01-09 03:30:00"),niftyBank,"5minute",false,false);
                    String historicURL1 = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDate + "+00:00:00&to=" + startDate1 + "+23:00:00";
                    //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-01+09:00:00&to=2021-01-01+11:15:00";
                    String response1 = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL1));
                    JSONObject json1 = new JSONObject(response1);
                    HistoricalData historicalDataIntra = new HistoricalData();
                    historicalDataIntra.parseResponse(json1);
                    if (historicalDataIntra.dataArrayList.size() > 0) {
                        TradeData tradeData = new TradeData();
                        if (diff > 100) {
                            tradeData.setEntryType("BUY");
                        } else {
                            tradeData.setEntryType("SELL");
                        }
                        historicalDataIntra.dataArrayList.stream().forEach(historicalData1 -> {
                            try {
                                Date openDatetime = dateTimeFormatT.parse(historicalData1.timeStamp);
                                String openDate = dateFormat.format(openDatetime);
                                Date closeDatetime = dateTimeFormatT.parse(historicalData1.timeStamp);
                                String closeDate = dateFormat.format(closeDatetime);

                                if (closeDate.equals(currentDate) && openDatetime.equals(dateTimeFormatT.parse(openDate + "T15:24:00")) && !tradeData.isOrderPlaced) {
                                    tradeData.isOrderPlaced=true;
                                    if (tradeData.getEntryType().equals("BUY")) {
                                        tradeData.setBuyPrice(new BigDecimal(historicalData1.close));
                                        tradeData.setBuyTradedPrice(new BigDecimal(historicalData1.close));
                                    } else {
                                        tradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                        tradeData.setSellTradedPrice(new BigDecimal(historicalData1.close));
                                    }
                                }
                                if(tradeData.isOrderPlaced && !tradeData.isExited) {
                                    if (closeDate.equals(startDate1) && openDatetime.equals(dateTimeFormatT.parse(startDate1 + "T09:15:00"))) {
                                        if (tradeData.getEntryType().equals("BUY")) {
                                            tradeData.setSellPrice(new BigDecimal(historicalData1.close));
                                        } else {
                                            tradeData.setBuyPrice(new BigDecimal(historicalData1.close));
                                        }
                                    }
                                    if (closeDate.equals(startDate1)){
                                        double pl =0;
                                        if (tradeData.getEntryType().equals("BUY")) {
                                            pl=(historicalData1.close - tradeData.getBuyTradedPrice().doubleValue())*25;
                                        }else {
                                            pl=( tradeData.getSellTradedPrice().doubleValue() - historicalData1.close)*25;
                                        }
                                        if(pl<-5000 && !tradeData.isExited){
                                            tradeData.isExited=true;
                                            if (tradeData.getEntryType().equals("BUY")) {
                                                tradeData.setSellTradedPrice(new BigDecimal(historicalData1.close));
                                            } else {
                                                tradeData.setBuyTradedPrice(new BigDecimal(historicalData1.close));
                                            }
                                        }
                                    if(openDatetime.equals(dateTimeFormatT.parse(startDate1 + "T15:24:00")) && !tradeData.isExited) {
                                        if (tradeData.getEntryType().equals("BUY")) {
                                            tradeData.setSellTradedPrice(new BigDecimal(historicalData1.close));
                                        } else {
                                            tradeData.setBuyTradedPrice(new BigDecimal(historicalData1.close));
                                        }
                                    }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        try {
                            Map<String,TradeData> optionMap=optionData.get(currentDate);
                            TradeData tradeData1;
                            String strikeType;
                            if(tradeData.getEntryType().equals("BUY")){
                                tradeData1=optionMap.get("CE");
                                strikeType="CE";
                            }else {
                                tradeData1=optionMap.get("PE");   strikeType="PE";
                            }
                            String[] data = {currentDate, tradeData.getEntryType(), tradeData.getBuyPrice().setScale(2,RoundingMode.HALF_EVEN).toString(), tradeData.getSellPrice().setScale(2,RoundingMode.HALF_EVEN).toString(), tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).multiply(new BigDecimal(25)).setScale(2, RoundingMode.HALF_EVEN).toString(), tradeData.getBuyTradedPrice().setScale(2,RoundingMode.HALF_EVEN).toString(), tradeData.getSellTradedPrice().setScale(2,RoundingMode.HALF_EVEN).toString(), tradeData.getSellTradedPrice().subtract(tradeData.getBuyTradedPrice()).multiply(new BigDecimal(25)).setScale(2, RoundingMode.HALF_EVEN).toString(),strikeType,tradeData1.getBuyPrice().setScale(2,RoundingMode.HALF_EVEN).toString(),tradeData1.getSellPrice().setScale(2,RoundingMode.HALF_EVEN).toString(),tradeData1.getProfitLoss().setScale(2,RoundingMode.HALF_EVEN).toString()};
                            csvWriter.writeNext(data);
                            try {
                                csvWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                stopWatch.stop();
                log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());

            }
            day++;
        }
    }

    public static Brokerage calculateBrokerage(TradeData tradeData, boolean isOptions,boolean isEquityIntraday, boolean isFutures,String slipage){
        Brokerage brokerage=new Brokerage();
        BigDecimal brokerC=new BigDecimal("0");
        if(isOptions){
            brokerC=new BigDecimal("40");
            brokerage.setBrokerCharge(brokerC);
        }else if(isEquityIntraday){
            brokerC=tradeData.getSellPrice().multiply(new BigDecimal("0.03")).multiply(new BigDecimal(tradeData.getQty()));
        }
        brokerage.setQty(tradeData.getQty());
        //STT
        BigDecimal stt=new BigDecimal("40");

        if(isOptions){
            stt=tradeData.getSellPrice().multiply(new BigDecimal("0.05")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));

        }else if(isEquityIntraday){
            stt=tradeData.getSellPrice().multiply(new BigDecimal("0.025")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));

        }

        brokerage.setStt(stt);
        //transaction charges
        BigDecimal transactionCharges=new BigDecimal("0");
        if(isOptions){
            transactionCharges=tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.053"));
        }else if(isEquityIntraday){
            transactionCharges= tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.00345"));

        }
        BigDecimal slippagePoints=new BigDecimal(slipage).divide(new BigDecimal("100"));
        BigDecimal buyslippageCost= tradeData.getSellPrice().multiply(slippagePoints).multiply(new BigDecimal(tradeData.getQty()));
        BigDecimal sellSlippageCost= tradeData.getBuyPrice().multiply(slippagePoints).multiply(new BigDecimal(tradeData.getQty()));
        brokerage.setSlipageCost(buyslippageCost.add(sellSlippageCost));
        brokerage.setTransactionCharges(transactionCharges);
        //GST: 18%
        BigDecimal gst=transactionCharges.add(brokerC).multiply(new BigDecimal("0.18"));
        brokerage.setGst(gst);
        BigDecimal stampDuty=new BigDecimal(.003).multiply(tradeData.getBuyPrice());
        brokerage.setGst(stampDuty);
        BigDecimal totalcharges=brokerC.add(stt).add(transactionCharges).add(gst).add(stampDuty);
        brokerage.setTotalCharges(totalcharges);
        return brokerage;
    }
    public HistoricalDataExtended mapCSVtoHistoric(CSVReader csvReader) {
        String[] lineS;
        HistoricalDataExtended historicalDataOp = new HistoricalDataExtended();
        try {
            int i=0;
            while ((lineS = csvReader.readNext()) != null) {
                if(i>0){
                HistoricalDataExtended historicalData = new HistoricalDataExtended();
                historicalData.timeStamp = lineS[0];
                historicalData.open = Double.valueOf(lineS[1]);
                historicalData.high = Double.valueOf(lineS[2]);
                historicalData.low = Double.valueOf(lineS[3]);
                historicalData.close = Double.valueOf(lineS[4]);
                historicalData.volume = Long.parseLong(lineS[5]);
                if (lineS.length > 6 && lineS[6]!=null && lineS[6].length() > 0 && lineS[6]!="") {
                    historicalData.oi = new Double(lineS[6]).longValue();
                }
                historicalDataOp.dataArrayList.add(historicalData);
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return historicalDataOp;

    }

    public void calculateVwap(List<HistoricalDataExtended> historicalDataExtendeds) {
        int i = 0;
        double pre_cumulativeTotal = 0;
        double pre_volume = 0;
        while (i < historicalDataExtendeds.size()) {
            HistoricalDataExtended historicalDataExtended = historicalDataExtendeds.get(i);
            if (i == 0) {

                double averagePrice = (historicalDataExtended.high + historicalDataExtended.low + historicalDataExtended.close) / 3;
                historicalDataExtendeds.get(i).vwap = averagePrice;
                pre_cumulativeTotal = averagePrice * historicalDataExtended.volume;
                pre_volume = historicalDataExtended.volume;
            } else {
                double averagePrice = (historicalDataExtended.high + historicalDataExtended.low + historicalDataExtended.close) / 3;

                double current_cumulativeTotal = averagePrice * historicalDataExtended.volume;
                double cumulativeTotal = current_cumulativeTotal + pre_cumulativeTotal;
                double vwap = cumulativeTotal / (historicalDataExtended.volume + pre_volume);
                pre_cumulativeTotal = cumulativeTotal;
                pre_volume = historicalDataExtended.volume + pre_volume;
                historicalDataExtendeds.get(i).vwap = vwap;
            }
            i++;
        }

    }

    public void saveChart(String name, String date, List<HistoricalDataExtended> historicalDataExtendedList) {
//https://github.com/Arction/lcjs-showcase-audio
        DateAxis domainAxis = new DateAxis("Date");
        OHLCDataset priceDataset = getPriceDataSet(name, date, historicalDataExtendedList);
        NumberAxis priceAxis = new NumberAxis("Price");
        CandlestickRenderer priceRenderer = new CandlestickRenderer();
        XYPlot pricePlot = new XYPlot(priceDataset, domainAxis, priceAxis, priceRenderer);
        priceRenderer.setSeriesPaint(0, Color.BLACK);
        priceRenderer.setDrawVolume(true);
        priceAxis.setAutoRangeIncludesZero(false);
        XYDataset otherDataSet = createRSIXYDataSet(name, date, historicalDataExtendedList);
        XYDataset otherDataSet1 = createOIXYDataSet(name, date, historicalDataExtendedList);
        NumberAxis rsiAxis = new NumberAxis("RSI");
        XYItemRenderer rsiRenderer = new XYLineAndShapeRenderer(true, false);
        rsiRenderer.setSeriesPaint(0, Color.blue);
        XYPlot rsiPlot = new XYPlot(otherDataSet, domainAxis, rsiAxis, rsiRenderer);
        NumberAxis OIAxis = new NumberAxis("OI");
        XYItemRenderer oiRenderer = new XYLineAndShapeRenderer(true, false);
        oiRenderer.setSeriesPaint(0, Color.blue);
        oiRenderer.setSeriesPaint(1, Color.red);
        XYPlot oiPlot = new XYPlot(otherDataSet1, domainAxis, OIAxis, oiRenderer);

        //create the plot
        CategoryPlot plot = new CategoryPlot();

//add the first dataset, and render as bar values
        CategoryDataset volumeDataSet = createVolumeDataSet(name, date, historicalDataExtendedList);
        XYDataset volumeMADataSet = createVolumeMADataSet(name, date, historicalDataExtendedList);
        CategoryItemRenderer renderer = new BarRenderer();
        plot.setDataset(0, volumeDataSet);
        plot.setRenderer(0, renderer);

//add the second dataset, render as lines

/*  CategoryItemRenderer renderer2 = new LineAndShapeRenderer();
        plot.setDataset(1, dataset);
        plot.setRenderer(1, renderer2);*/


        CombinedDomainXYPlot mainPlot = new CombinedDomainXYPlot(domainAxis);
        final CategoryAxis domainAxis1 = new CategoryAxis("Category");
        CombinedDomainCategoryPlot mainPlotC = new CombinedDomainCategoryPlot(domainAxis1);
        mainPlot.add(pricePlot);
        mainPlot.add(rsiPlot);
        mainPlot.add(oiPlot);
//       mainPlot.add(plot);

        JFreeChart chart = new JFreeChart(name, (Font) null, mainPlot, false);

        try {
            ChartUtilities.saveChartAsPNG(new File(trendPath + "/" + name+"_"+date + ".png"), chart, 1200, 600);
        } catch (Exception var22) {
            var22.printStackTrace();
        }
    }

    public OHLCDataset getPriceDataSet(String name, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        List<OHLCDataItem> dataItems = new ArrayList();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {
                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        if (cdate.equals(format.format(openDatetime))) {
                            double open = historicalDataExtended.open;
                            double high = historicalDataExtended.high;
                            double low = historicalDataExtended.low;
                            double close = historicalDataExtended.close;
                            double volume = historicalDataExtended.volume;
                            OHLCDataItem item = new OHLCDataItem(date, open, high, low, close, volume);
                            dataItems.add(item);
                        }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        Collections.reverse(dataItems);
        OHLCDataItem[] data = (OHLCDataItem[]) dataItems.toArray(new OHLCDataItem[dataItems.size()]);
        return new DefaultOHLCDataset(name, data);

    }

    public XYDataset createRSIXYDataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {

        TimeSeries s1 = new TimeSeries("RSI", Minute.class);


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.rsi);

                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }

    public XYDataset createOIXYDataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.oi);
                            s2.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.oima20);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        dataset1.addSeries(s2);
        return dataset1;
    }

    public CategoryDataset createVolumeDataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "Volume";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdf.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        if (cdate.equals(format.format(openDatetime))) {
                            //      s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(),day,month,year), historicalDataExtended.volume);
                            dataset.addValue(historicalDataExtended.volume, series1, date);
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset;
    }

    public XYDataset createVolumeMADataSet(String stockSymbol, String cdate, List<HistoricalDataExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = sdfformat.parse(historicalDataExtended.timeStamp);
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                       // if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.volumema50);
                       // }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }
}

