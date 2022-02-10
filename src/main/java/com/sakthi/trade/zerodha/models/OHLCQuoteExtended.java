package com.sakthi.trade.zerodha.models;

import com.google.gson.annotations.SerializedName;
import com.zerodhatech.models.OHLC;

public class OHLCQuoteExtended {
    @SerializedName("instrument_token")
    public long instrumentToken;
    @SerializedName("last_price")
    public double lastPrice;
    @SerializedName("ohlc")
    public OHLC ohlc;

    public OHLCQuoteExtended() {
    }
}
