package com.sakthi.trade.trade.account.manager;

import com.sakthi.trade.domain.TradeData;

public abstract class Account {

    public abstract void placeSLOrder(TradeData tradeData);

    public abstract void cancelOrder(TradeData tradeData);

    public abstract void exitOrder(TradeData tradeData);

    public abstract void login(TradeData tradeData);

    public abstract void login();
    public abstract String generateToken();
}
