package com.sakthi.trade.http;


import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class HttpClientBuilder {
    @Value("${okhttp.maxIdleConnection:20}")
    private int maxIdleConnection;
    @Value("${okhttp.connectionTimeout:10000}")
    private long connectionTimeout;
    @Value("${okhttp.readTimeout:10000}")
    private long readTimeout;
    @Value("${okhttp.maxIdleConnection:5}")
    private long keepAliveDuration;
    @Bean
    @Qualifier("createOkHttpClient")
    public OkHttpClient createOkHttpClient(){
        OkHttpClient.Builder okHttpBuilder=new OkHttpClient.Builder();
        okHttpBuilder.connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS);
        okHttpBuilder.readTimeout(readTimeout, TimeUnit.MILLISECONDS);
        okHttpBuilder.connectionPool(new ConnectionPool(maxIdleConnection,keepAliveDuration, TimeUnit.MINUTES));
        okHttpBuilder.retryOnConnectionFailure(true);
        return okHttpBuilder.build();
    }
}
