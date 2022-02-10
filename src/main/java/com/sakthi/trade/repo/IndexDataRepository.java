package com.sakthi.trade.repo;

import com.sakthi.trade.entity.IndexDataEntity;
import com.sakthi.trade.entity.IndexEntity;
import com.sakthi.trade.entity.StockDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndexDataRepository extends JpaRepository<IndexDataEntity,String> {

    @Query(value="select * from INDEX_DATA where index_key in (select index_key from index where index_name=:symbol) and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<IndexDataEntity> findSymbol(@Param("symbol") String symbol, @Param("dateFrom") String fromdate, @Param("dateTo") String toDate);

}
