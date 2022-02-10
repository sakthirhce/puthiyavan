
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
public class RealTimeSubscribeRequestDTO {

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
     * The symbols schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("symbols")
    @Expose
    @Nonnull
    private List<String> symbols = new ArrayList<String>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public RealTimeSubscribeRequestDTO() {
    }

    /**
     * 
     * @param method
     * @param symbols
     */
    public RealTimeSubscribeRequestDTO(String method, List<String> symbols) {
        super();
        this.method = method;
        this.symbols = symbols;
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
     * The symbols schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public List<String> getSymbols() {
        return symbols;
    }

    /**
     * The symbols schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(method).append(symbols).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof RealTimeSubscribeRequestDTO) == false) {
            return false;
        }
        RealTimeSubscribeRequestDTO rhs = ((RealTimeSubscribeRequestDTO) other);
        return new EqualsBuilder().append(method, rhs.method).append(symbols, rhs.symbols).isEquals();
    }

}
