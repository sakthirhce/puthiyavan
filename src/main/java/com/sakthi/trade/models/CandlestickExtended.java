package com.sakthi.trade.models;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
@Setter
@Getter
public class CandlestickExtended {

        private Long openTime;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private Long closeTime;
        private BigDecimal quoteAssetVolume;
        private Integer numTrades;
        private BigDecimal takerBuyBaseAssetVolume;
        private BigDecimal takerBuyQuoteAssetVolume;
        private BigDecimal ignore;
        public double vwap;
        public double rsi;
        public double sma20;
        public double oima20;
        public double volumema50;
        public double volumema20;
    public CandlestickExtended() {
    }
    }

