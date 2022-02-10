package com.sakthi.trade.domain;

public enum OrderType {
    LIMIT_ORDER(1),MARKET_ORDER(2),STOP_LOSS_MARKET(3),STOP_LOSS_LIMIT(4);
    int type;
    OrderType(int type){
        this.type=type;
    }
    public int getType(){
        return type;
    }
}
