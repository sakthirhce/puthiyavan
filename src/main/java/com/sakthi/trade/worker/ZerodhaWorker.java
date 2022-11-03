package com.sakthi.trade.worker;

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

@Component
@Slf4j
public class ZerodhaWorker implements BrokerWorker {
    @Override
    public Order placeOrder(OrderParams orderParams, User user, TradeData tradeData) throws IOException, KiteException {
        try {
            Order order=user.getKiteConnect().placeOrder(orderParams, "regular");
            return order;
        } catch (KiteException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
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
            return user.getKiteConnect().getOrders();
        }
        catch (KiteException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

    @Override
    public List<Position> getPositions(User user) {
        try {
            return user.getKiteConnect().getPositions().get("net");
        } catch (KiteException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Order cancelOrder(String orderId, User user) throws IOException, KiteException {
        return user.getKiteConnect().cancelOrder(orderId, "regular");
    }

    @Override
    public Order modifyOrder(String orderId, OrderParams orderPlacementRequest, User user,TradeData tradeData) {
        try {
            return user.getKiteConnect().modifyOrder(orderId,orderPlacementRequest,"regular");
        } catch (KiteException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
