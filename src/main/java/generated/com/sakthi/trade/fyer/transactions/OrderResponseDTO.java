
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
public class OrderResponseDTO {

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
     * The code schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("code")
    @Expose
    @Nonnull
    private Integer code = 0;
    /**
     * The message schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("message")
    @Expose
    @Nonnull
    private String message = "";
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
    public OrderResponseDTO() {
    }

    /**
     * 
     * @param s
     * @param code
     * @param id
     * @param message
     */
    public OrderResponseDTO(String s, Integer code, String message, String id) {
        super();
        this.s = s;
        this.code = code;
        this.message = message;
        this.id = id;
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
     * The code schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getCode() {
        return code;
    }

    /**
     * The code schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * The message schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getMessage() {
        return message;
    }

    /**
     * The message schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setMessage(String message) {
        this.message = message;
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
        return new HashCodeBuilder().append(s).append(code).append(message).append(id).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OrderResponseDTO) == false) {
            return false;
        }
        OrderResponseDTO rhs = ((OrderResponseDTO) other);
        return new EqualsBuilder().append(s, rhs.s).append(code, rhs.code).append(message, rhs.message).append(id, rhs.id).isEquals();
    }

}
