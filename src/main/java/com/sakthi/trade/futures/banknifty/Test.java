/*
package com.sakthi.trade.futures.banknifty;

import com.google.gson.Gson;
import com.zerodhatech.models.HistoricalData;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Test {
    public  static void main(String[] args) throws IOException {
        Gson gson= new Gson();
        StringBuilder builder = new StringBuilder();

        // try block to check for exceptions where
        // object of BufferedReader class us created
        // to read filepath
            String str = new String(
                    Files.readAllBytes(Paths.get("/home/hasvanth/Downloads/input.json")));

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        HistoricalData historicalData = new HistoricalData();
        JSONObject json = new JSONObject(str);
        String status = json.getString("status");
        if (!status.equals("error")) {
            historicalData.parseResponse(json);
            historicalData.dataArrayList.forEach(historicalDataPrice -> {
                try {
                    Date priceDatetime = sdf.parse(historicalDataPrice.timeStamp);
                    String priceDate = format.format(priceDatetime);
                    if (sdf.format(priceDatetime).equals(priceDate + "T09:34:00")) {
                        System.out.println(sdf.format(priceDatetime));

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

    }
}
*/
