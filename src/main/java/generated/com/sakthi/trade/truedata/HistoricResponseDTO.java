
package com.sakthi.trade.truedata;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * The root schema
 * <p>
 * The root schema comprises the entire JSON document.
 * 
 */
public class HistoricResponseDTO {

    /**
     * The status schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("status")
    @Expose
    @Nonnull
    private String status = "";
    /**
     * The symbolid schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("symbolid")
    @Expose
    @Nonnull
    private Integer symbolid = 0;
    /**
     * The symbol schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("symbol")
    @Expose
    @Nonnull
    private String symbol = "";
    /**
     * The interval schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("interval")
    @Expose
    @Nonnull
    private String interval = "";
    /**
     * The data schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("data")
    @Expose
    @Nonnull
    private List<List<Object>> data = new ArrayList<List<Object>>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public HistoricResponseDTO() {
    }

    /**
     * 
     * @param symbolid
     * @param symbol
     * @param data
     * @param interval
     * @param status
     */
    public HistoricResponseDTO(String status, Integer symbolid, String symbol, String interval, List<List<Object>> data) {
        super();
        this.status = status;
        this.symbolid = symbolid;
        this.symbol = symbol;
        this.interval = interval;
        this.data = data;
    }

    /**
     * The status schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getStatus() {
        return status;
    }

    /**
     * The status schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * The symbolid schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getSymbolid() {
        return symbolid;
    }

    /**
     * The symbolid schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSymbolid(Integer symbolid) {
        this.symbolid = symbolid;
    }

    /**
     * The symbol schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * The symbol schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * The interval schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getInterval() {
        return interval;
    }

    /**
     * The interval schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setInterval(String interval) {
        this.interval = interval;
    }

    /**
     * The data schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public List<List<Object>> getData() {
        return data;
    }

    /**
     * The data schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setData(List<List<Object>> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(status).append(symbolid).append(symbol).append(interval).append(data).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof HistoricResponseDTO) == false) {
            return false;
        }
        HistoricResponseDTO rhs = ((HistoricResponseDTO) other);
        return new EqualsBuilder().append(status, rhs.status).append(symbolid, rhs.symbolid).append(symbol, rhs.symbol).append(interval, rhs.interval).append(data, rhs.data).isEquals();
    }

}
