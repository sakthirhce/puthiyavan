package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity(name = "trade_strategy")
public class TradeStrategy {
    @Id
    @Column(name="trade_strategy_key", nullable =false)
    String  tradeStrategyKey;
    String  index ; //NIFTY/BNF
    String entryTime;
    String candleCheckTime;
    String tradeValidity; //MIS,BTST
    String exitTime;
    String intradayExitTime;
    String tradeDays;
    String userId;
    String strikeSelectionType ;//ATM/Price Range
    BigDecimal strikePriceRangeLow ;//350
    BigDecimal strikePriceRangeHigh ;//450
    BigDecimal strikeClosestPremium ;//5
    String orderType ; //BUY/SELL
    String  strikeType ; //PE/CE
    String entryOrderType ; //market/limit
    String exitOrderType ; //market/limit
    boolean simpleMomentum ;
    String simpleMomentumType ; //percent/point
    BigDecimal simpleMomentumValue ;
    boolean rangeBreak ;
    boolean strategyEnabled;
    String rangeBreakType ; //percent/point
    String rangeBreakTime ;
    String rangeStartTime ;
    BigDecimal rangeBreakValue ;
    String rangeBreakSide ; //high/low
    String rangeBreakInstrument ; //index/options
    boolean  reentry ;
    int intradayLotSiz;
    int intradayLotSize;
    int lotSize;
    int positionalLotSize;
    String reentryType ; //ASAP/COST
    BigDecimal reentryCount ;
    String slType ; //percent/point
    BigDecimal slValue ;
    String trailSlType ; //percent/point
    BigDecimal trailSlMoves ;
    BigDecimal trailSlMove ;
    String slOrderType ;//market/limit
    boolean target ;
    String targetType ;//percent/point
    BigDecimal targetValue ;
    String targetOrderType ;//market/limit
    String aliasName;
    BigDecimal rangeLow ;
    BigDecimal rangeHigh ;
}
