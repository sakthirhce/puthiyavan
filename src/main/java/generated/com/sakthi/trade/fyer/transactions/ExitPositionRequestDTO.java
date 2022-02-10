
package com.sakthi.trade.fyer.transactions;

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
public class ExitPositionRequestDTO {

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
    private String id = "";

    /**
     * No args constructor for use in serialization
     * 
     */
    public ExitPositionRequestDTO() {
    }

    /**
     * 
     * @param id
     */
    public ExitPositionRequestDTO(String id) {
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
    public String getId() {
        return id;
    }

    /**
     * The id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setId(String id) {
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
        if ((other instanceof ExitPositionRequestDTO) == false) {
            return false;
        }
        ExitPositionRequestDTO rhs = ((ExitPositionRequestDTO) other);
        return new EqualsBuilder().append(id, rhs.id).isEquals();
    }

}
