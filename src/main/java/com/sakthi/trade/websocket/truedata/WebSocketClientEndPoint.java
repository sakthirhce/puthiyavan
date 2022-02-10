package com.sakthi.trade.websocket.truedata;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.Session;
import java.util.concurrent.CountDownLatch;

@ClientEndpoint
public class WebSocketClientEndPoint {
    private static CountDownLatch countDownLatch;
    @OnClose
    public void onClose(Session session, CloseReason closeReason){
        if(session!=null) {
            System.out.println(String.format("Session %s closed because  of %s", session.getId(), closeReason));
            countDownLatch.countDown();
        }
    }
    public void error(Throwable throwable){
        System.out.println("Exception while connecting websocket: "+ throwable.getMessage());
    }
}
