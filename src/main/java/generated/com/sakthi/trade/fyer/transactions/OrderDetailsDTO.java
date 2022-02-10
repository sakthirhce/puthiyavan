
package com.sakthi.trade.fyer.transactions;

import java.math.BigDecimal;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * The orderDetails schema
 * <p>
 * An explanation about the purpose of this instance.
 * 
 */
public class OrderDetailsDTO {

    /**
     * The status schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("status")
    @Expose
    @Nonnull
    private Integer status = 0;
    /**
     * The symbol schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("symbol")
    @Expose
    @Nonnull
    private String symbol = "";
    /**
     * The qty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("qty")
    @Expose
    @Nonnull
    private Integer qty = 0;
    /**
     * The orderNumStatus schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("orderNumStatus")
    @Expose
    @Nonnull
    private String orderNumStatus = "";
    /**
     * The dqQtyRem schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("dqQtyRem")
    @Expose
    @Nonnull
    private Integer dqQtyRem = 0;
    /**
     * The orderDateTime schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("orderDateTime")
    @Expose
    @Nonnull
    private String orderDateTime = "";
    /**
     * The orderValidity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("orderValidity")
    @Expose
    @Nonnull
    private String orderValidity = "";
    /**
     * The fyToken schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("fyToken")
    @Expose
    @Nonnull
    private String fyToken = "";
    /**
     * The slNo schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("slNo")
    @Expose
    @Nonnull
    private Integer slNo = 0;
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
     * The segment schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("segment")
    @Expose
    @Nonnull
    private String segment = "";
    /**
     * The id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("id")
    @Expose
    @Nonnull
    private String id = "";
    /**
     * The stopPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("stopPrice")
    @Expose
    @Nonnull
    private BigDecimal stopPrice = new BigDecimal("0");
    /**
     * The instrument schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("instrument")
    @Expose
    @Nonnull
    private String instrument = "";
    /**
     * The exchOrdId schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("exchOrdId")
    @Expose
    @Nonnull
    private String exchOrdId = "";
    /**
     * The remainingQuantity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("remainingQuantity")
    @Expose
    @Nonnull
    private Integer remainingQuantity = 0;
    /**
     * The filledQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("filledQty")
    @Expose
    @Nonnull
    private Integer filledQty = 0;
    /**
     * The limitPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("limitPrice")
    @Expose
    @Nonnull
    private BigDecimal limitPrice = new BigDecimal("0");
    /**
     * The offlineOrder schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("offlineOrder")
    @Expose
    @Nonnull
    private Boolean offlineOrder = false;
    /**
     * The source schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("source")
    @Expose
    @Nonnull
    private String source = "";
    /**
     * The productType schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("productType")
    @Expose
    @Nonnull
    private String productType = "";
    /**
     * The type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("type")
    @Expose
    @Nonnull
    private Integer type = 0;
    /**
     * The side schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("side")
    @Expose
    @Nonnull
    private Integer side = 0;
    /**
     * The tradedPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("tradedPrice")
    @Expose
    @Nonnull
    private BigDecimal tradedPrice = new BigDecimal("0");
    /**
     * The discloseQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("discloseQty")
    @Expose
    @Nonnull
    private Integer discloseQty = 0;

    /**
     * No args constructor for use in serialization
     * 
     */
    public OrderDetailsDTO() {
    }

    /**
     * 
     * @param remainingQuantity
     * @param symbol
     * @param instrument
     * @param source
     * @param type
     * @param slNo
     * @param offlineOrder
     * @param segment
     * @param dqQtyRem
     * @param id
     * @param productType
     * @param orderDateTime
     * @param side
     * @param limitPrice
     * @param tradedPrice
     * @param message
     * @param fyToken
     * @param stopPrice
     * @param qty
     * @param orderValidity
     * @param exchOrdId
     * @param discloseQty
     * @param filledQty
     * @param orderNumStatus
     * @param status
     */
    public OrderDetailsDTO(Integer status, String symbol, Integer qty, String orderNumStatus, Integer dqQtyRem, String orderDateTime, String orderValidity, String fyToken, Integer slNo, String message, String segment, String id, BigDecimal stopPrice, String instrument, String exchOrdId, Integer remainingQuantity, Integer filledQty, BigDecimal limitPrice, Boolean offlineOrder, String source, String productType, Integer type, Integer side, BigDecimal tradedPrice, Integer discloseQty) {
        super();
        this.status = status;
        this.symbol = symbol;
        this.qty = qty;
        this.orderNumStatus = orderNumStatus;
        this.dqQtyRem = dqQtyRem;
        this.orderDateTime = orderDateTime;
        this.orderValidity = orderValidity;
        this.fyToken = fyToken;
        this.slNo = slNo;
        this.message = message;
        this.segment = segment;
        this.id = id;
        this.stopPrice = stopPrice;
        this.instrument = instrument;
        this.exchOrdId = exchOrdId;
        this.remainingQuantity = remainingQuantity;
        this.filledQty = filledQty;
        this.limitPrice = limitPrice;
        this.offlineOrder = offlineOrder;
        this.source = source;
        this.productType = productType;
        this.type = type;
        this.side = side;
        this.tradedPrice = tradedPrice;
        this.discloseQty = discloseQty;
    }

    /**
     * The status schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * The status schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setStatus(Integer status) {
        this.status = status;
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
     * The qty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getQty() {
        return qty;
    }

    /**
     * The qty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setQty(Integer qty) {
        this.qty = qty;
    }

    /**
     * The orderNumStatus schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getOrderNumStatus() {
        return orderNumStatus;
    }

    /**
     * The orderNumStatus schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setOrderNumStatus(String orderNumStatus) {
        this.orderNumStatus = orderNumStatus;
    }

    /**
     * The dqQtyRem schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getDqQtyRem() {
        return dqQtyRem;
    }

    /**
     * The dqQtyRem schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setDqQtyRem(Integer dqQtyRem) {
        this.dqQtyRem = dqQtyRem;
    }

    /**
     * The orderDateTime schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getOrderDateTime() {
        return orderDateTime;
    }

    /**
     * The orderDateTime schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setOrderDateTime(String orderDateTime) {
        this.orderDateTime = orderDateTime;
    }

    /**
     * The orderValidity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getOrderValidity() {
        return orderValidity;
    }

    /**
     * The orderValidity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setOrderValidity(String orderValidity) {
        this.orderValidity = orderValidity;
    }

    /**
     * The fyToken schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getFyToken() {
        return fyToken;
    }

    /**
     * The fyToken schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setFyToken(String fyToken) {
        this.fyToken = fyToken;
    }

    /**
     * The slNo schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getSlNo() {
        return slNo;
    }

    /**
     * The slNo schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSlNo(Integer slNo) {
        this.slNo = slNo;
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
     * The segment schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSegment() {
        return segment;
    }

    /**
     * The segment schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSegment(String segment) {
        this.segment = segment;
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

    /**
     * The stopPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getStopPrice() {
        return stopPrice;
    }

    /**
     * The stopPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setStopPrice(BigDecimal stopPrice) {
        this.stopPrice = stopPrice;
    }

    /**
     * The instrument schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getInstrument() {
        return instrument;
    }

    /**
     * The instrument schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    /**
     * The exchOrdId schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getExchOrdId() {
        return exchOrdId;
    }

    /**
     * The exchOrdId schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setExchOrdId(String exchOrdId) {
        this.exchOrdId = exchOrdId;
    }

    /**
     * The remainingQuantity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    /**
     * The remainingQuantity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setRemainingQuantity(Integer remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    /**
     * The filledQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getFilledQty() {
        return filledQty;
    }

    /**
     * The filledQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setFilledQty(Integer filledQty) {
        this.filledQty = filledQty;
    }

    /**
     * The limitPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    /**
     * The limitPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    /**
     * The offlineOrder schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Boolean getOfflineOrder() {
        return offlineOrder;
    }

    /**
     * The offlineOrder schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setOfflineOrder(Boolean offlineOrder) {
        this.offlineOrder = offlineOrder;
    }

    /**
     * The source schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSource() {
        return source;
    }

    /**
     * The source schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * The productType schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getProductType() {
        return productType;
    }

    /**
     * The productType schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setProductType(String productType) {
        this.productType = productType;
    }

    /**
     * The type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getType() {
        return type;
    }

    /**
     * The type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * The side schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getSide() {
        return side;
    }

    /**
     * The side schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSide(Integer side) {
        this.side = side;
    }

    /**
     * The tradedPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getTradedPrice() {
        return tradedPrice;
    }

    /**
     * The tradedPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setTradedPrice(BigDecimal tradedPrice) {
        this.tradedPrice = tradedPrice;
    }

    /**
     * The discloseQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getDiscloseQty() {
        return discloseQty;
    }

    /**
     * The discloseQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setDiscloseQty(Integer discloseQty) {
        this.discloseQty = discloseQty;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(status).append(symbol).append(qty).append(orderNumStatus).append(dqQtyRem).append(orderDateTime).append(orderValidity).append(fyToken).append(slNo).append(message).append(segment).append(id).append(stopPrice).append(instrument).append(exchOrdId).append(remainingQuantity).append(filledQty).append(limitPrice).append(offlineOrder).append(source).append(productType).append(type).append(side).append(tradedPrice).append(discloseQty).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OrderDetailsDTO) == false) {
            return false;
        }
        OrderDetailsDTO rhs = ((OrderDetailsDTO) other);
        return new EqualsBuilder().append(status, rhs.status).append(symbol, rhs.symbol).append(qty, rhs.qty).append(orderNumStatus, rhs.orderNumStatus).append(dqQtyRem, rhs.dqQtyRem).append(orderDateTime, rhs.orderDateTime).append(orderValidity, rhs.orderValidity).append(fyToken, rhs.fyToken).append(slNo, rhs.slNo).append(message, rhs.message).append(segment, rhs.segment).append(id, rhs.id).append(stopPrice, rhs.stopPrice).append(instrument, rhs.instrument).append(exchOrdId, rhs.exchOrdId).append(remainingQuantity, rhs.remainingQuantity).append(filledQty, rhs.filledQty).append(limitPrice, rhs.limitPrice).append(offlineOrder, rhs.offlineOrder).append(source, rhs.source).append(productType, rhs.productType).append(type, rhs.type).append(side, rhs.side).append(tradedPrice, rhs.tradedPrice).append(discloseQty, rhs.discloseQty).isEquals();
    }

}
