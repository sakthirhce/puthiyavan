package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.Entity;

import javax.persistence.Column;
import javax.persistence.Id;
import java.util.Date;

@Getter
@Setter
@Entity(name = "BANK_NIFTY_OPTION_DATA")
public class BankNiftyOptionDataEntity {

    @Id
    @Column(name="DATA_KEY", nullable =false)
    String optionDataKey;
    @Column(name="EXP_KEY", nullable =false)
    String expKey;
    @Column(name="OPEN", nullable=false)
    Double open;
    @Column(name="HIGH", nullable=false)
    Double high;
    @Column(name="LOW", nullable=false)
    Double low;
    @Column(name="CLOSE", nullable=false)
    Double close;
    @Column(name="VWAP", nullable=false)
    Double vwap;
    @Column(name="TRADE_TIME", nullable=false)
    Date tradeTime;
    @Column(name="VOLUME", nullable = false)
    Integer volume;
    @Column(name="OI", nullable = false)
    Integer oi;
}
