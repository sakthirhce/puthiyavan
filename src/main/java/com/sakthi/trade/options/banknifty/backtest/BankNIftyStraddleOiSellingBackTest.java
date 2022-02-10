package com.sakthi.trade.options.banknifty.backtest;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.TradeData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BankNIftyStraddleOiSellingBackTest {
    @Value("${filepath.trend}")
    String trendPath;
public void bankNiftyStraddleLongTest() throws IOException, CsvValidationException {
    CSVWriter csvWriter1 = new CSVWriter(new FileWriter(trendPath + "/banknifty_long_stradle_backtest_100.csv", true));
    String[] dataHeader = {"Date", "StockName", "buy", "Closing Price", "profit/loss","Entry"};
    csvWriter1.writeNext(dataHeader);
    csvWriter1.flush();
    CSVWriter csvWriter2 = new CSVWriter(new FileWriter(trendPath + "/banknifty_long_stradle_backtest_eod.csv", true));
    String[] dataHeader2 = {"Date", "StockName", "buy", "Closing Price", "profit/loss","Entry"};

    csvWriter2.writeNext(dataHeader2);
    csvWriter2.flush();
    CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/BankNifty_Short_Straddle_Short_50.csv"));
    Map<String, List<TradeData>> slData=new HashMap<>();
    Map<String, List<TradeData>> closeData=new HashMap<>();
    String[] line;
    while ((line = csvReader.readNext()) != null) {
        if(!line[0].equals("")){
            if (!line[0].equals("Date")) {
                if (line[2] != null && !line[2].equals("")) {
                    String[] data = {line[0], line[3], line[2], line[1], (new BigDecimal(line[1]).subtract(new BigDecimal(line[2]))).multiply(new BigDecimal("25")).toString(), "SHORT"};
                    csvWriter2.writeNext(data);
                }
                if (line[6] != null && !line[6].equals("")) {
                    String[] data1 = {line[0], line[7], line[6], line[5], (new BigDecimal(line[5]).subtract(new BigDecimal(line[6]))).multiply(new BigDecimal("25")).toString(), "SHORT"};
                    csvWriter2.writeNext(data1);
                }
                csvWriter2.flush();
            }
        if(!line[4].equals("") || !line[8].equals("")) {
            if (!line[0].equals("Date")) {
       //         System.out.println("Date: " + line[0] + " PE: " + line[3] + " CE: " + line[7]);
           //     System.out.println(line[0]);

                boolean slHit = false;
                List<TradeData> tradeDataLs = new ArrayList<>();
                if (!line[4].equals("")) {
                    TradeData tradeData = new TradeData();
                    tradeData.setSlPrice(new BigDecimal(line[1]));
                    tradeData.setBuyPrice(new BigDecimal(line[2]));
                    tradeData.setStockName(line[3]);
                    tradeData.setBuyTime(line[4]);
                    tradeDataLs.add(tradeData);
                    slHit = true;
                }

                if (!line[8].equals("")) {
                    TradeData tradeData = new TradeData();
                    tradeData.setSlPrice(new BigDecimal(line[5]));
                    tradeData.setBuyPrice(new BigDecimal(line[6]));
                    tradeData.setStockName(line[7]);
                    tradeData.setBuyTime(line[8]);
                    tradeDataLs.add(tradeData);
                    slHit = true;
                }
                if (slHit) {
                    slData.put(line[0], tradeDataLs);
                }
            }
        }
        }
    }

    CSVReader csvReader2 = new CSVReader(new FileReader(trendPath + "/BankNifty_Short_Straddle_Short_100.csv"));
    Map<String, List<TradeData>> sl100Data=new HashMap<>();
    String[] line100;
    while ((line100 = csvReader2.readNext()) != null) {
        if(line100[0]!=null && !line100[0].equals("")){
            if (!line100[0].equals("Date")) {
                System.out.println("Date: " + line100[0] + " PE: " + line100[3]);
                List<TradeData> tradeDataLs = new ArrayList<>();

                TradeData tradeData = new TradeData();
                //tradeData.setSlPrice(new BigDecimal(line100[1]));
                tradeData.setSellPrice(new BigDecimal(line100[1]));
                tradeData.setBuyPrice(new BigDecimal(line100[2]));
                tradeData.setStockName(line100[3]);
                tradeData.setBuyTime(line100[4]);
                tradeDataLs.add(tradeData);

                if(line100[6]!=null && !line100[6].equals("")) {
                    TradeData tradeData1 = new TradeData();
                    //tradeData1.setSlPrice(new BigDecimal(line100[5]));
                    tradeData1.setBuyPrice(new BigDecimal(line100[6]));
                    tradeData1.setSellPrice(new BigDecimal(line100[5]));
                    tradeData1.setStockName(line100[7]);
                    tradeData1.setBuyTime(line100[8]);
                    tradeDataLs.add(tradeData1);

                }
                sl100Data.put(line100[0], tradeDataLs);

            }

        }}
        CSVReader csvReader1 = new CSVReader(new FileReader(trendPath + "/BankNifty_Short_Straddle_Short_WithoutSL.csv"));
        String[] line1;
        while ((line1 = csvReader1.readNext()) != null) {
            if(line1[0]!=null && !line1[0].equals("")){
                System.out.println("Date: " + line1[0] + " PE: " + line1[3] );
                if (!line1[0].equals("Date")) {
                    List<TradeData> tradeDataLs = new ArrayList<>();
                    TradeData tradeData = new TradeData();
                    tradeData.setBuyPrice(new BigDecimal(line1[2]));
                    tradeData.setStockName(line1[3]);
                    tradeData.setSellPrice(new BigDecimal(line1[1]));
                    tradeDataLs.add(tradeData);


                    TradeData tradeData1 = new TradeData();
                    tradeData1.setBuyPrice(new BigDecimal(line1[5]));
                    tradeData1.setStockName(line1[6]);
                    tradeData1.setSellPrice(new BigDecimal(line1[4]));
                    tradeDataLs.add(tradeData1);


                    closeData.put(line1[0], tradeDataLs);

                }
            }
        }
//

        System.out.println("total size:"+slData.size());
        slData.entrySet().stream().forEach(stringListEntry -> {
            System.out.println(stringListEntry.getKey() + ":" + stringListEntry.getValue().size());
            List<TradeData> slLs = stringListEntry.getValue();
            List<TradeData> closeLS = sl100Data.get(stringListEntry.getKey());
            System.out.println( new Gson().toJson(closeLS));
           if( closeLS!=null){
            slLs.stream().forEach(slList -> {
                closeLS.stream().forEach(closeList -> {
                    if (slList.getStockName().equals(closeList.getStockName())) {
                        BigDecimal sellPrice;
                        if (closeList.getBuyPrice().compareTo(slList.getSlPrice()) < 0) {
                            sellPrice = slList.getSlPrice();
                        } else {
                            sellPrice = closeList.getBuyPrice();
                        }
                  //      System.out.println(slList.getStockName() + ": buy Price: " + slList.getBuyPrice() + ": close Price: " + sellPrice + ": sl Price: " + slList.getSlPrice() + ": sell Price: " + sellPrice);
                        String[] data = {stringListEntry.getKey(), slList.getStockName(), slList.getBuyPrice().toString(), sellPrice.toString(), (sellPrice.subtract(slList.getBuyPrice())).multiply(new BigDecimal("25")).toString()};
                        csvWriter1.writeNext(data);
                        try {
                            csvWriter1.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            });}
        });
    slData.entrySet().stream().forEach(stringListEntry -> {
        System.out.println(stringListEntry.getKey() + ":" + stringListEntry.getValue().size());
        List<TradeData> slLs = stringListEntry.getValue();
        List<TradeData> closeLS = closeData.get(stringListEntry.getKey());
        System.out.println( new Gson().toJson(closeLS));
        if( closeLS!=null){
            slLs.stream().forEach(slList -> {
                closeLS.stream().forEach(closeList -> {
                    if (slList.getStockName().equals(closeList.getStockName().trim())) {
                        BigDecimal sellPrice;
                        if (closeList.getBuyPrice().compareTo(slList.getSlPrice()) < 0) {
                            sellPrice = slList.getSlPrice();
                        } else {
                            sellPrice = closeList.getBuyPrice();
                        }
                        //      System.out.println(slList.getStockName() + ": buy Price: " + slList.getBuyPrice() + ": close Price: " + sellPrice + ": sl Price: " + slList.getSlPrice() + ": sell Price: " + sellPrice);
                        String[] data = {stringListEntry.getKey(), slList.getStockName(), slList.getBuyPrice().toString(), sellPrice.toString(), (sellPrice.subtract(slList.getBuyPrice())).multiply(new BigDecimal("25")).toString(),"LONG"};
                        csvWriter2.writeNext(data);
                        try {
                            csvWriter2.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            });}
    });

/*
    CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/Bank_Nifty_with_50_SL.csv"));
    Map<String, List<TradeData>> slData=new HashMap<>();
    Map<String, List<TradeData>> closeData=new HashMap<>();
    String[] line;
    while ((line = csvReader.readNext()) != null) {
        if(line.length>5) {
            if(!line[0].equals("Date")) {
                System.out.println("Date: "+line[0]+" PE: "+line[4]+" CE: "+line[5]);
                String[] splitStr= line[4].split(" ");
                boolean slHit=false;
                List<TradeData> tradeDataLs=new ArrayList<>();
                if(splitStr.length==6) {
                    TradeData tradeData=new TradeData();
                    System.out.println(splitStr.length);
                    System.out.println(splitStr[4]);
                    String[] splitStrP= splitStr[1].split("-");
                    String[] splitStrSL=splitStrP[0].split("\\(");
                    tradeData.setSlPrice(new BigDecimal(splitStrSL[1]));
                    tradeData.setBuyPrice(new BigDecimal(splitStrP[1]));
                    tradeData.setStockName(splitStr[4]);
                    tradeData.setBuyTime(splitStr[5]);
                    tradeDataLs.add(tradeData);
                    slHit=true;
                }
                String[] splitStrCE= line[5].split(" ");
                if(splitStrCE.length==6) {
                    TradeData tradeData=new TradeData();
                    System.out.println(splitStrCE.length);
                    System.out.println(splitStrCE[4]);
                    String[] splitStrP= splitStrCE[1].split("-");
                    String[] splitStrSL=splitStrP[0].split("\\(");
                    tradeData.setSlPrice(new BigDecimal(splitStrSL[1]));
                    tradeData.setBuyPrice(new BigDecimal(splitStrP[1]));
                    tradeData.setStockName(splitStrCE[4]);
                    tradeData.setBuyTime(splitStrCE[5]);
                    tradeDataLs.add(tradeData);
                    slHit=true;
                }
                if(slHit){
                    slData.put(line[0].substring(0,11).trim(),tradeDataLs);
                }
            }
        }
    }

    CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/Bank_Nifty_with_50_SL.csv"));
    Map<String, List<TradeData>> slData=new HashMap<>();
    Map<String, List<TradeData>> closeData=new HashMap<>();
    String[] line;
    while ((line = csvReader.readNext()) != null) {
        if(line.length>5) {
            if(!line[0].equals("Date")) {
                System.out.println("Date: "+line[0]+" PE: "+line[4]+" CE: "+line[5]);
                String[] splitStr= line[4].split(" ");
                boolean slHit=false;
                List<TradeData> tradeDataLs=new ArrayList<>();
                if(splitStr.length==6) {
                    TradeData tradeData=new TradeData();
                    System.out.println(splitStr.length);
                    System.out.println(splitStr[4]);
                    String[] splitStrP= splitStr[1].split("-");
                    String[] splitStrSL=splitStrP[0].split("\\(");
                    tradeData.setSlPrice(new BigDecimal(splitStrSL[1]));
                    tradeData.setBuyPrice(new BigDecimal(splitStrP[1]));
                    tradeData.setStockName(splitStr[4]);
                    tradeData.setBuyTime(splitStr[5]);
                    tradeDataLs.add(tradeData);
                    slHit=true;
                }
                String[] splitStrCE= line[5].split(" ");
                if(splitStrCE.length==6) {
                    TradeData tradeData=new TradeData();
                    System.out.println(splitStrCE.length);
                    System.out.println(splitStrCE[4]);
                    String[] splitStrP= splitStrCE[1].split("-");
                    String[] splitStrSL=splitStrP[0].split("\\(");
                    tradeData.setSlPrice(new BigDecimal(splitStrSL[1]));
                    tradeData.setBuyPrice(new BigDecimal(splitStrP[1]));
                    tradeData.setStockName(splitStrCE[4]);
                    tradeData.setBuyTime(splitStrCE[5]);
                    tradeDataLs.add(tradeData);
                    slHit=true;
                }
                if(slHit){
                    slData.put(line[0].substring(0,11).trim(),tradeDataLs);
                }
            }
        }
    }
    CSVReader csvReader1 = new CSVReader(new FileReader(trendPath + "/Bank_Nifty_Without_SL.csv"));
    String[] line1;
    while ((line1 = csvReader1.readNext()) != null) {
        if(line1.length==5) {
            if(!line1[0].equals("Date")) {
                System.out.println("Date: "+line1[0]+" PE: "+line1[3]+" CE: "+line1[4]);
                String[] splitStr= line1[3].split(" ");
                boolean slHit=false;
                List<TradeData> tradeDataLs=new ArrayList<>();
                if(splitStr.length==5) {
                    TradeData tradeData=new TradeData();
                    System.out.println(splitStr.length);
                    System.out.println(splitStr[4]);
                    String[] splitStrP= splitStr[1].split("-");
                    tradeData.setSellPrice(new BigDecimal(splitStrP[1]));
                    tradeData.setStockName(splitStr[4]);
                    tradeDataLs.add(tradeData);
                    slHit=true;
                }
                String[] splitStrCE= line1[4].split(" ");
                if(splitStrCE.length==5) {
                    TradeData tradeData=new TradeData();
                    System.out.println(splitStrCE.length);
                    System.out.println(splitStrCE[4]);
                    String[] splitStrP= splitStrCE[1].split("-");
                    tradeData.setSellPrice(new BigDecimal(splitStrP[1]));
                    tradeData.setStockName(splitStrCE[4]);
                    tradeDataLs.add(tradeData);
                    slHit=true;
                }
                if(slHit){
                    closeData.put(line1[0].substring(0,11).trim(),tradeDataLs);
                }
            }
        }
    }
    CSVWriter csvWriter1=new CSVWriter(new FileWriter(trendPath+"/banknifty_long_stradle_backtest.csv",true));
    String[] dataHeader={"Date","StockName","buy","Closing Price","profit/loss"};
    csvWriter1.writeNext(dataHeader);
    csvWriter1.flush();

    slData.entrySet().stream().forEach(stringListEntry -> {
        System.out.println(stringListEntry.getKey()+":"+stringListEntry.getValue().size());
        List<TradeData> slLs=stringListEntry.getValue();
        List<TradeData> closeLS=closeData.get(stringListEntry.getKey());
        slLs.stream().forEach(slList->{
            closeLS.stream().forEach(closeList->{
               if(slList.getStockName().equals(closeList.getStockName())){
                    BigDecimal sellPrice;
                   if(closeList.getSellPrice().compareTo(slList.getSlPrice())<0){
                       sellPrice=slList.getSlPrice();
                   }else {
                       sellPrice=closeList.getSellPrice();
                   }
                   System.out.println(slList.getStockName()+": buy Price: "+slList.getSlPrice()+": close Price: "+closeList.getSellPrice()+": sl Price: "+slList.getSlPrice() +": sell Price: "+sellPrice  );
                   String[] data = {stringListEntry.getKey(),slList.getStockName(), slList.getBuyPrice().toString(), sellPrice.toString(),(sellPrice.subtract(slList.getBuyPrice())).multiply(new BigDecimal("25")).toString()};
                   csvWriter1.writeNext(data);
                   try {
                       csvWriter1.flush();
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               }
            });
        });
    });*/
    }
    public void summaryReport() throws Exception {
        CSVReader csvReaderReport = new CSVReader(new FileReader(trendPath + "/banknifty_long_stradle_backtest_eod.csv"));
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
        String[] line;
        while ((line = csvReaderReport.readNext()) != null) {
            if (linecount > 0) {
                if ((Double.parseDouble(line[5]) < 0) || currentDDAmount<0) {
                    currentDDAmount = currentDDAmount + Double.parseDouble(line[5]);
                }
                if (currentDDAmount < 0) {
                    currentDD++;
                } else {
                    currentDD = 0;
                    currentDDAmount=0;
                }
                if(currentDD>maxDD){
                    maxDD=currentDD;
                    maxDDDate=line[0];

                }

                if(currentDDAmount<maxDDAmount){
                    maxDDAmount=currentDDAmount;
                    maxDDAmountDate=line[0];
                }
                if(currentDD>10 || currentDDAmount<-15000) {
                    String logMessage = MessageFormat.format("currentDD:{0}, maxDDAmount:{1}, maxDDDate:{2}", currentDD, currentDDAmount, line[0]);
                    log.info(logMessage);
                }

            }
            linecount++;
        }
        String logMessage= MessageFormat.format("maxDD:{0}, maxDDAmount:{1}, maxDDDate:{2}, maxDDAmountDate:{3}",maxDD,maxDDAmount,maxDDDate,maxDDAmountDate);
        log.info(logMessage);
    }
}
