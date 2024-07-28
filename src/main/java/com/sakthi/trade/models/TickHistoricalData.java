package com.sakthi.trade.models;

import java.util.List;

public class TickHistoricalData {
    private List<List<Object>> data;
    private int instrument_token;

    // Getters and Setters
    public List<List<Object>> getData() {
        return data;
    }

    public void setData(List<List<Object>> data) {
        this.data = data;
    }

    public int getInstrument_token() {
        return instrument_token;
    }

    public void setInstrument_token(int instrument_token) {
        this.instrument_token = instrument_token;
    }
}