package com.sakthi.trade.backtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencsv.CSVWriter;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.models.TickHistoricalData;
import com.sakthi.trade.util.CommonUtil;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class TickBacktest {
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String API_URL = "http://localhost:5000/data"; // Replace with your actual API URL
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    KiteConnect kiteConnect;
    @Autowired
    UserList userList;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    CommonUtil commonUtil;
    static ObjectMapper objectMapper = new ObjectMapper();
    public static final Logger LOGGER = LoggerFactory.getLogger(TradeEngine.class.getName());

    public void tickBackTest(int day, double targetValue,double slValue, LocalTime entryTime,int closeTime) throws ParseException, IOException, KiteException {
     //   SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        DateTimeFormatter dateFormat1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate localDate= LocalDate.now();
        String fileName = "trade_data_" + localDate.format(dateFormat1) + ".csv";
        CSVWriter csvWriter;
        if (Files.exists(Paths.get(fileName))) {
            System.out.println("File exists: " + fileName);
            csvWriter = new CSVWriter(new FileWriter("trade_data_"+localDate.format(dateFormat1)+":"+".csv", true));
        } else {
            String[] header = {"strike name","Buy Price", "Sell Price", "Buy Time", "Sell Time", "P&L"};
            csvWriter = new CSVWriter(new FileWriter("trade_data_"+localDate.format(dateFormat1)+":"+".csv", true));
            csvWriter.writeNext(header);
        }

        //CSVWriter csvWriter = new CSVWriter(new FileWriter("trade_data_"+localDate.format(dateFormat1)+":"+".csv", true));
        List<TradeData> tradeDataList = new ArrayList<>();

        while (day <= 0) {
            int totalQty;
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, day);

            LOGGER.info("Date:" + dateFormat.format(calendar.getTime()));
            Calendar cthurs = Calendar.getInstance();
            cthurs.add(Calendar.DAY_OF_MONTH, day);
            int dayadd = 5 - cthurs.get(Calendar.DAY_OF_WEEK);
            if (dayadd > 0) {
                cthurs.add(Calendar.DAY_OF_MONTH, dayadd);
            } else if (dayadd == -2) {
                cthurs.add(Calendar.DAY_OF_MONTH, 5);
            } else if (dayadd < 0) {
                cthurs.add(Calendar.DAY_OF_MONTH, 6);
            }
            Date cdate = calendar.getTime();
            String interval = "minute";
            String token = null; // Default token
            String index = "";
            int lotSize=1;
            int indexLotSize=15;
            // Check if the day is Tuesday
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY) {
                token = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
                index = "FN";
                 indexLotSize=40;
            }
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                token = zerodhaTransactionService.niftyIndics.get("NIFTY MID SELECT");
                index = "MC";
                indexLotSize=75;
            }
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY) {
                token = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
                index = "BNF";
                indexLotSize=15;
            }
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) {
                token = zerodhaTransactionService.niftyIndics.get("NIFTY 50");
                index = "NF";
                indexLotSize=25;
            }
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                token = zerodhaTransactionService.niftyIndics.get("SENSEX");
                index = "SS";
                indexLotSize=10;
            }
            totalQty=indexLotSize*lotSize;
            if (token != null) {
                String currentDate = dateFormat.format(cdate);
                if (kiteConnect == null) {
                    User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
                    kiteConnect = user.getKiteConnect();
                }
                HistoricalData historicalData = kiteConnect.getHistoricalData(
                        dateTimeFormat.parse(currentDate + " 00:00:00"),
                        dateTimeFormat.parse(currentDate + " 23:59:59"),
                        token, interval, false, false);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
                if (!historicalData.dataArrayList.isEmpty()) {

                    String finalIndex = index;
                    String finalIndex1 = index;
                    historicalData.dataArrayList.forEach(historicalData1 -> {

                        ZonedDateTime zonedDateTime = ZonedDateTime.parse(historicalData1.timeStamp, formatter);
                        LocalTime time = zonedDateTime.toLocalTime();

                        if (time.equals(entryTime)) {
                            try {
                                double open = historicalData1.open;
                                int atm = commonUtil.findATM((int) open, finalIndex);
                                String filePath = "/home/hasvanth/Downloads/tick_" + currentDate + "/instrument_" + currentDate + ".csv"; // Replace with your actual file path
                                List<String[]> matchedInstruments = loadAndMatchInstrument(filePath, String.valueOf(atm));

                                if (matchedInstruments.isEmpty()) {
                                    System.out.println("No matching instruments found.");
                                    System.out.println("No matching instruments found. atm:"+atm+": time"+entryTime+":index" + finalIndex1+":"+currentDate);
                                } else {
                                    String[] match = matchedInstruments.stream().filter(matchedInstrument -> matchedInstrument[1].contains("CE")).findFirst().get();
                                    String[] matchPE = matchedInstruments.stream().filter(matchedInstrument -> matchedInstrument[1].contains("PE")).findFirst().get();
                                    tradeCode(currentDate, match[0], tradeDataList, match[1], targetValue,slValue, entryTime,closeTime, totalQty);
                                    tradeCode(currentDate, matchPE[0], tradeDataList, matchPE[1], targetValue,slValue, entryTime,closeTime, totalQty);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
            day++;
        }

        tradeDataList.forEach(tradeData -> {
            String[] data = {
                    tradeData.getStockName(),
                    tradeData.getBuyPrice().setScale(2, RoundingMode.HALF_EVEN).toString(),
                    tradeData.getSellPrice().setScale(2, RoundingMode.HALF_EVEN).toString(),
                    tradeData.getBuyTime(),
                    tradeData.getSellTime(),
                    new BigDecimal(tradeData.getQty()).multiply((tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).setScale(2, RoundingMode.HALF_EVEN))).toString()
            };
            csvWriter.writeNext(data);
        });

        csvWriter.close();
    }

    public static void tradeCode(String currentDate, String strikeId, List<TradeData> tradeDataList, String strikeName, double targetValue, double slValue, LocalTime entryTime,int closeTime,int lot) throws Exception {
        TradeData tradeData = new TradeData();
        String response = callTokenApi(currentDate, strikeId);
        TickHistoricalData tickHistoricalData = objectMapper.readValue(response, TickHistoricalData.class);
        List<List<Object>> data = tickHistoricalData.getData();
        int i = 0;
        while (i < data.size() - 1) {
            if (i > 0 && !tradeData.isExited) {
                List<Object> previousTick = data.get(i - 1);
                List<Object> nextTick = data.get(i + 1);
                List<Object> tick = data.get(i);
                String timestamp = (String) tick.get(0);
                DateTimeFormatter dateTimeFormatT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                LocalDateTime zonedDateTimeTick = LocalDateTime.parse(timestamp, dateTimeFormatT);
                LocalTime timeTick = zonedDateTimeTick.toLocalTime();
                double value = (Double) tick.get(1);
                if (timeTick.isAfter(entryTime)) {

                    double previousValue = (Double) previousTick.get(1);
                    double diff = value - previousValue;
                    double targetPrice = targetValue/100 * (Double) nextTick.get(1);
                    double slPrice = slValue/100 * (Double) nextTick.get(1);
                    if (diff > 0 && value > 20) {
                        double percentageDiffBWPreviousAndRecentTick = 1.1 * previousValue;
                        double nextTickValue = (Double) nextTick.get(1);
                        if (value > percentageDiffBWPreviousAndRecentTick && !tradeData.isOrderPlaced && value<300) {
                            tradeData.isOrderPlaced = true;
                            tradeData.setQty(lot);
                            tradeData.setStockName(strikeName);
                            tradeData.setBuyPrice(new BigDecimal(nextTickValue));
                            tradeData.setSlPrice(tradeData.getBuyPrice().subtract(new BigDecimal(slPrice)));
                            tradeData.setBuyTime(timestamp);
                            tradeData.setTargetPrice(tradeData.getBuyPrice().add(new BigDecimal(targetPrice)));
                            String tickMessage = "last traded price is more than 10% higher than previous tick. Difference: " + String.format("%,.2f", diff) + ". last trade price: " + value + ". ";
                            LOGGER.info(tickMessage);
                        }
                    }
                    if (tradeData.isOrderPlaced && !tradeData.isExited && value >= tradeData.getTargetPrice().doubleValue()) {
                        tradeData.isExited = true;
                        tradeData.setSellPrice(tradeData.getTargetPrice());
                        tradeData.setSellTime(timestamp);
                        String tickMessage = "target hit";
                        LOGGER.info(tickMessage);
                    }
                    if (tradeData.isOrderPlaced && !tradeData.isExited && value <=tradeData.getSlPrice().doubleValue()) {
                        tradeData.isExited = true;
                        tradeData.setSellPrice(new BigDecimal(value));
                        tradeData.setSellTime(timestamp);
                        String tickMessage = "sl hit";
                        LOGGER.info(tickMessage);
                    }

                }
                if (tradeData.isOrderPlaced && !tradeData.isExited) {
                    LocalDateTime buyTime = LocalDateTime.parse(tradeData.getBuyTime(), dateTimeFormatT);
                    if (zonedDateTimeTick.isAfter(buyTime.plusMinutes(closeTime))) {
                        tradeData.isExited = true;
                        tradeData.setSellPrice(new BigDecimal(value));
                        tradeData.setSellTime(timestamp);
                        String tickMessage = "1 hour passed, closing trade";
                        LOGGER.info(tickMessage);
                    }
                }
            }
            i++;
        }
        if (tradeData.isOrderPlaced&& tradeData.isExited) {
            String tradeMessage = "Trade Log: " + strikeName + ": buy price:" + tradeData.getBuyPrice().setScale(2, RoundingMode.HALF_EVEN) + ": sell price:" + tradeData.getSellPrice().setScale(2, RoundingMode.HALF_EVEN) + ": buy time:" + tradeData.getBuyTime() + ": sell time:" + tradeData.getSellTime() + " pl:" + tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).setScale(2, RoundingMode.HALF_EVEN);
            LOGGER.info(tradeMessage);
            tradeDataList.add(tradeData);
        }
    }

    public static List<String[]> loadAndMatchInstrument(String filePath, String instrumentToMatch) {
        List<String[]> matchedInstruments = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 4 && values[1].contains(instrumentToMatch)) {
                    matchedInstruments.add(values);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return matchedInstruments;
    }

    private static String callTokenApi(String date, String strikeId) throws Exception {
        String url = API_URL + "/" + date + "/" + Long.valueOf(strikeId.replace("\"", ""));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + getJwtToken()) // You need to implement getJwtToken()
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("API call failed. Status code: " + response.statusCode());
        }
        return response.body();
    }

    private static String getJwtToken() throws Exception {
        String loginUrl = "http://localhost:5000/login";
        String jsonBody = "{\"username\":\"LTK728\",\"password\":\"StradleP@9529\"}";
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> loginResponse = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        if (loginResponse.statusCode() == 200) {
            String responseBody = loginResponse.body();
            JsonObject jsonObject = new JsonParser().parse(responseBody).getAsJsonObject();
            return jsonObject.get("access_token").getAsString();
        } else {
            throw new Exception("Failed to get JWT token");
        }
    }
}