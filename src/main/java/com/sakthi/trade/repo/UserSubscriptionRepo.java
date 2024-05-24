package com.sakthi.trade.repo;

import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserSubscriptionRepo extends JpaRepository<UserSubscription,String> {
    @Query(value="select ts.* from user_subscription ts where ts.trade_strategy_key=?1",nativeQuery=true)
    List<UserSubscription> getUserSubs(String strategyKey);

}
