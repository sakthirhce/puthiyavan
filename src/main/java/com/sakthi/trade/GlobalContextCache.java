package com.sakthi.trade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.gson.Gson;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class GlobalContextCache {
    private Cache<String,GlobalContext> globalContextCache;
    public static final Logger LOGGER = LoggerFactory.getLogger(GlobalContextCache.class.getName());
    @PostConstruct
    private void initCache(){
        RemovalListener<String,GlobalContext> removalListener=new RemovalListener<String, GlobalContext>() {
            @Override
            public void onRemoval(RemovalNotification<String, GlobalContext> removal) {
              /*  LOGGER.info("Removed key from global cache:"+removal.getKey());
                System.out.println("cache size:"+globalContextCache.size());
                globalContextCache.asMap().entrySet().forEach(stringGlobalContextEntry -> {
                    System.out.println(stringGlobalContextEntry.getKey()+":"+stringGlobalContextEntry.getValue().historicalDataMap.size());
                    stringGlobalContextEntry.getValue().historicalDataMap.entrySet().forEach(stringGlobalContEntry -> {  System.out.println(stringGlobalContEntry.getKey());});
                });*/
            }
        };
        globalContextCache= CacheBuilder.newBuilder().maximumSize(2000).removalListener(removalListener).build();
    }
    public String getHistoricData(String time,String stockId){
        GlobalContext globalContext=globalContextCache.getIfPresent(time);
        if(globalContext!=null){
            String historicalData=globalContext.historicalDataMap.get(stockId);
            return historicalData;
        }
        return null;
    }

    public void setHistoricData(String time,String stockId,String historicalData){
        synchronized (time) {
            GlobalContext globalContext = globalContextCache.getIfPresent(time);
            if (globalContext != null) {
                globalContext.historicalDataMap.put(stockId, historicalData);
                globalContextCache.put(time,globalContext);
            } else {
                globalContext=new GlobalContext();
                globalContext.historicalDataMap.put(stockId, historicalData);
                globalContextCache.put(time,globalContext);
            }
        }
    }


}
