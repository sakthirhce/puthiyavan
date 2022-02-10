
package com.sakthi.trade.fyer.transactions;

import java.math.BigDecimal;
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
public class PlaceOrderRequestDTO {

    /**
     * The symbol schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("symbol")
    @Expose
    @Nonnull
    private String symbol = "";
    /**
     * The qty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("qty")
    @Expose
    @Nonnull
    private Integer qty = 0;
    /**
     * The type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("type")
    @Expose
    @Nonnull
    private Integer type = 0;
    /**
     * The side schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("side")
    @Expose
    @Nonnull
    private Integer side = 0;
    /**
     * The productType schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("productType")
    @Expose
    @Nonnull
    private String productType = "";
    /**
     * The limitPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("limitPrice")
    @Expose
    @Nonnull
    private BigDecimal limitPrice = new BigDecimal("0");
    /**
     * The stopPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("stopPrice")
    @Expose
    @Nonnull
    private BigDecimal stopPrice = new BigDecimal("0");
    /**
     * The disclosedQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("disclosedQty")
    @Expose
    @Nonnull
    private Integer disclosedQty = 0;
    /**
     * The validity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("validity")
    @Expose
    @Nonnull
    private String validity = "";
    /**
     * The offlineOrder schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("offlineOrder")
    @Expose
    @Nonnull
    private String offlineOrder = "";
    /**
     * The stopLoss schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("stopLoss")
    @Expose
    @Nonnull
    private BigDecimal stopLoss = new BigDecimal("0");
    /**
     * The takeProfit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("takeProfit")
    @Expose
    @Nonnull
    private BigDecimal takeProfit = new BigDecimal("0");

    /**
     * No args constructor for use in serialization
     * 
     */
    public PlaceOrderRequestDTO() {
    }

    /**
     * 
     * @param symbol
     * @param side
     * @param stopPrice
     * @param offlineOrder
     * @param limitPrice
     * @param qty
     * @param stopLoss
     * @param disclosedQty
     * @param validity
     * @param type
     * @param takeProfit
     * @param productType
     */
    public PlaceOrderRequestDTO(String symbol, Integer qty, Integer type, Integer side, String productType, BigDecimal limitPrice, BigDecimal stopPrice, Integer disclosedQty, String validity, String offlineOrder, BigDecimal stopLoss, BigDecimal takeProfit) {
        super();
        this.symbol = symbol;
        this.qty = qty;
        this.type = type;
        this.side = side;
        this.productType = productType;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.disclosedQty = disclosedQty;
        this.validity = validity;
        this.offlineOrder = offlineOrder;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
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
     * The disclosedQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getDisclosedQty() {
        return disclosedQty;
    }

    /**
     * The disclosedQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setDisclosedQty(Integer disclosedQty) {
        this.disclosedQty = disclosedQty;
    }

    /**
     * The validity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getValidity() {
        return validity;
    }

    /**
     * The validity schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setValidity(String validity) {
        this.validity = validity;
    }

    /**
     * The offlineOrder schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getOfflineOrder() {
        return offlineOrder;
    }

    /**
     * The offlineOrder schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setOfflineOrder(String offlineOrder) {
        this.offlineOrder = offlineOrder;
    }

    /**
     * The stopLoss schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    /**
     * The stopLoss schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }

    /**
     * The takeProfit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getTakeProfit() {
        return takeProfit;
    }

    /**
     * The takeProfit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setTakeProfit(BigDecimal takeProfit) {
        this.takeProfit = takeProfit;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(symbol).append(qty).append(type).append(side).append(productType).append(limitPrice).append(stopPrice).append(disclosedQty).append(validity).append(offlineOrder).append(stopLoss).append(takeProfit).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PlaceOrderRequestDTO) == false) {
            return false;
        }
        PlaceOrderRequestDTO rhs = ((PlaceOrderRequestDTO) other);
        return new EqualsBuilder().append(symbol, rhs.symbol).append(qty, rhs.qty).append(type, rhs.type).append(side, rhs.side).append(productType, rhs.productType).append(limitPrice, rhs.limitPrice).append(stopPrice, rhs.stopPrice).append(disclosedQty, rhs.disclosedQty).append(validity, rhs.validity).append(offlineOrder, rhs.offlineOrder).append(stopLoss, rhs.stopLoss).append(takeProfit, rhs.takeProfit).isEquals();
    }

}
