package com.sakthi.trade.util;

import com.sakthi.trade.domain.Brokerage;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.mapper.TradeDataMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;

/**
 * Math Utilities.
 */
public class MathUtils
{
    /**
     * Simple Moving Average
     */

    public static BigDecimal percentageValueOfAmount(BigDecimal percent, BigDecimal amount){
        BigDecimal value=amount.divide(new BigDecimal("100"),2,BigDecimal.ROUND_UP).multiply(percent).setScale(0, RoundingMode.HALF_UP);
        return value;
    }
    public static BigDecimal percentageMove(BigDecimal open, BigDecimal close){
        BigDecimal value=((close.subtract(open)).divide(open, RoundingMode.HALF_EVEN)).multiply(new BigDecimal(100)).setScale(2,BigDecimal.ROUND_UP);
        return value;
    }

    public static double percentageMove(double open, double close){
        double value=((close-open)/open)*100;
        return value;
    }

    public static void main(String args[]){
        TradeData tradeData=new TradeData();
        tradeData.setStockName("abc");
        tradeData.setBuyTradedPrice(new BigDecimal("448.96"));
        tradeData.setSellTradedPrice(new BigDecimal("629.4"));
        tradeData.setQty(150);
        calculateBrokerage(tradeData,true,false,false,"0");
    }
    public static Brokerage calculateBrokerage(TradeData tradeData, boolean isOptions, boolean isEquityIntraday, boolean isFutures, String slipage){

        Brokerage brokerage=new Brokerage();
        BigDecimal brokerC=new BigDecimal("0");
        if(isOptions){
            brokerC=new BigDecimal("40");
            brokerage.setBrokerCharge(brokerC);
        }else if(isEquityIntraday){
            brokerC=MathUtils.percentageValueOfAmount(new BigDecimal("0.03"),tradeData.getSellTradedPrice().add(tradeData.getBuyTradedPrice())).multiply(new BigDecimal(tradeData.getQty()));
        }
        brokerage.setQty(tradeData.getQty());
        //STT
        BigDecimal stt=new BigDecimal("40");

        if(isOptions){
         //   stt=tradeData.getSellPrice().multiply(new BigDecimal("0.05")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));
            stt=MathUtils.percentageValueOfAmount(new BigDecimal("0.05"),(tradeData.getSellTradedPrice().multiply(new BigDecimal(tradeData.getQty()))));

        }else if(isEquityIntraday){
           // stt=tradeData.getSellPrice().multiply(new BigDecimal("0.025")).add(tradeData.getBuyPrice().multiply(new BigDecimal("0.05")));
            stt=MathUtils.percentageValueOfAmount(new BigDecimal("0.025"),tradeData.getSellTradedPrice().multiply(new BigDecimal(tradeData.getQty())));
        }

        brokerage.setStt(stt);
        //transaction charges
        BigDecimal transactionCharges=new BigDecimal("0");
        if(isOptions){
          //  transactionCharges=tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.053"));
            transactionCharges=MathUtils.percentageValueOfAmount(new BigDecimal("0.053"),tradeData.getSellTradedPrice().add(tradeData.getBuyTradedPrice()).multiply(new BigDecimal(tradeData.getQty())));
        }else if(isEquityIntraday){
          //  transactionCharges= tradeData.getSellPrice().add(tradeData.getBuyPrice()).multiply(new BigDecimal("0.00345"));
            transactionCharges=MathUtils.percentageValueOfAmount(new BigDecimal("0.00345"),tradeData.getSellTradedPrice().add(tradeData.getBuyTradedPrice()).multiply(new BigDecimal(tradeData.getQty())));


        }
     //   BigDecimal slippagePoints=new BigDecimal(slipage).divide(new BigDecimal("100"));
        BigDecimal buyslippageCost= MathUtils.percentageValueOfAmount(new BigDecimal(slipage),tradeData.getSellTradedPrice()).multiply(new BigDecimal(tradeData.getQty()));
        BigDecimal sellSlippageCost= MathUtils.percentageValueOfAmount(new BigDecimal(slipage),tradeData.getBuyTradedPrice()).multiply(new BigDecimal(tradeData.getQty()));
        brokerage.setSlipageCost(new BigDecimal("0"));
        brokerage.setTransactionCharges(transactionCharges);
        //GST: 18%
        BigDecimal gst=MathUtils.percentageValueOfAmount(new BigDecimal("18"),transactionCharges.add(brokerC));
        brokerage.setGst(gst);
        BigDecimal stampDuty=new BigDecimal(.003).multiply(tradeData.getBuyTradedPrice());
        brokerage.setGst(stampDuty);
        BigDecimal totalcharges=brokerC.add(stt).add(transactionCharges).add(gst).add(stampDuty);
        brokerage.setTotalCharges(totalcharges);
        tradeData.setCharges(totalcharges.setScale(2, RoundingMode.HALF_UP));
        return brokerage;
    }
    public static class SMA
    {
        private LinkedList values = new LinkedList();

        private int length;

        private double sum = 0;

        private double average = 0;

        /**
         *
         * @param length the maximum length
         */
        public SMA(int length)
        {
            if (length <= 0)
            {
                throw new IllegalArgumentException("length must be greater than zero");
            }
            this.length = length;
        }

        public double currentAverage()
        {
            return average;
        }

        /**
         * Compute the moving average.
         * Synchronised so that no changes in the underlying data is made during calculation.
         * @param value The value
         * @return The average
         */
        public synchronized double compute(double value)
        {
            if (values.size() == length && length > 0)
            {
                sum -= ((Double) values.getFirst()).doubleValue();
                values.removeFirst();
            }
            sum += value;
            values.addLast(new Double(value));
            average = sum / values.size();
            return average;
        }
    }

    public static class RSI
    {
        private LinkedList<Double> gains = new LinkedList<>();
        private LinkedList<Double>  loss= new LinkedList<>();
        private LinkedList<Double>  closeList= new LinkedList<>();
        private LinkedList<Double>  averageGains= new LinkedList<>();
        private LinkedList<Double>  averageLosses= new LinkedList<>();
        private int periodLength;

        private double sum = 0;
        private int counter = 0;
        private double average = 0;

        /**
         *
         * @param periodLength the maximum length
         */
        public RSI(int periodLength)
        {
            if (periodLength <= 0)
            {
                throw new IllegalArgumentException("length must be greater than zero");
            }
            this.periodLength = periodLength;
        }

        public double currentAverage()
        {
            return average;
        }

        /**
         * Compute the moving average.
         * Synchronised so that no changes in the underlying data is made during calculation.
         * @param value The value
         * @return The average
         */
        public synchronized double compute(double value)
        {
            double rsi=0;
            if(counter==0){
                closeList.addLast(value);
            } else if(counter>0){
                if(closeList.size()==1){
                    double change=value-closeList.getFirst();
                    if (change>0){
                        gains.addLast(change);
                        loss.addLast(0.0);
                    } else if (change<0){
                        gains.addLast(0.0);
                        loss.addLast(Math.abs(change));
                    } else {
                        gains.addLast(0.0);
                        loss.addLast(0.0);
                    }
                    }
                    closeList.removeFirst();
                    closeList.addLast(value);
                }

             if(counter>=14) {
                 double averageGain =0;
                 double averageLoss =0;
                 if (counter == 14) {
                     double gainsSum = gains.stream()
                             .filter(a -> a != null)
                             .mapToDouble(a -> a)
                             .sum();
                     averageGain = gainsSum / 14;
                     averageGains.addLast(averageGain);
                     double lossSum = loss.stream()
                             .filter(a -> a != null)
                             .mapToDouble(a -> a)
                             .sum();
                      averageLoss = lossSum / 14;
                     averageLosses.addLast(averageLoss);
                 }else {
                     double averageG=averageGains.getFirst();
                     averageGain=((averageG*13)+gains.getLast())/14;
                     averageGains.removeFirst();
                     averageGains.addLast(averageGain);
                     double averageL=averageLosses.getFirst();
                     averageLoss=((averageL*13)+loss.getLast())/14;
                     averageLosses.removeFirst();
                     averageLosses.addLast(averageLoss);

                 }

                 double rs = averageGain / averageLoss;
                 rsi = 100 - (100 / (1 + rs));

            }
            counter++;
            return rsi;
        }
    }
}