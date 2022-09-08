package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
@Getter
@Setter
@Entity(name = "ALGO_TEST_DATA")
public class AlgoTestEntity{
    String instrument;
    String algoName;
    int qty=0;
    @Id
    @Column(name="DATA_KEY", nullable =false)
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
    String entryDay;
}
