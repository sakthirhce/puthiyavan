package com.sakthi.trade.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class GlobalTick {
    List<Double> historicalDataMap=new ArrayList<>();
}