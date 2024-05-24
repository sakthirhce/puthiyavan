package com.sakthi.trade;

import com.google.gson.Gson;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.zerodhatech.models.HistoricalData;
import org.json.JSONObject;
import org.jvnet.hk2.annotations.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BollingerSqueeze1 {

    private static final int WINDOW = 20;
    private static final double MULTIPLIER = 2.0;
    @Autowired
    MathUtils mathUtils;

    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    TransactionService transactionService;

    public void squeeze(int day) {
        List<Candle> candles = new ArrayList<>();
        int daycount = day;
            while (daycount > 0) {
            LocalDate startDate = LocalDate.now().minusDays(daycount);
            DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            System.out.println(daycount);
            try {

                String stockId = "260105";
                String historicURL = "https://api.kite.trade/instruments/historical/" + stockId + "/3minute?from=" + startDate + "+00:00:00&to=" + df1.format(startDate) + "+23:59:59";
                String response = transactionService.callAPI(transactionService.createZerodhaGetRequest(historicURL,"o1wluh7qbc286ar8","ezX61ZtNheKMiL8jFwcO8rw16LHO6UIV"));
                HistoricalData historicalData = new HistoricalData();
                JSONObject json = new JSONObject(response);
                String status = json.getString("status");
                if (!status.equals("error")) {
                    historicalData.parseResponse(json);
                    historicalData.dataArrayList.forEach(historicalData1 -> {
                        Candle candle = mapCandleFromHistoric(historicalData1);
                        candles.add(candle);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            daycount--;
             }
            LinkedList<Double> closingPrices = new LinkedList<>();
            double[] bandwidths = new double[candles.size() - WINDOW + 1];

            for (int i = 0; i < candles.size(); i++) {
                closingPrices.add(candles.get(i).getClose());
                if (closingPrices.size() > WINDOW) {
                    closingPrices.removeFirst();
                }

                if (i >= WINDOW - 1) {
                    double sma = calculateSMA(closingPrices);
                    double standardDeviation = calculateStandardDeviation(closingPrices, sma);
                    double lowerBand = sma - MULTIPLIER * standardDeviation;
                    double upperBand = sma + MULTIPLIER * standardDeviation;
                    double bandwidth = upperBand - lowerBand;

                    // Store the calculated values back in the candle
                    Candle currentCandle = candles.get(i);
                    currentCandle.setSMA(sma);
                    currentCandle.setLowerBollingerBand(lowerBand);
                    currentCandle.setUpperBollingerBand(upperBand);

                    // Store the bandwidth for squeeze calculation
                    bandwidths[i - WINDOW + 1] = bandwidth;
                }
            }
            double squeezeThreshold = calculatePercentile(bandwidths, 5);
        System.out.println(squeezeThreshold);
        System.out.println(new Gson().toJson(candles));
            // Now you can go through the candles to find where a squeeze occurred based on the threshold
            for (int i = WINDOW - 1; i < candles.size(); i++) {
                Candle candle = candles.get(i);
                if (candle.getUpperBollingerBand() - candle.getLowerBollingerBand() <= squeezeThreshold) {
                    // Squeeze condition met
                    System.out.println("Bollinger Squeeze detected at index: " + candle.timeStamp);
                }
            }
        }

    private static double calculateSMA(LinkedList<Double> closingPrices) {
        return closingPrices.stream()
                .collect(Collectors.averagingDouble(Double::doubleValue));
    }

    private static double calculateStandardDeviation(LinkedList<Double> closingPrices, double mean) {
        double sumDiffsSquared = closingPrices.stream()
                .mapToDouble(price -> Math.pow(price - mean, 2))
                .sum();
        return Math.sqrt(sumDiffsSquared / (closingPrices.size() - 1));
    }

    private static double calculatePercentile(double[] values, double percentile) {
        Arrays.sort(values);
        int index = (int) Math.ceil(percentile / 100.0 * values.length) - 1;
        return values[index];
    }
    private Candle mapCandleFromHistoric(HistoricalData historicalData){
        Candle candle=new Candle();
        candle.close=historicalData.close;
        candle.open=historicalData.open;
        candle.high=historicalData.high;
        candle.low=historicalData.low;
        candle.timeStamp= historicalData.timeStamp;
        return candle;

    }
}

