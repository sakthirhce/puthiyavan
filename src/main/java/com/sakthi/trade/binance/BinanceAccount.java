
package com.sakthi.trade.binance;

import com.binance.client.RequestOptions;
import com.binance.client.impl.RestApiRequestImpl;
import com.binance.client.impl.SyncRequestImpl;
import com.binance.client.model.enums.CandlestickInterval;
import com.binance.client.model.market.Candlestick;
import com.binance.client.model.market.ExchangeInformation;
import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.sakthi.trade.binance.models.CandlestickExtended;
import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.TradeDataCrypto;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
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
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
@Slf4j
public class      BinanceAccount {

    @Value("${filepath.trend}")
    String trendPath;
    @Value("${binance.sathiyaseelanrhce.v11.secret}")
    private String binanceSecretKey;
    @Value("${binance.sathiyaseelanrhce.v11.apikey}")
    private String binanceApiKey;
    RestApiRequestImpl restApiRequest=new RestApiRequestImpl(binanceApiKey,binanceSecretKey,new RequestOptions());
    SyncRequestImpl syncRequest=new SyncRequestImpl(restApiRequest);


    public static String encode(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
    }

    public String binanceAccountInfo() throws BinanceApiException {
      BinanceRequest binanceRequest=new BinanceRequest("https://fapi.binance.com/fapi/v2/account");
        binanceRequest.sign(binanceApiKey,binanceSecretKey,null);
        binanceRequest.read();
        System.out.println(binanceRequest.lastResponse);
        log.info("Binance Account response:"+ binanceRequest.lastResponse);
        return binanceRequest.lastResponse;
    }
    public void binanceTest() throws IOException {
        ExchangeInformation exchn=syncRequest.getExchangeInformation();
        CSVWriter csvWriter = new CSVWriter(new FileWriter(trendPath + "/Cypto/test1.csv", true));
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z");
        System.out.println("ldt " + LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        System.out.println("ctm " + System.currentTimeMillis());
        LocalDate today = LocalDate.now( ZoneId.systemDefault()  ) ;
        ZonedDateTime zdtStart = today.atStartOfDay( ZoneId.systemDefault() ) ;
        ZonedDateTime zdtStop = today.plusDays( 1 ).atStartOfDay( ZoneId.systemDefault() ) ;
        System.out.println(zdtStart.toInstant().toEpochMilli() + ":" + zdtStop.toInstant().toEpochMilli());
        exchn.getSymbols().stream().forEach( symbols-> {
            List<CandlestickExtended> candlestickListEx=new ArrayList<>();
         //   System.out.println(symbols.getSymbol());
            if(symbols.getSymbol().contains("USDT")) {
                List<Candlestick> candlestickList = syncRequest.getCandlestick(symbols.getSymbol(), CandlestickInterval.FIVE_MINUTES, null, null, 500);
              //  System.out.println(" " + symbols.getSymbol() + ":" + new Gson().toJson(candlestickList));
                MathUtils.SMA smaVolume20 = new MathUtils.SMA(20);
                MathUtils.SMA sma50 = new MathUtils.SMA(50);
                MathUtils.RSI rsi = new MathUtils.RSI(6);
                TradeDataCrypto tradeData =new TradeDataCrypto();
                AtomicInteger count=new AtomicInteger();
                candlestickList.stream().forEach(candlestick -> {

                   // System.out.println(" Open Time:"+simple.format(opentime)+" Close Time:"+simple.format(closetime)+" Open:"+candlestick.getOpen()+" Close:"+candlestick.getClose()+" High:"+candlestick.getHigh()+" Low:"+candlestick.getLow()+" Volume:"+candlestick.getVolume());
                   CandlestickExtended candlestickExtended=new Gson().fromJson(new Gson().toJson(candlestick),CandlestickExtended.class);
                    candlestickExtended.setRsi(rsi.compute(candlestickExtended.getClose().doubleValue()));
                    candlestickExtended.setVolumema20(smaVolume20.compute(candlestickExtended.getVolume().doubleValue()));
                    candlestickListEx.add(candlestickExtended);
                    BigDecimal percentMove=MathUtils.percentageMove(candlestickExtended.getOpen(),candlestickExtended.getClose());
                 /*   if(percentMove.compareTo(new BigDecimal(2))>0){
                        if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(4)))>0){
                         if(!tradeData.isOrderPlaced){
                             tradeData.isOrderPlaced=true;
                             tradeData.setStockName(symbols.getSymbol());
                             int qty= new BigDecimal(100).divide(candlestickExtended.getClose(),RoundingMode.HALF_EVEN).intValue();
                             tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                             tradeData.setQty(qty);
                             tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                             double slPoints = (tradeData.getBuyPrice()*5)/100;
                             double slPrice = tradeData.getBuyPrice()-slPoints;
                             tradeData.setSlPrice(slPrice);
                             tradeData.setSlTrialPoints(slPoints);
                         }
                        }
                    }
                    if(tradeData.isOrderPlaced) {
                        if (!tradeData.isExited) {
                            if (candlestickExtended.getLow().doubleValue() < tradeData.getSlPrice()) {
                                tradeData.isExited = true;
                                tradeData.setSellPrice(tradeData.getSlPrice());
                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));

                                //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                double diff = candlestickExtended.getClose().doubleValue() - tradeData.getBuyPrice();
                                if (diff > tradeData.getSlTrialPoints()) {
                                    double mod = (diff / tradeData.getSlTrialPoints());
                                    double newSL = (tradeData.getBuyPrice() - tradeData.getSlTrialPoints()) + (mod * (tradeData.getSlTrialPoints()));
                                    if (newSL > tradeData.getSlPrice()) {
                                        tradeData.setSlPrice(newSL);
                                    }
                                }
                            }
                        }
                    }*/

                    count.getAndAdd(1);
                    if(candlestickExtended.getRsi()<25 && count.get()>20){
                        //if(candlestickExtended.getVolume().compareTo(new BigDecimal(candlestickExtended.getVolumema20()).multiply(new BigDecimal(2)))>0){
                            if(!tradeData.isOrderPlaced){
                                tradeData.isOrderPlaced=true;
                                tradeData.setStockName(symbols.getSymbol());
                                double qty= 100/candlestickExtended.getClose().doubleValue();
                                tradeData.setBuyPrice(candlestickExtended.getClose().doubleValue());
                                tradeData.setQty(qty);
                                tradeData.setBuyTime(simple.format(candlestickExtended.getCloseTime()));
                            }
                      //  }
                    }
                    if (tradeData.isOrderPlaced) {
                        double percentMoveAfterTrade = MathUtils.percentageMove(tradeData.getBuyPrice(), candlestickExtended.getClose().doubleValue());
                        if (candlestickExtended.getRsi() > 60 || percentMoveAfterTrade>5) {
                            if (tradeData.isOrderPlaced && !tradeData.isExited) {
                                tradeData.setSellPrice(candlestickExtended.getClose().doubleValue());
                                tradeData.setSellTime(simple.format(candlestickExtended.getCloseTime()));
                                tradeData.isExited = true;
                                tradeData.isOrderPlaced = false;
                                //  dateMap.put("NEXT_CHECK", dateTimeFormat.parse(historicalDataExtended.timeStamp));
                                double profitLoss = (tradeData.getSellPrice() - tradeData.getBuyPrice()) * tradeData.getQty();
                                String[] data = {tradeData.getStockName(), String.valueOf(tradeData.getBuyPrice()), String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()), String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                                System.out.println(Arrays.toString(data));
                                csvWriter.writeNext(data);
                                try {
                                    csvWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }
                        }
                    }

                });
                //  System.out.println(new Gson().toJson(candlestickListEx));
            Date date=new Date();
            SimpleDateFormat simpleDateFormat=new SimpleDateFormat("dd-MM-yyyy");
                saveChart(symbols.getSymbol(),simpleDateFormat.format(date),candlestickListEx);
            CandlestickExtended lastCandle = candlestickListEx.get(candlestickListEx.size()-1);

        //    System.out.println("Symbol:"+symbols.getSymbol()+" RSI:"+lastCandle.getRsi()+" Open Time:"+simple.format(lastCandle.getOpenTime())+" Close Time:"+simple.format(lastCandle.getCloseTime())+" Open:"+lastCandle.getOpen()+" Close:"+lastCandle.getClose()+" High:"+lastCandle.getHigh()+" Low:"+lastCandle.getLow()+" Volume:"+lastCandle.getVolume());
                if(tradeData.isOrderPlaced && !tradeData.isExited){
                    tradeData.setSellPrice(lastCandle.getClose().doubleValue());
                    tradeData.setSellTime(simple.format(lastCandle.getCloseTime()));
                    double profitLoss=(tradeData.getSellPrice()-tradeData.getBuyPrice())*tradeData.getQty();
                    String[] data = {tradeData.getStockName(),  String.valueOf(tradeData.getBuyPrice()),  String.valueOf(tradeData.getSellPrice()), String.valueOf(tradeData.getQty()),String.valueOf(profitLoss), tradeData.getBuyTime(), tradeData.getSellTime()};
                    System.out.println(Arrays.toString(data));
                    csvWriter.writeNext(data);
                    try {
                        csvWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }

        });

    }
    public void saveChart(String name, String date, List<CandlestickExtended> historicalDataExtendedList) {
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

    public XYDataset createVolumeMADataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                        //if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.volumema50);
                     //   }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }
    public OHLCDataset getPriceDataSet(String name, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        List<OHLCDataItem> dataItems = new ArrayList();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {
                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                    //    System.out.println(sdfformat.format(openDatetime));
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                   //     if (cdate.equals(format.format(openDatetime))) {
                            double open = historicalDataExtended.getOpen().doubleValue();
                            double high = historicalDataExtended.getHigh().doubleValue();
                            double low = historicalDataExtended.getLow().doubleValue();
                            double close = historicalDataExtended.getClose().doubleValue();
                            double volume = historicalDataExtended.getVolume().doubleValue();
                            OHLCDataItem item = new OHLCDataItem(date, open, high, low, close, volume);
                            dataItems.add(item);
                    //    }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        Collections.reverse(dataItems);
        OHLCDataItem[] data = (OHLCDataItem[]) dataItems.toArray(new OHLCDataItem[dataItems.size()]);
        return new DefaultOHLCDataset(name, data);

    }

    public XYDataset createRSIXYDataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {

        TimeSeries s1 = new TimeSeries("RSI", Minute.class);


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                     //   if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.rsi);

                     //   }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset1;
    }

    public XYDataset createOIXYDataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "KLINE";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                      //  if (cdate.equals(format.format(openDatetime))) {
                            s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.volumema20);
                            s2.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(), day, month, year), historicalDataExtended.getVolume());
                      //  }
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

    public CategoryDataset createVolumeDataSet(String stockSymbol, String cdate, List<CandlestickExtended> historicalDataExtendedList) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "Volume";
        String series2 = "DLINE";
        TimeSeries s1 = new TimeSeries("OI", Minute.class);
        TimeSeries s2 = new TimeSeries("OIMA20", Minute.class);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfformat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        historicalDataExtendedList.stream().forEach(historicalDataExtended -> {
                    try {


                        Date openDatetime = new Date(historicalDataExtended.getOpenTime());
                        Date date = sdfformat.parse(sdfformat.format(openDatetime));
                        Calendar calendar = new GregorianCalendar();
                        calendar.setTime(date);
                        int year = calendar.get(Calendar.YEAR);
//Add one to month {0 - 11}
                        int month = calendar.get(Calendar.MONTH) + 1;
                        int day = calendar.get(Calendar.DAY_OF_MONTH);
                     //   if (cdate.equals(format.format(openDatetime))) {
                            //      s1.add(new Minute(openDatetime.getMinutes(), openDatetime.getHours(),day,month,year), historicalDataExtended.volume);
                            dataset.addValue(historicalDataExtended.getVolume(), series1, date);
                      //  }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
        );
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        dataset1.addSeries(s1);
        return dataset;
    }
}

