package com.sakthi.trade.options.buy.banknifty;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.TradeReport;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;


@Slf4j
public class Report {

    public static void main(String[] args) throws FileNotFoundException {
        String path="/home/hasvanth/Downloads/Nifty_Backtest/test1/";
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles) {
            if (file.isFile()) {
                System.out.println(file.getName());

        String filePath = path+file.getName();
        System.out.println(filePath);
        CSVReader csvReader = new CSVReader(new FileReader(filePath));
        try {
            int i = 0;
            int linecount = 0;
            int maxDD = 0;
            double totalProfit = 100000;
            double maxDDAmount = 0;
            int currentDD = 0;
            double currentDDAmount = 0;
            String maxDDDate = "";
            String maxDDAmountDate = "";
            double peak = 100000;
            int timeToRecover=0;
            String[] line;
            double totalCharges=0;
            double totalSlipages=0;
            Map<String, TradeReport> tradeReportMap = new HashMap<>();
            while ((line = csvReader.readNext()) != null) {
                TradeReport tradeReport;
                if (tradeReportMap.get(line[0].substring(0, 7)) != null) {
                    tradeReport = tradeReportMap.get(line[0].substring(0, 7));
                } else {
                    tradeReport = new TradeReport();
                }
                tradeReport.setMonth(line[0].substring(0, 7));
                double profit = tradeReport.getProfit() + Double.parseDouble(line[8]);
                tradeReport.setProfit(profit);
                int winningDays = tradeReport.getWinningTrade();
                if (Double.parseDouble(line[8]) > 0 && Double.parseDouble(line[2]) != Double.parseDouble(line[3])) {
                    tradeReport.setWinningTrade(winningDays + 1);
                }
                int lossingDays = tradeReport.getLossTrade();
                if (Double.parseDouble(line[8]) < 0 && Double.parseDouble(line[2]) != Double.parseDouble(line[3])) {
                    tradeReport.setLossTrade(lossingDays + 1);
                }
                int tslCost = tradeReport.getTslCostTrade();
                int monthTotalTrade = tradeReport.getTotalTrade() + 1;
                tradeReport.setTotalTrade(monthTotalTrade);
                if (Double.parseDouble(line[2]) == Double.parseDouble(line[3])) {
                    tradeReport.setTslCostTrade(tslCost + 1);
                }
                totalProfit = totalProfit + Double.parseDouble(line[8]);
                totalCharges = totalCharges + Double.parseDouble(line[5]);
                totalSlipages = totalSlipages + Double.parseDouble(line[6]);
                tradeReportMap.put(line[0].substring(0, 7), tradeReport);
                if(peak<totalProfit){
                    peak=totalProfit;
                    currentDDAmount=0;
                    currentDD=0;
                    timeToRecover=linecount-timeToRecover;
                    String logMessage = MessageFormat.format("time for new high {0} new high:{1}", peak,timeToRecover);
                    log.info(logMessage);
                    timeToRecover=linecount;
                }
                double dd=totalProfit-peak;
                if(dd<0 && dd<currentDDAmount){
                    currentDDAmount=dd;
                    currentDD++;
                }
                if(maxDD<currentDD){
                    maxDD=currentDD;
                }
                if(currentDDAmount<maxDDAmount){
                    maxDDAmount=currentDDAmount;
                    maxDDDate=line[0];
                    String logMessage = MessageFormat.format("currentDD:{0}, maxDDAmount:{1}, maxDDDate:{2}", currentDD, currentDDAmount, maxDDDate);
                    log.info(logMessage);
                }

                linecount++;
            }
            ;
            CSVWriter csvWriter = new CSVWriter(new FileWriter(path+"/report.csv", true));
            String[] dataFile = {"File:" + filePath};
            csvWriter.writeNext(dataFile);
            String[] dataHeader = {"Month", "Profit", "Total_trade", "Winning Trade", "Lossing Trade", "TSL_Cost"};
            csvWriter.writeNext(dataHeader);
            tradeReportMap.entrySet().stream().forEach(tradeReport -> {
                TradeReport tradeReport1 = tradeReport.getValue();
                String[] data = {tradeReport1.getMonth(), String.valueOf(tradeReport1.getProfit()), String.valueOf(tradeReport1.getTotalTrade()), String.valueOf(tradeReport1.getWinningTrade()), String.valueOf(tradeReport1.getLossTrade()), String.valueOf(tradeReport1.getTslCostTrade())};
                csvWriter.writeNext(data);
            });
            String[] datatotalCharges = {" totalCharges:" + totalCharges};
            csvWriter.writeNext(datatotalCharges);
            String[] datatotalSplipages = {" total Slipages:" + totalSlipages};
            csvWriter.writeNext(datatotalSplipages);
            String[] data = {"Total Profit:" + (totalProfit-100000)};
            csvWriter.writeNext(data);
            String[] data1 = {"Max DD days:" + maxDD};
            csvWriter.writeNext(data1);
            String[] data2 = {"Max DD amount:" + maxDDAmount};
            csvWriter.writeNext(data2);
            String[] data3 = {"Max DD Date:" + maxDDAmountDate};
            csvWriter.writeNext(data3);
            String[] totalTrades = {"Total trades:" + linecount};
            csvWriter.writeNext(totalTrades);

            csvWriter.flush();


        } catch (Exception e) {
            e.printStackTrace();
        }
            }
        }
    }
}
