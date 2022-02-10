package com.sakthi.trade.domain.zerodha;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.zerodhatech.models.OHLCQuote;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class OHLCQuoteResponse {

    @SerializedName("status")
    @Expose
    private String status;

    @SerializedName("data")
    private Map<String, OHLCQuote> ohlcQuoteMap;

}