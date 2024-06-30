package com.sakthi.trade.service;

import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TradingStrategyAndTradeData {

    public Map<String, Map<String, List<TradeStrategy>>> strategyMap = new LinkedHashMap<>();
    public Map<String, List<TradeStrategy>> rangeStrategyMap = new ConcurrentHashMap<>();

    public Map<String, List<TradeData>> openTrade = Collections.synchronizedMap(new LinkedHashMap<>());
}
