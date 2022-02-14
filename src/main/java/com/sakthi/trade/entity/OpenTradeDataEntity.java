package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@Entity(name="open_trade_data")
public class OpenTradeDataEntity {
    String stockName;
    int qty;
    BigDecimal price;
    BigDecimal slPrice;
    String userId;
    String status;
    int stockId;
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


