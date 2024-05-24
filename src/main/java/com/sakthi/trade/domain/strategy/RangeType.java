package com.sakthi.trade.domain.strategy;

public enum RangeType {
    HIGH("HIGH"),LOW("LOW");
    String type;
    RangeType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
