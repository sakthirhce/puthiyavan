package com.sakthi.trade.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OptionOIData {
    public long ceOI;
    public long peOI;
    public long ceOI2;
    public long peOI2;
    public long ceOI3;
    public long peOI3;
    public boolean isTrade;
    public boolean isTradePE;
    public boolean isTradeCE;
    public boolean isTradeExcecuted;
    String tradeTime;
}
