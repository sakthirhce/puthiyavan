package com.sakthi.trade.domain;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class FundResponseDTO {

    @SerializedName("s")
    @Expose
    private String s;
    @SerializedName("fund_limit")
    @Expose
    private List<FundLimit> fundLimit = null;

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public List<FundLimit> getFundLimit() {
        return fundLimit;
    }

    public void setFundLimit(List<FundLimit> fundLimit) {
        this.fundLimit = fundLimit;
    }

}