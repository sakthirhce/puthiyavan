package com.sakthi.trade.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class FundLimit {

    @SerializedName("equityAmount")
    @Expose
    private Double equityAmount;
    @SerializedName("commodityAmount")
    @Expose
    private Long commodityAmount;
    @SerializedName("id")
    @Expose
    private Long id;
    @SerializedName("title")
    @Expose
    private String title;

    public Double getEquityAmount() {
        return equityAmount;
    }

    public void setEquityAmount(Double equityAmount) {
        this.equityAmount = equityAmount;
    }

    public Long getCommodityAmount() {
        return commodityAmount;
    }

    public void setCommodityAmount(Long commodityAmount) {
        this.commodityAmount = commodityAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}