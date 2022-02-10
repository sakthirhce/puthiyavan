
package com.sakthi.trade.truedata;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
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
public class TickResponseDTO {

    /**
     * The trade schema
     * <p>
     * An explanation about the purpose of this instance.
     * (Can be null)
     * 
     */
    @Nullable
    @SerializedName("trade")
    @Expose
    private List<String> trade = new ArrayList<String>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public TickResponseDTO() {
    }

    /**
     * 
     * @param trade
     */
    public TickResponseDTO(List<String> trade) {
        super();
        this.trade = trade;
    }

    /**
     * The trade schema
     * <p>
     * An explanation about the purpose of this instance.
     * 
     */
    public List<String> getTrade() {
        return trade;
    }

    /**
     * The trade schema
     * <p>
     * An explanation about the purpose of this instance.
     * 
     */
    public void setTrade(List<String> trade) {
        this.trade = trade;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(trade).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TickResponseDTO) == false) {
            return false;
        }
        TickResponseDTO rhs = ((TickResponseDTO) other);
        return new EqualsBuilder().append(trade, rhs.trade).isEquals();
    }

}
