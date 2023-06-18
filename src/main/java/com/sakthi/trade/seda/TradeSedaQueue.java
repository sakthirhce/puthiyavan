package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.sakthi.trade.seda.TelegramSedaProcessor;
import com.sakthi.trade.telegram.TelegramData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
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
                        .process(telegramSedaProcessor)
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
        camelContext.start();
        return camelContext;
    }

    public void sendTelemgramSeda(String message,String chatId){
        TelegramData telegramData=new TelegramData();
        telegramData.setMessage(message);
        telegramData.setGroupChatId(chatId);
        camelContext.createProducerTemplate().sendBody("seda:telegramQueue", gson.toJson(telegramData));
    }
    public void sendTelemgramSeda(String message){
        TelegramData telegramData=new TelegramData();
        telegramData.setMessage(message);
        User user =userList.getUser().stream().filter(user1 -> user1.isAdmin()).findFirst().get();
        telegramData.setGroupChatId(user.telegramBot.getGroupId());
        camelContext.createProducerTemplate().sendBody("seda:telegramQueue", gson.toJson(telegramData));
    }
    public void sendPriorityTelemgramSeda(String message){
        camelContext.createProducerTemplate().sendBody("seda:priorityTelegramQueue", message);
    }
}
