package com.sakthi.trade.domain.strategy;

public enum ValueType {
    PERCENT_UP("PERCENT_UP"),POINTS_UP("POINTS_UP"),PERCENT_DOWN("PERCENT_DOWN"),POINTS_DOWN("POINTS_DOWN");

    String type;
    ValueType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
