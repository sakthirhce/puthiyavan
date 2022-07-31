package com.sakthi.trade.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;

@Getter
@Setter
public class User {
    String userName;
    String name;
    String userId;
}
