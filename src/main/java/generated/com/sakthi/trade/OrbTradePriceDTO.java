
package com.sakthi.trade;

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
public class OrbTradePriceDTO {

    /**
     * The stock_name schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("stock_name")
    @Expose
    @Nonnull
    private String stockName = "";
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
     * The high_price schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("high_price")
    @Expose
    @Nonnull
    private BigDecimal highPrice = new BigDecimal("0");
    /**
     * The low_price schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("low_price")
    @Expose
    @Nonnull
    private BigDecimal lowPrice = new BigDecimal("0");
    /**
     * The stock_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("stock_id")
    @Expose
    @Nonnull
    private Integer stockId = 0;
    /**
     * The is_order_placed schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("is_order_placed")
    @Expose
    @Nonnull
    private Boolean isOrderPlaced = false;
    /**
     * The is_sl_placed schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("is_sl_placed")
    @Expose
    @Nonnull
    private Boolean isSlPlaced = false;
    /**
     * The is_exited schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("is_exited")
    @Expose
    @Nonnull
    private Boolean isExited = false;
    /**
     * The entry_order_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("entry_order_id")
    @Expose
    @Nonnull
    private String entryOrderId = "";
    /**
     * The sl_order_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("sl_order_id")
    @Expose
    @Nonnull
    private String slOrderId = "";
    /**
     * The entry_type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("entry_type")
    @Expose
    @Nonnull
    private String entryType = "";
    /**
     * The amount_per_stock schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("amount_per_stock")
    @Expose
    @Nonnull
    private BigDecimal amountPerStock = new BigDecimal("0");
    /**
     * The is_errored schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("is_errored")
    @Expose
    @Nonnull
    private Boolean isErrored = false;

    /**
     * No args constructor for use in serialization
     * 
     */
    public OrbTradePriceDTO() {
    }

    /**
     * 
     * @param entryType
     * @param isSlPlaced
     * @param amountPerStock
     * @param isOrderPlaced
     * @param slOrderId
     * @param stockName
     * @param isErrored
     * @param lowPrice
     * @param isExited
     * @param qty
     * @param highPrice
     * @param stockId
     * @param entryOrderId
     */
    public OrbTradePriceDTO(String stockName, Integer qty, BigDecimal highPrice, BigDecimal lowPrice, Integer stockId, Boolean isOrderPlaced, Boolean isSlPlaced, Boolean isExited, String entryOrderId, String slOrderId, String entryType, BigDecimal amountPerStock, Boolean isErrored) {
        super();
        this.stockName = stockName;
        this.qty = qty;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.stockId = stockId;
        this.isOrderPlaced = isOrderPlaced;
        this.isSlPlaced = isSlPlaced;
        this.isExited = isExited;
        this.entryOrderId = entryOrderId;
        this.slOrderId = slOrderId;
        this.entryType = entryType;
        this.amountPerStock = amountPerStock;
        this.isErrored = isErrored;
    }

    /**
     * The stock_name schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getStockName() {
        return stockName;
    }

    /**
     * The stock_name schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setStockName(String stockName) {
        this.stockName = stockName;
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
     * The high_price schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getHighPrice() {
        return highPrice;
    }

    /**
     * The high_price schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    /**
     * The low_price schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    /**
     * The low_price schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    /**
     * The stock_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getStockId() {
        return stockId;
    }

    /**
     * The stock_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setStockId(Integer stockId) {
        this.stockId = stockId;
    }

    /**
     * The is_order_placed schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Boolean getIsOrderPlaced() {
        return isOrderPlaced;
    }

    /**
     * The is_order_placed schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setIsOrderPlaced(Boolean isOrderPlaced) {
        this.isOrderPlaced = isOrderPlaced;
    }

    /**
     * The is_sl_placed schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Boolean getIsSlPlaced() {
        return isSlPlaced;
    }

    /**
     * The is_sl_placed schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setIsSlPlaced(Boolean isSlPlaced) {
        this.isSlPlaced = isSlPlaced;
    }

    /**
     * The is_exited schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Boolean getIsExited() {
        return isExited;
    }

    /**
     * The is_exited schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setIsExited(Boolean isExited) {
        this.isExited = isExited;
    }

    /**
     * The entry_order_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getEntryOrderId() {
        return entryOrderId;
    }

    /**
     * The entry_order_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setEntryOrderId(String entryOrderId) {
        this.entryOrderId = entryOrderId;
    }

    /**
     * The sl_order_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSlOrderId() {
        return slOrderId;
    }

    /**
     * The sl_order_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSlOrderId(String slOrderId) {
        this.slOrderId = slOrderId;
    }

    /**
     * The entry_type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getEntryType() {
        return entryType;
    }

    /**
     * The entry_type schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    /**
     * The amount_per_stock schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getAmountPerStock() {
        return amountPerStock;
    }

    /**
     * The amount_per_stock schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setAmountPerStock(BigDecimal amountPerStock) {
        this.amountPerStock = amountPerStock;
    }

    /**
     * The is_errored schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Boolean getIsErrored() {
        return isErrored;
    }

    /**
     * The is_errored schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setIsErrored(Boolean isErrored) {
        this.isErrored = isErrored;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(stockName).append(qty).append(highPrice).append(lowPrice).append(stockId).append(isOrderPlaced).append(isSlPlaced).append(isExited).append(entryOrderId).append(slOrderId).append(entryType).append(amountPerStock).append(isErrored).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof OrbTradePriceDTO) == false) {
            return false;
        }
        OrbTradePriceDTO rhs = ((OrbTradePriceDTO) other);
        return new EqualsBuilder().append(stockName, rhs.stockName).append(qty, rhs.qty).append(highPrice, rhs.highPrice).append(lowPrice, rhs.lowPrice).append(stockId, rhs.stockId).append(isOrderPlaced, rhs.isOrderPlaced).append(isSlPlaced, rhs.isSlPlaced).append(isExited, rhs.isExited).append(entryOrderId, rhs.entryOrderId).append(slOrderId, rhs.slOrderId).append(entryType, rhs.entryType).append(amountPerStock, rhs.amountPerStock).append(isErrored, rhs.isErrored).isEquals();
    }

}
