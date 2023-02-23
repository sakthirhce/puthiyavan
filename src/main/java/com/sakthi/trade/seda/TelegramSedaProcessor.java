package com.sakthi.trade.seda;

import com.sakthi.trade.telegram.TelegramMessenger;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class TelegramSedaProcessor implements Processor {
    @Autowired
    TelegramMessenger telegramMessenger;
    @Override
    public void process(Exchange camelContextRoute) throws Exception {
        String message = camelContextRoute.getIn().getBody().toString();
        telegramMessenger.sendToTelegram(message,"1253078571:AAFflWPSLFYuw7codvwAQnd4F14NV-ZVnag");
    }
}
