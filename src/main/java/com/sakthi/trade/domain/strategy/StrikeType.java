package com.sakthi.trade.domain.strategy;

public enum StrikeType {
    CE("CE"),PE("PE");
    String type;
    StrikeType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
