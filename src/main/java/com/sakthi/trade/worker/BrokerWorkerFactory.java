package com.sakthi.trade.worker;

import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.domain.common.brokers.OrderPlacementRequest;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.User;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrokerWorkerFactory {
    Map<String, BrokerWorker> orderWorkerMap=new HashMap<>();

    @Autowired
    List<BrokerWorker> orderWorkerList;

    @PostConstruct
    public void initWorkerFactory(){
        orderWorkerList.stream().forEach(orderWorker -> {
            orderWorkerMap.put(orderWorker.broker(),orderWorker);
        });
    }
    public BrokerWorker getWorker(User user){
        return orderWorkerMap.get(user.getBroker().toUpperCase());
    }
    public BrokerWorker getWorker(String brokerName){
        return orderWorkerMap.get(brokerName.toUpperCase());
    }
}
