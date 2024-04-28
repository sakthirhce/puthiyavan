package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;

@Getter
@Setter
@Entity(name="live_pl_change")
public class LivePLDataEntity {
    @Id
    @Column(name="DATA_KEY", nullable =false)
    String dataKey;
    public String tradeStrategyKey;
    String stockName;
    BigDecimal close;
    BigDecimal pl;
    Timestamp dataTime;
    String entryType;
    String strikeType;
    String index;
    String openTradeDataKey;

}


