package com.sakthi.trade.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ProfileResponseDTO {

    @SerializedName("s")
    @Expose
    private String s;
    @SerializedName("code")
    @Expose
    private String code;
    @SerializedName("result")
    @Expose
    private Result result;

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

}
