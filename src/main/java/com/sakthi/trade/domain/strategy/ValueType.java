package com.sakthi.trade.domain.strategy;

public enum ValueType {
    PERCENT_UP("PERCENT_UP"),POINT_UP("POINT_UP"),PERCENT_DOWN("PERCENT_DOWN"),POINT_DOWN("POINT_DOWN");

    String type;
    ValueType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
