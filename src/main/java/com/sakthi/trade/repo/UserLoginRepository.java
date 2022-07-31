package com.sakthi.trade.repo;

import com.sakthi.trade.entity.UserLoginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserLoginRepository extends JpaRepository<UserLoginEntity,String> {

}
