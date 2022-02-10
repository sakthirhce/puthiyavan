
package com.sakthi.trade.fyer.transactions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
public class OrderStatusResponseDTO {

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
     * The orderDetails schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("orderDetails")
    @Expose
    @Nonnull
    private OrderDetailsDTO orderDetails;

    /**
     * No args constructor for use in serialization
     * 
     */
    public OrderStatusResponseDTO() {
    }

    /**
     * 
     * @param orderDetails
     * @param s
     * @param message
     */
    public OrderStatusResponseDTO(String s, String message, OrderDetailsDTO orderDetails) {
        super();
        this.s = s;
        this.message = message;
        this.orderDetails = orderDetails;
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
     * The orderDetails schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public OrderDetailsDTO getOrderDetails() {
        return orderDetails;
    }

    /**
     * The orderDetails schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setOrderDetails(OrderDetailsDTO orderDetails) {
        this.orderDetails = orderDetails;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(s).append(message).append(orderDetails).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OrderStatusResponseDTO) == false) {
            return false;
        }
        OrderStatusResponseDTO rhs = ((OrderStatusResponseDTO) other);
        return new EqualsBuilder().append(s, rhs.s).append(message, rhs.message).append(orderDetails, rhs.orderDetails).isEquals();
    }

}
