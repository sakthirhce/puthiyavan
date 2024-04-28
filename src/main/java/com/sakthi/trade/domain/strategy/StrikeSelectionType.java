package com.sakthi.trade.domain.strategy;

public enum StrikeSelectionType {
    ATM("ATM"),PRICE_RANGE("PRICE_RANGE"),CLOSE_PREMIUM("CLOSE_PREMIUM"),OTM("OTM"),ITM("ITM");

    String type;
    StrikeSelectionType(String type){
        this.type=type;
    }
    public String getType(){
        return type;
    }

}
