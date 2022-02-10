
package com.sakthi.trade.trend;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
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
public class StockVwapRsiOiVolumeBuyBacktest {
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

    static String[] suffixes =
            //    0     1     2     3     4     5     6     7     8     9
            {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    10    11    12    13    14    15    16    17    18    19
                    "th", "th", "th", "th", "th", "th", "th", "th", "th", "th",
                    //    20    21    22    23    24    25    26    27    28    29
                    "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    30    31
                    "th", "st"};

    public void buy(int day,String tf,String trialPercent,String multi, String slipage) throws ParseException, KiteException, IOException {



        //   String currentDate=format.format(date);
        //start:test
        while (day<0) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            Calendar calendar = Calendar.getInstance();
            System.out.println(calendar.getFirstDayOfWeek());
            System.out.println(calendar.get(DAY_OF_WEEK));

            calendar.add(DAY_OF_MONTH, day);
            log.info("Date:"+dateFormat.format(calendar.getTime()));
            Calendar cthurs = Calendar.getInstance();
            cthurs.add(DAY_OF_MONTH, day);

            Date cdate = calendar.getTime();

            String currentDate = dateFormat.format(cdate);
           String stock = zerodhaTransactionService.lsSymbols.get("TATAMOTORS");
          //  System.out.println(nifty);
            Map<String, TradeData> tradeMap = new HashMap<>();
            String interval;
            if(tf.equals("5min")){
                interval="5minute";
            }else
            {
                interval="3minute";
            }
            HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(currentDate + " 09:15:00"), dateTimeFormat.parse(currentDate + " 15:15:00"), stock, interval, false, false);
            //    HistoricalData historicalData=zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse("2021-01-05 09:00:00"),sdfformat.parse("2021-01-09 03:30:00"),niftyBank,"5minute",false,false);
            if (historicalData.dataArrayList.size() > 0) {
            HistoricalDataExtended historicalDataEx=new Gson().fromJson(new Gson().toJson(historicalData),HistoricalDataExtended.class);
            MathUtils.SMA sma50 = new MathUtils.SMA(50);
            MathUtils.RSI rsi = new MathUtils.RSI(14);
            CommonUtil.calculateVwap(historicalDataEx.dataArrayList);
            historicalDataEx.dataArrayList.stream().forEach(historicalDataExtended -> {
                double vol50 = sma50.compute(historicalDataExtended.volume);
                historicalDataExtended.setVolumema50(vol50);
                double rsi14 = rsi.compute(historicalDataExtended.close);
                historicalDataExtended.setRsi(rsi14);

            });

              //  System.out.println(nifty);
                Map<String, Date> dateMap = new HashMap<>();
                historicalDataEx.dataArrayList.stream().forEach(historicalDataExtended -> {
                    try {
                        Date openDatetime = dateTimeFormatT.parse(historicalDataExtended.timeStamp);
                        String openDate = dateFormat.format(openDatetime);
                        Date closeDatetime = dateTimeFormatT.parse(historicalDataExtended.timeStamp);
                        String closeDate = dateFormat.format(closeDatetime);
                        Date closTime = dateTimeFormat.parse(historicalDataExtended.timeStamp);
                        boolean vapCo = historicalDataExtended.close > historicalDataExtended.vwap;
                        boolean volumeCo = historicalDataExtended.volume * 1.5 > historicalDataExtended.volumema50;
                        boolean rsiCo = historicalDataExtended.rsi > 60;
                        if (closeDate.equals(currentDate) && openDatetime.after(dateTimeFormatT.parse(openDate + "T09:14:00")) && closeDatetime.before(dateTimeFormatT.parse(closeDate + "T15:00:00")) && (dateMap.get("NEXT_CHECK") == null || (closeDatetime.equals(dateMap.get("NEXT_CHECK"))) || (closeDatetime.after(dateMap.get("NEXT_CHECK"))))) {

                            if (closeDatetime.equals(closTime)) {

                                if (rsiCo && volumeCo && vapCo) {
                                    TradeData tradeData = new TradeData();
                                    tradeData.isOrderPlaced = true;
                                    tradeData.setStockName(stock);
                                    BigDecimal qty = new BigDecimal("20000").divide(new BigDecimal(historicalDataExtended.close).multiply(new BigDecimal("75")), 0, RoundingMode.DOWN);
                                    tradeData.setBuyPrice(new BigDecimal(historicalDataExtended.close));
                                    BigDecimal slPoints = (tradeData.getBuyPrice().multiply(new BigDecimal(trialPercent))).divide(new BigDecimal(100)).setScale(0, BigDecimal.ROUND_DOWN);
                                    BigDecimal slPrice = (tradeData.getBuyPrice().subtract(slPoints)).setScale(0, RoundingMode.HALF_UP);
                                    tradeData.setSlTrialPoints(slPoints);
                                    tradeData.setQty(qty.intValue() * 75);
                                    tradeData.setSlPrice(slPrice);
                                    tradeData.isSlPlaced = true;
                                    tradeData.setBuyTime(historicalDataExtended.timeStamp);
                                    tradeMap.put(stock, tradeData);
                                                  /*  try {
                                                        saveChart(strike, currentDate, historicalDataEx.dataArrayList);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }*/

                                }
                            }
                            if (tradeMap.get(stock) != null && tradeMap.get(stock).isOrderPlaced && !tradeMap.get(stock).isExited && closTime.after(closeDatetime)) {

                                TradeData tradeData = tradeMap.get(stock);
                                String endTime;
                                if (tf.equals("3min")) {
                                    endTime = " 15:09:00";
                                } else {
                                    endTime = " 15:05:00";
                                }
                                if (!tradeData.isExited && closTime.equals(dateTimeFormat.parse(dateFormat.format(closTime) + endTime))) {
                                    tradeData.isExited = true;
                                    tradeData.setSellPrice(new BigDecimal(historicalDataExtended.close));
                                    tradeData.setSellTime(historicalDataExtended.timeStamp);
                                    dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                    Brokerage brokerage = calculateBrokerage(tradeData, true, false, false, slipage);
                                    CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Nifty_Backtest/vwap_nifty_buy_back_vwap_oi_" + tf + "_" + trialPercent + "_pt" + multi + "_" + slipage + "_oi.csv", true));
                                    BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                    BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                    String[] data = {openDate, tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime()};
                                    csvWriter.writeNext(data);
                                    csvWriter.flush();
                                }
                                if (new BigDecimal(historicalDataExtended.low).compareTo(tradeData.getSlPrice()) < 0) {
                                    boolean slExit = rsiCo && volumeCo && vapCo;
                                    boolean revisedSL = false;
                                                 /* if (slExit && tradeData.getStopLossCount()==0) {
                                                        BigDecimal newSL=tradeData.getSlPrice().subtract(tradeData.getSlTrialPoints());
                                                        log.info("vwap still up new SL:"+newSL.toString());
                                                        tradeData.setSlPrice(newSL);
                                                      revisedSL=true;
                                                        tradeData.setStopLossCount(1);
                                                    }
                                                    if((!slExit && tradeData.getStopLossCount()==0) || (tradeData.getStopLossCount()==1 && !revisedSL)) {*/
                                    tradeData.isExited = true;
                                    tradeData.setSellPrice(tradeData.getSlPrice());
                                    tradeData.setSellTime(historicalDataExtended.timeStamp);
                                    dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                    Brokerage brokerage = calculateBrokerage(tradeData, true, false, false, slipage);
                                    CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Nifty_Backtest/vwap_nifty_buy_back_vwap_oi_" + tf + "_" + trialPercent + "_pt" + multi + "_" + slipage + "_oi.csv", true));

                                    BigDecimal profitLoss = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP);
                                    BigDecimal profitLossAfterChargeSlippage = (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).subtract(brokerage.getTotalCharges()).subtract(brokerage.getSlipageCost()).setScale(0, RoundingMode.HALF_UP);
                                    String[] data = {openDate, tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), String.valueOf(tradeData.getQty()), String.valueOf(brokerage.getTotalCharges()), String.valueOf(brokerage.getSlipageCost()), String.valueOf(profitLoss), String.valueOf(profitLossAfterChargeSlippage), tradeData.getBuyTime(), tradeData.getSellTime()};
                                    csvWriter.writeNext(data);
                                    csvWriter.flush();
                                    //}
                                } else if (!tradeData.isExited) {
                                    BigDecimal diff = new BigDecimal(historicalDataExtended.high).subtract(tradeData.getBuyPrice());
                                    if (diff.compareTo(tradeData.getSlTrialPoints()) > 0) {
                                        BigDecimal mod = (diff.divide(tradeData.getSlTrialPoints(), 0, BigDecimal.ROUND_DOWN));
                                        BigDecimal newSL = (tradeData.getBuyPrice().subtract(tradeData.getSlTrialPoints())).add(mod.multiply(tradeData.getSlTrialPoints())).setScale(0, RoundingMode.HALF_UP);
                                        if (newSL.compareTo(tradeData.getSlPrice()) > 0) {
                                            tradeData.setSlPrice(newSL);
                                        }
                                    }
                                }

                                                /*   BigDecimal newSL = new BigDecimal(historicalDataExtended.high).subtract(tradeData.getSlTrialPoints());
                                                    if (newSL.compareTo(tradeData.getBuyPrice()) > 0 && newSL.compareTo(tradeData.getSlPrice())>0) {
                                                            tradeData.setSlPrice(newSL);
                                                        }*/


                            }

                        }
                    } catch (IOException | ParseException e) {
                        e.printStackTrace();
                    }
                });



                        }
            stopWatch.stop();
            log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());
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

