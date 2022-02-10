package com.sakthi.trade.fyer.model;


import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class FyerHistoryResponseDTO {

    /**
     *
     * (Required)
     *
     */
    @SerializedName("s")
    @Expose
    @Nonnull
    private String s;
    /**
     *
     * (Required)
     *
     */
    @SerializedName("candles")
    @Expose
    @Nonnull
    private List<Object> candles = new ArrayList<>();

    /**
     * No args constructor for use in serialization
     *
     */
    public FyerHistoryResponseDTO() {
    }

    /**
     *
     * @param s
     * @param candles
     */
    public FyerHistoryResponseDTO(String s, List<Object> candles) {
        super();
        this.s = s;
        this.candles = candles;
    }

    /**
     *
     * (Required)
     *
     */
    public String getS() {
        return s;
    }

    /**
     *
     * (Required)
     *
     */
    public void setS(String s) {
        this.s = s;
    }

    /**
     *
     * (Required)
     *
     */
    public List<Object> getCandles() {
        return candles;
    }

    /**
     *
     * (Required)
     *
     */
    public void setCandles(List<Object> candles) {
        this.candles = candles;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(s).append(candles).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof com.sakthi.trade.fyer.model.FyerHistoryResponseDTO) == false) {
            return false;
        }
        com.sakthi.trade.fyer.model.FyerHistoryResponseDTO rhs = ((com.sakthi.trade.fyer.model.FyerHistoryResponseDTO) other);
        return new EqualsBuilder().append(s, rhs.s).append(candles, rhs.candles).isEquals();
    }

}

