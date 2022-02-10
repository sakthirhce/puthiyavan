package com.sakthi.trade.repo;

import com.sakthi.trade.entity.CryptoFuturesDataEntity;
import com.sakthi.trade.entity.CryptoFuturesDayDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CryptoFuturesDayDataRepository extends JpaRepository<CryptoFuturesDayDataEntity,String> {

    @Query(value="select * from crypto_futures_day_data where symbol=:symbol and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<CryptoFuturesDataEntity> findSymbol(@Param("symbol") String symbol,@Param("dateFrom") String fromdate,@Param("dateTo") String toDate);
    @Query(value="select max(trade_time) from crypto_futures_day_data cfd where symbol=:symbol",nativeQuery=true)
    String findLastDate(@Param("symbol") String symbol);
    @Query(value="select * from crypto_futures_day_data where symbol=:symbol and trade_time< cast(:dateTo AS timestamp) ",nativeQuery=true)
    List<CryptoFuturesDayDataEntity> findSymbolData(@Param("symbol") String symbol,@Param("dateTo") LocalDate toDate);
    @Query(value="select max(close) from crypto_futures_day_data where symbol=:symbol and trade_time< cast(:dateTo AS timestamp) ",nativeQuery=true)
    String findSymbolMaxClose(@Param("symbol") String symbol,@Param("dateTo") LocalDate toDate);
}
