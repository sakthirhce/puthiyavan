package com.sakthi.trade.dhan;

public enum Validity {
    DAY("DAY"),
    IOC("IOC");
    private String validity;
    private Validity(String validity){
        this.validity=validity;
    }
}
