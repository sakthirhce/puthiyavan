package com.sakthi.trade.repo;

import com.sakthi.trade.domain.OpenPositionData;
import com.sakthi.trade.entity.LivePLDataEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface LivePLDataRepo extends JpaRepository<LivePLDataEntity,String> {

}

