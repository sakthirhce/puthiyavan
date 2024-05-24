package com.sakthi.trade.cache;

import com.zerodhatech.models.HistoricalData;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GlobalContext {
    Map<String, String> historicalDataMap=new HashMap<>();
}