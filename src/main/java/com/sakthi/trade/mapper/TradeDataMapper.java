package com.sakthi.trade.mapper;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import com.sakthi.trade.repo.TradeStrategyRepo;
import com.sakthi.trade.util.MathUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class TradeDataMapper {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(TradeDataMapper.class.getName());
    public TradeData mapTradeEntityToTradeData(OpenTradeDataEntity openTradeDataEntity){
        TradeData tradeData = new TradeData();
        tradeData.setDataKey(openTradeDataEntity.getDataKey());
        tradeData.setAlgoName(openTradeDataEntity.getAlgoName());
        tradeData.setStockName(openTradeDataEntity.getStockName());
        tradeData.setEntryType(openTradeDataEntity.getEntryType());
        tradeData.setUserId(openTradeDataEntity.getUserId());
        tradeData.isOrderPlaced = openTradeDataEntity.isOrderPlaced;
        tradeData.isSlPlaced = openTradeDataEntity.isSlPlaced();
        tradeData.isExited = openTradeDataEntity.isExited();
        tradeData.isErrored = openTradeDataEntity.isErrored;
        tradeData.isSLHit = openTradeDataEntity.isSLHit;
        tradeData.setBuyTradedPrice(openTradeDataEntity.getBuyTradedPrice());
        tradeData.setSellTradedPrice(openTradeDataEntity.getSellTradedPrice());
        tradeData.setExitOrderId(openTradeDataEntity.getExitOrderId());
        tradeData.setBuyPrice(openTradeDataEntity.getBuyPrice());
        tradeData.setSellPrice(openTradeDataEntity.getSellPrice());
        tradeData.setSlPrice(openTradeDataEntity.getSlPrice());
        tradeData.setQty(openTradeDataEntity.getQty());
        tradeData.setSlPercentage(openTradeDataEntity.getSlPercentage());
        tradeData.setEntryOrderId(openTradeDataEntity.getEntryOrderId());
        tradeData.setSlOrderId(openTradeDataEntity.getSlOrderId());
        tradeData.setZerodhaStockId(openTradeDataEntity.getStockId());

        if(tradeData.isExited) {
            try {
                if (tradeData.getBuyTradedPrice() != null && tradeData.getBuyPrice() != null) {
                    BigDecimal slipagge= tradeData.getBuyPrice().subtract(tradeData.getBuyTradedPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                    if (openTradeDataEntity.getEntryType().equals("BUY")) {
                        tradeData.setEntrySlipage(slipagge);
                        openTradeDataEntity.setEntrySlipage(slipagge);
                    }else {
                        tradeData.setExitSlipage(slipagge);
                        openTradeDataEntity.setExitSlipage(slipagge);
                        if(tradeData.isSLHit){
                            tradeData.setSlSlipage(slipagge);
                            openTradeDataEntity.setSlSlipage(slipagge);
                        }
                    }
                }
                if (tradeData.getSellPrice() != null && tradeData.getSellTradedPrice() != null) {
                    BigDecimal slipagge= tradeData.getSellTradedPrice().subtract(tradeData.getSellPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                    if (openTradeDataEntity.getEntryType().equals("BUY")) {
                        tradeData.setExitSlipage(slipagge);
                        openTradeDataEntity.setExitSlipage(slipagge);
                    }else {
                        tradeData.setEntrySlipage(slipagge);
                        if(tradeData.isSLHit){
                            tradeData.setSlSlipage(slipagge);
                            openTradeDataEntity.setSlSlipage(slipagge);
                        }
                    }
                }
                if (tradeData.getBuyTradedPrice() != null && tradeData.getSellTradedPrice() != null) {
                    try {
                        BigDecimal pl = tradeData.getSellTradedPrice().subtract(tradeData.getBuyTradedPrice())
                                .setScale(2, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                        tradeData.setProfitLoss(pl);
                        boolean isOptions = true;
                        boolean isFutures = false;
                        if (tradeData.getStockName().contains("FUT")) {
                            isOptions = false;
                            isFutures = true;
                        }
                        MathUtils.calculateBrokerage(tradeData, isOptions, false, isFutures, "0");
                        openTradeDataEntity.setCharges(tradeData.charges.setScale(2, RoundingMode.HALF_UP));
                        tradeData.plAfterCharges = pl.subtract(tradeData.charges).setScale(2, RoundingMode.HALF_UP);
                        if (tradeData.getSellPrice() != null && tradeData.getBuyPrice() != null) {
                            BigDecimal paperPl = tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                        }
                    }catch (Exception e){
                        LOGGER.info("error while calculating PL and slipage,charges");
                    }
                }

            }catch (Exception e){
                LOGGER.info("error while calculating PL and slipage");
            }}
        return tradeData;
    }
    public TradeData mapTradeBackupEntityToTradeData(OpenTradeDataBackupEntity openTradeDataEntity){
        TradeData tradeData = new TradeData();
        tradeData.setDataKey(openTradeDataEntity.getDataKey());
        tradeData.setAlgoName(openTradeDataEntity.getAlgoName());
        tradeData.setStockName(openTradeDataEntity.getStockName());
        tradeData.setEntryType(openTradeDataEntity.getEntryType());
        tradeData.setUserId(openTradeDataEntity.getUserId());
        tradeData.isOrderPlaced = openTradeDataEntity.isOrderPlaced;
        tradeData.isSlPlaced = openTradeDataEntity.isSlPlaced();
        tradeData.isExited = openTradeDataEntity.isExited();
        tradeData.isErrored = openTradeDataEntity.isErrored;
        tradeData.isSLHit = openTradeDataEntity.isSLHit;
        tradeData.setBuyTradedPrice(openTradeDataEntity.getBuyTradedPrice());
        tradeData.setSellTradedPrice(openTradeDataEntity.getSellTradedPrice());
        tradeData.setExitOrderId(openTradeDataEntity.getExitOrderId());
        tradeData.setBuyPrice(openTradeDataEntity.getBuyPrice());
        tradeData.setSellPrice(openTradeDataEntity.getSellPrice());
        tradeData.setSlPrice(openTradeDataEntity.getSlPrice());
        tradeData.setQty(openTradeDataEntity.getQty());
        tradeData.setSlPercentage(openTradeDataEntity.getSlPercentage());
        tradeData.setEntryOrderId(openTradeDataEntity.getEntryOrderId());
        tradeData.setSlOrderId(openTradeDataEntity.getSlOrderId());
        tradeData.setZerodhaStockId(openTradeDataEntity.getStockId());
        if(tradeData.isExited) {
            try {
                if (tradeData.getBuyTradedPrice() != null && tradeData.getBuyPrice() != null) {
                    BigDecimal slipagge= tradeData.getBuyPrice().subtract(tradeData.getBuyTradedPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                    if (openTradeDataEntity.getEntryType().equals("BUY")) {
                        tradeData.setEntrySlipage(slipagge);
                        openTradeDataEntity.setEntrySlipage(slipagge);
                    }else {
                        tradeData.setExitSlipage(slipagge);
                        openTradeDataEntity.setExitSlipage(slipagge);
                        if(tradeData.isSLHit){
                            tradeData.setSlSlipage(slipagge);
                            openTradeDataEntity.setSlSlipage(slipagge);
                        }
                    }
                }
                if (tradeData.getSellPrice() != null && tradeData.getSellTradedPrice() != null) {
                    BigDecimal slipagge= tradeData.getSellTradedPrice().subtract(tradeData.getSellPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                    if (openTradeDataEntity.getEntryType().equals("BUY")) {
                        tradeData.setExitSlipage(slipagge);
                        openTradeDataEntity.setExitSlipage(slipagge);
                    }else {
                        tradeData.setEntrySlipage(slipagge);
                        if(tradeData.isSLHit){
                            tradeData.setSlSlipage(slipagge);
                            openTradeDataEntity.setSlSlipage(slipagge);
                        }
                    }
                }
                if (tradeData.getBuyTradedPrice() != null && tradeData.getSellTradedPrice() != null) {
                    try {
                        BigDecimal pl = tradeData.getSellTradedPrice().subtract(tradeData.getBuyTradedPrice())
                                .setScale(2, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                        tradeData.setProfitLoss(pl);
                        boolean isOptions = true;
                        boolean isFutures = false;
                        if (tradeData.getStockName().contains("FUT")) {
                            isOptions = false;
                            isFutures = true;
                        }
                        MathUtils.calculateBrokerage(tradeData, isOptions, false, isFutures, "0");
                        openTradeDataEntity.setCharges(tradeData.charges.setScale(2, RoundingMode.HALF_UP));
                        tradeData.plAfterCharges = pl.subtract(tradeData.charges).setScale(2, RoundingMode.HALF_UP);
                        if (tradeData.getSellPrice() != null && tradeData.getBuyPrice() != null) {
                            BigDecimal paperPl = tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                        }
                    }catch (Exception e){
                        LOGGER.info("error while calculating PL and slipage,charges");
                    }
                }

            }catch (Exception e){
                LOGGER.info("error while calculating PL and slipage");
            }}
        return tradeData;
    }

    @Autowired
    OpenTradeDataRepo openTradeDataRepo;
    public void mapTradeDataToSaveOpenTradeDataEntity(TradeData tradeData,boolean orderPlaced,String algoName) {
        try {
            OpenTradeDataEntity openTradeDataEntity = new OpenTradeDataEntity();
            openTradeDataEntity.setDataKey(tradeData.getDataKey());
            openTradeDataEntity.setAlgoName(algoName);
            openTradeDataEntity.setStockName(tradeData.getStockName());
            openTradeDataEntity.setEntryType(tradeData.getEntryType());
            openTradeDataEntity.setUserId(tradeData.getUserId());
            openTradeDataEntity.isOrderPlaced = tradeData.isOrderPlaced;
            openTradeDataEntity.isSlPlaced = tradeData.isSlPlaced();
            openTradeDataEntity.isExited = tradeData.isExited();
            openTradeDataEntity.isErrored = tradeData.isErrored;
            openTradeDataEntity.isSLHit = tradeData.isSLHit;
            openTradeDataEntity.range=tradeData.range;
            openTradeDataEntity.setRangeHigh(tradeData.getRangeHigh());
            openTradeDataEntity.setRangeLow(tradeData.getRangeLow());
            openTradeDataEntity.setBuyTradedPrice(tradeData.getBuyTradedPrice());
            openTradeDataEntity.setSellTradedPrice(tradeData.getSellTradedPrice());
            openTradeDataEntity.setExitOrderId(tradeData.getExitOrderId());
            openTradeDataEntity.setBuyPrice(tradeData.getBuyPrice());
            openTradeDataEntity.setSellPrice(tradeData.getSellPrice());
            openTradeDataEntity.setSlPrice(tradeData.getSlPrice());
            openTradeDataEntity.setQty(tradeData.getQty());
            openTradeDataEntity.setSlPercentage(tradeData.getSlPercentage());
            openTradeDataEntity.setEntryOrderId(tradeData.getEntryOrderId());
            openTradeDataEntity.setSlOrderId(tradeData.getSlOrderId());
            openTradeDataEntity.setStockId(tradeData.getZerodhaStockId());
            openTradeDataEntity.setTempSlPrice(tradeData.getTempSlPrice());
            openTradeDataEntity.setWebsocketSlModified(tradeData.isWebsocketSlModified());
            openTradeDataEntity.setWebsocketSlTime(tradeData.getWebsocketSlTime());
            if(tradeData.getTradeStrategy()!=null) {
                openTradeDataEntity.setTradeStrategyKey(tradeData.getTradeStrategy().getTradeStrategyKey());
            }
            Date date = new Date();
            String tradeDate = format.format(date);
            if(orderPlaced) {
                openTradeDataEntity.setTradeDate(tradeDate);
                tradeData.setTradeDate(tradeDate);
            }else{
                openTradeDataEntity.setTradeDate(tradeData.getTradeDate());
                openTradeDataEntity.setModifyDate(tradeDate);
                tradeData.setModifyDate(tradeDate);
            }
            if(tradeData.isExited) {
                try {
                    if (tradeData.getBuyTradedPrice() != null && tradeData.getBuyPrice() != null) {
                        BigDecimal slipagge= tradeData.getBuyPrice().subtract(tradeData.getBuyTradedPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                        if (openTradeDataEntity.getEntryType().equals("BUY")) {
                            tradeData.setEntrySlipage(slipagge);
                            openTradeDataEntity.setEntrySlipage(slipagge);
                        }else {
                            tradeData.setExitSlipage(slipagge);
                            openTradeDataEntity.setExitSlipage(slipagge);
                            if(tradeData.isSLHit){
                                tradeData.setSlSlipage(slipagge);
                                openTradeDataEntity.setSlSlipage(slipagge);
                            }
                        }
                    }
                    if (tradeData.getSellPrice() != null && tradeData.getSellTradedPrice() != null) {
                        BigDecimal slipagge= tradeData.getSellTradedPrice().subtract(tradeData.getSellPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                        if (openTradeDataEntity.getEntryType().equals("BUY")) {
                            tradeData.setExitSlipage(slipagge);
                            openTradeDataEntity.setExitSlipage(slipagge);
                        }else {
                            tradeData.setEntrySlipage(slipagge);
                            if(tradeData.isSLHit){
                                tradeData.setSlSlipage(slipagge);
                                openTradeDataEntity.setSlSlipage(slipagge);
                            }
                        }
                    }
                    if (tradeData.getBuyTradedPrice() != null && tradeData.getSellTradedPrice() != null) {
                        try {
                            BigDecimal pl = tradeData.getSellTradedPrice().subtract(tradeData.getBuyTradedPrice())
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                            tradeData.setProfitLoss(pl);
                            boolean isOptions = true;
                            boolean isFutures = false;
                            if (tradeData.getStockName().contains("FUT")) {
                                isOptions = false;
                                isFutures = true;
                            }
                            MathUtils.calculateBrokerage(tradeData, isOptions, false, isFutures, "0");
                            openTradeDataEntity.setCharges(tradeData.charges.setScale(2, RoundingMode.HALF_UP));
                            tradeData.plAfterCharges = pl.subtract(tradeData.charges).setScale(2, RoundingMode.HALF_UP);
                            openTradeDataEntity.setPlAfterCharges(tradeData.plAfterCharges);
                            if (tradeData.getSellPrice() != null && tradeData.getBuyPrice() != null) {
                                BigDecimal paperPl = tradeData.getSellPrice().subtract(tradeData.getBuyPrice()).setScale(2, RoundingMode.HALF_UP).multiply(new BigDecimal(tradeData.getQty())).setScale(2, RoundingMode.HALF_UP);
                            }
                        }catch (Exception e){
                            LOGGER.info("error while calculating PL and slipage,charges");
                        }
                    }

                }catch (Exception e){
                    LOGGER.info("error while calculating PL and slipage");
                }
            }
            saveTradeData(openTradeDataEntity);
            LOGGER.info("sucessfully saved trade data:"+new Gson().toJson(openTradeDataEntity));
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }

    }
    @Autowired
    TradeStrategyRepo tradeStrategyRepo;
    public TradeData mapTradeDataEntityToTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            TradeData tradeData = new TradeData();
            tradeData.setDataKey(openTradeDataEntity.getDataKey());
            tradeData.setAlgoName(openTradeDataEntity.getAlgoName());
            tradeData.setStockName(openTradeDataEntity.getStockName());
            tradeData.setEntryType(openTradeDataEntity.getEntryType());
            tradeData.setUserId(openTradeDataEntity.getUserId());
            tradeData.isOrderPlaced = openTradeDataEntity.isOrderPlaced;
            tradeData.isSlPlaced = openTradeDataEntity.isSlPlaced();
            tradeData.isExited = openTradeDataEntity.isExited();
            tradeData.isErrored = openTradeDataEntity.isErrored;
            tradeData.isSLHit = openTradeDataEntity.isSLHit;
            tradeData.setBuyTradedPrice(openTradeDataEntity.getBuyTradedPrice());
            tradeData.setSellTradedPrice(openTradeDataEntity.getSellTradedPrice());
            tradeData.setTradeDate(openTradeDataEntity.getTradeDate());
            tradeData.setExitOrderId(openTradeDataEntity.getExitOrderId());
            tradeData.setBuyPrice(openTradeDataEntity.getBuyPrice());
            tradeData.setSellPrice(openTradeDataEntity.getSellPrice());
            tradeData.setSlPrice(openTradeDataEntity.getSlPrice());
            tradeData.setQty(openTradeDataEntity.getQty());
            tradeData.setSlPercentage(openTradeDataEntity.getSlPercentage());
            tradeData.setEntryOrderId(openTradeDataEntity.getEntryOrderId());
            tradeData.setSlOrderId(openTradeDataEntity.getSlOrderId());
            tradeData.setZerodhaStockId(openTradeDataEntity.getStockId());
            if(openTradeDataEntity.getTradeStrategyKey()!=null) {
                TradeStrategy tradeStrategy=tradeStrategyRepo.getStrategyByStrategyKey(openTradeDataEntity.getTradeStrategyKey());
                tradeData.setTradeStrategy(tradeStrategy);
            }

        return tradeData;
            //LOGGER.info("sucessfully saved trade data:"+new Gson().toJson(openTradeDataEntity));
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }return null;

    }
    public void saveTradeData(OpenTradeDataEntity openTradeDataEntity) {
        try {
            openTradeDataRepo.save(openTradeDataEntity);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }
}
