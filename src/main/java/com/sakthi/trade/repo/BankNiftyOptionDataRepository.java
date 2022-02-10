package com.sakthi.trade.repo;

import com.sakthi.trade.entity.BankNiftyOptionDataEntity;
import com.sakthi.trade.entity.BankNiftyOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface BankNiftyOptionDataRepository extends JpaRepository<BankNiftyOptionDataEntity,String> {

    @Query(value="select * from BANK_NIFTY_OPTION_DATA where exp_key=?1 order by trade_time",nativeQuery=true)
    List<BankNiftyOptionDataEntity> findExpOptionData( String strikeKey);
}
