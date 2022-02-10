package com.sakthi.trade.domain;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
@Slf4j
@Data
public class OptionCandleData {
    long x;
    List<Double> y;
}
