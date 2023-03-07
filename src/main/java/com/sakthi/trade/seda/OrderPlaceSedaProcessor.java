package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.sakthi.trade.domain.OrderSedaContract;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderPlaceSedaProcessor implements Processor {
    @Autowired
    TelegramMessenger telegramMessenger;

    @Autowired
    UserList userList;

    @Autowired
    BrokerWorkerFactory brokerWorkerFactory;
   Gson gson= new Gson();
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        String message = camelContextRoute.getIn().getBody().toString();
        OrderSedaContract orderSedaContract=gson.fromJson(message,OrderSedaContract.class);

        userList.getUser().stream().filter(
                user -> user.getName() != null && user.getName().equals(orderSedaContract.getUserId())
        ).forEach(user -> {
            BrokerWorker worker= brokerWorkerFactory.getWorker(orderSedaContract.getBroker());
            try {
                worker.placeOrder(orderSedaContract.getOrderParams(),user,null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (KiteException e) {
                throw new RuntimeException(e);
            }
        });

    }
}
