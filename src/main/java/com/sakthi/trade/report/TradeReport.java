package com.sakthi.trade.report;

import com.google.gson.Gson;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.mapper.TradeDataMapper;
import com.sakthi.trade.repo.OpenTradeDataBackupRepo;
import com.sakthi.trade.repo.OpenTradeDataRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TradeReport {

    @Autowired
    OpenTradeDataBackupRepo openTradeDataBackupRepo;

    @Autowired
    OpenTradeDataRepo openTradeDataRepo;
    @Autowired
    TradeDataMapper tradeDataMapper;

    public List<TradeData> tradeReport(String userId,String date){
        System.out.println("user report:"+userId+" : "+date);
        List<OpenTradeDataEntity> openTradeDataEntities=openTradeDataRepo.findByUserIdAndTradeDate(userId,date);
        System.out.println("report db report:"+new Gson().toJson(openTradeDataEntities));
        List<TradeData> tradeDataList = new ArrayList<>();
        if(openTradeDataEntities !=null && openTradeDataEntities.size()>0){
            openTradeDataEntities.stream().forEach(openTradeDataEntity -> {
                TradeData tradeData = tradeDataMapper.mapTradeEntityToTradeData(openTradeDataEntity);
                tradeDataList.add(tradeData);
            });
        }
        List<OpenTradeDataBackupEntity> openTradeDataBackupEntities=openTradeDataBackupRepo.findByUserIdAndTradeDate(userId,date);
        System.out.println("report db back report:"+new Gson().toJson(openTradeDataBackupEntities));
        if(openTradeDataBackupEntities !=null && openTradeDataBackupEntities.size()>0){
            openTradeDataBackupEntities.stream().forEach(openTradeDataEntity -> {
                TradeData tradeData = tradeDataMapper.mapTradeBackupEntityToTradeData(openTradeDataEntity);
                tradeDataList.add(tradeData);
            });
        }
        System.out.println("report final report:"+new Gson().toJson(tradeDataList));
        return tradeDataList;
    }

}
