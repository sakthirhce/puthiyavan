package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class TradeDataCrypto {

    String stockName;
    double qty=0;

    double reverseSlPrice;
    double reverseSlTradedPrice;
    double buyPrice;
    double buyTradedPrice;
    String reverseExitTime;
    double reverseSellTradedPrice;
    double sellPrice;
    double sellTradedPrice;
    String buyTime;
    String sellTime;
    String slTime;
    String reverseSlTime;
    int stockId;
    String zerodhaExchangeId;
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
    double slPrice;
    double targetPrice;
    double slTradedPrice;
    String reverseSLOrderId;
    String entryType;
    int strike;
    double amountPerStock;
    public boolean isErrored;
    public Date createTimestamp;
    public String trueDataSymbol;
    public String fyersSymbol;
    String exitOrderId;
    double highPrice;
    double lowPrice;
    double slTrialPoints;
    double slTrialPercentage;
    int pyramidCount;
    int pyramidQty;
    String pyramidTime;
    String pyramidTime1;
    int stopLossCount;
    double marginTotal;

}