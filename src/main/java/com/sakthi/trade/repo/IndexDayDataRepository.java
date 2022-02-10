package com.sakthi.trade.repo;

import com.sakthi.trade.entity.IndexDataEntity;
import com.sakthi.trade.entity.IndexDayDataEntity;
import com.sakthi.trade.entity.IndexMonthlyDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndexDayDataRepository extends JpaRepository<IndexDayDataEntity,String> {

    @Query(value="select * from INDEX_DAY_DATA where index_key in (select index_key from index where index_name=:symbol) and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<IndexDayDataEntity> findSymbol(@Param("symbol") String symbol, @Param("dateFrom") String fromdate, @Param("dateTo") String toDate);

    @Query(value="select * from INDEX_DAY_DATA where index_key=:symbol and trade_time = cast(:dateFrom AS timestamp)",nativeQuery=true)
    IndexDayDataEntity findSymbolWithDate(@Param("symbol") String symbol, @Param("dateFrom") String fromdate);

    @Query(value="select max(trade_time) from INDEX_DAY_DATA cfd where index_key=:symbol",nativeQuery=true)
    String findLastDate(@Param("symbol") String symbol);

    @Query(value="select * from INDEX_DAY_DATA cfd where index_key=:symbol order by trade_time DESC LIMIT 1 ",nativeQuery=true)
    IndexDayDataEntity findLastRecord(@Param("symbol") String symbol);

    @Query(value="select * from (select * from INDEX_DAY_DATA cfd where index_key=:symbol and trade_time < cast(:dateFrom AS timestamp) order by trade_time DESC) as a LIMIT 1 ",nativeQuery=true)
    IndexDayDataEntity findHistoricLastRecord(@Param("symbol") String symbol, @Param("dateFrom") String fromdate);
}
