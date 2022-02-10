
package com.sakthi.trade.fyer;

import javax.annotation.Nonnull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class AuthTokenResponseDTO {

    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("s")
    @Expose
    @Nonnull
    private String s;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("code")
    @Expose
    @Nonnull
    private Integer code;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("message")
    @Expose
    @Nonnull
    private String message;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("access_token")
    @Expose
    @Nonnull
    private String accessToken;

    /**
     * No args constructor for use in serialization
     * 
     */
    public AuthTokenResponseDTO() {
    }

    /**
     * 
     * @param s
     * @param code
     * @param message
     * @param accessToken
     */
    public AuthTokenResponseDTO(String s, Integer code, String message, String accessToken) {
        super();
        this.s = s;
        this.code = code;
        this.message = message;
        this.accessToken = accessToken;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getS() {
        return s;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setS(String s) {
        this.s = s;
    }

    /**
     * 
     * (Required)
     * 
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getMessage() {
        return message;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 
     * (Required)
     * 
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * 
     * (Required)
     * 
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(s).append(code).append(message).append(accessToken).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AuthTokenResponseDTO) == false) {
            return false;
        }
        AuthTokenResponseDTO rhs = ((AuthTokenResponseDTO) other);
        return new EqualsBuilder().append(s, rhs.s).append(code, rhs.code).append(message, rhs.message).append(accessToken, rhs.accessToken).isEquals();
    }

}
