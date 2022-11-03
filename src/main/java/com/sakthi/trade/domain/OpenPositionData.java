package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;


public interface OpenPositionData {
    String getStockName();
    int getQty();
}
