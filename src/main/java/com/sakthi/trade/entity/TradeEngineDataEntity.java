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
@Entity(name="trade_engine_data")
public class TradeEngineDataEntity {
    @Id
    @Column(name="DATA_KEY", nullable =false)
    String dataKey;
    String stockName;
    int qty;
    BigDecimal buyPrice;
    BigDecimal sellPrice;
    BigDecimal buyTradedPrice;
    BigDecimal sellTradedPrice;
    BigDecimal slPrice;
    BigDecimal slPercentage;
    BigDecimal entrySlipage;
    BigDecimal slSlipage;
    BigDecimal exitSlipage;
    BigDecimal entrySystemPrice;
    BigDecimal exitSystemPrice;
    BigDecimal slExecutedPrice;
    BigDecimal profitLoss;
    String userId;
    String status;
    int stockId;
    String algoName;
    public boolean isOrderPlaced;
    public boolean isSlPlaced;
    public boolean isSlCancelled;
    public boolean isExited;
    @Column(name="isslhit")
    public boolean isSLHit=false;
    String entryOrderId;
    String exitOrderId;
    String slOrderId;
    String entryType;
    BigDecimal amountPerStock;
    public boolean isErrored;
    public Date createTimestamp;
    public String tradeDate;
    public String modifyDate;
    public BigDecimal charges;
    public BigDecimal plAfterCharges;
}


