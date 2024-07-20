package com.sakthi.trade.service;

import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Tick;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TickToMinuteConverter {
    public static DateTimeFormatter candleDateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public static SimpleDateFormat candleDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    public static List<HistoricalData> convertTickToMinuteData(List<Tick> tickDataList) {
        Map<LocalDateTime, List<Tick>> minuteBuckets = new TreeMap<>();
        tickDataList.subList(tickDataList.size()-120,tickDataList.size()-1);
        for (Tick tick : tickDataList) {
            LocalDateTime minute = LocalDateTime.ofInstant(tick.getTickTimestamp().toInstant(), ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.MINUTES);
            minuteBuckets.computeIfAbsent(minute, k -> new ArrayList<>()).add(tick);
        }

        List<HistoricalData> minuteDataList = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<Tick>> entry : minuteBuckets.entrySet()) {
            LocalDateTime minute = entry.getKey();
            List<Tick> ticks = entry.getValue();

            double open = ticks.get(0).getLastTradedPrice();
            double close = ticks.get(ticks.size() - 1).getLastTradedPrice();
            double high = ticks.stream().mapToDouble(Tick::getLastTradedPrice).max().orElse(open);
            double low = ticks.stream().mapToDouble(Tick::getLastTradedPrice).min().orElse(open);
            HistoricalData minData=new HistoricalData();
            minData.close=close;
            minData.open=open;
            minData.high=high;
            minData.low=low;
            minData.timeStamp=minute.format(candleDateTimeFormat);
            minuteDataList.add(minData);
        }

        return minuteDataList;
    }

    public static void main(String[] args) throws FileNotFoundException {
/*
        List<Tick> tickDataList = new ArrayList<>();
        String csvFile = "/home/hasvanth/Downloads/tick_2024-07-05/tick_2024-07-05_265-1.csv";
        CSVReader reader = new CSVReader(new FileReader(csvFile));
        StopWatch stopWatch=new StopWatch();
        stopWatch.start();
        try{
            String[] line;
            int i=0;
            while ((line = reader.readNext()) != null) {
                if (i > 0) {
                    // Process the line
                    // for (String value : line) {
                    System.out.println(line[1] + " " + line[2]);
                    Tick tick = new Tick();
                    tick.setTickTimestamp(candleDateTime.parse(line[1]));
                    tick.setLastTradedPrice(Double.parseDouble(line[2]));
                    tickDataList.add(tick);
                }
                i++;
                //   }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
      *//*  tickDataList.add(new Tick(Instant.parse("2023-07-06T10:01:30Z"), 100.0));
        tickDataList.add(new Tick(Instant.parse("2023-07-06T10:01:45Z"), 101.0));
        tickDataList.add(new Tick(Instant.parse("2023-07-06T10:02:10Z"), 102.0));
        tickDataList.add(new Tick(Instant.parse("2023-07-06T10:02:50Z"), 103.0));
*//*
        List<HistoricalData> minuteDataList = convertTickToMinuteData(tickDataList);
        Gson gson=new Gson();
        for (HistoricalData minuteData : minuteDataList) {
            System.out.println(gson.toJson(minuteData));
        }
        stopWatch.stop();
        System.out.println("total time:"+stopWatch.getTotalTimeMillis());*/
        TickToMinuteConverter tick=new TickToMinuteConverter();
        tick.findATMTick((int) 79549.71,"SS");
    }

    public long findATMTick(int currentValue,String index) {
        long atm=0;
        System.out.println(currentValue);
        if("FN".equals(index)|| "NF".equals(index)){
            atm= (int) Math.round(currentValue / 50.0) * 50;
        }else if("MC".equals(index)) {
            atm= (int) Math.round(currentValue / 25.0) * 25;
        }else if("BNF".equals(index)|| "SS".equals(index)){
            int a = (currentValue / 100) * 100;
            int b = a + 100;
            atm= (currentValue - a > b - currentValue) ? b : a;
        }
        System.out.println(atm);
        return atm;
    }
    }

