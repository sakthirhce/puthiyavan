package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@Entity(name="position_pl_data")
public class PositionPLDataEntity {
    @Id
    @Column(name="DATA_KEY", nullable =false)
    String dataKey;
    BigDecimal pl;
    Timestamp dataTime;

}


