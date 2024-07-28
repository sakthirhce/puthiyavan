package com.sakthi.trade.models;

public class TickBacktestData {
    private String timestamp;
    private double value;

    // Constructor
    public TickBacktestData(String timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    // Getters and Setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
