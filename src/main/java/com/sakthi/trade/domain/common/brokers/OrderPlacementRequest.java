package com.sakthi.trade.domain.common.brokers;

import com.sakthi.trade.zerodha.account.StrikeData;
import lombok.Data;

@Data
public class OrderPlacementRequest {
    StrikeData strikeData;
    String stockName;
    String stockId;
    String orderType;
    String transactionType;
    String exchange;
    String exchangeSegment;
    String validity;
    String productType;
    double price;
    double triggerPrice;
    int qty;
    String accessToken;

}
