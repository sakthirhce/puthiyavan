package com.sakthi.trade.domain;

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
public class CancelRequestDTO {

    /**
     * The id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     *
     */
    @SerializedName("id")
    @Expose
    @Nonnull
    private long id = 0;

    /**
     * No args constructor for use in serialization
     *
     */
    public CancelRequestDTO() {
    }

    /**
     *
     * @param id
     */
    public CancelRequestDTO(long id) {
        super();
        this.id = id;
    }

    /**
     * The id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     *
     */
    public long getId() {
        return id;
    }

    /**
     * The id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     *
     */
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CancelRequestDTO) == false) {
            return false;
        }
        CancelRequestDTO rhs = ((CancelRequestDTO) other);
        return new EqualsBuilder().append(id, rhs.id).isEquals();
    }

}
