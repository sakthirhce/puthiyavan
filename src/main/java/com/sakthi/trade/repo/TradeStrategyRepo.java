package com.sakthi.trade.repo;

import com.sakthi.trade.entity.TradeStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeStrategyRepo  extends JpaRepository<TradeStrategy,String> {
    @Query(value="select ts.* from trade_strategy ts where ts.strategy_enabled=true",nativeQuery=true)
    List<TradeStrategy> getActiveUsersActiveStrategy();
}
