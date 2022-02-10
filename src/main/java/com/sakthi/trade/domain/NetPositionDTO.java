
package com.sakthi.trade.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;


/**
 * The first anyOf schema
 * <p>
 * An explanation about the purpose of this instance.
 * 
 */
public class NetPositionDTO {

    /**
     * The crossCurrency schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("crossCurrency")
    @Expose
    @Nonnull
    private String crossCurrency = "";
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
     * The realized_profit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("realized_profit")
    @Expose
    @Nonnull
    private BigDecimal realizedProfit = new BigDecimal("0");
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
     * The unrealized_profit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("unrealized_profit")
    @Expose
    @Nonnull
    private BigDecimal unrealizedProfit = new BigDecimal("0");
    /**
     * The buyQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("buyQty")
    @Expose
    @Nonnull
    private Integer buyQty = 0;
    /**
     * The sellAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("sellAvg")
    @Expose
    @Nonnull
    private BigDecimal sellAvg = new BigDecimal("0");
    /**
     * The sellQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("sellQty")
    @Expose
    @Nonnull
    private Integer sellQty = 0;
    /**
     * The buyAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("buyAvg")
    @Expose
    @Nonnull
    private BigDecimal buyAvg = new BigDecimal("0");
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
     * The avgPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("avgPrice")
    @Expose
    @Nonnull
    private BigDecimal avgPrice = new BigDecimal("0");
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
     * The dummy schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("dummy")
    @Expose
    @Nonnull
    private String dummy = "";
    /**
     * The rbiRefRate schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("rbiRefRate")
    @Expose
    @Nonnull
    private BigDecimal rbiRefRate = new BigDecimal("0");
    /**
     * The netQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("netQty")
    @Expose
    @Nonnull
    private Integer netQty = 0;
    /**
     * The pl schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("pl")
    @Expose
    @Nonnull
    private BigDecimal pl = new BigDecimal("0");
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
     * The netAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("netAvg")
    @Expose
    @Nonnull
    private BigDecimal netAvg = new BigDecimal("0");
    /**
     * The qtyMulti_com schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("qtyMulti_com")
    @Expose
    @Nonnull
    private BigDecimal qtyMultiCom = new BigDecimal("0");

    /**
     * No args constructor for use in serialization
     * 
     */
    public NetPositionDTO() {
    }

    /**
     * 
     * @param symbol
     * @param crossCurrency
     * @param netQty
     * @param rbiRefRate
     * @param avgPrice
     * @param sellAvg
     * @param sellQty
     * @param buyAvg
     * @param fyToken
     * @param slNo
     * @param netAvg
     * @param dummy
     * @param realizedProfit
     * @param buyQty
     * @param qty
     * @param segment
     * @param unrealizedProfit
     * @param id
     * @param qtyMultiCom
     * @param pl
     * @param productType
     */
    public NetPositionDTO(String crossCurrency, Integer qty, BigDecimal realizedProfit, String id, BigDecimal unrealizedProfit, Integer buyQty, BigDecimal sellAvg, Integer sellQty, BigDecimal buyAvg, String symbol, String fyToken, Integer slNo, BigDecimal avgPrice, String segment, String dummy, BigDecimal rbiRefRate, Integer netQty, BigDecimal pl, String productType, BigDecimal netAvg, BigDecimal qtyMultiCom) {
        super();
        this.crossCurrency = crossCurrency;
        this.qty = qty;
        this.realizedProfit = realizedProfit;
        this.id = id;
        this.unrealizedProfit = unrealizedProfit;
        this.buyQty = buyQty;
        this.sellAvg = sellAvg;
        this.sellQty = sellQty;
        this.buyAvg = buyAvg;
        this.symbol = symbol;
        this.fyToken = fyToken;
        this.slNo = slNo;
        this.avgPrice = avgPrice;
        this.segment = segment;
        this.dummy = dummy;
        this.rbiRefRate = rbiRefRate;
        this.netQty = netQty;
        this.pl = pl;
        this.productType = productType;
        this.netAvg = netAvg;
        this.qtyMultiCom = qtyMultiCom;
    }

    /**
     * The crossCurrency schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getCrossCurrency() {
        return crossCurrency;
    }

    /**
     * The crossCurrency schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setCrossCurrency(String crossCurrency) {
        this.crossCurrency = crossCurrency;
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
     * The realized_profit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getRealizedProfit() {
        return realizedProfit;
    }

    /**
     * The realized_profit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setRealizedProfit(BigDecimal realizedProfit) {
        this.realizedProfit = realizedProfit;
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
     * The unrealized_profit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getUnrealizedProfit() {
        return unrealizedProfit;
    }

    /**
     * The unrealized_profit schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
        this.unrealizedProfit = unrealizedProfit;
    }

    /**
     * The buyQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getBuyQty() {
        return buyQty;
    }

    /**
     * The buyQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setBuyQty(Integer buyQty) {
        this.buyQty = buyQty;
    }

    /**
     * The sellAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getSellAvg() {
        return sellAvg;
    }

    /**
     * The sellAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSellAvg(BigDecimal sellAvg) {
        this.sellAvg = sellAvg;
    }

    /**
     * The sellQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getSellQty() {
        return sellQty;
    }

    /**
     * The sellQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSellQty(Integer sellQty) {
        this.sellQty = sellQty;
    }

    /**
     * The buyAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getBuyAvg() {
        return buyAvg;
    }

    /**
     * The buyAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setBuyAvg(BigDecimal buyAvg) {
        this.buyAvg = buyAvg;
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
     * The avgPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    /**
     * The avgPrice schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
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
     * The dummy schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getDummy() {
        return dummy;
    }

    /**
     * The dummy schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setDummy(String dummy) {
        this.dummy = dummy;
    }

    /**
     * The rbiRefRate schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getRbiRefRate() {
        return rbiRefRate;
    }

    /**
     * The rbiRefRate schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setRbiRefRate(BigDecimal rbiRefRate) {
        this.rbiRefRate = rbiRefRate;
    }

    /**
     * The netQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public Integer getNetQty() {
        return netQty;
    }

    /**
     * The netQty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setNetQty(Integer netQty) {
        this.netQty = netQty;
    }

    /**
     * The pl schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getPl() {
        return pl;
    }

    /**
     * The pl schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setPl(BigDecimal pl) {
        this.pl = pl;
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
     * The netAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getNetAvg() {
        return netAvg;
    }

    /**
     * The netAvg schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setNetAvg(BigDecimal netAvg) {
        this.netAvg = netAvg;
    }

    /**
     * The qtyMulti_com schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public BigDecimal getQtyMultiCom() {
        return qtyMultiCom;
    }

    /**
     * The qtyMulti_com schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setQtyMultiCom(BigDecimal qtyMultiCom) {
        this.qtyMultiCom = qtyMultiCom;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(crossCurrency).append(qty).append(realizedProfit).append(id).append(unrealizedProfit).append(buyQty).append(sellAvg).append(sellQty).append(buyAvg).append(symbol).append(fyToken).append(slNo).append(avgPrice).append(segment).append(dummy).append(rbiRefRate).append(netQty).append(pl).append(productType).append(netAvg).append(qtyMultiCom).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof NetPositionDTO) == false) {
            return false;
        }
        NetPositionDTO rhs = ((NetPositionDTO) other);
        return new EqualsBuilder().append(crossCurrency, rhs.crossCurrency).append(qty, rhs.qty).append(realizedProfit, rhs.realizedProfit).append(id, rhs.id).append(unrealizedProfit, rhs.unrealizedProfit).append(buyQty, rhs.buyQty).append(sellAvg, rhs.sellAvg).append(sellQty, rhs.sellQty).append(buyAvg, rhs.buyAvg).append(symbol, rhs.symbol).append(fyToken, rhs.fyToken).append(slNo, rhs.slNo).append(avgPrice, rhs.avgPrice).append(segment, rhs.segment).append(dummy, rhs.dummy).append(rbiRefRate, rhs.rbiRefRate).append(netQty, rhs.netQty).append(pl, rhs.pl).append(productType, rhs.productType).append(netAvg, rhs.netAvg).append(qtyMultiCom, rhs.qtyMultiCom).isEquals();
    }

}
