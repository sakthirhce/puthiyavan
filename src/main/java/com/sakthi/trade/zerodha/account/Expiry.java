package com.sakthi.trade.zerodha.account;

public enum Expiry {
    NF_CURRENT("NF_CURRENT"),
    BNF_CURRENT("BNF_CURRENT"),
    NF_NEXT("NF_NEXT"),
    BNF_NEXT("BNF_NEXT");
    public String expiryName;
    private Expiry(String expiryName){
        this.expiryName=expiryName;
    }
}
