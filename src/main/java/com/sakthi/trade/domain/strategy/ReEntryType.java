package com.sakthi.trade.domain.strategy;

public enum ReEntryType {

    COST("COST"),ASAP("ASAP");
    String type;
    ReEntryType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
