package com.sakthi.trade.seda;

import com.sakthi.trade.telegram.TelegramMessenger;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PriorityTelegramSedaProcessor implements Processor {
    @Autowired
    TelegramMessenger telegramMessenger;
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        String message = camelContextRoute.getIn().getBody().toString();
        telegramMessenger.sendToTelegram(message,"1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o");
    }
}
