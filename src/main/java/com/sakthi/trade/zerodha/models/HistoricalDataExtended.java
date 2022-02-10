package com.sakthi.trade.zerodha.models;

import com.zerodhatech.models.HistoricalData;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
@Setter
@Getter
public class HistoricalDataExtended {
    public String timeStamp;
    public double open;
    public double high;
    public double low;
    public double close;
    public long volume;
    public long oi;
    public double vwap;
    public double rsi;
    public double oima20;
    public double volumema50;
    public List<HistoricalDataExtended> dataArrayList = new ArrayList();

    public HistoricalDataExtended() {
    }

    public void parseResponse(JSONObject response) throws JSONException {
        JSONObject data = response.getJSONObject("data");
        JSONArray candleArray = data.getJSONArray("candles");

        for(int i = 0; i < candleArray.length(); ++i) {
            JSONArray itemArray = candleArray.getJSONArray(i);
            HistoricalDataExtended historicalData = new HistoricalDataExtended();
            historicalData.timeStamp = itemArray.getString(0);
            historicalData.open = itemArray.getDouble(1);
            historicalData.high = itemArray.getDouble(2);
            historicalData.low = itemArray.getDouble(3);
            historicalData.close = itemArray.getDouble(4);
            historicalData.volume = itemArray.getLong(5);
            if (itemArray.length() > 6) {
                historicalData.oi = itemArray.getLong(6);
            }

            this.dataArrayList.add(historicalData);
        }

    }
}
