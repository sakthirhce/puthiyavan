package com.sakthi.trade.zerodha.models;

import com.google.gson.annotations.SerializedName;
import com.zerodhatech.models.MarketDepth;
import com.zerodhatech.models.OHLC;

import java.util.Date;

public class QuoteExtended {
    @SerializedName("volume")
    public double volumeTradedToday;
    @SerializedName("net_change")
    public double change;
    @SerializedName("oi")
    public double oi;
    @SerializedName("last_price")
    public Double lastPrice;
    @SerializedName("per_change")
    public Double perChange;
    @SerializedName("ohlc")
    public OHLC ohlc;
    @SerializedName("instrument_token")
    public long instrumentToken;
    @SerializedName("timestamp")
    public Date timestamp;

    public QuoteExtended() {
    }
}
