package com.sakthi.trade.service;

import com.google.gson.Gson;
import com.sakthi.trade.DataResampler;
import com.sakthi.trade.entity.IndicatorData;
import com.sakthi.trade.repo.IndicatorDataRepo;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import com.zerodhatech.models.HistoricalData;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Component
public class TradeEngineCleanUp {
    @Autowired
    IndicatorDataRepo indicatorDataRepo;
    List<HistoricalDataExtended> dataArrayList3MHistory = new ArrayList<>();
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    public TransactionService transactionService;
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Autowired
    TradingStrategyAndTradeData tradingStrategyAndTradeData;
    public void TableToImage() {
        String[] columnNames = {"Column 1", "Column 2", "Column 3"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(model);

        tradingStrategyAndTradeData.openTrade.forEach((key, value) -> value.forEach(tradeData -> {
            String qty = null;
            String buyPrice = null;
            String sellPrice = null;
            if (tradeData.getQty() > 0) {
                qty = String.valueOf(tradeData.getQty());
            }
            if (tradeData.getBuyPrice() != null) {
                buyPrice = tradeData.getBuyPrice().toString();
            }
            if (tradeData.getSellPrice() != null) {
                sellPrice = tradeData.getSellPrice().toString();
            }
            String[] newRow = {tradeData.getTradeStrategy().getTradeStrategyKey(), tradeData.getStockName(), qty, buyPrice,
                    sellPrice};
            model.addRow(newRow);
        }));

        // Render to BufferedImage
        table.setPreferredScrollableViewportSize(table.getPreferredSize());
        BufferedImage bufferedImage = new BufferedImage(table.getWidth(), table.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        table.printAll(g2d);
        g2d.dispose();

        // Save as PNG
        try {
            ImageIO.write(bufferedImage, "png", new File("/home/hasvanth/pl.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public List<HistoricalDataExtended> mapBBSData(List<IndicatorData> indicatorDataList) {
        List<HistoricalDataExtended> dataArrayList3M = new ArrayList<>();
        indicatorDataList.forEach(indicatorData -> {
            HistoricalDataExtended historicalDataExtended = new HistoricalDataExtended();
            historicalDataExtended.open = indicatorData.getOpen().doubleValue();
            historicalDataExtended.high = indicatorData.getHigh().doubleValue();
            historicalDataExtended.low = indicatorData.getLow().doubleValue();
            historicalDataExtended.close = indicatorData.getClose().doubleValue();
            historicalDataExtended.sma = indicatorData.getBbSma().doubleValue();
            historicalDataExtended.bolingerLowerBand = indicatorData.getBbLowerband().doubleValue();
            historicalDataExtended.bolingerUpperBand = indicatorData.getBbUpperband().doubleValue();
            historicalDataExtended.timeStamp = tradingStrategyAndTradeData.candleDateTimeFormat.format(indicatorData.getCandleTime().getTime());
            dataArrayList3M.add(historicalDataExtended);
        });
        return dataArrayList3M;
    }



    //@Scheduled(cron = "${tradeEngine.load.bbs.update}")
    public void bbsUpdate() throws ParseException {
        Date date = new Date();
        System.out.println("started bbs update");
        //      MDC.put("run_time",candleDateTimeFormat.format(date));
        String currentDateStr = tradingStrategyAndTradeData.dateFormat.format(date);
        List<HistoricalData> dataArrayList = new ArrayList();
        List<IndicatorData> indicatorDataList = indicatorDataRepo.getLast20IndicatorData();
        List<HistoricalDataExtended> dataArrayList3MHistory = mapBBSData(indicatorDataList);
        IndicatorData lastCandle = indicatorDataList.get(indicatorDataList.size() - 1);
        int j = lastCandle.getIndicatorDataKey() + 1;
        try {

            String stockId = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/minute?from=" + currentDateStr + "+09:15:00&to=" + currentDateStr + "+15:30:00";
            System.out.println(historicURL);
            String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL));
            System.out.println(response);
            HistoricalData historicalData = new HistoricalData();
            JSONObject json = new JSONObject(response);
            String status = json.getString("status");
            if (!status.equals("error")) {
                historicalData.parseResponse(json);
                int i = 0;
                dataArrayList.addAll(historicalData.dataArrayList);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<HistoricalDataExtended> dataArrayList3M = new ArrayList<>();
        dataArrayList3M.addAll(dataArrayList3MHistory);
        List<HistoricalDataExtended> dataArrayList3MRecentTemp = DataResampler.resampleOHLCData(dataArrayList, 3);
        dataArrayList3M.addAll(dataArrayList3MRecentTemp);
        LinkedList<Double> closingPrices = new LinkedList<>();
        int i = 0;
        while (i < dataArrayList3M.size()) {
            HistoricalDataExtended historicalDataExtended = dataArrayList3M.get(i);
            closingPrices.add(historicalDataExtended.getClose());
            if (closingPrices.size() > 20) {
                closingPrices.removeFirst();
            }
            if (i >= 20 - 1 && historicalDataExtended.sma == 0) {
                double sma = MathUtils.calculateSMA(closingPrices);
                double standardDeviation = MathUtils.calculateStandardDeviation(closingPrices, sma);
                double lowerBand = sma - 2 * standardDeviation;
                double upperBand = sma + 2 * standardDeviation;
                double bandwidth = upperBand - lowerBand;
                // Store the calculated values back in the candle
                historicalDataExtended.setSma(sma);
                historicalDataExtended.setBolingerLowerBand(lowerBand);
                historicalDataExtended.setBolingerUpperBand(upperBand);
                historicalDataExtended.setBolingerBandwith(bandwidth);
                IndicatorData indicatorData = new IndicatorData();
                indicatorData.setDataKey("BNF-260105-3M");
                indicatorData.setIndicatorDataKey(j);
                indicatorData.setOpen(BigDecimal.valueOf(historicalDataExtended.open));
                indicatorData.setClose(BigDecimal.valueOf(historicalDataExtended.close));
                indicatorData.setLow(BigDecimal.valueOf(historicalDataExtended.low));
                indicatorData.setHigh(BigDecimal.valueOf(historicalDataExtended.high));
                indicatorData.setBbSma(BigDecimal.valueOf(historicalDataExtended.getSma()));
                indicatorData.setBbLowerband(BigDecimal.valueOf(historicalDataExtended.getBolingerLowerBand()));
                indicatorData.setBbUpperband(BigDecimal.valueOf(historicalDataExtended.getBolingerUpperBand()));
                Timestamp timestamp = new Timestamp(tradingStrategyAndTradeData.candleDateTimeFormat.parse(historicalDataExtended.getTimeStamp()).getTime());
                indicatorData.setCandleTime(timestamp);
                try {
                    indicatorDataRepo.save(indicatorData);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            i++;
            j++;
        }
        System.out.println(new Gson().toJson(dataArrayList3M));
    }


    // @Scheduled(cron = "${tradeEngine.load.bbs.data}")
    public void loadBBSData() {
        List<IndicatorData> indicatorDataList = indicatorDataRepo.getLast20IndicatorData();
        dataArrayList3MHistory.addAll(mapBBSData(indicatorDataList));
        HistoricalDataExtended historicalDataExtended = dataArrayList3MHistory.get(dataArrayList3MHistory.size() - 1);
        tradeSedaQueue.sendTelemgramSeda("Loaded BBS historical data:" + historicalDataExtended.timeStamp + ": sma: " + historicalDataExtended.sma + ": upper band: " + historicalDataExtended.bolingerUpperBand, "-848547540");
    }
}
