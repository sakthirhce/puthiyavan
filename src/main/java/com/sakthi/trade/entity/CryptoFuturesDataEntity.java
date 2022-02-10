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
@Entity(name = "Crypto_Futures_DATA")
public class CryptoFuturesDataEntity {
    @Column(name="symbol", nullable =false)
    String symbol;
    @Id
    @Column(name="DATA_KEY", nullable =false)
    String dataKey;
    @Column(name="OPEN", nullable=false, precision = 20, scale = 10)
    BigDecimal open;
    @Column(name="HIGH", nullable=false, precision = 20, scale = 10)
    BigDecimal high;
    @Column(name="LOW", nullable=false, precision = 20, scale = 10)
    BigDecimal low;
    @Column(name="CLOSE", nullable=false,precision = 20, scale = 10)
    BigDecimal close;
    @Column(name="VWAP", nullable=false, precision = 20, scale = 10)
    BigDecimal vwap;
    @Column(name="TRADE_TIME", nullable=false)
    Date tradeTime;
    @Column(name="VOLUME", nullable = false)
    Integer volume;
}

