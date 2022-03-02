package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@Entity(name="open_trade_data")
public class OpenTradeDataEntity {
    @Id
    @Column(name="DATA_KEY", nullable =false)
    String dataKey;
    String stockName;
    int qty;
    BigDecimal buyPrice;
    BigDecimal sellPrice;
    BigDecimal slPrice;
    BigDecimal slPercentage;
    String userId;
    String status;
    int stockId;
    String algoName;
    public boolean isOrderPlaced;
    public boolean isSlPlaced;
    public boolean isSlCancelled;
    public boolean isExited;
    public boolean isSLHit;
    String entryOrderId;
    String exitOrderId;
    String slOrderId;
    String entryType;
    BigDecimal amountPerStock;
    public boolean isErrored;
    public Date createTimestamp;
}


