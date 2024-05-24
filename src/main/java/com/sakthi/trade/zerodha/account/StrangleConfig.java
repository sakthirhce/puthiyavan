package com.sakthi.trade.zerodha.account;

import com.sakthi.trade.domain.TradeData;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class StrangleConfig {
    boolean enabled;
    int lot;
    List<String> days;
    public Map<String, TradeData> strangleTradeMap = new HashMap<>();

}
