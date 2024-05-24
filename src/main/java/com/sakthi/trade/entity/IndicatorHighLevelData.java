package com.sakthi.trade.entity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "indicator_high_level_data")
public class IndicatorHighLevelData {

    @Id
    @Column(name = "data_key", nullable = false, length = 100)
    private String dataKey;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "strike_id", nullable = false, length = 100)
    private String strikeId;

    @Column(name = "interval", precision = 60, scale = 2)
    private BigDecimal interval;

    // Constructors, Getters, and Setters

    public IndicatorHighLevelData() {
    }

    public String getDataKey() {
        return dataKey;
    }

    public void setDataKey(String dataKey) {
        this.dataKey = dataKey;
    }

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public String getStrikeId() {
        return strikeId;
    }

    public void setStrikeId(String strikeId) {
        this.strikeId = strikeId;
    }

    public BigDecimal getInterval() {
        return interval;
    }

    public void setInterval(BigDecimal interval) {
        this.interval = interval;
    }

    // Override toString, equals, and hashCode methods as necessary
}
