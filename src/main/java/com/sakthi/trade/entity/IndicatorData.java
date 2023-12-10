package com.sakthi.trade.entity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "indicator_data")
@Getter
@Setter
public class IndicatorData {

    @Id
    @Column(name = "indicator_data_key", length = 100)
    private int indicatorDataKey;

    @Column(name = "data_key", length = 100)
    private String dataKey;

    @Column(name = "candle_time")
    private Timestamp candleTime;

    @Column(name = "open", precision = 10, scale = 2)
    private BigDecimal open;

    @Column(name = "high", precision = 10, scale = 2)
    private BigDecimal high;

    @Column(name = "low", precision = 10, scale = 2)
    private BigDecimal low;

    @Column(name = "close", precision = 10, scale = 2)
    private BigDecimal close;

    @Column(name = "volume", precision = 50, scale = 2)
    private BigDecimal volume;

    @Column(name = "oi", precision = 50, scale = 2)
    private BigDecimal oi;

    @Column(name = "vwap", precision = 10, scale = 2)
    private BigDecimal vwap;

    @Column(name = "bb_upperband", precision = 50, scale = 2)
    private BigDecimal bbUpperband;

    @Column(name = "bb_lowerband", precision = 50, scale = 2)
    private BigDecimal bbLowerband;

    @Column(name = "bb_sma", precision = 50, scale = 2)
    private BigDecimal bbSma;
    // Constructors, Getters, and Setters

    public IndicatorData() {
    }

    // Getters and Setters

    // Add the rest of the getters and setters for all fields

    // Override toString, equals, and hashCode methods as necessary
}


