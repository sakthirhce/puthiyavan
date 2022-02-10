package com.sakthi.trade.repo;

import com.sakthi.trade.entity.CryptoFuturesDataEntity;
import com.sakthi.trade.entity.StockDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CryptoFuturesDataRepository extends JpaRepository<CryptoFuturesDataEntity,String> {

    @Query(value="select * from Crypto_Futures_DATA where symbol=:symbol and trade_time between cast(:dateFrom AS timestamp) and cast(:dateTo AS timestamp)",nativeQuery=true)
    List<CryptoFuturesDataEntity> findSymbol(@Param("symbol") String symbol,@Param("dateFrom") String fromdate,@Param("dateTo") String toDate);
    @Query(value="select max(trade_time) from crypto_futures_data cfd where symbol=:symbol",nativeQuery=true)
    String findLastDate(@Param("symbol") String symbol);

}
