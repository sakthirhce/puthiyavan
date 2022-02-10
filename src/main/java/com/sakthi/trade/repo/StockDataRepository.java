package com.sakthi.trade.repo;

import com.sakthi.trade.entity.StockDataEntity;
import com.sakthi.trade.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockDataRepository extends JpaRepository<StockDataEntity,String> {

    @Query(value="select * from Stock_data where symbol=:symbol and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<StockDataEntity> findSymbol(@Param("symbol") String symbol,@Param("dateFrom") String fromdate,@Param("dateTo") String toDate);
}
