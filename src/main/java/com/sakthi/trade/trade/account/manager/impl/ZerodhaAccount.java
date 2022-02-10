/*
package com.sakthi.trade.trade.account.manager.impl;

import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.fyer.AuthRequestDTO;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.trade.account.manager.Account;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
@Slf4j
@Component
public class ZerodhaAccount extends Account {

    @Value("${zerodha.login.url}")
    String zerodhaLoginURL;
    @Value("${zerodha.app.key}")
    public String zerodhaAppKey;
    @Value("${zerodha.api.secret}")
    String zerodhaApiSecret;
    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${zerodha.username}")
    String zerodhaUsername;
    @Value("${zerodha.password}")
    String zerodhaPassword;
    @Value("${zerodha.pin}")
    String zerodhaPin;
    @Autowired
    SendMessage sendMessage;
    @Value("${chromedriver.path}")
    String driverPath;
    @Value("${filepath.trend}")
    String trendPath;
    @Autowired
    TransactionService transactionService;

    public User user;
    public String token=null;
    public KiteConnect kiteSdk;

    @Override
    public void placeSLOrder(TradeData tradeData) {

    }

    @Override
    public void cancelOrder(TradeData tradeData) {

    }

    @Override
    public void exitOrder(TradeData tradeData) {

    }

    @Override
    public void login(TradeData tradeData) {

    }

    @Override
    public void login() {

    }

    @Override
    public String generateToken() {
        try {

            log.info("generating token at: " + LocalDateTime.now().toString());

            if(token==null) {
                kiteSdk = new KiteConnect(zerodhaAppKey);
                kiteSdk.setUserId("RS4899");
                String url = kiteSdk.getLoginURL();
                System.setProperty("webdriver.chrome.driver", driverPath);
                ChromeOptions ChromeOptions = new ChromeOptions();
                ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
                WebDriver webDriver = new ChromeDriver(ChromeOptions);
                AuthRequestDTO authRequest = new AuthRequestDTO();
                webDriver.get(url);

                Thread.sleep(1000);
                webDriver.findElements(By.xpath("//input")).get(0).sendKeys(zerodhaUsername);
                webDriver.findElements(By.xpath("//input")).get(1).sendKeys(zerodhaPassword);
                webDriver.findElements(By.xpath("//button")).get(0).click();
                Thread.sleep(1000);
                webDriver.findElements(By.xpath("//*[@id=\"pin\"]")).get(0).sendKeys(zerodhaPin);
                webDriver.findElements(By.xpath("//button")).get(0).click();
                Thread.sleep(1000);
                System.out.println(webDriver.getCurrentUrl());
                List<NameValuePair> queryParams = new URIBuilder(webDriver.getCurrentUrl()).getQueryParams();
                String requestToken = queryParams.stream().filter(param -> param.getName().equals("request_token")).map(NameValuePair::getValue).findFirst().orElse("");

                user = kiteSdk.generateSession(requestToken, zerodhaApiSecret);
                System.out.println(user.accessToken);
                kiteSdk.setAccessToken(user.accessToken);
                token=user.accessToken;
                kiteSdk.setPublicToken(user.publicToken);
                Margin margins = kiteSdk.getMargins("equity");
                System.out.println(margins.available.cash);
                System.out.println(margins.utilised.debits);
                sendMessage.sendToTelegram("Token :"+kiteSdk.getAccessToken(),telegramToken);
                sendMessage.sendToTelegram("Available Cash :"+margins.available.cash,telegramToken);
                webDriver.quit();
            }
        }catch (Exception | KiteException e){
            e.printStackTrace();
            try {
                sendMessage.sendToTelegram("Token generation failed",telegramToken);
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }
        return null;
    }

}
*/
