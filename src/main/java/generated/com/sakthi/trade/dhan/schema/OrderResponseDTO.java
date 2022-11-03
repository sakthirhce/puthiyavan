
package com.sakthi.trade.dhan.schema;

import java.math.BigDecimal;
import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class OrderResponseDTO {

    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("dhanClientId")
    @Expose
    @Nonnull
    private String dhanClientId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("orderId")
    @Expose
    @Nonnull
    private String orderId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("correlationId")
    @Expose
    @Nonnull
    private String correlationId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("orderStatus")
    @Expose
    @Nonnull
    private String orderStatus;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("transactionType")
    @Expose
    @Nonnull
    private String transactionType;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("exchangeSegment")
    @Expose
    @Nonnull
    private String exchangeSegment;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("productType")
    @Expose
    @Nonnull
    private String productType;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("orderType")
    @Expose
    @Nonnull
    private String orderType;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("validity")
    @Expose
    @Nonnull
    private String validity;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("tradingSymbol")
    @Expose
    @Nonnull
    private String tradingSymbol;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("securityId")
    @Expose
    @Nonnull
    private String securityId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("quantity")
    @Expose
    @Nonnull
    private Integer quantity;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("disclosedQuantity")
    @Expose
    @Nonnull
    private Integer disclosedQuantity;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("price")
    @Expose
    @Nonnull
    private BigDecimal price;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("triggerPrice")
    @Expose
    @Nonnull
    private BigDecimal triggerPrice;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("afterMarketOrder")
    @Expose
    @Nonnull
    private Boolean afterMarketOrder;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("boProfitValue")
    @Expose
    @Nonnull
    private BigDecimal boProfitValue;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("boStopLossValue")
    @Expose
    @Nonnull
    private BigDecimal boStopLossValue;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("legName")
    @Expose
    @Nonnull
    private String legName;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("createTime")
    @Expose
    @Nonnull
    private String createTime;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("updateTime")
    @Expose
    @Nonnull
    private String updateTime;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("exchangeTime")
    @Expose
    @Nonnull
    private String exchangeTime;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("drvExpiryDate")
    @Expose
    @Nonnull
    private Object drvExpiryDate;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("drvOptionType")
    @Expose
    @Nonnull
    private Object drvOptionType;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("drvStrikePrice")
    @Expose
    @Nonnull
    private BigDecimal drvStrikePrice;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("omsErrorCode")
    @Expose
    @Nonnull
    private Object omsErrorCode;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("omsErrorDescription")
    @Expose
    @Nonnull
    private Object omsErrorDescription;

    /**
     * No args constructor for use in serialization
     * 
     */
    public OrderResponseDTO() {
    }

    /**
     * 
     * @param exchangeSegment
     * @param orderType
     * @param orderId
     * @param orderStatus
     * @param boProfitValue
     * @param price
     * @param correlationId
     * @param tradingSymbol
     * @param productType
     * @param drvExpiryDate
     * @param dhanClientId
     * @param disclosedQuantity
     * @param quantity
     * @param triggerPrice
     * @param exchangeTime
     * @param omsErrorDescription
     * @param securityId
     * @param boStopLossValue
     * @param updateTime
     * @param transactionType
     * @param drvOptionType
     * @param legName
     * @param omsErrorCode
     * @param createTime
     * @param validity
     * @param afterMarketOrder
     * @param drvStrikePrice
     */
    public OrderResponseDTO(String dhanClientId, String orderId, String correlationId, String orderStatus, String transactionType, String exchangeSegment, String productType, String orderType, String validity, String tradingSymbol, String securityId, Integer quantity, Integer disclosedQuantity, BigDecimal price, BigDecimal triggerPrice, Boolean afterMarketOrder, BigDecimal boProfitValue, BigDecimal boStopLossValue, String legName, String createTime, String updateTime, String exchangeTime, Object drvExpiryDate, Object drvOptionType, BigDecimal drvStrikePrice, Object omsErrorCode, Object omsErrorDescription) {
        super();
        this.dhanClientId = dhanClientId;
        this.orderId = orderId;
        this.correlationId = correlationId;
        this.orderStatus = orderStatus;
        this.transactionType = transactionType;
        this.exchangeSegment = exchangeSegment;
        this.productType = productType;
        this.orderType = orderType;
        this.validity = validity;
        this.tradingSymbol = tradingSymbol;
        this.securityId = securityId;
        this.quantity = quantity;
        this.disclosedQuantity = disclosedQuantity;
        this.price = price;
        this.triggerPrice = triggerPrice;
        this.afterMarketOrder = afterMarketOrder;
        this.boProfitValue = boProfitValue;
        this.boStopLossValue = boStopLossValue;
        this.legName = legName;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.exchangeTime = exchangeTime;
        this.drvExpiryDate = drvExpiryDate;
        this.drvOptionType = drvOptionType;
        this.drvStrikePrice = drvStrikePrice;
        this.omsErrorCode = omsErrorCode;
        this.omsErrorDescription = omsErrorDescription;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getDhanClientId() {
        return dhanClientId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDhanClientId(String dhanClientId) {
        this.dhanClientId = dhanClientId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getOrderStatus() {
        return orderStatus;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getTransactionType() {
        return transactionType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getExchangeSegment() {
        return exchangeSegment;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setExchangeSegment(String exchangeSegment) {
        this.exchangeSegment = exchangeSegment;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getProductType() {
        return productType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setProductType(String productType) {
        this.productType = productType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getOrderType() {
        return orderType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getValidity() {
        return validity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setValidity(String validity) {
        this.validity = validity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getTradingSymbol() {
        return tradingSymbol;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setTradingSymbol(String tradingSymbol) {
        this.tradingSymbol = tradingSymbol;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getSecurityId() {
        return securityId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getDisclosedQuantity() {
        return disclosedQuantity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDisclosedQuantity(Integer disclosedQuantity) {
        this.disclosedQuantity = disclosedQuantity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Boolean getAfterMarketOrder() {
        return afterMarketOrder;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setAfterMarketOrder(Boolean afterMarketOrder) {
        this.afterMarketOrder = afterMarketOrder;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getBoProfitValue() {
        return boProfitValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setBoProfitValue(BigDecimal boProfitValue) {
        this.boProfitValue = boProfitValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getBoStopLossValue() {
        return boStopLossValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setBoStopLossValue(BigDecimal boStopLossValue) {
        this.boStopLossValue = boStopLossValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getLegName() {
        return legName;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setLegName(String legName) {
        this.legName = legName;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getCreateTime() {
        return createTime;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getUpdateTime() {
        return updateTime;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getExchangeTime() {
        return exchangeTime;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setExchangeTime(String exchangeTime) {
        this.exchangeTime = exchangeTime;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Object getDrvExpiryDate() {
        return drvExpiryDate;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDrvExpiryDate(Object drvExpiryDate) {
        this.drvExpiryDate = drvExpiryDate;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Object getDrvOptionType() {
        return drvOptionType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDrvOptionType(Object drvOptionType) {
        this.drvOptionType = drvOptionType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getDrvStrikePrice() {
        return drvStrikePrice;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDrvStrikePrice(BigDecimal drvStrikePrice) {
        this.drvStrikePrice = drvStrikePrice;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Object getOmsErrorCode() {
        return omsErrorCode;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setOmsErrorCode(Object omsErrorCode) {
        this.omsErrorCode = omsErrorCode;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Object getOmsErrorDescription() {
        return omsErrorDescription;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setOmsErrorDescription(Object omsErrorDescription) {
        this.omsErrorDescription = omsErrorDescription;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(dhanClientId).append(orderId).append(correlationId).append(orderStatus).append(transactionType).append(exchangeSegment).append(productType).append(orderType).append(validity).append(tradingSymbol).append(securityId).append(quantity).append(disclosedQuantity).append(price).append(triggerPrice).append(afterMarketOrder).append(boProfitValue).append(boStopLossValue).append(legName).append(createTime).append(updateTime).append(exchangeTime).append(drvExpiryDate).append(drvOptionType).append(drvStrikePrice).append(omsErrorCode).append(omsErrorDescription).toHashCode();
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
        return new EqualsBuilder().append(dhanClientId, rhs.dhanClientId).append(orderId, rhs.orderId).append(correlationId, rhs.correlationId).append(orderStatus, rhs.orderStatus).append(transactionType, rhs.transactionType).append(exchangeSegment, rhs.exchangeSegment).append(productType, rhs.productType).append(orderType, rhs.orderType).append(validity, rhs.validity).append(tradingSymbol, rhs.tradingSymbol).append(securityId, rhs.securityId).append(quantity, rhs.quantity).append(disclosedQuantity, rhs.disclosedQuantity).append(price, rhs.price).append(triggerPrice, rhs.triggerPrice).append(afterMarketOrder, rhs.afterMarketOrder).append(boProfitValue, rhs.boProfitValue).append(boStopLossValue, rhs.boStopLossValue).append(legName, rhs.legName).append(createTime, rhs.createTime).append(updateTime, rhs.updateTime).append(exchangeTime, rhs.exchangeTime).append(drvExpiryDate, rhs.drvExpiryDate).append(drvOptionType, rhs.drvOptionType).append(drvStrikePrice, rhs.drvStrikePrice).append(omsErrorCode, rhs.omsErrorCode).append(omsErrorDescription, rhs.omsErrorDescription).isEquals();
    }

}
