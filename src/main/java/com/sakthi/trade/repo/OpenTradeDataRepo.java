package com.sakthi.trade.repo;

import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface OpenTradeDataRepo extends JpaRepository<OpenTradeDataEntity,String> {

    @Query(value="select * from open_trade_data where create_timestamp=?1",nativeQuery=true)
     List<OpenTradeDataEntity> getOrbTradeDataEntityByCreateTimestampIn(Date createtimestamp);
    @Query(value="select * from public.open_trade_data where user_id=?1 and trade_date=?2",nativeQuery=true)
    List<OpenTradeDataEntity> getOrderDetails(String userId,String tradeDate);
    @Query(value="select * from public.open_trade_data where user_id=?1 and is_exited=false",nativeQuery=true)
    List<OpenTradeDataEntity> getOpenPositionDetails(String userId);

    @Query(value="select * from public.open_trade_data where user_id=?1 AND trade_date=?2 and is_exited=true",nativeQuery=true)
    List<OpenTradeDataEntity> findByUserIdAndTradeDate(String userId, String trade_date);

    @Query(value="select * from public.open_trade_data",nativeQuery=true)
     List<OpenTradeDataEntity> findAll();
}

