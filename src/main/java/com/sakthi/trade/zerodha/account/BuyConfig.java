package com.sakthi.trade.zerodha.account;

import com.sakthi.trade.domain.TradeData;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class BuyConfig {
    boolean enabled;
    int lotSize;
    public Map<String, TradeData> straddleTradeMap = new HashMap<>();
    public Map<String, TradeData> straddlePreviousDayTradeMap = new HashMap<>();
    public Map<String, String> lotConfig;
}
