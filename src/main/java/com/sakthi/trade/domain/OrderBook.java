package com.sakthi.trade.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class OrderBook {

    @SerializedName("status")
    @Expose
    private Long status;
    @SerializedName("symbol")
    @Expose
    private String symbol;
    @SerializedName("qty")
    @Expose
    private Long qty;
    @SerializedName("orderNumStatus")
    @Expose
    private String orderNumStatus;
    @SerializedName("dqQtyRem")
    @Expose
    private Long dqQtyRem;
    @SerializedName("orderDateTime")
    @Expose
    private String orderDateTime;
    @SerializedName("orderValidity")
    @Expose
    private String orderValidity;
    @SerializedName("fyToken")
    @Expose
    private String fyToken;
    @SerializedName("slNo")
    @Expose
    private Long slNo;
    @SerializedName("message")
    @Expose
    private String message;
    @SerializedName("segment")
    @Expose
    private String segment;
    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("stopPrice")
    @Expose
    private Long stopPrice;
    @SerializedName("exchOrdId")
    @Expose
    private Long exchOrdId;
    @SerializedName("remainingQuantity")
    @Expose
    private Long remainingQuantity;
    @SerializedName("filledQty")
    @Expose
    private Long filledQty;
    @SerializedName("limitPrice")
    @Expose
    private Long limitPrice;
    @SerializedName("offlineOrder")
    @Expose
    private String offlineOrder;
    @SerializedName("instrument")
    @Expose
    private String instrument;
    @SerializedName("productType")
    @Expose
    private String productType;
    @SerializedName("type")
    @Expose
    private Long type;
    @SerializedName("side")
    @Expose
    private Long side;
    @SerializedName("discloseQty")
    @Expose
    private Long discloseQty;
    @SerializedName("tradedPrice")
    @Expose
    private Long tradedPrice;

    public Long getStatus() {
        return status;
    }

    public void setStatus(Long status) {
        this.status = status;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getQty() {
        return qty;
    }

    public void setQty(Long qty) {
        this.qty = qty;
    }

    public String getOrderNumStatus() {
        return orderNumStatus;
    }

    public void setOrderNumStatus(String orderNumStatus) {
        this.orderNumStatus = orderNumStatus;
    }

    public Long getDqQtyRem() {
        return dqQtyRem;
    }

    public void setDqQtyRem(Long dqQtyRem) {
        this.dqQtyRem = dqQtyRem;
    }

    public String getOrderDateTime() {
        return orderDateTime;
    }

    public void setOrderDateTime(String orderDateTime) {
        this.orderDateTime = orderDateTime;
    }

    public String getOrderValidity() {
        return orderValidity;
    }

    public void setOrderValidity(String orderValidity) {
        this.orderValidity = orderValidity;
    }

    public String getFyToken() {
        return fyToken;
    }

    public void setFyToken(String fyToken) {
        this.fyToken = fyToken;
    }

    public Long getSlNo() {
        return slNo;
    }

    public void setSlNo(Long slNo) {
        this.slNo = slNo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getStopPrice() {
        return stopPrice;
    }

    public void setStopPrice(Long stopPrice) {
        this.stopPrice = stopPrice;
    }

    public Long getExchOrdId() {
        return exchOrdId;
    }

    public void setExchOrdId(Long exchOrdId) {
        this.exchOrdId = exchOrdId;
    }

    public Long getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(Long remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public Long getFilledQty() {
        return filledQty;
    }

    public void setFilledQty(Long filledQty) {
        this.filledQty = filledQty;
    }

    public Long getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(Long limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getOfflineOrder() {
        return offlineOrder;
    }

    public void setOfflineOrder(String offlineOrder) {
        this.offlineOrder = offlineOrder;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Long getType() {
        return type;
    }

    public void setType(Long type) {
        this.type = type;
    }

    public Long getSide() {
        return side;
    }

    public void setSide(Long side) {
        this.side = side;
    }

    public Long getDiscloseQty() {
        return discloseQty;
    }

    public void setDiscloseQty(Long discloseQty) {
        this.discloseQty = discloseQty;
    }

    public Long getTradedPrice() {
        return tradedPrice;
    }

    public void setTradedPrice(Long tradedPrice) {
        this.tradedPrice = tradedPrice;
    }

}