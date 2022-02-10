package com.sakthi.trade.test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class TestMap2 {

    public static void main(String[] arg){
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE");
        Date date=new Date();
        System.out.println(dayFormat.format(date));
    }
}

