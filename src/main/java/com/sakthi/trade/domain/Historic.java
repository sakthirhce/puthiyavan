package com.sakthi.trade.domain;

import lombok.Data;

@Data
public class Historic {
    public double closeHigh;
    public double closeLow;
    public double recentClose;
    public int day;
}
