package com.sakthi.trade.repo;

import com.sakthi.trade.entity.AlgoTestEntity;
import com.sakthi.trade.entity.BankNiftyOptionDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface AlgoTestDataRepo extends JpaRepository<AlgoTestEntity,String> {
    @Query(value="select * from ALGO_TEST_DATA order by entry_time",nativeQuery=true)
    List<AlgoTestEntity> findOrderedData();

}
