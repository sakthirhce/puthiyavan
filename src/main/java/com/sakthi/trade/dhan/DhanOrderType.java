package com.sakthi.trade.dhan;

public enum DhanOrderType {
    LIMIT("LIMIT"),
    MARKET("MARKET"),
    SL("STOP_LOSS"),
    STOP_LOSS_MARKET("STOP_LOSS_MARKET");
    private String orderType;
    public String getOrderType()
    {
        return this.orderType;
    }

    private DhanOrderType(String orderType){
        this.orderType=orderType;
    }
}
