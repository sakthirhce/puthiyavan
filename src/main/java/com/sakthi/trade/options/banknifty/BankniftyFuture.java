/*
package com.sakthi.trade.options.banknifty;

import com.sakthi.trade.domain.TradeData;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Slf4j
public class BankniftyFuture {

    public void futureTrade(String tf,int day){
        Calendar calendar = Calendar.getInstance();
        System.out.println(calendar.getFirstDayOfWeek());
        System.out.println(calendar.get(DAY_OF_WEEK));
        //TODO: add logic to determine wednesday exp date if thursday is trade holiday

        calendar.add(DAY_OF_MONTH, day);

        log.info("Date:"+dateFormat.format(calendar.getTime()));
        Calendar cthurs = Calendar.getInstance();
        cthurs.add(DAY_OF_MONTH, day);
        int dayadd = 5 - cthurs.get(DAY_OF_WEEK);
        if (dayadd > 0) {
            cthurs.add(DAY_OF_MONTH, dayadd);
        } else if (dayadd == -2) {
            cthurs.add(DAY_OF_MONTH, 5);
        } else if (dayadd < 0) {
            cthurs.add(DAY_OF_MONTH, 6);
        }
        Date cdate = calendar.getTime();
        String interval;
        if(tf.equals("5min")){
            interval="5minute";
        }else
        {
            interval="3minute";
        }
        String startDate = dateFormat.format(cdate);
        String currentDate = dateFormat.format(cdate);
        // String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
        Map<String, TradeData> tradeMap = new HashMap<>();
        HistoricalData historicalData = zerodhaAccount.kiteSdk.getHistoricalData(dateTimeFormat.parse(currentDate + " 09:15:00"), dateTimeFormat.parse(currentDate + " 15:15:00"), "260105", interval, false, false);

    }
}
*/
