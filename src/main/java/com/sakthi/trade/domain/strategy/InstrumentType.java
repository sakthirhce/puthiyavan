package com.sakthi.trade.domain.strategy;

public enum InstrumentType {
    FUTURES("FUTURES"),OPTIONS("OPTIONS"),INDEX("INDEX");
    String type;
    InstrumentType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
