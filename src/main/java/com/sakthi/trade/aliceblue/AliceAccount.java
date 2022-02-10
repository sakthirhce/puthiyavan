package com.sakthi.trade.aliceblue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sakthi.trade.domain.AliceTokenResponse;
import com.sakthi.trade.domain.FundResponseDTO;
import com.sakthi.trade.domain.ProfileResponseDTO;
import com.sakthi.trade.fyer.AuthRequestDTO;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.telegram.SendMessage;
import com.sakthi.trade.websocket.truedata.WebSocketClientEndPoint;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.websocket.ContainerProvider;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AliceAccount {

    Gson gson = new GsonBuilder().setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    @Value("${aliceblue.secret}")
    String aliceblueSecretKey;
    @Value("${aliceblue.userid}")
    String aliceblueUsername;
    @Value("${aliceblue.password}")
    String alicebluePassword;

    @Value("${aliceblue.authurl}")
    String authEndpoint;
    @Value("${aliceblue.tokenurl}")
    String tokenEndPoint;
    @Value("${aliceblue.callbackurl}")
    String callbackEndpoint;
    @Value("${aliceblue.baseurl}")
    String aliceBaseURL;
    @Value("${chromedriver.path}")
    String driverPath;

    @Autowired
    SendMessage sendMessage;

    @Value("${telegram.orb.bot.token}")
    String telegramToken;

    @Value("${fyers.get.profile}")
    String getProfileURL;

    @Value("${fyers.get.fund}")
    String getFundURL;
    @Value("${aliceblue.wss:wss://ant.aliceblueonline.com/hydrasocket/v2/websocket?access_token=strAccessToken}")
    String aliceWSS;
    @Value("${aliceblue.masterContract:/api/v2/contracts.json?exchanges=strexchange")
    String aliceMasterContract;
    @Autowired
    TransactionService transactionService;
    Map<String,String> twoFAMap=null;

    public String authCode=null;
    public String accessToken=null;

    public Session session = null;
    String truedataURL = null;

    public void emptyToken() throws IOException, InterruptedException, URISyntaxException {
        log.info("setting token to null at: "+ LocalDateTime.now().toString());
        accessToken=null;
    }

    public void twoFA() throws IOException, InterruptedException, URISyntaxException {
       twoFAMap=new HashMap<>();
        twoFAMap.put("honeymoon","kodaikanal");
        twoFAMap.put("Birthplace","dharmapuri");
        twoFAMap.put("Married","2013");
        twoFAMap.put("child","hasvanth");
        twoFAMap.put("colour","yellow");

    }

    public void passwordValidityCheck() throws IOException, InterruptedException, URISyntaxException {
        log.info("Password validity check scheduler started");
        try {
            Request request=transactionService.createGetRequest(getProfileURL, null);
            String strResponse=transactionService.callAPI(request);
            ProfileResponseDTO profileResponseDTO=new Gson().fromJson(strResponse,ProfileResponseDTO.class);
            long passwordExpDays=profileResponseDTO.getResult().getPasswordExpiryDays();
            if(passwordExpDays<=5){
                try {
                    sendMessage.sendToTelegram("Reminder: Password expires in "+passwordExpDays+" days. please reset and update password in app.properties to avoid algo execution failure",telegramToken);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void availableFund() throws IOException, InterruptedException, URISyntaxException {
        log.info("Fund check scheduler started");
        try {
            Request request=transactionService.createGetRequest(getFundURL, null);
            String strResponse=transactionService.callAPI(request);
            FundResponseDTO fundResponseDTO=new Gson().fromJson(strResponse,FundResponseDTO.class);
           if(fundResponseDTO.getS().equals("ok")){
               StringBuilder str=new StringBuilder();
               fundResponseDTO.getFundLimit().stream().forEach( fund-> {
                   str.append(fund.getTitle()+":"+fund.getEquityAmount()+"\n");

               });
               try {
                   sendMessage.sendToTelegram(str.toString(), telegramToken);
               } catch (Exception e) {
                   e.printStackTrace();
               }
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public String aliceToken() throws IOException, InterruptedException, URISyntaxException {

        try {
            twoFA();
            if (accessToken == null) {
                try {
                    sendMessage.sendToTelegram("Token generation started",telegramToken);
                }catch (Exception e){
                    e.printStackTrace();
                }
                log.info("generating token at: " + LocalDateTime.now().toString());
                System.setProperty("webdriver.chrome.driver", driverPath);
                ChromeOptions ChromeOptions = new ChromeOptions();
                ChromeOptions.addArguments("--headless", "window-size=1024,768", "--no-sandbox");
                WebDriver webDriver = new ChromeDriver(ChromeOptions);
                String authURL=aliceBaseURL+authEndpoint+"?response_type=code&state=test_state&client_id="+aliceblueUsername+"&redirect_uri="+aliceBaseURL+callbackEndpoint;
                webDriver.get(authURL);
                Thread.sleep(3000);
                webDriver.findElements(By.xpath("//input")).get(0).sendKeys(aliceblueUsername);
                webDriver.findElements(By.xpath("//input")).get(1).sendKeys(alicebluePassword);
                webDriver.findElements(By.xpath("//button")).get(0).click();

                Thread.sleep(3000);
                String question1=webDriver.findElements(By.xpath("//*[@id=\"login_form\"]/form/div[2]/p[1]")).get(0).getText();
                String question2= webDriver.findElements(By.xpath("//*[@id=\"login_form\"]/form/div[2]/p[2]")).get(0).getText();
                twoFAMap.entrySet().stream().filter(stringStringEntry -> question1.contains(stringStringEntry.getKey())).findFirst().ifPresent(
                        FAMap->{
                            webDriver.findElements(By.xpath("//*[@id=\"login_form\"]/form/div[2]/input[1]")).get(0).sendKeys(FAMap.getValue());
                        }
                );
                twoFAMap.entrySet().stream().filter(stringStringEntry ->
                        question2.contains(stringStringEntry.getKey()
                        )).findFirst().ifPresent(
                        FAMap->{
                            webDriver.findElements(By.xpath("//*[@id=\"login_form\"]/form/div[2]/input[3]")).get(0).sendKeys(FAMap.getValue());
                        }
                );
                webDriver.findElements(By.xpath("//*[@id=\"login_form\"]/form/div[2]/div/button")).get(0).click();
                Thread.sleep(3000);
                List<NameValuePair> queryParams = new URIBuilder(webDriver.getCurrentUrl()).getQueryParams();
                authCode = queryParams.stream().filter(param -> param.getName().equals("code")).map(NameValuePair::getValue).findFirst().orElse("");
                log.info("Token: "+authCode);
                String urlStr="https://ant.aliceblueonline.com/oauth2/token";
                String auth = "150823:HE0WCKK0EB6FSTYTUMK8CHR14R7TYYI9X0RBFMISO57VU8RDCAXJ2ZTAX82VM0CP";
                try {
                    URL url = new URL (urlStr);
                    String encoding = Base64.getEncoder().encodeToString((auth).getBytes(StandardCharsets.UTF_8));
                    String urlParameters  = "code="+authCode+"&grant_type=authorization_code&redirect_uri=https://ant.aliceblueonline.com/plugin/callback";
                    byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
                    int    postDataLength = postData.length;
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36");
                    connection.setDoOutput(true);
                    connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded");
                    connection.setRequestProperty( "charset", "utf-8");
                    connection.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
                    connection.setRequestProperty("Authorization", "Basic "+encoding);
                    connection.setUseCaches( false );
                    try( DataOutputStream wr = new DataOutputStream( connection.getOutputStream())) {
                        wr.write( postData );
                    }
                    InputStream content = (InputStream)connection.getInputStream();
                    BufferedReader in   =
                            new BufferedReader (new InputStreamReader (content));
                    String line;
                    while ((line = in.readLine()) != null) {
                        AliceTokenResponse aliceTokenResponse= new Gson().fromJson(line,AliceTokenResponse.class);
                        accessToken=aliceTokenResponse.getAccessToken();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                try {
                    sendMessage.sendToTelegram("Token: "+accessToken,telegramToken);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            try {
                sendMessage.sendToTelegram("Token generation failed",telegramToken);
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }
        return accessToken;
    }
    public String aliceWebsocket(){
        WebSocketContainer webSocketContain = null;
        try {
            webSocketContain = ContainerProvider.getWebSocketContainer();
            webSocketContain.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
            webSocketContain.setDefaultMaxTextMessageBufferSize(1024 * 1024);
            aliceWSS = aliceWSS.replace("strAccessToken", accessToken);
            session = webSocketContain.connectToServer(WebSocketClientEndPoint.class, new URI(aliceWSS));
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @SneakyThrows
                @Override
                public void onMessage(byte[] img) {
                    System.out.println("length :"+img.length);
                    for(int i=0;i<img.length;i++) {

                        System.out.print(Integer.parseInt(String.valueOf(img[i])) + " ");
                    }
                }
            });

            try {
                if (session != null && session.isOpen()) {
                    session.getBasicRemote().sendText("{\"a\": \"subscribe\", \"v\": [[4,226006]], \"m\": \"marketdata\"}");
                 //   session.getBasicRemote().sendText(" {\"a\": \"subscribe\", \"v\": [1,2,3,4,6], \"m\": \"market_status\"}");

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
    @Scheduled(cron = "${aliceblue.heartbeat}")
    public void heartBeat(){
        try {
            if (session != null && session.isOpen()) {
              //  System.out.println("heartbeat");
                session.getBasicRemote().sendText("{\"a\": \"h\", \"v\": [], \"m\": \"\"}");

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
