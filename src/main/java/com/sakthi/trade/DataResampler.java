package com.sakthi.trade;

import com.sakthi.trade.zerodha.models.HistoricalDataExtended;
import com.zerodhatech.models.HistoricalData;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class DataResampler {
    static SimpleDateFormat candleDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public static List<HistoricalDataExtended> resampleOHLCData(List<HistoricalData> data, int interval) throws ParseException {
        List<HistoricalDataExtended> resampledData = new ArrayList<>();
        HistoricalDataExtended currentBar = null;
        for (HistoricalData dp : data) {
            Date candleTime=candleDateTimeFormat.parse(dp.timeStamp);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(candleTime);
            int minute = calendar.get(Calendar.MINUTE);
            int reminder=minute%interval;
            if (currentBar == null || reminder==0) {
                // Save the last bar
                if (currentBar != null) {
                    resampledData.add(currentBar);
                }
                // Start a new bar
                currentBar = new HistoricalDataExtended(dp.timeStamp, dp.open, dp.high, dp.low, dp.close);
            } else {
                // Aggregate data
                currentBar.high = Math.max(currentBar.high, dp.high);
                currentBar.low = Math.min(currentBar.low, dp.low);
                currentBar.close = dp.close; // Close will be set to the last data point's close
            }
        }
        // Add the last bar if it wasn't added in the loop
        if (currentBar != null) {
            resampledData.add(currentBar);
        }

        return resampledData;
    }
}
