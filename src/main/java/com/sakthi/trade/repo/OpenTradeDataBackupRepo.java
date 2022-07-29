package com.sakthi.trade.repo;

import com.sakthi.trade.entity.OpenTradeDataBackupEntity;
import com.sakthi.trade.entity.OpenTradeDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface OpenTradeDataBackupRepo extends JpaRepository<OpenTradeDataBackupEntity,String> {

    @Query(value="select * from open_trade_data_backup where create_timestamp=?1",nativeQuery=true)
     List<OpenTradeDataEntity> getOrbTradeDataEntityByCreateTimestampIn(Date createtimestamp);
    @Query(value="select * from public.open_trade_data_backup",nativeQuery=true)
     List<OpenTradeDataBackupEntity> findAll();
    @Query(value="select * from public.open_trade_data_backup where user_id=?1 AND trade_date=?2 and is_exited=true",nativeQuery=true)
    List<OpenTradeDataBackupEntity> findByUserIdAndTradeDate(String userId,String trade_date);
}

