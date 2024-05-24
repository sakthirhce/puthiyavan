package com.sakthi.trade.domain;

import lombok.Data;

@Data
public class OIChangeData {
    long previousOI;
    long currentOI;
    double oiPercentChange;
    boolean positiveChange;
    boolean negativeChange;
    String strikeName;
    boolean orderPlaced;
}
