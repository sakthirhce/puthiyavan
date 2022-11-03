package com.sakthi.trade.dhan;

public enum DhanTransactionType {
    BUY("BUY"),
    SELL("SELL");
    private String transactionType;
    private DhanTransactionType(String transactionType){
        this.transactionType=transactionType;
    }
}
