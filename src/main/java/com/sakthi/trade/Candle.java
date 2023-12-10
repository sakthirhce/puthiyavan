package com.sakthi.trade;


public class Candle {
    public String timeStamp;
    public double open;
    public double high;
    public double low;
    public double close;
    public long volume;
    public long oi;
    private double sma;
    private double lowerBollingerBand;
    private double upperBollingerBand;

    // Getters and setters for close, sma, lowerBollingerBand, upperBollingerBand
    // ...

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }
    public double getOpen() {
        return open;
    }
    public void setOpen(double open) {
        this.open = open;
    }

    public double getSMA() {
        return sma;
    }

    public void setSMA(double sma) {
        this.sma = sma;
    }

    public double getLowerBollingerBand() {
        return lowerBollingerBand;
    }

    public void setLowerBollingerBand(double lowerBollingerBand) {
        this.lowerBollingerBand = lowerBollingerBand;
    }

    public double getUpperBollingerBand() {
        return upperBollingerBand;
    }

    public void setUpperBollingerBand(double upperBollingerBand) {
        this.upperBollingerBand = upperBollingerBand;
    }
}
