package com.sakthi.trade.zerodha.account;

import com.sakthi.trade.domain.TradeData;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class StraddleConfig {
    boolean enabled;
    boolean nrmlEnabled;
    public Map<String, TradeData> straddleTradeMap = new HashMap<>();
    public Map<String, String> lotConfig;
    public BuyConfig buyConfig;
}
