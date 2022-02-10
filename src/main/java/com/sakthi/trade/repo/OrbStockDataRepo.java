/*package com.sakthi.trade.repo;

import com.sakthi.trade.entity.OrbStockDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrbStockDataRepo extends JpaRepository<OrbStockDataEntity,String> {
    @Query(value="select * from ORB_STOCK_DATA where stock_price>='100' and stock_price<'20000' order by change_percentage",nativeQuery=true)
    public List<OrbStockDataEntity> findAllORB();
}*/
