package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Getter
@Setter
@Entity(name = "Crypto_Futures")
public class CryptoFuturesEntity {

    @Id
    @Column(name="symbol", nullable =false)
    String symbol;
}

