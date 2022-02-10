/*
package com.sakthi.trade.http;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

*/
/**
 * A simple example that uses HttpClient to execute an HTTP request against
 * a target site that requires user authentication.
 * @author Ramesh Fadatare
 *//*

public class ClientAuthentication {

    public static void main(String[] args) throws Exception {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new UsernamePasswordCredentials("user", "passwd"));
        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
        try {
            HttpGet httpget = new HttpGet("https://ant.aliceblueonline.com/oauth2/token?code=\"+token+\"&client_id=150823&client_secret=HE0WCKK0EB6FSTYTUMK8CHR14R7TYYI9X0RBFMISO57VU8RDCAXJ2ZTAX82VM0CP&grant_type=authorization_code&redirect_uri=https://ant.aliceblueonline.com/plugin/callback");

            System.out.println("Executing request " + httpget.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                System.out.println("----------------------------------------");
                System.out.println(response.getStatusLine());
                System.out.println(EntityUtils.toString(response.getEntity()));
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
    }
}*/
