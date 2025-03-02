package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
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
    @Transient
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
    BigDecimal simpleMomentumValue;
    boolean rangeBreak ;
    boolean strategyEnabled;
    String rangeBreakType ; //percent/point
    String rangeType ;
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
    String trailSlType ; //percent/points
    boolean trailEnabled ;
    BigDecimal trailSlMoves;
    BigDecimal trailPointMove ;
    String slOrderType ;//market/limit
    boolean target ;
    String targetType ;//percent/point
    BigDecimal targetValue ;
    String targetOrderType ;//market/limit
    String aliasName;
    @Column(name="trail_to_cost", nullable=true)
    boolean trailToCost;
    BigDecimal rangeLow ;
    BigDecimal rangeHigh ;
    @Transient
    UserSubscriptions userSubscriptions;
    int rangeCandleInterval;
    int bbsWindow;
    int multiplier;
    boolean sPositionTaken;
    boolean noSl;
    boolean noExit;
    double sz;
    boolean websocketSlEnabled;
    String tempSlType;
    BigDecimal tempSlValue;
    boolean hedge;
    @Column(name="freak_sl_place")
    boolean freakSlPlace;
    boolean freakBuy;
}
