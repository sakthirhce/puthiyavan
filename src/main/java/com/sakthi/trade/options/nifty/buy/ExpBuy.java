package com.sakthi.trade.options.nifty.buy;

import com.sakthi.trade.entity.OpenTradeDataEntity;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.account.UserList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class ExpBuy {
    @Autowired
    TransactionService transactionService;
    @Autowired
    ZerodhaTransactionService zerodhaTransactionService;
    @Autowired
    SendMessage sendMessage;

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(5);
    @Autowired
    UserList userList;
    List<OpenTradeDataEntity> openTradeDataEntities = new ArrayList<>();
    public void buy(){
        //NIFTY FIN SERVICE
        //NIFTY 50
        String fnifty = zerodhaTransactionService.niftyIndics.get("NIFTY FIN SERVICE");
        String nifty = zerodhaTransactionService.niftyIndics.get("NIFTY 50");



    }
}
