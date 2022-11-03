package com.sakthi.trade.fyer.service;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.sakthi.trade.fyer.Account;
import com.sakthi.trade.options.nifty.buy.NiftyOptionBuy935V2;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.sakthi.trade.zerodha.account.ZerodhaAccount;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

@Component
@Slf4j
public class TransactionService {

    @Autowired
    @Qualifier("createOkHttpClient")
        OkHttpClient okHttpClient;
    @Autowired
    Account account;

    @Autowired
    UserList userList;

    KiteConnect kiteConnect;
    public void setup(){
        User user =userList.getUser().stream().filter(User::isAdmin).findFirst().get();
        kiteConnect = user.getKiteConnect();
    }
    public static final Logger LOGGER = Logger.getLogger(TransactionService.class.getName());
    public Request createPostPutDeleteRequest(HttpMethod httpMethod,String uri,String payload){
        Request.Builder requestBuilder=new Request.Builder();
        requestBuilder.addHeader("Content-Type","application/json;charset=UTF-8");
        requestBuilder.addHeader("Authorization" , account.token);
        requestBuilder.url(uri);
        MediaType JSON=MediaType.parse("application/json");
        log.info("payload: "+ payload);

        if(HttpMethod.POST.equals(httpMethod)){
            RequestBody body= RequestBody.create(JSON,payload);
        requestBuilder.post(body);}
        else if(HttpMethod.PUT.equals(httpMethod)){
            RequestBody body= RequestBody.create(JSON,payload);
            requestBuilder.put(body);
        }else if(HttpMethod.DELETE.equals(httpMethod)){
            RequestBody body= RequestBody.create(JSON,payload);
            requestBuilder.delete(body);
        }
        return requestBuilder.build();
    }

    public Request createGetRequest(String uri,String queryValue){
        Request.Builder requestBuilder=new Request.Builder();
        requestBuilder.addHeader("Content-Type","application/json;charset=UTF-8");
        requestBuilder.addHeader("Authorization" , account.token);
        if(queryValue!=null){
            uri=uri+Long.valueOf(queryValue);
        }
        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();
    }

    public Request createPostRequest(String url, JSONObject params, String accessToken) {
        MediaType JSON=MediaType.parse("application/json");
        String payload= new Gson().toJson(params);
        LOGGER.info("dhan API Payload:" + payload);
        RequestBody requestBody = RequestBody.create(JSON,payload);
        Request request = (new Request.Builder()).url(url).post(requestBody).header("Content-Type","application/json").header("access-token",  accessToken).build();
        return request;
    }
    public Request createGetRequests(String url, String accessToken) {
     //   MediaType JSON=MediaType.parse("application/json");
     //   RequestBody requestBody = RequestBody.create(JSON,new Gson().toJson(params));
        Request request = (new Request.Builder()).url(url).header("access-token",  accessToken).build();
        return request;
    }
    public Request createDeleteRequest(String url,String accessToken){
        Request.Builder requestBuilder=new Request.Builder();
        requestBuilder.addHeader("access-token" , accessToken);
        requestBuilder.url(url);
        requestBuilder.delete();
        return requestBuilder.build();
    }
    public Request createPutRequest(String url,String payload,String accessToken){
        Request.Builder requestBuilder=new Request.Builder();
        requestBuilder.addHeader("access-token" , accessToken);
        requestBuilder.url(url);
        MediaType JSON=MediaType.parse("application/json");
        RequestBody body= RequestBody.create(JSON,payload);
        requestBuilder.put(body);
        return requestBuilder.build();
    }
    public Request createGetTrueRequest(String uri){
        Request.Builder requestBuilder=new Request.Builder();
        requestBuilder.addHeader("Content-Type","application/json;charset=UTF-8");
        requestBuilder.addHeader("Authorization" , "Bearer 6j37ha1sQObvm7LLnqpFK0DDYY-nQLuFoJ-DzpNtlpvN7DHTCpTleUG-DG_o5OHz0dn8KGQRptd2Fa29ZK9xD6M7yYv3QVYAPJH3MFWHmvxS78dxnQEpPL73JvDNXT8NNJghQbKW1a4Ulnm-tsqv3_BaUiGn2pOE_9MAxsWk-PQ9jQaCs8A-EqsMj2UfXGMbLfJacjWpCmifbxJxMGwSHt5CbnebLv70PTxCXnjfWLsQvo8J2VmRf3a15vQrxezS97tJleuic_iHuak_3mY8Vg");
        requestBuilder.url(uri);
        requestBuilder.get();
        return requestBuilder.build();
    }
    public Request createZerodhaGetRequest(String uri){
        if(kiteConnect == null) {
            User user =userList.getUser().stream().filter(user1 -> user1.isAdmin()).findFirst().get();
            kiteConnect = user.getKiteConnect();
        }
            log.info("uri:" + uri);
            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.addHeader("X-Kite-Version", "3");
            requestBuilder.addHeader("Authorization", "token " + kiteConnect.getApiKey() + ":" + kiteConnect.getAccessToken());

            requestBuilder.url(uri);
            requestBuilder.get();
            return requestBuilder.build();

    }
    public String downloadInstrumentData(String uri){
        log.info("uri:" + uri);
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(uri);
        requestBuilder.get();
        String responseStr=callAPI(requestBuilder.build());
        return responseStr;

    }
    public String callAPI(Request request){
        String responseStr=null;
        try{
            StopWatch watch=new StopWatch();
            watch.start();
            Response response=okHttpClient.newCall(request).execute();
            watch.stop();
          //  log.info("Total time taken for api call in millisecs: "+ watch.getTotalTimeMillis());
            responseStr=response.body().string();
        }catch (Exception e){
            e.printStackTrace();
        }
        return responseStr;
    }
    public String callOrderPlaceAPI(Request request){
        String responseStr=null;
        try{
            StopWatch watch=new StopWatch();
            watch.start();
          //  log.info("Order Request: "+ request.body().toString());
            Response response=okHttpClient.newCall(request).execute();
            watch.stop();
            log.info("Total time taken for api call in millisecs: "+ watch.getTotalTimeMillis());
            responseStr=response.body().string();
            log.info("Order Response: "+ responseStr);
        }catch (Exception e){
            e.printStackTrace();
        }
        return responseStr;
    }
}
