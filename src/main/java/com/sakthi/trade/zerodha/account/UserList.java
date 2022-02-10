package com.sakthi.trade.zerodha.account;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Setter
@Slf4j
@Component
@ConfigurationProperties(prefix = "account")
public class UserList {
    private List<User> user;

    public List<User> getUser(){
        return user.stream().filter(user1 -> user1.enabled).collect(Collectors.toList());
    }


}
