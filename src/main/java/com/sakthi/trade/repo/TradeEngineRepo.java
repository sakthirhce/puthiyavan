package com.sakthi.trade.repo;

import com.sakthi.trade.entity.TradeEngineDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TradeEngineRepo extends JpaRepository<TradeEngineDataEntity,String> {
}
