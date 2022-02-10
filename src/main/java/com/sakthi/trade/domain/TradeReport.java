package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeReport {
    String month;
    double profit;
    int winningTrade;
    int lossTrade;
    int totalTrade;
    int tslCostTrade;
    double avargeProfit;
}
