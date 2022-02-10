package com.sakthi.trade.repo;

import com.sakthi.trade.entity.BankNiftyOptionDataEntity;
import com.sakthi.trade.entity.BankNiftyOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface BankNiftyOptionRepository extends JpaRepository<BankNiftyOptionEntity,String> {

    @Query(value="select * from BANK_NIFTY_OPTION where exp_date=?1 and strike=?2",nativeQuery=true)
    BankNiftyOptionEntity findExpStrike(Date date,String strike);
    @Query(value="select distinct exp_date from BANK_NIFTY_OPTION order by exp_date desc",nativeQuery=true)
    List<String> getExpDate();
    @Query(value="select distinct SUBSTRING(strike,0,6) from BANK_NIFTY_OPTION where exp_date=?1 order by SUBSTRING(strike,0,6      ) desc",nativeQuery=true)
    List<String> getExpStikeAll(Date date);
    @Query(value="select * from BANK_NIFTY_OPTION where exp_date=?1 and strike=?2",nativeQuery=true)
    List<BankNiftyOptionEntity> findExpStrikeAll(Date date,String strike);
    @Query(value="select * from BANK_NIFTY_OPTION_DATA where exp_key=?1",nativeQuery=true)
    List<BankNiftyOptionDataEntity> findExpDataAll(String strike);
    @Query(value="select * from BANK_NIFTY_OPTION_DATA where exp_date=?1",nativeQuery=true)
    BankNiftyOptionDataEntity findExpData(String strike);
}
