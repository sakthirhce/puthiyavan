package com.sakthi.trade.worker;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.sakthi.trade.dhan.DhanRoutes;
import com.sakthi.trade.dhan.schema.OrderResponseDTO;
import com.sakthi.trade.dhan.schema.PositionResponseDTO;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.fyer.service.TransactionService;
import com.sakthi.trade.zerodha.account.User;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Component
@Slf4j
public class DhanWorker implements BrokerWorker {

    @Autowired
    TransactionService transactionService;
    @Autowired
    private DhanRoutes routes;
    public static final Logger LOGGER = Logger.getLogger(TransactionService.class.getName());
    @Override
    public Order placeOrder(OrderParams orderPlacementRequest, User user, TradeData tradeData) {
      //  Map<String, Object> params = new HashMap();
        LOGGER.info("Inside dhan place order:"+ new Gson().toJson(orderPlacementRequest));
        JSONObject params = new JSONObject();
        if (tradeData.getStrikeId() != null) {
            params.put("securityId", tradeData.getStrikeId());
        }

        if (orderPlacementRequest.quantity >0) {
            params.put("quantity", orderPlacementRequest.quantity);
        }

        if (orderPlacementRequest.transactionType != null) {
            params.put("transactionType", orderPlacementRequest.transactionType);
        }

        if (user.getClientId() != null) {
            params.put("dhanClientId", user.getClientId());
        }

        if (orderPlacementRequest.exchange != null) {
            params.put("exchangeSegment", orderPlacementRequest.exchange);
        }

        if (orderPlacementRequest.validity != null) {
            params.put("validity", orderPlacementRequest.validity);
        }

        if (orderPlacementRequest.orderType != null) {
            params.put("orderType", orderPlacementRequest.orderType);
        }

        if (orderPlacementRequest.product != null) {
            params.put("productType", orderPlacementRequest.product);
        }

        if (orderPlacementRequest.price >0) {
            params.put("price", orderPlacementRequest.price);
        }

        if (orderPlacementRequest.triggerPrice>0) {
            params.put("triggerPrice", orderPlacementRequest.triggerPrice);
        }

       //     params.put("amoTime", "OPEN");


        Request request= transactionService.createPostRequest(routes.get("orders"),params,user.getAccessToken());
        String rsponse=transactionService.callAPI(request);
        LOGGER.info("dhan order placement response:"+ rsponse);
        OrderResponseDTO orderResponseDTO=gson.fromJson(rsponse,OrderResponseDTO.class);
        if(orderResponseDTO!=null) {
            Order order = new Order();
            order.status = orderResponseDTO.getOrderStatus();
            order.orderId = orderResponseDTO.getOrderId();
            return order;
        }
        return null;
    }


    @Override
    public String broker() {
        return Broker.DHAN.name();
    }

    Gson gson=new Gson();
    @Override
    public  List<Order> getOrders(User user) {
        Request request= transactionService.createGetRequests(routes.get("orders"),user.getAccessToken());
        String rsponse=transactionService.callAPI(request);
        List<OrderResponseDTO> orderResponseDTOList=Arrays.asList(gson.fromJson(rsponse,OrderResponseDTO[].class));
        List<Order> orderList = new ArrayList<>();
        orderResponseDTOList.stream().forEach(orderResponseDTO -> {
            Order order=new Order();
            order.orderId=orderResponseDTO.getOrderId();
            order.status=orderResponseDTO.getOrderStatus();
            orderList.add(order);
        });
    return orderList;

    }

    @Override
    public List<Position>  getPositions(User user) {
        Request request= transactionService.createGetRequests(routes.get("portfolio.positions"),user.getAccessToken());
        String rsponse=transactionService.callAPI(request);
        List<PositionResponseDTO> orderResponseDTOList=Arrays.asList(gson.fromJson(rsponse,PositionResponseDTO[].class));
        List<Position> positions=new ArrayList<>();
        orderResponseDTOList.stream().forEach(orderResponseDTO ->{
            Position position=new Position();
            position.tradingSymbol=orderResponseDTO.getSecurityId();
            position.product=orderResponseDTO.getProductType();
            position.netQuantity=orderResponseDTO.getNetQty();
            positions.add(position);
        });
        return positions;
    }

    @Override
    public Order cancelOrder(String orderId, User user) {
        String url = this.routes.get("orders.cancel").replace(":order_id", orderId);
        Request request= transactionService.createDeleteRequest(url,user.getAccessToken());
        String rsponse=transactionService.callAPI(request);
        OrderResponseDTO orderResponseDTO=gson.fromJson(rsponse,OrderResponseDTO.class);
        if(orderResponseDTO!=null) {
            Order order = new Order();
            order.status = orderResponseDTO.getOrderStatus();
            order.orderId = orderResponseDTO.getOrderId();
            return order;
        }
        return null;
    }

    @Override
    public Order modifyOrder(String orderId, OrderParams orderPlacementRequest, User user,TradeData tradeData) {
        String url = this.routes.get("orders.modify").replace(":order_id", orderId);
        JSONObject params = new JSONObject();
        if (tradeData.getStrikeId() != null) {
            params.put("securityId", tradeData.getStrikeId());
        }

        if (orderPlacementRequest.quantity >0) {
            params.put("quantity", orderPlacementRequest.quantity);
        }

        if (orderPlacementRequest.transactionType != null) {
            params.put("transactionType", orderPlacementRequest.transactionType);
        }

        if (user.getClientId() != null) {
            params.put("dhanClientId", user.getClientId());
        }

        if (orderPlacementRequest.exchange != null) {
            params.put("exchangeSegment", orderPlacementRequest.exchange);
        }

        if (orderPlacementRequest.validity != null) {
            params.put("validity", orderPlacementRequest.validity);
        }

        if (orderPlacementRequest.orderType != null) {
            params.put("orderType", orderPlacementRequest.orderType);
        }

        if (orderPlacementRequest.product != null) {
            params.put("productType", orderPlacementRequest.product);
        }

        if (orderPlacementRequest.price >0) {
            params.put("price", orderPlacementRequest.price);
        }

        if (orderPlacementRequest.triggerPrice>0) {
            params.put("triggerPrice", orderPlacementRequest.triggerPrice);
        }
        String payload= new Gson().toJson(params);
        Request request= transactionService.createPutRequest(url,payload,user.getAccessToken());
        String response=transactionService.callAPI(request);
        LOGGER.info("dhan order modification response:"+ response);
        OrderResponseDTO orderResponseDTO=gson.fromJson(response,OrderResponseDTO.class);
        if(orderResponseDTO!=null) {
            Order order = new Order();
            order.status = orderResponseDTO.getOrderStatus();
            order.orderId = orderResponseDTO.getOrderId();
            return order;
        }
        return null;
    }

  /*  public static void main(String[] args){
        Gson gson=new Gson();
        String sampleData="[\n" +
                "    {\n" +
                "        \"dhanClientId\": \"1000000003\",\n" +
                "        \"orderId\": \"112111182198\",\n" +
                "        \"correlationId\":\"123abc678\",\n" +
                "        \"orderStatus\": \"PENDING\",\n" +
                "        \"transactionType\": \"BUY\",\n" +
                "        \"exchangeSegment\": \"NSE_EQ\",\n" +
                "        \"productType\": \"INTRADAY\",\n" +
                "        \"orderType\": \"MARKET\",\n" +
                "        \"validity\": \"DAY\",\n" +
                "        \"tradingSymbol\": \"\",\n" +
                "        \"securityId\": \"11536\",\n" +
                "        \"quantity\": 5,\n" +
                "        \"disclosedQuantity\": 0,\n" +
                "        \"price\": 0.0,\n" +
                "        \"triggerPrice\": 0.0,\n" +
                "        \"afterMarketOrder\": false,\n" +
                "        \"boProfitValue\": 0.0,\n" +
                "        \"boStopLossValue\": 0.0,\n" +
                "        \"legName\": \"ss\",\n" +
                "        \"createTime\": \"2021-11-24 13:33:03\",\n" +
                "        \"updateTime\": \"2021-11-24 13:33:03\",\n" +
                "        \"exchangeTime\": \"2021-11-24 13:33:03\",\n" +
                "        \"drvExpiryDate\": null,\n" +
                "        \"drvOptionType\": null,\n" +
                "        \"drvStrikePrice\": 0.0,\n" +
                "        \"omsErrorCode\": null,\n" +
                "        \"omsErrorDescription\": null\n" +
                "    }\n" +
                "]\n";
        List<OrderResponseDTO> orderResponseDTOList= Arrays.asList(gson.fromJson(sampleData,OrderResponseDTO[].class));
        System.out.println(orderResponseDTOList);
    }*/
}
