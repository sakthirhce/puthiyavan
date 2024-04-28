package com.sakthi.trade.seda;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TickData {
    public Map<String, Map<String, Double>> tickCurrentPrice = new HashMap<>();
}
