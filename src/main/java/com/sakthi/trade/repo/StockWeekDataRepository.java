package com.sakthi.trade.repo;

import com.sakthi.trade.entity.StockDataEntity;
import com.sakthi.trade.entity.StockWeekDataEntity;
import com.sakthi.trade.entity.StockYearDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockWeekDataRepository extends JpaRepository<StockWeekDataEntity,String> {

    @Query(value="select * from STOCK_WEEK_DATA where symbol=:symbol and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<StockWeekDataEntity> findSymbol(@Param("symbol") String symbol,@Param("dateFrom") String fromdate,@Param("dateTo") String toDate);
    @Query(value="select * from STOCK_WEEK_DATA where symbol=:symbol and trade_time = cast(:dateFrom AS timestamp)",nativeQuery=true)
    StockWeekDataEntity findSymbolWithDate(@Param("symbol") String symbol, @Param("dateFrom") String fromdate);

}
