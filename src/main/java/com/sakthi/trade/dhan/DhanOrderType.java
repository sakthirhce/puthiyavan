package com.sakthi.trade.dhan;

public enum DhanOrderType {
    LIMIT("LIMIT"),
    MARKET("MARKET"),
    STOP_LOSS("STOP_LOSS"),
    STOP_LOSS_MARKET("STOP_LOSS_MARKET");
    private String orderType;
    private DhanOrderType(String orderType){
        this.orderType=orderType;
    }
}
