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
@Entity(name="strangle_trade_data")
public class StrangleTradeDataEntity {
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
    public boolean isExited;
    String entryOrderId;
    String slOrderId;
    String entryType;
    BigDecimal amountPerStock;
    public boolean isErrored;
    public Date createTimestamp;
}


