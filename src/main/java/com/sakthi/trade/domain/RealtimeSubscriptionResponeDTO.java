package com.sakthi.trade.domain;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "success",
        "message",
        "symbolsadded",
        "symbollist",
        "totalsymbolsubscribed"
})
public class RealtimeSubscriptionResponeDTO {

    @JsonProperty("success")
    private Boolean success;
    @JsonProperty("message")
    private String message;
    @JsonProperty("symbolsadded")
    private Long symbolsadded;
    @JsonProperty("symbollist")
    private List<String> symbollist = null;
    @JsonProperty("totalsymbolsubscribed")
    private Long totalsymbolsubscribed;

    @JsonProperty("success")
    public Boolean getSuccess() {
        return success;
    }

    @JsonProperty("success")
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("message")
    public void setMessage(String message) {
        this.message = message;
    }

    @JsonProperty("symbolsadded")
    public Long getSymbolsadded() {
        return symbolsadded;
    }

    @JsonProperty("symbolsadded")
    public void setSymbolsadded(Long symbolsadded) {
        this.symbolsadded = symbolsadded;
    }

    @JsonProperty("symbollist")
    public List<String> getSymbollist() {
        return symbollist;
    }

    @JsonProperty("symbollist")
    public void setSymbollist(List<String> symbollist) {
        this.symbollist = symbollist;
    }

    @JsonProperty("totalsymbolsubscribed")
    public Long getTotalsymbolsubscribed() {
        return totalsymbolsubscribed;
    }

    @JsonProperty("totalsymbolsubscribed")
    public void setTotalsymbolsubscribed(Long totalsymbolsubscribed) {
        this.totalsymbolsubscribed = totalsymbolsubscribed;
    }

}