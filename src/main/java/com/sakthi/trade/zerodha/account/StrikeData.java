package com.sakthi.trade.zerodha.account;

import lombok.Data;

@Data
public class StrikeData {
    String strike;
    String zerodhaId;
    String zerodhaSymbol;
    String dhanId;
    String dhanSymbol;
    String strikeType;
}
