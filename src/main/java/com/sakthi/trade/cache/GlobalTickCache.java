package com.sakthi.trade.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class GlobalTickCache {
    private Cache<String,GlobalTick> globalTickCache;
    public static final Logger LOGGER = LoggerFactory.getLogger(GlobalTickCache.class.getName());
    @PostConstruct
    private void initCache(){
        RemovalListener<String,GlobalTick> removalListener=new RemovalListener<String, GlobalTick>() {
            @Override
            public void onRemoval(RemovalNotification<String, GlobalTick> removal) {
              /*  LOGGER.info("Removed key from global cache:"+removal.getKey());
                System.out.println("cache size:"+globalContextCache.size());
                globalContextCache.asMap().entrySet().forEach(stringGlobalContextEntry -> {
                    System.out.println(stringGlobalContextEntry.getKey()+":"+stringGlobalContextEntry.getValue().historicalDataMap.size());
                    stringGlobalContextEntry.getValue().historicalDataMap.entrySet().forEach(stringGlobalContEntry -> {  System.out.println(stringGlobalContEntry.getKey());});
                });*/
            }
        };
        globalTickCache= CacheBuilder.newBuilder().maximumSize(2000).removalListener(removalListener).build();
    }
    public String getHistoricData(String time,String stockId){
        GlobalTick globalContext=globalTickCache.getIfPresent(time);
        if(globalContext!=null){
            String historicalData=globalContext.historicalDataMap.get(stockId);
            return historicalData;
        }
        return null;
    }

    public void setHistoricData(String time,String stockId,String historicalData){
        synchronized (time) {
            GlobalTick globalContext = globalTickCache.getIfPresent(time);
            if (globalContext != null) {
                globalContext.historicalDataMap.put(stockId, historicalData);
                globalTickCache.put(time,globalContext);
            } else {
                globalContext=new GlobalTick();
                globalContext.historicalDataMap.put(stockId, historicalData);
                globalTickCache.put(time,globalContext);
            }
        }
    }


}
