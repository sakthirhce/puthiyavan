package com.sakthi.trade.dhan;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DhanRoutes {
    public Map<String, String> routes = new HashMap<String, String>() {
        {
            this.put("orders", "/orders");
            this.put("orders.modify", "/orders/:order_id");
            this.put("orders.cancel", "/orders/:order_id");
            this.put("portfolio.positions", "/positions");
            this.put("portfolio.holdings", "/holdings");
            this.put("portfolio.positions.convert", "/positions/convert");
            this.put("funds","/fundlimit");
        }
    };
    private static String _rootUrl = "https://api.dhan.co";
  //  private static String _loginUrl = "https://kite.trade/connect/login";
  //  private static String _wsuri = "wss://ws.kite.trade/?access_token=:access_token&api_key=:api_key";

    public DhanRoutes() {
    }

    public String get(String key) {
        return _rootUrl + (String)this.routes.get(key);
    }

}
