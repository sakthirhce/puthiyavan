package com.sakthi.trade.zerodha;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.cache.GlobalContextCache;
import com.sakthi.trade.ratelimit.TokenBucket;
import com.sakthi.trade.seda.TickData;
import com.sakthi.trade.seda.WebSocketTicksSedaProcessor;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class TransactionService {

    public static final Logger LOGGER = LoggerFactory.getLogger(TransactionService.class.getName());
    @Autowired
    @Qualifier("createOkHttpClient")
    OkHttpClient okHttpClient;
    TokenBucket tokenBucket = new TokenBucket(5, 5, 1000);
/*    @Autowired
    WebSocketTicksSedaProcessor webSocketTicksSedaProcessor;*/
    @Autowired
    GlobalContextCache globalContextCache;

    @Autowired
    TickData tickData;

    public Map<String, Map<String, Double>> tickCurrentPrice = new HashMap<>();

/*    @Autowired
    WebSocketTicksSedaProcessor webSocketTicksSedaProcessor;*/

    @Autowired
    UserList userList;
    KiteConnect kiteConnect;

    public void setup() {
        User user = userList.getUser().stream().filter(User::isAdmin).findFirst().get();
        kiteConnect = user.getKiteConnect();
    }

    public Request createPostRequest(String url, JSONObject params, String accessToken) {
        MediaType JSON = MediaType.parse("application/json");
        String payload = new Gson().toJson(params);
        LOGGER.info("dhan API Payload:" + payload);
        RequestBody requestBody = RequestBody.create(JSON, payload);
        Request request = (new Request.Builder()).url(url).post(requestBody).header("Content-Type", "application/json").header("access-token", accessToken).build();
        return request;
    }

    public Request createGetRequests(String url, String accessToken) {
        //   MediaType JSON=MediaType.parse("application/json");
        //   RequestBody requestBody = RequestBody.create(JSON,new Gson().toJson(params));
        Request request = (new Request.Builder()).url(url).header("access-token", accessToken).build();
        return request;
    }

    public Request createDeleteRequest(String url, String accessToken) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("access-token", accessToken);
        requestBuilder.url(url);
        requestBuilder.delete();
        return requestBuilder.build();
    }

    public Request createPutRequest(String url, String payload, String accessToken) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("access-token", accessToken);
        requestBuilder.url(url);
        MediaType JSON = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(JSON, payload);
        requestBuilder.put(body);
        return requestBuilder.build();
    }

    public Request createGetTrueRequest(String uri) {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("Content-Type", "application/json;charset=UTF-8");
        requestBuilder.addHeader("Authorization", "Bearer 6j37ha1sQObvm7LLnqpFK0DDYY-nQLuFoJ-DzpNtlpvN7DHTCpTleUG-DG_o5OHz0dn8KGQRptd2Fa29ZK9xD6M7yYv3QVYAPJH3MFWHmvxS78dxnQEpPL73JvDNXT8NNJghQbKW1a4Ulnm-tsqv3_BaUiGn2pOE_9MAxsWk-PQ9jQaCs8A-EqsMj2UfXGMbLfJacjWpCmifbxJxMGwSHt5CbnebLv70PTxCXnjfWLsQvo8J2VmRf3a15vQrxezS97tJleuic_iHuak_3mY8Vg");
        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();
    }

    public Request createZerodhaGetRequest(String uri) {
        if (kiteConnect == null) {
            User user = userList.getUser().stream().filter(user1 -> user1.isAdmin()).findFirst().get();
            kiteConnect = user.getKiteConnect();
        }
        //  LOGGER.info("uri:" + uri);
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("X-Kite-Version", "3");
        requestBuilder.addHeader("Authorization", "token " + kiteConnect.getApiKey() + ":" + kiteConnect.getAccessToken());

        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();

    }

    public Request createZerodhaGetRequest(String uri, String apiKey, String token) {

        //  LOGGER.info("uri:" + uri);
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("X-Kite-Version", "3");
        requestBuilder.addHeader("Authorization", "token " + apiKey + ":" + token);

        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();

    }

    public Request createZerodhaGetRequestWithoutLog(String uri) {
        if (kiteConnect == null) {
            User user = userList.getUser().stream().filter(user1 -> user1.isAdmin()).findFirst().get();
            kiteConnect = user.getKiteConnect();
        }
        //  LOGGER.info("uri:" + uri);
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("X-Kite-Version", "3");
        requestBuilder.addHeader("Authorization", "token " + kiteConnect.getApiKey() + ":" + kiteConnect.getAccessToken());

        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();

    }

    public Request createZerodhaGetRequestTest(String uri) {

        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.addHeader("X-Kite-Version", "3");
        requestBuilder.addHeader("Authorization", "token o1wluh7qbc286ar8:ezX61ZtNheKMiL8jFwcO8rw16LHO6UIV");

        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();

    }

    public String downloadInstrumentData(String uri) {
        log.info("uri:" + uri);
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(uri);
        requestBuilder.get();
        String responseStr = callAPI(requestBuilder.build());
        return responseStr;

    }

    public String callAPI(Request request) {
        String responseStr = null;
        try {
            StopWatch watch = new StopWatch();
            watch.start();
            Response response = okHttpClient.newCall(request).execute();
            watch.stop();
            //  log.info("Total time taken for api call in millisecs: "+ watch.getTotalTimeMillis());
            responseStr = response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return responseStr;
    }

    public String callAPI(Request request, String stockId, String time) {
        StopWatch watch1 = new StopWatch();
        watch1.start();
        String responseStr = globalContextCache.getHistoricData(time, stockId);
        try {
            if (tickData!=null && tickData.tickCurrentPrice != null) {
                Map<String,Double> tickPriceMap=tickData.tickCurrentPrice.get(stockId);
                if (tickPriceMap!=null){
                    double tickPrice =tickPriceMap.get(time);
                    LOGGER.info("websocket time:"+time+" price:"+tickPrice);
                }
            }
        } catch (Exception e) {
            LOGGER.error("error while getting websocket price:"+e.getMessage());
            e.printStackTrace();
        }
        try {
            if (responseStr == null) {
                //log.info("details not found in cache:"+stockId+":"+responseStr);
                if (tokenBucket.tryConsumeWithWait()) {
                    StopWatch watch = new StopWatch();
                    watch.start();
                    Response response = okHttpClient.newCall(request).execute();
                    watch.stop();
                    //log.info("Total time taken for zerodha api cal"+stockId+" in millisecs: "+ watch.getTotalTimeMillis());
                    responseStr = response.body().string();
                    //     log.info("api response"+stockId+":"+responseStr);
                    globalContextCache.setHistoricData(time, stockId, responseStr);
                }
            } else {
                // LOGGER.info("api response:"+stockId+" from cache:");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        watch1.stop();
        //     LOGGER.info("Total time taken for api call in millisecs: "+ watch1.getTotalTimeMillis());
        return responseStr;
    }

    public String callOrderPlaceAPI(Request request) {
        String responseStr = null;
        try {
            StopWatch watch = new StopWatch();
            watch.start();
            //  log.info("Order Request: "+ request.body().toString());
            Response response = okHttpClient.newCall(request).execute();
            watch.stop();
            log.info("Total time taken for api call in millisecs: " + watch.getTotalTimeMillis());
            responseStr = response.body().string();
            log.info("Order Response: " + responseStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return responseStr;
    }
}
