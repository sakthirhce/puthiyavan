package com.sakthi.trade.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class GlobalTick {
    public static final int MAX_SIZE = 100;
    List<Double> historicalDataMap=new ArrayList<>(MAX_SIZE);
}