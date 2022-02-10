
package com.sakthi.trade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * The first anyOf schema
 * <p>
 * An explanation about the purpose of this instance.
 * 
 */
public class DatumDTO {

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
     * The series schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("series")
    @Expose
    @Nonnull
    private String series = "";
    /**
     * The xDt schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("xDt")
    @Expose
    @Nonnull
    private String xDt = "";
    /**
     * The caAct schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("caAct")
    @Expose
    @Nonnull
    private String caAct = "";
    /**
     * The iep schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("iep")
    @Expose
    @Nonnull
    private String iep = "";
    /**
     * The chn schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("chn")
    @Expose
    @Nonnull
    private String chn = "";
    /**
     * The perChn schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("perChn")
    @Expose
    @Nonnull
    private String perChn = "";
    /**
     * The pCls schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("pCls")
    @Expose
    @Nonnull
    private String pCls = "";
    /**
     * The trdQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("trdQnty")
    @Expose
    @Nonnull
    private String trdQnty = "";
    /**
     * The iVal schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("iVal")
    @Expose
    @Nonnull
    private String iVal = "";
    /**
     * The mktCap schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("mktCap")
    @Expose
    @Nonnull
    private String mktCap = "";
    /**
     * The yHigh schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("yHigh")
    @Expose
    @Nonnull
    private String yHigh = "";
    /**
     * The yLow schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("yLow")
    @Expose
    @Nonnull
    private String yLow = "";
    /**
     * The sumVal schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("sumVal")
    @Expose
    @Nonnull
    private String sumVal = "";
    /**
     * The sumQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("sumQnty")
    @Expose
    @Nonnull
    private String sumQnty = "";
    /**
     * The finQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("finQnty")
    @Expose
    @Nonnull
    private String finQnty = "";
    /**
     * The sumfinQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * (Required)
     * 
     */
    @Nullable
    @SerializedName("sumfinQnty")
    @Expose
    @Nonnull
    private String sumfinQnty = "";

    /**
     * No args constructor for use in serialization
     * 
     */
    public DatumDTO() {
    }

    /**
     * 
     * @param symbol
     * @param yLow
     * @param sumfinQnty
     * @param mktCap
     * @param chn
     * @param xDt
     * @param pCls
     * @param iVal
     * @param trdQnty
     * @param perChn
     * @param yHigh
     * @param finQnty
     * @param caAct
     * @param iep
     * @param series
     * @param sumVal
     * @param sumQnty
     */
    public DatumDTO(String symbol, String series, String xDt, String caAct, String iep, String chn, String perChn, String pCls, String trdQnty, String iVal, String mktCap, String yHigh, String yLow, String sumVal, String sumQnty, String finQnty, String sumfinQnty) {
        super();
        this.symbol = symbol;
        this.series = series;
        this.xDt = xDt;
        this.caAct = caAct;
        this.iep = iep;
        this.chn = chn;
        this.perChn = perChn;
        this.pCls = pCls;
        this.trdQnty = trdQnty;
        this.iVal = iVal;
        this.mktCap = mktCap;
        this.yHigh = yHigh;
        this.yLow = yLow;
        this.sumVal = sumVal;
        this.sumQnty = sumQnty;
        this.finQnty = finQnty;
        this.sumfinQnty = sumfinQnty;
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
     * The series schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSeries() {
        return series;
    }

    /**
     * The series schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSeries(String series) {
        this.series = series;
    }

    /**
     * The xDt schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getXDt() {
        return xDt;
    }

    /**
     * The xDt schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setXDt(String xDt) {
        this.xDt = xDt;
    }

    /**
     * The caAct schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getCaAct() {
        return caAct;
    }

    /**
     * The caAct schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setCaAct(String caAct) {
        this.caAct = caAct;
    }

    /**
     * The iep schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getIep() {
        return iep;
    }

    /**
     * The iep schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setIep(String iep) {
        this.iep = iep;
    }

    /**
     * The chn schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getChn() {
        return chn;
    }

    /**
     * The chn schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setChn(String chn) {
        this.chn = chn;
    }

    /**
     * The perChn schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getPerChn() {
        return perChn;
    }

    /**
     * The perChn schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setPerChn(String perChn) {
        this.perChn = perChn;
    }

    /**
     * The pCls schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getPCls() {
        return pCls;
    }

    /**
     * The pCls schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setPCls(String pCls) {
        this.pCls = pCls;
    }

    /**
     * The trdQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getTrdQnty() {
        return trdQnty;
    }

    /**
     * The trdQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setTrdQnty(String trdQnty) {
        this.trdQnty = trdQnty;
    }

    /**
     * The iVal schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getIVal() {
        return iVal;
    }

    /**
     * The iVal schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setIVal(String iVal) {
        this.iVal = iVal;
    }

    /**
     * The mktCap schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getMktCap() {
        return mktCap;
    }

    /**
     * The mktCap schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setMktCap(String mktCap) {
        this.mktCap = mktCap;
    }

    /**
     * The yHigh schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getYHigh() {
        return yHigh;
    }

    /**
     * The yHigh schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setYHigh(String yHigh) {
        this.yHigh = yHigh;
    }

    /**
     * The yLow schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getYLow() {
        return yLow;
    }

    /**
     * The yLow schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setYLow(String yLow) {
        this.yLow = yLow;
    }

    /**
     * The sumVal schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSumVal() {
        return sumVal;
    }

    /**
     * The sumVal schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSumVal(String sumVal) {
        this.sumVal = sumVal;
    }

    /**
     * The sumQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSumQnty() {
        return sumQnty;
    }

    /**
     * The sumQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSumQnty(String sumQnty) {
        this.sumQnty = sumQnty;
    }

    /**
     * The finQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getFinQnty() {
        return finQnty;
    }

    /**
     * The finQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setFinQnty(String finQnty) {
        this.finQnty = finQnty;
    }

    /**
     * The sumfinQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSumfinQnty() {
        return sumfinQnty;
    }

    /**
     * The sumfinQnty schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSumfinQnty(String sumfinQnty) {
        this.sumfinQnty = sumfinQnty;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(symbol).append(series).append(xDt).append(caAct).append(iep).append(chn).append(perChn).append(pCls).append(trdQnty).append(iVal).append(mktCap).append(yHigh).append(yLow).append(sumVal).append(sumQnty).append(finQnty).append(sumfinQnty).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DatumDTO) == false) {
            return false;
        }
        DatumDTO rhs = ((DatumDTO) other);
        return new EqualsBuilder().append(symbol, rhs.symbol).append(series, rhs.series).append(xDt, rhs.xDt).append(caAct, rhs.caAct).append(iep, rhs.iep).append(chn, rhs.chn).append(perChn, rhs.perChn).append(pCls, rhs.pCls).append(trdQnty, rhs.trdQnty).append(iVal, rhs.iVal).append(mktCap, rhs.mktCap).append(yHigh, rhs.yHigh).append(yLow, rhs.yLow).append(sumVal, rhs.sumVal).append(sumQnty, rhs.sumQnty).append(finQnty, rhs.finQnty).append(sumfinQnty, rhs.sumfinQnty).isEquals();
    }

}
