package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class PreOpenDetails {

    String stockName;
    double previousClose;
    double openPrice;
    double perCh;
    Date tradeDate;
}
