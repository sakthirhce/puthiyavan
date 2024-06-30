package com.sakthi.trade.service;

import com.google.gson.Gson;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.repo.TradeStrategyRepo;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.models.Position;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LoadNormalPositionService {
    @Autowired
    public OpenTradeDataRepo openTradeDataRepo;
    public Map<String, List<TradeData>> openTrade = Collections.synchronizedMap(new LinkedHashMap<>());
    Gson gson = new Gson();
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    @Autowired
    public TradeSedaQueue tradeSedaQueue;
    @Autowired
    BrokerWorkerFactory workerFactory;
    @Autowired
    public UserList userList;
    @Autowired
    TradeDataMapper tradeDataMapper;
    public static final Logger LOGGER = LoggerFactory.getLogger(TradeEngine.class.getName());
    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;

    public void loadNrmlPositions() {
        Iterable<OpenTradeDataEntity> openTradeDataEntities = openTradeDataRepo.findAll();
        List<OpenTradeDataEntity> openList = new ArrayList<>();

        openTradeDataEntities.forEach(openList::add);

        openList.forEach(entity -> {
            System.out.println(gson.toJson(entity));
            TradeStrategy tradeStrategy = tradeStrategyRepo.getStrategyByStrategyKey(entity.tradeStrategyKey);
            System.out.println(gson.toJson(tradeStrategy));

            if (isEligibleStrategy(tradeStrategy)) {
                handleTradeData(entity, tradeStrategy);
            } else {
                backupAndDeleteEntity(entity);
            }
        });
    }
    private boolean isEligibleStrategy(TradeStrategy tradeStrategy) {
        return "BTST".equals(tradeStrategy.getTradeValidity()) || "CNC".equals(tradeStrategy.getTradeValidity());
    }

    private void handleTradeData(OpenTradeDataEntity entity, TradeStrategy tradeStrategy) {
        User user = findUserById(entity.getUserId());
        BrokerWorker brokerWorker = workerFactory.getWorker(user);
        List<Position> positions = brokerWorker.getRateLimitedPositions(user);

        positions.stream()
                .filter(position -> isMatchingPosition(entity, position))
                .findFirst()
                .ifPresent(position -> {
                    int positionQty = Math.abs(position.netQuantity);
                    if (positionQty != entity.getQty()) {
                        handleQtyMismatch(entity);
                    }

                    updateEntityStatus(entity, tradeStrategy);
                    addTradeToOpenList(entity, user, tradeStrategy);

                    /*try {
                        addTradeStriketoWebsocket(Long.parseLong(entity.getStrikeId()), entity.getStockName(), tradeStrategy.getIndex());
                    } catch (Exception | KiteException e) {
                        LOGGER.error("error while adding overnight position to websocket:{}, error: {}", entity.getStockName(), e.getMessage());
                        e.printStackTrace();
                    }*/
                    tradeSedaQueue.sendTelemgramSeda("Open Position: " + entity.getStockName() + ":" + entity.getUserId(), "exp-trade");
                });
    }

    private User findUserById(String userId) {
        return userList.getUser().stream()
                .filter(user -> user.getName().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    private boolean isMatchingPosition(OpenTradeDataEntity entity, Position position) {
        return "NRML".equals(position.product) && entity.getStockName().equals(position.tradingSymbol) && position.netQuantity != 0;
    }

    private void handleQtyMismatch(OpenTradeDataEntity entity) {
        tradeSedaQueue.sendTelemgramSeda("Position qty mismatch for: " + entity.getStockName() + ":" + entity.getUserId() + ", over riding position qty as trade qty.", "exp-trade");
        LOGGER.info("Position qty mismatch for: {}:{}, over riding position qty as trade qty.", entity.getStockName(), entity.getUserId(), "exp-trade");
    }

    private void updateEntityStatus(OpenTradeDataEntity entity, TradeStrategy tradeStrategy) {
        entity.isSlPlaced = false;
        tradeStrategy.setWebsocketSlEnabled(false);
        entity.isOrderPlaced = true;
        entity.setSlOrderId(null);
    }

    private void addTradeToOpenList(OpenTradeDataEntity entity, User user, TradeStrategy tradeStrategy) {
        TradeData tradeData = tradeDataMapper.mapTradeDataEntityToTradeData(entity);
        List<TradeData> tradeDataList = openTrade.computeIfAbsent(user.getName(), k -> new ArrayList<>());
        tradeData.setTradeStrategy(tradeStrategy);
        tradeDataList.add(tradeData);
    }

    private void backupAndDeleteEntity(OpenTradeDataEntity entity) {
        String entityJson = gson.toJson(entity);
        OpenTradeDataBackupEntity backupEntity = gson.fromJson(entityJson, OpenTradeDataBackupEntity.class);
        openTradeDataBackupRepo.save(backupEntity);
        openTradeDataRepo.deleteById(entity.getDataKey());
    }

}
