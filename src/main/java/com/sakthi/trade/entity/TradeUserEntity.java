package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Getter
@Setter
@Entity(name = "trade_user")
public class TradeUserEntity {
    @Id
    @Column(name="user_id", nullable =false)
    String userId;
    @Column(name="broker", nullable =false)
    String broker;
    @Column(name="enabled")
    boolean enabled;
}

