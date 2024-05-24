package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

@Getter
@Setter
@Entity(name = "INDEX_YEAR_DATA")
public class IndexYearDataEntity {
    @Column(name="index_key", nullable =false)
    String indexKey;
    @Id
    @Column(name="DATA_KEY", nullable =false)
    String dataKey;
    @Column(name="OPEN", nullable=false)
    Double open;
    @Column(name="HIGH", nullable=false)
    Double high;
    @Column(name="LOW", nullable=false)
    Double low;
    @Column(name="CLOSE", nullable=false)
    Double close;
    @Column(name="VWAP")
    Double vwap;
    @Column(name="TRADE_TIME", nullable=false)
    Date tradeTime;
    @Column(name="VOLUME")
    Integer volume;
}

