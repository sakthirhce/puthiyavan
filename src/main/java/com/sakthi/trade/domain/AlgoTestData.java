package com.sakthi.trade.domain;

import java.math.BigDecimal;
public class AlgoTestData {
    String instrument;
    String algoName;
    int qty=0;
    String dataKey;
    BigDecimal buyPrice;
    BigDecimal sellPrice;
    String userId;
    String entryType;
    int strike;
    BigDecimal profitLoss;
    public java.sql.Date exitDate;
    public java.sql.Date tradeDate;
    public java.sql.Date entryDate;
    public java.sql.Timestamp entryTime;
    private java.sql.Timestamp exitTime;
    public BigDecimal charges;
    public BigDecimal plAfterCharges;
}
