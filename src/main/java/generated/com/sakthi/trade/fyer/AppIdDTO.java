
package com.sakthi.trade.fyer;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * The app_id schema
 * <p>
 * An explanation about the purpose of this instance.
 * 
 */
public class AppIdDTO {


    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AppIdDTO) == false) {
            return false;
        }
        AppIdDTO rhs = ((AppIdDTO) other);
        return new EqualsBuilder().isEquals();
    }

}
