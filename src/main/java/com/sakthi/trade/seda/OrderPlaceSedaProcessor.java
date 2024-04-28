package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.telegram.TelegramMessenger;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderPlaceSedaProcessor implements Processor {
    public static final Logger LOGGER = LoggerFactory.getLogger(OrderPlaceSedaProcessor.class.getName());
    @Autowired
    BrokerWorkerFactory brokerWorkerFactory;
   Gson gson= new Gson();
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        try {
            Object message = camelContextRoute.getIn().getBody();
            OrderSedaData orderSedaContract = (OrderSedaData) message;
            BrokerWorker brokerWorker = brokerWorkerFactory.getWorker(orderSedaContract.getUser());
            orderSedaContract.getOrderParams().orderType = "MARKET";
            LOGGER.info("user:{},order-id:{},order data:{}", orderSedaContract.getUser().getName(), orderSedaContract.getOrderId(), gson.toJson(orderSedaContract.getOrderParams()));
            try {
            if("placeOrder".equals(orderSedaContract.getOrderModificationType())){
                brokerWorker.placeOrder(orderSedaContract.getOrderParams(),orderSedaContract.getUser(),null);
            }else if("modify".equals(orderSedaContract.getOrderModificationType())){
                brokerWorker.modifyOrder(orderSedaContract.getOrderId(),orderSedaContract.getOrderParams(),orderSedaContract.getUser(),null);
            }
                // brokerWorker.placeOrder(orderSedaContract.getOrderParams(),orderSedaContract.getUser(),null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } catch (KiteException e) {
                throw new RuntimeException(e);
            }
        }catch (Exception e){
            LOGGER.error("error: {}",e.getMessage());
        }
    }

}
