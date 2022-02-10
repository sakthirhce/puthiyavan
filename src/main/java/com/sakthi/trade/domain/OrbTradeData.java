package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class OrbTradeData {
    String stockName;
    int qty;
    BigDecimal slPrice;
    BigDecimal slTradedPrice;
    BigDecimal buyPrice;
    BigDecimal buyTradedPrice;
    BigDecimal sellPrice;
    BigDecimal sellTradedPrice;
    String buyTime;
    String sellTime;
    int stockId;
    public boolean isOrderPlaced;
    public boolean isSLCancelled;
    public boolean isCancelled;
    public boolean isSlPlaced;
    public boolean isReverseTradePlaced;
    public boolean isReverseSLPlaced;
    public boolean isExited;
    public boolean isSLHit;
    String entryOrderId;
    String reverseOrderId;
    String slOrderId;
    String reverseSLOrderId;
    String entryType;
    BigDecimal amountPerStock;
    public boolean isErrored;
    public Date createTimestamp;
    public String trueDataSymbol;
    public String fyersSymbol;
    String exitOrderId;
    BigDecimal highPrice;
    BigDecimal lowPrice;
}