
package com.sakthi.trade.dhan.schema;

import java.math.BigDecimal;
import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class FundResponseDTO {

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
    @SerializedName("availabelBalance")
    @Expose
    @Nonnull
    private BigDecimal availabelBalance;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("sodLimit")
    @Expose
    @Nonnull
    private Integer sodLimit;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("collateralAmount")
    @Expose
    @Nonnull
    private BigDecimal collateralAmount;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("receiveableAmount")
    @Expose
    @Nonnull
    private BigDecimal receiveableAmount;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("utilizedAmount")
    @Expose
    @Nonnull
    private BigDecimal utilizedAmount;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("blockedPayoutAmount")
    @Expose
    @Nonnull
    private BigDecimal blockedPayoutAmount;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("withdrawableBalance")
    @Expose
    @Nonnull
    private BigDecimal withdrawableBalance;

    /**
     * No args constructor for use in serialization
     * 
     */
    public FundResponseDTO() {
    }

    /**
     * 
     * @param availabelBalance
     * @param receiveableAmount
     * @param collateralAmount
     * @param blockedPayoutAmount
     * @param withdrawableBalance
     * @param sodLimit
     * @param dhanClientId
     * @param utilizedAmount
     */
    public FundResponseDTO(String dhanClientId, BigDecimal availabelBalance, Integer sodLimit, BigDecimal collateralAmount, BigDecimal receiveableAmount, BigDecimal utilizedAmount, BigDecimal blockedPayoutAmount, BigDecimal withdrawableBalance) {
        super();
        this.dhanClientId = dhanClientId;
        this.availabelBalance = availabelBalance;
        this.sodLimit = sodLimit;
        this.collateralAmount = collateralAmount;
        this.receiveableAmount = receiveableAmount;
        this.utilizedAmount = utilizedAmount;
        this.blockedPayoutAmount = blockedPayoutAmount;
        this.withdrawableBalance = withdrawableBalance;
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
    public BigDecimal getAvailabelBalance() {
        return availabelBalance;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setAvailabelBalance(BigDecimal availabelBalance) {
        this.availabelBalance = availabelBalance;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getSodLimit() {
        return sodLimit;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setSodLimit(Integer sodLimit) {
        this.sodLimit = sodLimit;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getCollateralAmount() {
        return collateralAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCollateralAmount(BigDecimal collateralAmount) {
        this.collateralAmount = collateralAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getReceiveableAmount() {
        return receiveableAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setReceiveableAmount(BigDecimal receiveableAmount) {
        this.receiveableAmount = receiveableAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getUtilizedAmount() {
        return utilizedAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setUtilizedAmount(BigDecimal utilizedAmount) {
        this.utilizedAmount = utilizedAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getBlockedPayoutAmount() {
        return blockedPayoutAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setBlockedPayoutAmount(BigDecimal blockedPayoutAmount) {
        this.blockedPayoutAmount = blockedPayoutAmount;
    }

    /**
     * 
     * (Required)
     * 
     */
    public BigDecimal getWithdrawableBalance() {
        return withdrawableBalance;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setWithdrawableBalance(BigDecimal withdrawableBalance) {
        this.withdrawableBalance = withdrawableBalance;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(dhanClientId).append(availabelBalance).append(sodLimit).append(collateralAmount).append(receiveableAmount).append(utilizedAmount).append(blockedPayoutAmount).append(withdrawableBalance).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FundResponseDTO) == false) {
            return false;
        }
        FundResponseDTO rhs = ((FundResponseDTO) other);
        return new EqualsBuilder().append(dhanClientId, rhs.dhanClientId).append(availabelBalance, rhs.availabelBalance).append(sodLimit, rhs.sodLimit).append(collateralAmount, rhs.collateralAmount).append(receiveableAmount, rhs.receiveableAmount).append(utilizedAmount, rhs.utilizedAmount).append(blockedPayoutAmount, rhs.blockedPayoutAmount).append(withdrawableBalance, rhs.withdrawableBalance).isEquals();
    }

}
