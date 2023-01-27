package com.sakthi.trade.domain.strategy;

public enum StrikeSelectionType {
    ATM("ATM"),PRICE_RANGE("PRICE_RANGE"),CLOSE_PREMUIM("CLOSE_PREMUIM");

    String type;
    StrikeSelectionType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }

}
