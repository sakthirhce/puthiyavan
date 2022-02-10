package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Brokerage {
    int qty;
    BigDecimal buyPrice;
    BigDecimal sellPrice;
    BigDecimal totalCharges;
    BigDecimal brokerCharge;
    BigDecimal stt;
    BigDecimal transactionCharges;
    BigDecimal gst;
    BigDecimal stampDuty;
    BigDecimal slipageCost;
}
