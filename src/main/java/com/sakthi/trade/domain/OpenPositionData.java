package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenPositionData {
    String stockName;
    int qty;
    public OpenPositionData(String stock,int qty){
        this.qty=qty;
        this.stockName=stock;
    }
}
