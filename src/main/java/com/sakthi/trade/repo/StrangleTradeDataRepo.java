package com.sakthi.trade.repo;

import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.entity.StrangleTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface StrangleTradeDataRepo extends JpaRepository<StrangleTradeDataEntity,String> {

    @Query(value="select * from strangle_trade_data where create_timestamp=?1",nativeQuery=true)
     List<StrangleTradeDataEntity> getOrbTradeDataEntityByCreateTimestampIn(Date createtimestamp);
    @Query(value="select * from public.strangle_trade_data",nativeQuery=true)
     List<StrangleTradeDataEntity> findAll();
}

