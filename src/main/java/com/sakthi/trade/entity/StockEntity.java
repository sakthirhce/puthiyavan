package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

@Getter
@Setter
@Entity(name = "Stock")
public class StockEntity{

    @Id
    @Column(name="symbol", nullable =false)
    String symbol;
    @Column(name="fyer_symbol", nullable =false)
    String fyerSymbol;
}

