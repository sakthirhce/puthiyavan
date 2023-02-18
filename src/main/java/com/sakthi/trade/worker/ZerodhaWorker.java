package com.sakthi.trade.worker;

import com.google.gson.Gson;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.zerodha.account.User;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

@Component
@Slf4j
public class ZerodhaWorker implements BrokerWorker {
    public static final Logger LOGGER = Logger.getLogger(ZerodhaWorker.class.getName());
    @Override
    public Order placeOrder(OrderParams orderParams, User user, TradeData tradeData) throws IOException, KiteException {
        try {
            long start = System.currentTimeMillis();
            LOGGER.info("zerodha order input: "+user.getName()+":"+new Gson().toJson(orderParams));
            Order order=user.getKiteConnect().placeOrder(orderParams, "regular");
            long finish = System.currentTimeMillis();
            long timeElapsed = finish - start;
            LOGGER.info("order execution time:"+timeElapsed);
            return order;
        } catch (KiteException e) {
            log.error(e.message+":"+e.code);
            throw new RuntimeException(e);
        } catch (JSONException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    @Override
    public String broker() {
        return Broker.ZERODHA.name();
    }

    @Override
    public List<Order> getOrders(User user){
        try {
            long start = System.currentTimeMillis();
            List<Order> orderList= user.getKiteConnect().getOrders();
            long finish = System.currentTimeMillis();
            long timeElapsed = finish - start;
//            LOGGER.info("get order time:"+timeElapsed);
            return orderList;
        }
        catch (KiteException e) {
            log.error(e.message+":"+e.code);
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

    @Override
    public List<Position> getPositions(User user) {
        try {
            long start = System.currentTimeMillis();
            List<Position> positions=user.getKiteConnect().getPositions().get("net");
            long finish = System.currentTimeMillis();
            long timeElapsed = finish - start;
        //    LOGGER.info("get position time:"+timeElapsed);
            return positions;
        } catch (KiteException e) {
            log.error(e.message+":"+e.code);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Order cancelOrder(String orderId, User user) throws IOException, KiteException {
        try {
        long start = System.currentTimeMillis();
         Order order= user.getKiteConnect().cancelOrder(orderId, "regular");
        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;
        LOGGER.info("get position time:"+timeElapsed);
        return order;
        } catch (KiteException e) {
            log.error(e.message+":"+e.code);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Order modifyOrder(String orderId, OrderParams orderPlacementRequest, User user,TradeData tradeData) {
        try {
            long start = System.currentTimeMillis();
            Order order=  user.getKiteConnect().modifyOrder(orderId,orderPlacementRequest,"regular");
            long finish = System.currentTimeMillis();
            long timeElapsed = finish - start;
            LOGGER.info("get position time:"+timeElapsed);
            return order;
        } catch (KiteException e) {
            log.error(e.message+":"+e.code);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
