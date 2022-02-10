package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

@Getter
@Setter
@Entity(name = "bank_nifty_option")
public class BankNiftyOptionEntity {

    @Id
    @Column(name="EXP_KEY", nullable =false)
    String expKey;

    @Column(name="STRIKE", nullable = false)
    String strike;

    @Column(name="OPTION_TYPE", nullable=false)
    String optionType;

    @Column(name="EXP_DATE", nullable = false)
    Date expDate;

}
