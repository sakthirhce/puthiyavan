
package com.sakthi.trade.fyer;

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
public class AuthRequestDTO {

    /**
     * The app_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("app_id")
    @Expose
    @Nonnull
    private AppIdDTO appId;
    /**
     * The secret_key schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    @SerializedName("secret_key")
    @Expose
    @Nonnull
    private String secretKey = "";

    /**
     * No args constructor for use in serialization
     * 
     */
    public AuthRequestDTO() {
    }

    /**
     * 
     * @param secretKey
     * @param appId
     */
    public AuthRequestDTO(AppIdDTO appId, String secretKey) {
        super();
        this.appId = appId;
        this.secretKey = secretKey;
    }

    /**
     * The app_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public AppIdDTO getAppId() {
        return appId;
    }

    /**
     * The app_id schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setAppId(AppIdDTO appId) {
        this.appId = appId;
    }

    /**
     * The secret_key schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * The secret_key schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Required)
     * 
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(appId).append(secretKey).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AuthRequestDTO) == false) {
            return false;
        }
        AuthRequestDTO rhs = ((AuthRequestDTO) other);
        return new EqualsBuilder().append(appId, rhs.appId).append(secretKey, rhs.secretKey).isEquals();
    }

}
