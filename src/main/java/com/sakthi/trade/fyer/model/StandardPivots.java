package com.sakthi.trade.fyer.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StandardPivots {
    StandardPivot dayPivots;
    StandardPivot weekPivots;
    StandardPivot monthPivots;
    StandardPivot yearPivots;
}
