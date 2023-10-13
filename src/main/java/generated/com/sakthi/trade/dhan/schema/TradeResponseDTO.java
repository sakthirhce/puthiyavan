
package com.sakthi.trade.dhan.schema;

import java.math.BigDecimal;
import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Generated schema for Root
 * <p>
 * 
 * 
 */
public class TradeResponseDTO {

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
    @SerializedName("exchangeOrderId")
    @Expose
    @Nonnull
    private String exchangeOrderId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("exchangeTradeId")
    @Expose
    @Nonnull
    private String exchangeTradeId;
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
    @SerializedName("tradedQuantity")
    @Expose
    @Nonnull
    private BigDecimal tradedQuantity;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("tradedPrice")
    @Expose
    @Nonnull
    private BigDecimal tradedPrice;
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
     * No args constructor for use in serialization
     * 
     */
    public TradeResponseDTO() {
    }

    /**
     * 
     * @param exchangeSegment
     * @param orderType
     * @param orderId
     * @param tradedPrice
     * @param exchangeTime
     * @param securityId
     * @param updateTime
     * @param transactionType
     * @param exchangeTradeId
     * @param drvOptionType
     * @param createTime
     * @param tradingSymbol
     * @param productType
     * @param tradedQuantity
     * @param drvExpiryDate
     * @param dhanClientId
     * @param exchangeOrderId
     * @param drvStrikePrice
     */
    public TradeResponseDTO(String dhanClientId, String orderId, String exchangeOrderId, String exchangeTradeId, String transactionType, String exchangeSegment, String productType, String orderType, String tradingSymbol, String securityId, BigDecimal tradedQuantity, BigDecimal tradedPrice, String createTime, String updateTime, String exchangeTime, Object drvExpiryDate, Object drvOptionType, BigDecimal drvStrikePrice) {
        super();
        this.dhanClientId = dhanClientId;
        this.orderId = orderId;
        this.exchangeOrderId = exchangeOrderId;
        this.exchangeTradeId = exchangeTradeId;
        this.transactionType = transactionType;
        this.exchangeSegment = exchangeSegment;
        this.productType = productType;
        this.orderType = orderType;
        this.tradingSymbol = tradingSymbol;
        this.securityId = securityId;
        this.tradedQuantity = tradedQuantity;
        this.tradedPrice = tradedPrice;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.exchangeTime = exchangeTime;
        this.drvExpiryDate = drvExpiryDate;
        this.drvOptionType = drvOptionType;
        this.drvStrikePrice = drvStrikePrice;
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
    public String getExchangeOrderId() {
        return exchangeOrderId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getExchangeTradeId() {
        return exchangeTradeId;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setExchangeTradeId(String exchangeTradeId) {
        this.exchangeTradeId = exchangeTradeId;
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
    public BigDecimal getTradedQuantity() {
        return tradedQuantity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setTradedQuantity(BigDecimal tradedQuantity) {
        this.tradedQuantity = tradedQuantity;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getTradedPrice() {
        return tradedPrice;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setTradedPrice(BigDecimal tradedPrice) {
        this.tradedPrice = tradedPrice;
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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(dhanClientId).append(orderId).append(exchangeOrderId).append(exchangeTradeId).append(transactionType).append(exchangeSegment).append(productType).append(orderType).append(tradingSymbol).append(securityId).append(tradedQuantity).append(tradedPrice).append(createTime).append(updateTime).append(exchangeTime).append(drvExpiryDate).append(drvOptionType).append(drvStrikePrice).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TradeResponseDTO) == false) {
            return false;
        }
        TradeResponseDTO rhs = ((TradeResponseDTO) other);
        return new EqualsBuilder().append(dhanClientId, rhs.dhanClientId).append(orderId, rhs.orderId).append(exchangeOrderId, rhs.exchangeOrderId).append(exchangeTradeId, rhs.exchangeTradeId).append(transactionType, rhs.transactionType).append(exchangeSegment, rhs.exchangeSegment).append(productType, rhs.productType).append(orderType, rhs.orderType).append(tradingSymbol, rhs.tradingSymbol).append(securityId, rhs.securityId).append(tradedQuantity, rhs.tradedQuantity).append(tradedPrice, rhs.tradedPrice).append(createTime, rhs.createTime).append(updateTime, rhs.updateTime).append(exchangeTime, rhs.exchangeTime).append(drvExpiryDate, rhs.drvExpiryDate).append(drvOptionType, rhs.drvOptionType).append(drvStrikePrice, rhs.drvStrikePrice).isEquals();
    }

}
