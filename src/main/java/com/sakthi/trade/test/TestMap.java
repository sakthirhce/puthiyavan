/*
package com.sakthi.trade.test;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
public class TestMap {
    @Scheduled(cron = "${maptest.test}")
    public void testMap() throws InterruptedException {
        Map<String,String> atmStrikesStraddle=new HashMap<>();  System.out.println("thread 1 started");
        atmStrikesStraddle.put("3100","Test");
        atmStrikesStraddle.put("3400","Test");
        atmStrikesStraddle.entrySet().forEach(entry->{
            System.out.println(entry.getKey() + " " + entry.getValue());
        });
    }
}
*/
