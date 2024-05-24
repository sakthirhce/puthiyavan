package com.sakthi.trade.dhan;

public enum DhanExchangeSegment {
    NSE_EQ("NSE_EQ"),
    NFO("NSE_FNO"),
    NSE_CURRENCY("NSE_CURRENCY"),
    BSE_EQ("BSE_EQ"),
    MCX_COMM("MCX_COMM");
    private String exchangeSegment;
    public String getExchangeSegment()
    {
        return this.exchangeSegment;
    }
    private DhanExchangeSegment(String exchangeSegment){
        this.exchangeSegment=exchangeSegment;
    }
}
