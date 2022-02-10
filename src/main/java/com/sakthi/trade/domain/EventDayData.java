package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class EventDayData {
    String day;
    String eventInformation;
    int orbPercentMargin;
    int trendMargin;
    int straddleMargin;
}
