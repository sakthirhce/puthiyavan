package com.sakthi.trade.zerodha.account;

import com.sakthi.trade.domain.TradeData;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class StraddleConfig {
    boolean enabled;
    boolean nrmlEnabled;
    int buy325Size;
    int lotSize;
    BigDecimal sl;
    String telegramToken;

    public Map<String, TradeData> straddleTradeMap = new ConcurrentHashMap<>();
    public Map<String, String> lotConfig;
    public BuyConfig buyConfig;
    public ReverseEntry reverseEntry;

}
