package com.sakthi.trade.repo;

import com.sakthi.trade.entity.BankNiftyOptionDataEntity;
import com.sakthi.trade.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockRepository extends JpaRepository<StockEntity,String> {

    @Query(value="select * from Stock where symbol=?1",nativeQuery=true)
    List<StockEntity> findSymbol( String symbol);

    @Query(value = "select * from stock where symbol not in (select distinct symbol from stock_data)" ,nativeQuery=true)
    List<StockEntity> findMissingStockData();
}
