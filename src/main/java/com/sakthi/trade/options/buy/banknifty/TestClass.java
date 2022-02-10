package com.sakthi.trade.options.buy.banknifty;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TestClass {
    public static void main(String[] arg) {
        try
        {
        BigDecimal buyPrice = new BigDecimal("111");
        BigDecimal slPoints = (buyPrice.multiply(new BigDecimal("10"))).divide(new BigDecimal(100)).setScale(0,BigDecimal.ROUND_DOWN);
        BigDecimal slPrice = (buyPrice.subtract(slPoints)).setScale(0, RoundingMode.HALF_UP);
        System.out.println("slPrice:"+slPrice);
        BigDecimal closePrice = new BigDecimal("122");
        BigDecimal diff = closePrice.subtract(buyPrice);
        if (diff.compareTo(slPoints) >= 0) {
            BigDecimal mod = (diff.divide(slPoints,0,BigDecimal.ROUND_DOWN));
            BigDecimal newSL = (buyPrice.subtract(slPoints)).add(mod.multiply(slPoints)).setScale(0, RoundingMode.HALF_UP);
            if (newSL.compareTo(slPrice)>0) {
                System.out.println("newSL:" + newSL);
            }
        }
    }catch (Exception e){
            e.printStackTrace();
        }
    }
}
