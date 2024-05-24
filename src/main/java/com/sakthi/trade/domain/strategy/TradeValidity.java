package com.sakthi.trade.domain.strategy;

public enum TradeValidity {
    INTRADAY("INTRADAY"),BTST("BTST");
    String validity;
    TradeValidity(String validity){
        this.validity=validity;
    }
    public String getValidity(){
        return validity;
    }
}
