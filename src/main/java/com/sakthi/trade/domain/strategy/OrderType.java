package com.sakthi.trade.domain.strategy;

public enum OrderType {
    LIMIT_ORDER("LIMIT_ORDER"),MARKET_ORDER("MARKET_ORDER"),STOP_LOSS_MARKET("STOP_LOSS_MARKET"),STOP_LOSS_LIMIT("STOP_LOSS_LIMIT");
    String type;
    OrderType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
