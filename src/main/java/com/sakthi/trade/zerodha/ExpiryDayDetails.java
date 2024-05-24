package com.sakthi.trade.zerodha;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class ExpiryDayDetails {
    public boolean finNiftyExpiry;
    public boolean midCapExpiry;
    public boolean niftyExpiry;
    public boolean bankNiftyExpiry;
    public boolean sensexExpiry;
    public String expiryStockId;
    public String expIndexName;
    public Map<String, Map<String, String>> expiryOptions = new HashMap<>();
    public long expiryCurrentAtmValue;
    public long finNiftyCurrentAtmValue;
    public long midCapCurrentAtmValue;
    public long niftyCurrentAtmValue;
    public long bankNiftyCurrentAtmValue;
    public long sensexCurrentAtmValue;

}
