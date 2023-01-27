package com.sakthi.trade.repo;

import com.sakthi.trade.entity.TradeUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeUserRepository extends JpaRepository<TradeUserEntity,String> {

    @Query(value="select * from trade_user  where enabled=true",nativeQuery=true)
    List<TradeUserEntity> getActiveUser();

}
