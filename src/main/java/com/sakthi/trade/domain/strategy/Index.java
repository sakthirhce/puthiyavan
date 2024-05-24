package com.sakthi.trade.domain.strategy;

public enum Index {
    NF("NF"),BNF("BNF"),FN("FN");
    String type;
    Index(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
