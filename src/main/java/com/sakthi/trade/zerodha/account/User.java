package com.sakthi.trade.zerodha.account;

import com.sakthi.trade.domain.TradeData;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class User {
    String name;
    String password;
    String totp;
    String broker;
    String appkey;
    String loginurl;
    String secret;
    KiteConnect kiteConnect;
    boolean admin;
    boolean enabled;
    StraddleConfig straddleConfig;
    StraddleConfig straddleConfig920;
    StraddleConfig straddleConfigOld;
    StrangleConfig strangleConfig;
    TelegramBot telegramBot;
}
