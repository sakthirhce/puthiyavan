
package com.sakthi.trade.dhan.schema;

import java.math.BigDecimal;
import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class PositionResponseDTO {

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
    @SerializedName("positionType")
    @Expose
    @Nonnull
    private String positionType;
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
    @SerializedName("buyAvg")
    @Expose
    @Nonnull
    private BigDecimal buyAvg;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("buyQty")
    @Expose
    @Nonnull
    private Integer buyQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("sellAvg")
    @Expose
    @Nonnull
    private BigDecimal sellAvg;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("sellQty")
    @Expose
    @Nonnull
    private Integer sellQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("netQty")
    @Expose
    @Nonnull
    private Integer netQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("realizedProfit")
    @Expose
    @Nonnull
    private BigDecimal realizedProfit;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("unrealizedProfit")
    @Expose
    @Nonnull
    private BigDecimal unrealizedProfit;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("rbiReferenceRate")
    @Expose
    @Nonnull
    private BigDecimal rbiReferenceRate;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("multiplier")
    @Expose
    @Nonnull
    private Integer multiplier;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("carryForwardBuyQty")
    @Expose
    @Nonnull
    private Integer carryForwardBuyQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("carryForwardSellQty")
    @Expose
    @Nonnull
    private Integer carryForwardSellQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("carryForwardBuyValue")
    @Expose
    @Nonnull
    private BigDecimal carryForwardBuyValue;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("carryForwardSellValue")
    @Expose
    @Nonnull
    private BigDecimal carryForwardSellValue;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("dayBuyQty")
    @Expose
    @Nonnull
    private Integer dayBuyQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("daySellQty")
    @Expose
    @Nonnull
    private Integer daySellQty;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("dayBuyValue")
    @Expose
    @Nonnull
    private BigDecimal dayBuyValue;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("daySellValue")
    @Expose
    @Nonnull
    private BigDecimal daySellValue;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("drvExpiryDate")
    @Expose
    @Nonnull
    private String drvExpiryDate;
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
    @SerializedName("crossCurrency")
    @Expose
    @Nonnull
    private Boolean crossCurrency;

    /**
     * No args constructor for use in serialization
     * 
     */
    public PositionResponseDTO() {
    }

    /**
     * 
     * @param exchangeSegment
     * @param positionType
     * @param carryForwardSellQty
     * @param sellAvg
     * @param carryForwardSellValue
     * @param buyAvg
     * @param realizedProfit
     * @param unrealizedProfit
     * @param tradingSymbol
     * @param daySellValue
     * @param carryForwardBuyQty
     * @param productType
     * @param drvExpiryDate
     * @param dhanClientId
     * @param carryForwardBuyValue
     * @param netQty
     * @param crossCurrency
     * @param dayBuyQty
     * @param multiplier
     * @param rbiReferenceRate
     * @param daySellQty
     * @param securityId
     * @param dayBuyValue
     * @param sellQty
     * @param drvOptionType
     * @param buyQty
     * @param drvStrikePrice
     */
    public PositionResponseDTO(String dhanClientId, String tradingSymbol, String securityId, String positionType, String exchangeSegment, String productType, BigDecimal buyAvg, Integer buyQty, BigDecimal sellAvg, Integer sellQty, Integer netQty, BigDecimal realizedProfit, BigDecimal unrealizedProfit, BigDecimal rbiReferenceRate, Integer multiplier, Integer carryForwardBuyQty, Integer carryForwardSellQty, BigDecimal carryForwardBuyValue, BigDecimal carryForwardSellValue, Integer dayBuyQty, Integer daySellQty, BigDecimal dayBuyValue, BigDecimal daySellValue, String drvExpiryDate, Object drvOptionType, BigDecimal drvStrikePrice, Boolean crossCurrency) {
        super();
        this.dhanClientId = dhanClientId;
        this.tradingSymbol = tradingSymbol;
        this.securityId = securityId;
        this.positionType = positionType;
        this.exchangeSegment = exchangeSegment;
        this.productType = productType;
        this.buyAvg = buyAvg;
        this.buyQty = buyQty;
        this.sellAvg = sellAvg;
        this.sellQty = sellQty;
        this.netQty = netQty;
        this.realizedProfit = realizedProfit;
        this.unrealizedProfit = unrealizedProfit;
        this.rbiReferenceRate = rbiReferenceRate;
        this.multiplier = multiplier;
        this.carryForwardBuyQty = carryForwardBuyQty;
        this.carryForwardSellQty = carryForwardSellQty;
        this.carryForwardBuyValue = carryForwardBuyValue;
        this.carryForwardSellValue = carryForwardSellValue;
        this.dayBuyQty = dayBuyQty;
        this.daySellQty = daySellQty;
        this.dayBuyValue = dayBuyValue;
        this.daySellValue = daySellValue;
        this.drvExpiryDate = drvExpiryDate;
        this.drvOptionType = drvOptionType;
        this.drvStrikePrice = drvStrikePrice;
        this.crossCurrency = crossCurrency;
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
    public String getPositionType() {
        return positionType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setPositionType(String positionType) {
        this.positionType = positionType;
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
    public BigDecimal getBuyAvg() {
        return buyAvg;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setBuyAvg(BigDecimal buyAvg) {
        this.buyAvg = buyAvg;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getBuyQty() {
        return buyQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setBuyQty(Integer buyQty) {
        this.buyQty = buyQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getSellAvg() {
        return sellAvg;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setSellAvg(BigDecimal sellAvg) {
        this.sellAvg = sellAvg;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getSellQty() {
        return sellQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setSellQty(Integer sellQty) {
        this.sellQty = sellQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getNetQty() {
        return netQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setNetQty(Integer netQty) {
        this.netQty = netQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getRealizedProfit() {
        return realizedProfit;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setRealizedProfit(BigDecimal realizedProfit) {
        this.realizedProfit = realizedProfit;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getUnrealizedProfit() {
        return unrealizedProfit;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
        this.unrealizedProfit = unrealizedProfit;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getRbiReferenceRate() {
        return rbiReferenceRate;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setRbiReferenceRate(BigDecimal rbiReferenceRate) {
        this.rbiReferenceRate = rbiReferenceRate;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getMultiplier() {
        return multiplier;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setMultiplier(Integer multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getCarryForwardBuyQty() {
        return carryForwardBuyQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCarryForwardBuyQty(Integer carryForwardBuyQty) {
        this.carryForwardBuyQty = carryForwardBuyQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getCarryForwardSellQty() {
        return carryForwardSellQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCarryForwardSellQty(Integer carryForwardSellQty) {
        this.carryForwardSellQty = carryForwardSellQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getCarryForwardBuyValue() {
        return carryForwardBuyValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCarryForwardBuyValue(BigDecimal carryForwardBuyValue) {
        this.carryForwardBuyValue = carryForwardBuyValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getCarryForwardSellValue() {
        return carryForwardSellValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCarryForwardSellValue(BigDecimal carryForwardSellValue) {
        this.carryForwardSellValue = carryForwardSellValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getDayBuyQty() {
        return dayBuyQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDayBuyQty(Integer dayBuyQty) {
        this.dayBuyQty = dayBuyQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getDaySellQty() {
        return daySellQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDaySellQty(Integer daySellQty) {
        this.daySellQty = daySellQty;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getDayBuyValue() {
        return dayBuyValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDayBuyValue(BigDecimal dayBuyValue) {
        this.dayBuyValue = dayBuyValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getDaySellValue() {
        return daySellValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDaySellValue(BigDecimal daySellValue) {
        this.daySellValue = daySellValue;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getDrvExpiryDate() {
        return drvExpiryDate;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setDrvExpiryDate(String drvExpiryDate) {
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
    public Boolean getCrossCurrency() {
        return crossCurrency;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCrossCurrency(Boolean crossCurrency) {
        this.crossCurrency = crossCurrency;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(dhanClientId).append(tradingSymbol).append(securityId).append(positionType).append(exchangeSegment).append(productType).append(buyAvg).append(buyQty).append(sellAvg).append(sellQty).append(netQty).append(realizedProfit).append(unrealizedProfit).append(rbiReferenceRate).append(multiplier).append(carryForwardBuyQty).append(carryForwardSellQty).append(carryForwardBuyValue).append(carryForwardSellValue).append(dayBuyQty).append(daySellQty).append(dayBuyValue).append(daySellValue).append(drvExpiryDate).append(drvOptionType).append(drvStrikePrice).append(crossCurrency).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PositionResponseDTO) == false) {
            return false;
        }
        PositionResponseDTO rhs = ((PositionResponseDTO) other);
        return new EqualsBuilder().append(dhanClientId, rhs.dhanClientId).append(tradingSymbol, rhs.tradingSymbol).append(securityId, rhs.securityId).append(positionType, rhs.positionType).append(exchangeSegment, rhs.exchangeSegment).append(productType, rhs.productType).append(buyAvg, rhs.buyAvg).append(buyQty, rhs.buyQty).append(sellAvg, rhs.sellAvg).append(sellQty, rhs.sellQty).append(netQty, rhs.netQty).append(realizedProfit, rhs.realizedProfit).append(unrealizedProfit, rhs.unrealizedProfit).append(rbiReferenceRate, rhs.rbiReferenceRate).append(multiplier, rhs.multiplier).append(carryForwardBuyQty, rhs.carryForwardBuyQty).append(carryForwardSellQty, rhs.carryForwardSellQty).append(carryForwardBuyValue, rhs.carryForwardBuyValue).append(carryForwardSellValue, rhs.carryForwardSellValue).append(dayBuyQty, rhs.dayBuyQty).append(daySellQty, rhs.daySellQty).append(dayBuyValue, rhs.dayBuyValue).append(daySellValue, rhs.daySellValue).append(drvExpiryDate, rhs.drvExpiryDate).append(drvOptionType, rhs.drvOptionType).append(drvStrikePrice, rhs.drvStrikePrice).append(crossCurrency, rhs.crossCurrency).isEquals();
    }

}
