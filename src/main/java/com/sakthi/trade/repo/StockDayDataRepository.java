package com.sakthi.trade.repo;

import com.sakthi.trade.entity.IndexDayDataEntity;
import com.sakthi.trade.entity.StockDataEntity;
import com.sakthi.trade.entity.StockDayDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockDayDataRepository extends JpaRepository<StockDayDataEntity,String> {

    @Query(value="select * from STOCK_DAY_DATA where symbol=:symbol and trade_time >= cast(:dateFrom AS timestamp) and trade_time <= cast(:dateTo AS timestamp)",nativeQuery=true)
    List<StockDayDataEntity> findSymbol(@Param("symbol") String symbol,@Param("dateFrom") String fromdate,@Param("dateTo") String toDate);

    @Query(value="select * from STOCK_DAY_DATA where symbol=:symbol and trade_time = cast(:dateFrom AS timestamp)",nativeQuery=true)
    StockDayDataEntity findSymbolWithDate(@Param("symbol") String symbol,@Param("dateFrom") String fromdate);

    @Query(value="select max(trade_time) from STOCK_DAY_DATA cfd where symbol=:symbol",nativeQuery=true)
    String findLastDate(@Param("symbol") String symbol);

    @Query(value="select * from STOCK_DAY_DATA cfd where symbol=:symbol order by trade_time DESC LIMIT 1 ",nativeQuery=true)
    StockDayDataEntity findLastRecord(@Param("symbol") String symbol);


    @Query(value="select max(high) from STOCK_DAY_DATA cfd where symbol=:symbol and trade_time >= cast(:dateFrom AS timestamp)",nativeQuery=true)
    String findHigh(@Param("symbol") String symbol,@Param("dateFrom") String fromdate);
    @Query(value="select * from STOCK_DAY_DATA cfd where symbol=:symbol and trade_time = cast(:dateFrom AS timestamp)",nativeQuery=true)
    StockDayDataEntity findRecord(@Param("symbol") String symbol,@Param("dateFrom") String fromdate);
}
