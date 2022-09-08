package com.sakthi.trade.algotest.backtest.data;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.AlgoStrategyData;
import com.sakthi.trade.domain.OpenPositionData;
import com.sakthi.trade.domain.SummaryData;
import com.sakthi.trade.domain.SummaryDataList;
import com.sakthi.trade.entity.AlgoTestEntity;
import com.sakthi.trade.repo.AlgoTestDataRepo;
import org.apache.commons.lang.text.StrBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class Algotest {

    @Value("${algotest.data.path:/home/hasvanth/Algo_Test_Data}")
    String dataPath;
    Map<String, AlgoStrategyData> strategiesPath=new HashMap<>();
    public void loadBacktestData() throws IOException, CsvValidationException, ParseException {

        strategiesPath.put("STRADDLE_935", new AlgoStrategyData("BANKNIFTY",175,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("STRADDLE_935_BUY", new AlgoStrategyData("BANKNIFTY",175,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("STRADDLE_935_INTRADAY_BUY", new AlgoStrategyData("BANKNIFTY",175,Arrays.asList("Monday","Wednesday","Thursday","Friday")));
        strategiesPath.put("NIFTY_BUY_935", new AlgoStrategyData("NIFTY",350,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("BNF_FUTURE_930", new AlgoStrategyData("BANKNIFTY",25,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("NIFTY_BUY_1035", new AlgoStrategyData("NIFTY",350,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("INTRDAY_STRADDLE_920", new AlgoStrategyData("BANKNIFTY",150,Arrays.asList("Monday","Tuesday","Wednesday")));
        strategiesPath.put("INTRDAY_STRADDLE_920_THURSDAY", new AlgoStrategyData("BANKNIFTY",150,Arrays.asList("Thursday")));

        File dir = new File("/home/hasvanth/Algo_Test_Data");
        File[] files = dir.listFiles();
        String[] line100;
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory() && strategiesPath.containsKey(file.getName())) {
                    AlgoStrategyData openPositionData=strategiesPath.get(file.getName());
                    File[] innerFiles =file.listFiles();
                    for (File file1 : innerFiles) {
                        if (!file1.isDirectory()) {
                            System.out.println(file1.getAbsolutePath());
                            CSVReader csvReader = new CSVReader(new FileReader(file1.getAbsolutePath()));
                            while ((line100 = csvReader.readNext()) != null) {
                                if (line100[2] != null && !line100[2].equals("")&&line100.length>5) {
                                    System.out.println(line100[2]+":"+line100[3]+":"+line100[7]+":"+file.getName());
                                    saveEntity(line100,file.getName(),openPositionData);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @Autowired
    AlgoTestDataRepo algoTestDataRepo;

    public SummaryDataList getAlgoTestData(){

        List<AlgoTestEntity> algoTestEntities=algoTestDataRepo.findOrderedData();
        List<AlgoTestEntity> algoTestEntitiesOutput=new ArrayList<>();
        strategiesPath.put("STRADDLE_935", new AlgoStrategyData("BANKNIFTY",175,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("STRADDLE_935_BUY", new AlgoStrategyData("BANKNIFTY",175,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("STRADDLE_935_INTRADAY_BUY", new AlgoStrategyData("BANKNIFTY",175,Arrays.asList("Monday","Wednesday","Thursday","Friday")));
        strategiesPath.put("NIFTY_BUY_935", new AlgoStrategyData("NIFTY",350,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("BNF_FUTURE_930", new AlgoStrategyData("BANKNIFTY",25,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("NIFTY_BUY_1035", new AlgoStrategyData("NIFTY",350,Arrays.asList("Monday","Tuesday","Wednesday","Thursday","Friday")));
        strategiesPath.put("INTRDAY_STRADDLE_920", new AlgoStrategyData("BANKNIFTY",150,Arrays.asList("Monday","Tuesday","Wednesday")));
        strategiesPath.put("INTRDAY_STRADDLE_920_THURSDAY", new AlgoStrategyData("BANKNIFTY",150,Arrays.asList("Thursday")));
        int linecount = 0;
        int maxDD = 0;
        double maxDDAmount = 0;
        int currentDD = 0;
        double currentDDAmount = 0;
        String maxDDDate="";
        String maxDDAmountDate="";
        int maxDayDD = 0;
        int maxDayDDAmount = 0;
        int currentDayDD = 0;
        int currentDayDDAmount = 0;
        SummaryDataList summaryDataList = new SummaryDataList();
        TreeMap<String, SummaryData> summaryDataMap=new TreeMap<>();
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("YYYY-MM-dd");
        for (AlgoTestEntity algoTestEntity:algoTestEntities)  {
                AlgoStrategyData algoStrategyData= strategiesPath.get(algoTestEntity.getAlgoName());
                List<String> dayConfig=algoStrategyData.getDayConfig();
                if(dayConfig.contains(algoTestEntity.getEntryDay())) {
                    algoTestEntitiesOutput.add(algoTestEntity);
                    String tradeDate=algoTestEntity.entryDate.toString();
                    if (algoTestEntity.getProfitLoss().doubleValue() < 0 || currentDDAmount < 0) {
                        currentDDAmount = currentDDAmount + algoTestEntity.getProfitLoss().doubleValue();
                    }
                    if (currentDDAmount < 0) {
                        currentDD++;
                    } else {
                        currentDD = 0;
                        currentDDAmount = 0;
                    }
                    if (currentDD > maxDD) {
                        maxDD = currentDD;
                        maxDDDate = algoTestEntity.entryDate.toString();

                    }

                    if (currentDDAmount < maxDDAmount) {
                        maxDDAmount = currentDDAmount;
                        maxDDAmountDate = algoTestEntity.entryDate.toString();
                    }
                   /* if (currentDD > 10 || currentDDAmount < -15000) {
                        String logMessage = MessageFormat.format("currentDD:{0}, maxDDAmount:{1}, maxDDDate:{2}", currentDD, currentDDAmount, simpleDateFormat.format(algoTestEntity.entryDate));
                        System.out.println(logMessage);
                    }*/
                    if(summaryDataMap.containsKey(tradeDate)){
                        SummaryData summaryData=summaryDataMap.get(tradeDate);
                        summaryData.currentDD=currentDDAmount;
                        summaryDataMap.put(tradeDate,summaryData);
                    }else {
                        SummaryData summaryData=new SummaryData();
                        summaryData.currentDD=currentDDAmount;
                        summaryDataMap.put(tradeDate,summaryData);
                    }

                }

        }
        summaryDataList.summaryDataMap=summaryDataMap;
        return summaryDataList;
    }
    public void saveEntity(String[] line,String algoname,AlgoStrategyData openPositionData) throws ParseException {
      //  List<AlgoTestEntity> algoTestEntities=new ArrayList<>();
        try {
            if(!"Index".equals(line[0])) {
                AlgoTestEntity algoTestEntity = new AlgoTestEntity();
                algoTestEntity.setAlgoName(algoname);
                algoTestEntity.setDataKey(UUID.randomUUID().toString());
                if (!"Futures".equals(line[6])) {
                    algoTestEntity.setStrike(Integer.parseInt(line[7]));
                }
                if (line[8].equals("Sell")) {
                    algoTestEntity.setSellPrice(new BigDecimal(line[4]));
                    algoTestEntity.setBuyPrice(new BigDecimal(line[12]));
                } else {
                    algoTestEntity.setSellPrice(new BigDecimal(line[12]));
                    algoTestEntity.setBuyPrice(new BigDecimal(line[4]));
                }
                algoTestEntity.setEntryDate(java.sql.Date.valueOf(line[1]));
                algoTestEntity.setTradeDate(java.sql.Date.valueOf(line[1]));
                System.out.println(line[9]);
                algoTestEntity.setExitDate(java.sql.Date.valueOf(line[9]));
                algoTestEntity.setInstrument(line[6]);
                algoTestEntity.setQty(openPositionData.getQty());
                algoTestEntity.setEntryTime(java.sql.Timestamp.valueOf(line[1] + " " + line[3] + ".000"));
                algoTestEntity.setExitTime(java.sql.Timestamp.valueOf(line[9] + " " + line[11] + ".000"));
                algoTestEntity.setUserId("RS4899");
                BigDecimal pl = algoTestEntity.getSellPrice().subtract(algoTestEntity.getBuyPrice()).setScale(2, RoundingMode.HALF_EVEN).multiply(new BigDecimal(algoTestEntity.getQty())).setScale(2, RoundingMode.HALF_EVEN);
                algoTestEntity.setProfitLoss(pl);
                algoTestEntity.setEntryDay(line[2]);
                System.out.println(new Gson().toJson(algoTestEntity));
                algoTestDataRepo.save(algoTestEntity);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

  /*  public static void main(String args[]) throws IOException, CsvValidationException, ParseException {
        Algotest algotest=new Algotest();
        algotest.loadBacktestData();
    }*/
}
