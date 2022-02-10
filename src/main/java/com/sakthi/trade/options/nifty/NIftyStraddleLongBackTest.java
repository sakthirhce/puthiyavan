package com.sakthi.trade.options.nifty;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.TradeData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NIftyStraddleLongBackTest {
    @Value("${filepath.trend}")
    String trendPath;
public void niftyStraddleLongTest() throws IOException, CsvValidationException {

    CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/Nifty_Short Straddle_50_SL.csv"));
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
    CSVReader csvReader1 = new CSVReader(new FileReader(trendPath + "/Nifty_Short_Straddle_without_SL.csv"));
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
    CSVWriter csvWriter1=new CSVWriter(new FileWriter(trendPath+"/nifty_long_stradle_backtest.csv",true));
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
                   String[] data = {stringListEntry.getKey(),slList.getStockName(), slList.getBuyPrice().toString(), sellPrice.toString(),(sellPrice.subtract(slList.getBuyPrice())).multiply(new BigDecimal("75")).toString()};
                   csvWriter1.writeNext(data);
                   try {
                       csvWriter1.flush();
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               }
            });
        });
    });
}
}
