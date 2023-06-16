package com.sakthi.trade.options;

import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;

@Component
@Slf4j
public class OptionDayViceTest {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat dateTimeFormatT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat dateTimeFormatForwordSlash = new SimpleDateFormat("yyyy/MM/dd");
    SimpleDateFormat monthformatter = new SimpleDateFormat("MMMM");
    SimpleDateFormat mmformatter = new SimpleDateFormat("MM");
    SimpleDateFormat yearformatter = new SimpleDateFormat("yyyy");
    SimpleDateFormat dayformatter = new SimpleDateFormat("dd");
    @Autowired
    ZerodhaAccount zerodhaAccount;
    KiteConnect kiteConnect;
    @Autowired
    UserList userList;
    public void test(int day) throws ParseException, IOException, KiteException {
        while (day < 0) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            Calendar calendar = Calendar.getInstance();
            System.out.println(calendar.getFirstDayOfWeek());
            System.out.println(calendar.get(DAY_OF_WEEK));
            //TODO: add logic to determine wednesday exp date if thursday is trade holiday

            calendar.add(DAY_OF_MONTH, day);

            log.info("Date:" + dateFormat.format(calendar.getTime()));
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

            interval = "day";

            String startDate = dateFormat.format(cdate);
            String currentDate = dateFormat.format(cdate);
            // String niftyBank = zerodhaTransactionService.niftyIndics.get("NIFTY BANK");
            Map<String, TradeData> tradeMap = new HashMap<>();
            if(kiteConnect == null) {
                User user =userList.getUser().stream().filter(user1 -> user1.isAdmin()).findFirst().get();
                kiteConnect = user.getKiteConnect();
            }
            HistoricalData historicalData = kiteConnect.getHistoricalData(dateTimeFormat.parse(currentDate + " 00:00:00"), dateTimeFormat.parse(currentDate + " 23:59:59"), "260105", interval, false, false);
            //    HistoricalData historicalData=zerodhaAccount.kiteSdk.getHistoricalData(sdfformat.parse("2021-01-05 09:00:00"),sdfformat.parse("2021-01-09 03:30:00"),niftyBank,"5minute",false,false);
            if (historicalData.dataArrayList.size() > 0) {
                historicalData.dataArrayList.stream().forEach(historicalData1 -> {
                    System.out.println(historicalData1.timeStamp+":"+historicalData1.close);
                });
            }
            day++;
        }
    }
}
