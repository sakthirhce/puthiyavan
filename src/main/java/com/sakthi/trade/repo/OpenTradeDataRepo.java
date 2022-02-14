package com.sakthi.trade.repo;

import com.sakthi.trade.entity.OpenTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface OpenTradeDataRepo extends JpaRepository<OpenTradeDataEntity,String> {

    @Query(value="select * from open_trade_data where create_timestamp=?1",nativeQuery=true)
    public List<OpenTradeDataEntity> getOrbTradeDataEntityByCreateTimestampIn(Date createtimestamp);
}

