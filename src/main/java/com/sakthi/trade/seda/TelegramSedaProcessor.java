
package com.sakthi.trade.seda;

import com.google.gson.Gson;
import com.sakthi.trade.telegram.TelegramData;
import com.sakthi.trade.telegram.TelegramMessenger;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class TelegramSedaProcessor implements Processor {
    @Autowired
    TelegramMessenger telegramMessenger;

    @Value("${telegram.admin.bot}")
    String telegramBot;

    Gson gson=new Gson();
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        String message = camelContextRoute.getIn().getBody().toString();
        TelegramData telegramData=gson.fromJson(message,com.sakthi.trade.telegram.TelegramData.class);
        Thread.sleep(500);
        telegramMessenger.sendToTelegram(telegramData.getMessage(),telegramBot,telegramData.getGroupChatId());
    }
}

