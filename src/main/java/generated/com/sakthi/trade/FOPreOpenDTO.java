
package com.sakthi.trade;

import java.math.BigDecimal;
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
public class FOPreOpenDTO {

    /**
     * The declines schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("declines")
    @Expose
    @Nonnull
    private BigDecimal declines = new BigDecimal("0");
    /**
     * The noChange schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("noChange")
    @Expose
    @Nonnull
    private BigDecimal noChange = new BigDecimal("0");
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
    private List<DatumDTO> data = new ArrayList<DatumDTO>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public FOPreOpenDTO() {
    }

    /**
     * 
     * @param declines
     * @param noChange
     * @param data
     */
    public FOPreOpenDTO(BigDecimal declines, BigDecimal noChange, List<DatumDTO> data) {
        super();
        this.declines = declines;
        this.noChange = noChange;
        this.data = data;
    }

    /**
     * The declines schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getDeclines() {
        return declines;
    }

    /**
     * The declines schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setDeclines(BigDecimal declines) {
        this.declines = declines;
    }

    /**
     * The noChange schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getNoChange() {
        return noChange;
    }

    /**
     * The noChange schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setNoChange(BigDecimal noChange) {
        this.noChange = noChange;
    }

    /**
     * The data schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public List<DatumDTO> getData() {
        return data;
    }

    /**
     * The data schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setData(List<DatumDTO> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(declines).append(noChange).append(data).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FOPreOpenDTO) == false) {
            return false;
        }
        FOPreOpenDTO rhs = ((FOPreOpenDTO) other);
        return new EqualsBuilder().append(declines, rhs.declines).append(noChange, rhs.noChange).append(data, rhs.data).isEquals();
    }

}
