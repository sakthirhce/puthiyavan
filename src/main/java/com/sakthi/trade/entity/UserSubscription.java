package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;

@Getter
@Setter
@Entity(name = "user_subscription")
public class UserSubscription {
    @Column(name="trade_strategy_key", nullable =false)
    String  tradeStrategyKey;
    @Id
    @Column(name="user_subscription_key", nullable =false)
    String  userSubscriptionKey;
    String  userId;
    int lotSize;
}
