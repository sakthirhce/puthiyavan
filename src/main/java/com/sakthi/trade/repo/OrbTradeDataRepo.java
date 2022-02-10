/*
package com.sakthi.trade.repo;

import com.sakthi.trade.entity.OrbStockDataEntity;
import com.sakthi.trade.entity.OrbTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface OrbTradeDataRepo extends JpaRepository<OrbTradeDataEntity,String> {

    @Query(value="select * from orb_trade_data where create_timestamp=?1",nativeQuery=true)
    public List<OrbTradeDataEntity> getOrbTradeDataEntityByCreateTimestampIn(Date createtimestamp);
}
*/
