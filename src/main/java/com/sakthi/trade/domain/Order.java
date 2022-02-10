package com.sakthi.trade.domain;

public enum Order {
    BUY(1),SELL(-1);
    int type;
    Order(int type){
        this.type=type;
    }
    public int getType(){
        return type;
    }
}
