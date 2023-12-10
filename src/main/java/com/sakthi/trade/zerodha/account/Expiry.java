package com.sakthi.trade.zerodha.account;

public enum Expiry {
    NF_CURRENT("NF_CURRENT"),
    BNF_CURRENT("BNF_CURRENT"),
    FN_CURRENT("FN_CURRENT"),
    NF_NEXT("NF_NEXT"),
    BNF_NEXT("BNF_NEXT"),
    FN_NEXT("FN_NEXT"),
    MC_CURRENT("MC_CURRENT"),
    MC_NEXT("MC_NEXT");
    public String expiryName;
    private Expiry(String expiryName){
        this.expiryName=expiryName;
    }
}
