package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

@Getter
@Setter
@Entity(name = "user_login")
public class UserLoginEntity {
    @Id
    @Column(name="user_name", nullable =false)
    String userName;
    @Column(name="name", nullable =false)
    String name;
    @Column(name="user_id", nullable=false)
    String userId;
    @Column(name="password", nullable=false)
    String password;
}

