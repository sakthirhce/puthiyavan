package com.sakthi.trade.seda;

import com.sakthi.trade.seda.TelegramSedaProcessor;
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
    @PostConstruct
    @Qualifier("camelContextRoute")
    public CamelContext init() throws Exception {
        SedaComponent sedaComponent = new SedaComponent();
        sedaComponent.setQueueSize(3);
        camelContext.addComponent("telegramQueue", camelContext.getComponent("seda"));
        camelContext.addComponent("placeOrderQueue", camelContext.getComponent("seda"));
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
        camelContext.start();
        return camelContext;
    }

    public void sendTelemgramSeda(String message){
        camelContext.createProducerTemplate().sendBody("seda:telegramQueue", message);
    }
}
