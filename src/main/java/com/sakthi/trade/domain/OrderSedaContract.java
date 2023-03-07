package com.sakthi.trade.domain;

import com.zerodhatech.models.OrderParams;
import lombok.Data;

@Data
public class OrderSedaContract {
    String userId;
    String broker;
    String payload;
    String dataKey;
    OrderParams orderParams;
}
