package com.sakthi.trade.domain;

import lombok.Setter;

import java.util.List;

public class OptionCandleChartData {
    @Setter
    String name;
    @Setter
    List<OptionCandleData> data;
    @Setter
    List<OptionCandleData> data1;
}
