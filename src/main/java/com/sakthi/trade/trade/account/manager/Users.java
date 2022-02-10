package com.sakthi.trade.trade.account.manager;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties("account")
public class Users {
    List<User> user;
}
