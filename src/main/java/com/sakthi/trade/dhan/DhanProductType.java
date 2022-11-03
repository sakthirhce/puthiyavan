package com.sakthi.trade.dhan;

public enum DhanProductType {
    CNC("CNC"),
    INTRADAY("INTRADAY"),
    MARGIN("MARGIN"),
    CO("CO"),
    BO("BO");
    private String productType;
    private DhanProductType(String productType){
        this.productType=productType;
    }
}
