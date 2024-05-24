package com.sakthi.trade.worker;

public enum Broker {
    ZERODHA("zerodha"),
    DHAN("dhan");
    private String broker;

    private Broker(String broker){
        this.broker=broker;
    }
}
