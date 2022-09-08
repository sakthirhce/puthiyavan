package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AlgoStrategyData {
    String stockName;
    int qty;
    List<String> dayConfig=new ArrayList<>();
    public AlgoStrategyData(String stock, int qty){
        this.qty=qty;
        this.stockName=stock;
    }
    public AlgoStrategyData(String stock, int qty, List<String> dayConfig){
        this.qty=qty;
        this.stockName=stock;
        this.dayConfig=dayConfig;
    }
}
