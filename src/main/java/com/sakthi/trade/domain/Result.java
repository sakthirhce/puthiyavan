package com.sakthi.trade.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Result {

    @SerializedName("display_name")
    @Expose
    private Object displayName;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("email_id")
    @Expose
    private String emailId;
    @SerializedName("profile_image")
    @Expose
    private Object profileImage;
    @SerializedName("password_last_changed")
    @Expose
    private String passwordLastChanged;
    @SerializedName("user_id")
    @Expose
    private String userId;
    @SerializedName("PAN")
    @Expose
    private String pAN;
    @SerializedName("password_expiry_days")
    @Expose
    private Long passwordExpiryDays;

    public Object getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Object displayName) {
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public Object getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(Object profileImage) {
        this.profileImage = profileImage;
    }

    public String getPasswordLastChanged() {
        return passwordLastChanged;
    }

    public void setPasswordLastChanged(String passwordLastChanged) {
        this.passwordLastChanged = passwordLastChanged;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPAN() {
        return pAN;
    }

    public void setPAN(String pAN) {
        this.pAN = pAN;
    }

    public Long getPasswordExpiryDays() {
        return passwordExpiryDays;
    }

    public void setPasswordExpiryDays(Long passwordExpiryDays) {
        this.passwordExpiryDays = passwordExpiryDays;
    }

}