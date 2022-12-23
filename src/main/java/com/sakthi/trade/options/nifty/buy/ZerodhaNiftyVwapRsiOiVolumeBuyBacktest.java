
package com.sakthi.trade.options.nifty.buy;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.fyer.mapper.FyerTransactionMapper;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.TelegramMessenger;
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
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class ZerodhaNiftyVwapRsiOiVolumeBuyBacktest {
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
    TelegramMessenger sendMessage;
    @Autowired
    FyerTransactionMapper fyerTransactionMapper;
    @Value("${vwap.rsi.oi.volume.lot}")
    String lotSize;/*
    @Value("${vwap.rsi.oi.volume.sl.trail.percentage}")
    String trialPercent;*/

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    ExecutorService executorSer = Executors.newFixedThreadPool(5);
    SimpleDateFormat simpleDateFormatMi = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    static String[] suffixes =
            //    0     1     2     3     4     5     6     7     8     9
            {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    10    11    12    13    14    15    16    17    18    19
                    "th", "th", "th", "th", "th", "th", "th", "th", "th", "th",
                    //    20    21    22    23    24    25    26    27    28    29
                    "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th",
                    //    30    31
                    "th", "st"};

    public void buy(int day,String tf,String trialPercent,String multi) throws ParseException, KiteException, IOException {


        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -1);
        Date date = cal.getTime();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        //   String currentDate=format.format(date);
        //start:test
        Calendar calendar = Calendar.getInstance();
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday


        calendar.add(DAY_OF_MONTH, -6);
        Calendar calendarCurrentDate = Calendar.getInstance();
        calendarCurrentDate.add(DAY_OF_MONTH, -1);
        Date cdate = calendar.getTime();

        Map<String, TradeData> tradeMap = new HashMap<>();
        String startDate = format.format(cdate);
        String currentDate = format.format(date);
        // String currentDate = format.format(calendarCurrentDate.getTime());
        String nifty = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
        HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse(currentDate + " 09:15:00"), sdfformat.parse(sdfformat.format(date)), nifty, "5minute", false, false);
        //    HistoricalData historicalData=zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse("2021-01-05 09:00:00"),sdfformat.parse("2021-01-09 03:30:00"),niftyBank,"5minute",false,false);
            if (historicalData.dataArrayList.size() > 0) {
              //  System.out.println(nifty);
                Map<String, Date> dateMap = new HashMap<>();
                historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                    try {
                        Date openDatetime = sdf.parse(historicalData1.timeStamp);
                        String openDate = format.format(openDatetime);
                        Date closeDatetime = sdf.parse(historicalData1.timeStamp);
                        String closeDate = format.format(closeDatetime);


                        if (closeDate.equals(currentDate) && openDatetime.after(sdf.parse(openDate + "T09:14:00")) && closeDatetime.before(sdf.parse(closeDate + "T15:00:00")) && (dateMap.get("NEXT_CHECK") == null || closeDatetime.after(dateMap.get("NEXT_CHECK")))) {
                            int atmStrike = commonUtil.findATM((int) historicalData1.close);
                            Map<String, String> putITMs=zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike+50));
                            Map<String, String> callITMs=zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike-50));
                            Map<String, String> atmStrikes = zerodhaTransactionService.niftyWeeklyOptions.get(String.valueOf(atmStrike));
                            callITMs.entrySet().stream().filter(callIT->callIT.getKey().contains("CE")).findFirst().ifPresent(callITM->{
                                atmStrikes.put(callITM.getKey(),callITM.getValue());
                            });
                            putITMs.entrySet().stream().filter(putITM->putITM.getKey().contains("PE")).findFirst().ifPresent(putITM->{
                                atmStrikes.put(putITM.getKey(),putITM.getValue());
                            });

                            atmStrikes.entrySet().stream().forEach(strike -> {
                                try {

                                    String historicURL = "https://api.kite.trade/instruments/historical/" + strike.getValue() + "/3minute?from=" + startDate + "+09:00:00&to=" + simpleDateFormatMi.format(date) + ":00&oi=1";
                                    //     String historicURL = "https://api.kite.trade/instruments/historical/" + niftyBank + "/5minute?from=2021-01-05+09:00:00&to=2021-01-05+04:15:00";
                                    String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
                                    HistoricalDataExtended historicalDataOp = new HistoricalDataExtended();
                                    JSONObject json = new JSONObject(response);
                                    String status = json.getString("status");
                                    MathUtils.SMA sma20 = new MathUtils.SMA(20);
                                    MathUtils.SMA sma50 = new MathUtils.SMA(50);
                                    MathUtils.RSI rsi = new MathUtils.RSI(14);
                                    if (!status.equals("error")) {
                                        historicalDataOp.parseResponse(json);
                                        calculateVwap(historicalDataOp.dataArrayList);
                                        historicalDataOp.dataArrayList.stream().forEach(historicalDataExtended -> {
                                            double oi20 = sma20.compute(historicalDataExtended.oi);
                                            historicalDataExtended.setOima20(oi20);
                                            double vol50 = sma50.compute(historicalDataExtended.volume);
                                            historicalDataExtended.setVolumema50(vol50);
                                            double rsi14 = rsi.compute(historicalDataExtended.close);
                                            historicalDataExtended.setRsi(rsi14);

                                        });

                                    }


                                    historicalDataOp.dataArrayList.stream().forEach(historicalDataExtended -> {
                                        try {
                                            Date closTime = sdfformat.parse(historicalDataExtended.timeStamp);
                                            if (closeDatetime.equals(closTime)) {
                                                boolean vapCo = historicalDataExtended.close > historicalDataExtended.vwap;

                                                boolean volumeCo = historicalDataExtended.volume*1.5 > historicalDataExtended.volumema50;
                                                boolean oiCo = historicalDataExtended.oima20 > historicalDataExtended.oi;
                                                boolean rsiCo = historicalDataExtended.rsi > 60;
                                                if (rsiCo && volumeCo && oiCo && vapCo) {
                                                    TradeData tradeData = new TradeData();
                                                    tradeData.isOrderPlaced = true;
                                                    tradeData.setStockName(strike.getKey());
                                                    BigDecimal qty=new BigDecimal("20000").divide(new BigDecimal(historicalDataExtended.close).multiply(new BigDecimal("75")),0,RoundingMode.DOWN);
                                                    tradeData.setBuyPrice(new BigDecimal(historicalDataExtended.close));
                                                    BigDecimal slPoints = (tradeData.getBuyPrice().multiply(new BigDecimal(trialPercent))).divide(new BigDecimal(100)).setScale(0, BigDecimal.ROUND_DOWN);
                                                    BigDecimal slPrice = (tradeData.getBuyPrice().subtract(slPoints)).setScale(0, RoundingMode.HALF_UP);
                                                    tradeData.setSlTrialPoints(slPoints);
                                                    tradeData.setQty(qty.intValue()*75);
                                                    tradeData.setSlPrice(slPrice);
                                                    tradeData.isSlPlaced = true;
                                                    tradeData.setBuyTime(historicalDataExtended.timeStamp);
                                                    tradeMap.put(strike.getKey(), tradeData);
                                                  /*  try {
                                                        saveChart(strike, currentDate, historicalDataEx.dataArrayList);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }*/

                                                }
                                            }
                                            if (tradeMap.get(strike) != null && tradeMap.get(strike).isOrderPlaced && !tradeMap.get(strike).isExited && closTime.after(closeDatetime)) {

                                                TradeData tradeData = tradeMap.get(strike);
                                                if (new BigDecimal(historicalDataExtended.low).compareTo(tradeData.getSlPrice()) < 0) {
                                                    tradeData.isExited = true;
                                                    tradeData.setSellPrice(tradeData.getSlPrice());
                                                    tradeData.setSellTime(historicalDataExtended.timeStamp);
                                                    dateMap.put("NEXT_CHECK", sdfformat.parse(historicalDataExtended.timeStamp));
                                                    CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Nifty_Backtest/vwap_nifty_buy_zerodha_back_test_"+tf+"_"+trialPercent+"_pt"+multi+"_oi.csv", true));
                                                    String[] data = {openDate, tradeData.getStockName(), tradeData.getBuyPrice().setScale(0, RoundingMode.HALF_UP).toString(), tradeData.getSellPrice().setScale(0, RoundingMode.HALF_UP).toString(), (tradeData.getSellPrice().subtract(tradeData.getBuyPrice())).multiply(new BigDecimal(tradeData.getQty())).setScale(0, RoundingMode.HALF_UP).toString(),String.valueOf(tradeData.getQty()), tradeData.getBuyTime(), tradeData.getSellTime()};
                                                    csvWriter.writeNext(data);
                                                    csvWriter.flush();
                                                }
                                                else if (!tradeData.isExited) {
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

                                        } catch (ParseException | IOException e) {
                                            e.printStackTrace();
                                        }
                                    });


                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            stopWatch.stop();
            log.info("process completed in ms:" + stopWatch.getTotalTimeMillis());
            day++;
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

