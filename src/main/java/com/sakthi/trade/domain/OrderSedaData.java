package com.sakthi.trade.domain;

import com.zerodhatech.models.OrderParams;
import lombok.Getter;
import lombok.Setter;
import com.sakthi.trade.zerodha.account.User;
@Getter
@Setter
public class OrderSedaData {
    User user;
    OrderParams orderParams;
    String orderId;
    String orderModificationType;
}
