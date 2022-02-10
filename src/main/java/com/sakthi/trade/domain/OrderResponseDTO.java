package com.sakthi.trade.domain;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class OrderResponseDTO {

    @SerializedName("s")
    @Expose
    private String s;
    @SerializedName("message")
    @Expose
    private String message;
    @SerializedName("orderBook")
    @Expose
    private List<OrderBook> orderBook = null;

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<OrderBook> getOrderBook() {
        return orderBook;
    }

    public void setOrderBook(List<OrderBook> orderBook) {
        this.orderBook = orderBook;
    }

}