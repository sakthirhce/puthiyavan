package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddTrade {
    String orderId;
    String userId;
    String stockId;
    String strategyName;
}
