package com.sakthi.trade.zerodha.account;

import lombok.Data;

import java.util.Map;

@Data
public class ReverseEntry {
    boolean enabled;
    int count;
    public Map<String, String> retryCountConfig;
}
