package com.sakthi.trade.service;

import com.google.gson.Gson;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.entity.UserSubscription;
import com.sakthi.trade.entity.UserSubscriptions;
import com.sakthi.trade.repo.TradeStrategyRepo;
import com.sakthi.trade.repo.UserSubscriptionRepo;
import com.sakthi.trade.seda.TradeSedaQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoadStrategyService {
    @Autowired
    TradingStrategyAndTradeData tradingStrategyAndTradeData;
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    SimpleDateFormat dayFormat = new SimpleDateFormat("E");
    @Autowired
    UserSubscriptionRepo userSubscriptionRepo;
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Value("${lotSize.config}")
    List<String> config;
    Gson gson = new Gson();
    public void loadStrategy() {
        Date date = new Date();
        tradingStrategyAndTradeData.strategyMap = new LinkedHashMap<>();
        tradingStrategyAndTradeData.rangeStrategyMap = new ConcurrentHashMap<>();
        List<TradeStrategy> tradeStrategyList = tradeStrategyRepo.getActiveActiveStrategy();
        tradeStrategyList.forEach(strategy -> {
            loadUserSubscriptions(strategy);
            setLotSize(strategy);
            try {
                if (isTradeDay(strategy, date)) {
                    if (strategy.isRangeBreak()) {
                        addRangeStrategy(strategy);
                    } else {
                        addStrategy(strategy);
                    }
                }
            } catch (Exception e) {
                tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":error while loading");
            }
        });

        sendStrategyNotifications();
    }

    private void loadUserSubscriptions(TradeStrategy strategy) {
        List<UserSubscription> userSubscriptionList = userSubscriptionRepo.getUserSubs(strategy.getTradeStrategyKey());
        if (userSubscriptionList != null && !userSubscriptionList.isEmpty()) {
            System.out.println(gson.toJson(userSubscriptionList));
            UserSubscriptions userSubscriptions = new UserSubscriptions();
            userSubscriptions.setUserSubscriptionList(userSubscriptionList);
            strategy.setUserSubscriptions(userSubscriptions);
        }
    }

    private void setLotSize(TradeStrategy strategy) {
        String index = strategy.getIndex();
        AtomicInteger lotA = new AtomicInteger(0);
        config.forEach(lot -> {
            String[] lotSplit = lot.split("-");
            if (lotSplit[0].equals(index)) {
                lotA.set(Integer.parseInt(lotSplit[1]));
            }
        });
        strategy.setLotSize(strategy.getLotSize() * lotA.get());
    }

    private boolean isTradeDay(TradeStrategy strategy, Date date) {
        String day = dayFormat.format(date).toUpperCase();
        return (strategy.getTradeDays() != null && strategy.getTradeDays().toUpperCase().contains(day)) || "All".equalsIgnoreCase(strategy.getTradeDays());
    }

    private void addRangeStrategy(TradeStrategy strategy) {
        String index = strategy.getIndex();
        tradingStrategyAndTradeData.rangeStrategyMap.computeIfAbsent(index, k -> new ArrayList<>()).add(strategy);
    }

    private void addStrategy(TradeStrategy strategy) {
        String index = strategy.getIndex();
        tradingStrategyAndTradeData.strategyMap.computeIfAbsent(index, k -> new HashMap<>())
                .computeIfAbsent(strategy.getEntryTime(), k -> new ArrayList<>())
                .add(strategy);
    }

    private void sendStrategyNotifications() {
        tradingStrategyAndTradeData.strategyMap.forEach((key, stringTradeStrategyMap) -> stringTradeStrategyMap.forEach((key1, list) -> list.forEach(strategy -> {
            List<String> users = new ArrayList<>();
            strategy.getUserSubscriptions().getUserSubscriptionList().forEach(userSubscription ->
                    users.add(userSubscription.getUserId() + "-" + userSubscription.getLotSize()));
            tradeSedaQueue.sendTelemgramSeda(key + ":" + key1 + ":" + users + ":" + strategy.getTradeStrategyKey() + ":" + strategy.getLotSize());
        })));

        tradingStrategyAndTradeData.rangeStrategyMap.forEach((key, list) -> list.forEach(strategy -> {
            List<String> users = new ArrayList<>();
            strategy.getUserSubscriptions().getUserSubscriptionList().forEach(userSubscription ->
                    users.add(userSubscription.getUserId() + "-" + userSubscription.getLotSize()));
            tradeSedaQueue.sendTelemgramSeda(strategy.getTradeStrategyKey() + ":" + strategy.getIndex() + ":" + users + ":" + strategy.getRangeStartTime() + ":" + strategy.getRangeBreakTime() + ":range enabled:" + strategy.isRangeBreak() + ":" + strategy.getLotSize());
        }));
    }

}
