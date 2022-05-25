package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class TradeData {

    String stockName;
    int qty=0;
    String dataKey;
    BigDecimal reverseSlPrice;
    BigDecimal reverseSlTradedPrice;
    BigDecimal buyPrice;
    BigDecimal buyTradedPrice;
    String reverseExitTime;
    BigDecimal reverseSellTradedPrice;
    BigDecimal sellPrice;
    BigDecimal sellTradedPrice;
    String buyTime;
    String sellTime;
    String slTime;
    String reverseSlTime;
    int stockId;
    BigDecimal slPercentage;
    String userId;
    String zerodhaExchangeId;
    String parentEntry;
    public boolean isOrderPlaced;
    public boolean isSLCancelled;
    public boolean isCancelled;
    public boolean isSLHit=false;
    public boolean isReverseSLHit=false;
    public boolean isSlPlaced;
    public boolean isReverseTradePlaced;
    public boolean isReverseSLPlaced;
    public boolean isExited;
    String entryOrderId;
    String reverseOrderId;
    String slOrderId;
    BigDecimal slPrice;
    BigDecimal targetPrice;
    BigDecimal slTradedPrice;
    String reverseSLOrderId;
    String entryType;
    int strike;
    BigDecimal amountPerStock;
    public boolean isErrored;
    public Date createTimestamp;
    public String trueDataSymbol;
    public String fyersSymbol;
    String exitOrderId;
    BigDecimal highPrice;
    BigDecimal lowPrice;
    BigDecimal slTrialPoints;
    BigDecimal slTrialPercentage;
    int pyramidCount;
    int pyramidQty;
    String pyramidTime;
    String pyramidTime1;
    int stopLossCount;
    String comment;
    BigDecimal entrySlipage;
    BigDecimal slSlipage;
    boolean isSLModified;
    BigDecimal exitSlipage;
    BigDecimal entrySystemPrice;
    BigDecimal exitSystemPrice;
    BigDecimal slExecutedPrice;
    BigDecimal profitLoss;
    int rentryCount;

}