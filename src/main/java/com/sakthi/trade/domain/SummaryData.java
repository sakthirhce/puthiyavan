package com.sakthi.trade.domain;

import java.math.BigDecimal;

public class SummaryData {
    public java.sql.Date tradeDate;
    public java.sql.Date entryDate;
    public java.sql.Timestamp entryTime;
    private java.sql.Timestamp exitTime;
    public BigDecimal profit;
    public Double currentDD;
    public int ddDays;
    public BigDecimal plAfterCharges;
}
