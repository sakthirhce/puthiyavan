package com.sakthi.trade.worker;

import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.zerodha.account.User;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface BrokerWorker {
    Order placeOrder(OrderParams orderPlacementRequest, User user, TradeData tradeData) throws IOException, KiteException;
    public String broker();
    List<Order> getOrders(User user) throws IOException, KiteException;
    Order getOrder(User user,String orderId) throws IOException, KiteException;
    List<Position>  getPositions(User user);
    List<Position>  getRateLimitedPositions(User user);
    Order cancelOrder(String orderId,User user) throws IOException, KiteException;
    Map<String, LTPQuote> getQuotesLtp(User user,String[] instrument) throws IOException, KiteException;
    Order modifyOrder(String orderId,OrderParams orderPlacementRequest,User user,TradeData tradeData);
}
