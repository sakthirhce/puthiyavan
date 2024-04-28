package com.sakthi.trade.cache;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GlobalTick {
    Map<String, String> historicalDataMap=new HashMap<>();
}