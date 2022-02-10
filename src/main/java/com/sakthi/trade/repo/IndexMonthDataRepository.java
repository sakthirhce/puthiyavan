package com.sakthi.trade.repo;

import com.sakthi.trade.entity.IndexDayDataEntity;
import com.sakthi.trade.entity.IndexMonthlyDataEntity;
import com.sakthi.trade.entity.IndexWeekDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndexMonthDataRepository extends JpaRepository<IndexMonthlyDataEntity,String> {

    @Query(value="select * from INDEX_MONTH_DATA where index_key in (select index_key from index where index_name=:symbol) and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<IndexMonthlyDataEntity> findSymbol(@Param("symbol") String symbol, @Param("dateFrom") String fromdate, @Param("dateTo") String toDate);

    @Query(value="select * from INDEX_MONTH_DATA where index_key=:symbol and trade_time = cast(:dateFrom AS timestamp)",nativeQuery=true)
    IndexMonthlyDataEntity findSymbolWithDate(@Param("symbol") String symbol, @Param("dateFrom") String fromdate);

}
