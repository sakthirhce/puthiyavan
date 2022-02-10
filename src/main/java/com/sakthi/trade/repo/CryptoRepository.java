package com.sakthi.trade.repo;

import com.sakthi.trade.entity.CryptoFuturesEntity;
import com.sakthi.trade.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CryptoRepository extends JpaRepository<CryptoFuturesEntity,String> {

    @Query(value="select * from Crypto_Futures where symbol=?1",nativeQuery=true)
    List<CryptoFuturesEntity> findSymbol( String symbol);

    @Query(value = "select symbol from Crypto_Futures where symbol not in (select distinct symbol from Crypto_Futures_DATA)" ,nativeQuery=true)
    List<CryptoFuturesEntity> findMissingStockData();
}
