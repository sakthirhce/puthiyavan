package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.sakthi.trade.domain.OrderSedaData;
import com.sakthi.trade.seda.TelegramSedaProcessor;
import com.sakthi.trade.telegram.TelegramData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class TradeSedaQueue {

    CamelContext camelContext=new DefaultCamelContext();

    @Autowired
    TelegramSedaProcessor telegramSedaProcessor;
    @Autowired
    PriorityTelegramSedaProcessor priorityTelegramSedaProcessor;

    @Autowired
    OrderPlaceSedaProcessor orderPlaceSedaProcessor;

    @Autowired
    WebSocketOrderUpdateSedaProcessor webSocketOrderUpdateSedaProcessor;

    @Autowired
    WebSocketTicksSedaProcessor webSocketTicksSedaProcessor;

    @Autowired
    PositionDataSedaProcessor positionDataSedaProcessor;

    @Autowired
    UserList userList;

    Gson gson=new Gson();
    @PostConstruct
    @Qualifier("camelContextRoute")
    public CamelContext init() throws Exception {
        SedaComponent sedaComponent = new SedaComponent();
        sedaComponent.setQueueSize(3);
        camelContext.addComponent("telegramQueue", camelContext.getComponent("seda"));
        camelContext.addComponent("placeOrderQueue", camelContext.getComponent("seda"));
        camelContext.addComponent("priorityTelegramQueue", camelContext.getComponent("seda"));
        camelContext.addComponent("websocketOrderUpdateQueue", camelContext.getComponent("seda"));
        camelContext.addComponent("websocketTicksQueue", camelContext.getComponent("seda"));
        camelContext.addComponent("positionDataQueue", camelContext.getComponent("seda"));
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:telegramQueue")
                        .process(telegramSedaProcessor)
                        .end();
            }
        });
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:placeOrderQueue")
                        .process(orderPlaceSedaProcessor)
                        .end();
            }
        });
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:priorityTelegramQueue")
                        .process(priorityTelegramSedaProcessor)
                        .end();
            }
        });
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:websocketOrderUpdateQueue")
                        .process(webSocketOrderUpdateSedaProcessor)
                        .end();
            }
        });
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:websocketTicksQueue")
                        .process(webSocketTicksSedaProcessor)
                        .end();
            }
        });
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:positionDataQueue")
                        .process(positionDataSedaProcessor)
                        .end();
            }
        });
        camelContext.start();
        return camelContext;
    }

    public void sendTelemgramSeda(String message,String chatId){
        TelegramData telegramData=new TelegramData();
        telegramData.setMessage(message);
        telegramData.setGroupChatId(chatId);
        //camelContext.createProducerTemplate().sendBody("seda:telegramQueue", gson.toJson(telegramData));
        String webhookUrl = "https://hooks.slack.com/services/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";
        if("exp-trade".equals(chatId)) {
            webhookUrl = "https://hooks.slack.com/services/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";
        }else if("algo".equals(chatId)){
            webhookUrl = "https://hooks.slack.com/services/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";
        }
        else if("error".equals(chatId)){
            webhookUrl = "https://hooks.slack.com/services/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";
        }
        String message1 = "{\"text\": \""+message+"\"}"; // Your message in JSON format
        //System.out.printf(message1);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(message1));
            HttpResponse response = httpClient.execute(httpPost);
            System.out.printf(message1);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String result = EntityUtils.toString(entity);
                System.out.printf(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendTelemgramSeda(String message){
        TelegramData telegramData=new TelegramData();
        telegramData.setMessage(message);
        User user =userList.getUser().stream().filter(user1 -> user1.isAdmin()).findFirst().get();
        telegramData.setGroupChatId(user.telegramBot.getGroupId());
        //camelContext.createProducerTemplate().sendBody("seda:telegramQueue", gson.toJson(telegramData));
        String webhookUrl = "https://hooks.slack.com/services/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"; // Replace with your webhook URL
        String message1 = "{\"text\": \""+message+"\"}"; // Your message in JSON format
        System.out.printf(message1);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(message1));

            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String result = EntityUtils.toString(entity);
                System.out.println(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendWebsocketOrderUpdateSeda(String message){
        try {
            camelContext.createProducerTemplate().sendBody("seda:websocketOrderUpdateQueue", message);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void sendPositionDateSeda(String message){
        try {
            camelContext.createProducerTemplate().sendBody("seda:positionDataQueue", message);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void sendWebsocketTicksSeda(String message){
        try {
            camelContext.createProducerTemplate().sendBody("seda:websocketTicksQueue", message);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public void sendPriorityTelemgramSeda(String message){
        camelContext.createProducerTemplate().sendBody("seda:priorityTelegramQueue", message);
    }
    public void sendOrderPlaceSeda(OrderSedaData message){
        camelContext.createProducerTemplate().sendBody("seda:placeOrderQueue", message);
    }
}
