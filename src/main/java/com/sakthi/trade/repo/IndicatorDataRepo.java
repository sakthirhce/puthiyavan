package com.sakthi.trade.repo;

import com.sakthi.trade.domain.OpenPositionData;
import com.sakthi.trade.entity.IndicatorData;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface IndicatorDataRepo extends JpaRepository<IndicatorData,String> {
    @Query(value="select * from (select * from indicator_data order by candle_time desc limit 20) as indicator_data order by candle_time asc",nativeQuery=true)
    List<IndicatorData> getLast20IndicatorData();
}

