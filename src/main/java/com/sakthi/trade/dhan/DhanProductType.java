package com.sakthi.trade.dhan;

public enum DhanProductType {
    NRML("CNC"),
    MIS("INTRADAY"),
    CO("CO"),
    BO("BO");
    private String productType;
    public String getProductType()
    {
        return this.productType;
    }
     DhanProductType(String productType){
        this.productType=productType;
    }
}
