package com.sakthi.trade.eventday;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.domain.EventDayData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class EventDayConfiguration {
    @Value("${filepath.trend}")
     String trendPath;

    public Map<String, EventDayData> eventDayMap=new HashMap<>();
    public Map<String, EventDayData> eventDayConfig() throws IOException, CsvValidationException, ParseException {
        try {
            CSVReader csvReader = new CSVReader(new FileReader(trendPath + "/event_day_config.csv"));
            String[] line;
            int i = 0;
            while ((line = csvReader.readNext()) != null) {
                if (i > 0) {
                    Date date = new SimpleDateFormat("yyyy-MM-dd").parse(line[0]);
                    EventDayData eventDayData = new EventDayData();
                    eventDayData.setDay(line[0]);
                    eventDayData.setEventInformation(line[1]);
                    eventDayData.setOrbPercentMargin(Integer.valueOf(line[2]));
                    eventDayData.setTrendMargin(Integer.valueOf(line[3]));
                    eventDayData.setStraddleMargin(Integer.valueOf(line[4]));
                    eventDayMap.put(line[0], eventDayData);
                }
                i++;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return eventDayMap;
    }
}
