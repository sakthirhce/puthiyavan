package com.sakthi.trade.repo;

import com.sakthi.trade.entity.IndexEntity;
import com.sakthi.trade.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity,String> {

    @Query(value="select * from index where index_name=?1",nativeQuery=true)
    List<IndexEntity> findSymbol( String symbol);

}
