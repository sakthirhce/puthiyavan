
package com.sakthi.trade.truedata;

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
public class HistoricRequestDTO {

    /**
     * The method schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("method")
    @Expose
    @Nonnull
    private String method = "";
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
     * The from schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("from")
    @Expose
    @Nonnull
    private String from = "";
    /**
     * The to schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("to")
    @Expose
    @Nonnull
    private String to = "";

    /**
     * No args constructor for use in serialization
     * 
     */
    public HistoricRequestDTO() {
    }

    /**
     * 
     * @param symbol
     * @param method
     * @param interval
     * @param from
     * @param to
     */
    public HistoricRequestDTO(String method, String interval, String symbol, String from, String to) {
        super();
        this.method = method;
        this.interval = interval;
        this.symbol = symbol;
        this.from = from;
        this.to = to;
    }

    /**
     * The method schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getMethod() {
        return method;
    }

    /**
     * The method schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setMethod(String method) {
        this.method = method;
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
     * The from schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getFrom() {
        return from;
    }

    /**
     * The from schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * The to schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getTo() {
        return to;
    }

    /**
     * The to schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setTo(String to) {
        this.to = to;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(method).append(interval).append(symbol).append(from).append(to).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof HistoricRequestDTO) == false) {
            return false;
        }
        HistoricRequestDTO rhs = ((HistoricRequestDTO) other);
        return new EqualsBuilder().append(method, rhs.method).append(interval, rhs.interval).append(symbol, rhs.symbol).append(from, rhs.from).append(to, rhs.to).isEquals();
    }

}
