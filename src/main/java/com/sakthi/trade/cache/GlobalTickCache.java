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
    private Cache<Integer,GlobalTick> globalTickCache;
    public static final Logger LOGGER = LoggerFactory.getLogger(GlobalTickCache.class.getName());
    @PostConstruct
    private void initCache(){
        RemovalListener<Integer,GlobalTick> removalListener=new RemovalListener<Integer, GlobalTick>() {
            @Override
            public void onRemoval(RemovalNotification<Integer, GlobalTick> removal) {
              /*  LOGGER.info("Removed key from global cache:"+removal.getKey());
                System.out.println("cache size:"+globalContextCache.size());
                globalContextCache.asMap().entrySet().forEach(stringGlobalContextEntry -> {
                    System.out.println(stringGlobalContextEntry.getKey()+":"+stringGlobalContextEntry.getValue().historicalDataMap.size());
                    stringGlobalContextEntry.getValue().historicalDataMap.entrySet().forEach(stringGlobalContEntry -> {  System.out.println(stringGlobalContEntry.getKey());});
                });*/
            }
        };
        globalTickCache= CacheBuilder.newBuilder().maximumSize(1000).removalListener(removalListener).build();
    }
    public GlobalTick getHistoricData(int stockId){
        GlobalTick globalContext=globalTickCache.getIfPresent(stockId);
        if(globalContext!=null){
            return globalContext;
        }
        return null;
    }

    public void setHistoricData(int stockId,Double tickData){
        synchronized (String.valueOf(stockId)) {
            GlobalTick globalContext = globalTickCache.getIfPresent(stockId);
            if (globalContext != null) {
                globalContext.historicalDataMap.add(tickData);
                globalTickCache.put(stockId,globalContext);
            } else {
                globalContext=new GlobalTick();
                globalContext.historicalDataMap.add(tickData);
                globalTickCache.put(stockId,globalContext);
            }
        }
    }


}
