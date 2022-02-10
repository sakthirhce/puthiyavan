package com.sakthi.trade.binance;

public class BinanceApiException extends Throwable {
    public BinanceApiException(String message,String error) {
        super(message+":"+error);
    }
}