package com.sakthi.trade.repo;

import com.sakthi.trade.entity.LivePLDataEntity;
import com.sakthi.trade.entity.PositionPLDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionPLDataRepo extends JpaRepository<PositionPLDataEntity,String> {

}

