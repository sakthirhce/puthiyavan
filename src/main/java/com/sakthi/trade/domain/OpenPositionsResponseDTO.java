
package com.sakthi.trade.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;


/**
 * The root schema
 * <p>
 * The root schema comprises the entire JSON document.
 * 
 */
public class OpenPositionsResponseDTO {

    /**
     * The s schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("s")
    @Expose
    @Nonnull
    private String s = "";
    /**
     * The netPositions schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("netPositions")
    @Expose
    @Nonnull
    private List<NetPositionDTO> netPositions = new ArrayList<NetPositionDTO>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public OpenPositionsResponseDTO() {
    }

    /**
     * 
     * @param s
     * @param netPositions
     */
    public OpenPositionsResponseDTO(String s, List<NetPositionDTO> netPositions) {
        super();
        this.s = s;
        this.netPositions = netPositions;
    }

    /**
     * The s schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getS() {
        return s;
    }

    /**
     * The s schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setS(String s) {
        this.s = s;
    }

    /**
     * The netPositions schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public List<NetPositionDTO> getNetPositions() {
        return netPositions;
    }

    /**
     * The netPositions schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setNetPositions(List<NetPositionDTO> netPositions) {
        this.netPositions = netPositions;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(s).append(netPositions).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OpenPositionsResponseDTO) == false) {
            return false;
        }
        OpenPositionsResponseDTO rhs = ((OpenPositionsResponseDTO) other);
        return new EqualsBuilder().append(s, rhs.s).append(netPositions, rhs.netPositions).isEquals();
    }

}
