package com.sakthi.trade.dhan;

import com.zerodhatech.kiteconnect.Routes;
import org.springframework.stereotype.Component;

@Component
public class DhanConnect {
    private String accessToken;
    private String publicToken;
    private Routes routes;
    private String userId;
}
