
package com.sakthi.trade.fyer;

import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class AuthTokenRequestDTO {

    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("grant_type")
    @Expose
    @Nonnull
    private String grantType;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("appIdHash")
    @Expose
    @Nonnull
    private String appIdHash;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("code")
    @Expose
    @Nonnull
    private String code;

    /**
     * No args constructor for use in serialization
     * 
     */
    public AuthTokenRequestDTO() {
    }

    /**
     * 
     * @param code
     * @param appIdHash
     * @param grantType
     */
    public AuthTokenRequestDTO(String grantType, String appIdHash, String code) {
        super();
        this.grantType = grantType;
        this.appIdHash = appIdHash;
        this.code = code;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getGrantType() {
        return grantType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getAppIdHash() {
        return appIdHash;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setAppIdHash(String appIdHash) {
        this.appIdHash = appIdHash;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getCode() {
        return code;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(grantType).append(appIdHash).append(code).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AuthTokenRequestDTO) == false) {
            return false;
        }
        AuthTokenRequestDTO rhs = ((AuthTokenRequestDTO) other);
        return new EqualsBuilder().append(grantType, rhs.grantType).append(appIdHash, rhs.appIdHash).append(code, rhs.code).isEquals();
    }

}
