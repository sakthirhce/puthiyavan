package com.sakthi.trade.domain.strategy;

public enum TransactionType {
    BUY("BUY"),SELL("SELL");

    String type;
    TransactionType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }
}
