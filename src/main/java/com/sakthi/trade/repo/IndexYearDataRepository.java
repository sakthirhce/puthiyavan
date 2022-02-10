package com.sakthi.trade.repo;

import com.sakthi.trade.entity.IndexMonthlyDataEntity;
import com.sakthi.trade.entity.IndexYearDataEntity;
import com.sakthi.trade.entity.StockDayDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndexYearDataRepository extends JpaRepository<IndexYearDataEntity,String> {

    @Query(value="select * from INDEX_YEAR_DATA where index_key in (select index_key from index where index_name=:symbol) and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<IndexYearDataEntity> findSymbol(@Param("symbol") String symbol, @Param("dateFrom") String fromdate, @Param("dateTo") String toDate);
    @Query(value="select * from INDEX_YEAR_DATA where index_key=:symbol and trade_time = cast(:dateFrom AS timestamp)",nativeQuery=true)
    IndexYearDataEntity findSymbolWithDate(@Param("symbol") String symbol, @Param("dateFrom") String fromdate);


}
