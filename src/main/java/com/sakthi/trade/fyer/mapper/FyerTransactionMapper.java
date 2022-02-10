package com.sakthi.trade.fyer.mapper;

import com.sakthi.trade.domain.*;
import com.sakthi.trade.fyer.transactions.PlaceOrderRequestDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FyerTransactionMapper {
    public PlaceOrderRequestDTO placeOrderRequestDTO(String symbol, Order order, OrderType orderType, ProductType productType, Validity validity, int qty, BigDecimal price,BigDecimal stopLoss,BigDecimal targetPrice){
        PlaceOrderRequestDTO placeOrderRequestDTO=new PlaceOrderRequestDTO();
        placeOrderRequestDTO.setOfflineOrder("False");
        placeOrderRequestDTO.setSymbol("NSE:"+symbol+"-EQ");
        placeOrderRequestDTO.setQty(qty);
        placeOrderRequestDTO.setProductType(productType.name());
        placeOrderRequestDTO.setSide(order.getType());
        placeOrderRequestDTO.setType(orderType.getType());
        placeOrderRequestDTO.setValidity(validity.name());
        if(orderType==OrderType.LIMIT_ORDER || orderType== OrderType.STOP_LOSS_LIMIT){
            placeOrderRequestDTO.setLimitPrice(price);
        }
        if(productType==ProductType.BO || productType==ProductType.CO){
            placeOrderRequestDTO.setStopLoss(stopLoss);
        }
        if(orderType==OrderType.STOP_LOSS_MARKET || orderType== OrderType.STOP_LOSS_LIMIT){
            placeOrderRequestDTO.setStopPrice(stopLoss);
        }
        if(productType==ProductType.BO){
            placeOrderRequestDTO.setStopLoss(stopLoss);
        }
        return placeOrderRequestDTO;
    }
    public PlaceOrderRequestDTO placeOrderRequestFODTO(String symbol, Order order, OrderType orderType, ProductType productType, Validity validity, int qty, BigDecimal price,BigDecimal stopLoss,BigDecimal targetPrice){
        PlaceOrderRequestDTO placeOrderRequestDTO=new PlaceOrderRequestDTO();
        placeOrderRequestDTO.setOfflineOrder("False");
        placeOrderRequestDTO.setSymbol(symbol);
        placeOrderRequestDTO.setQty(qty);
        placeOrderRequestDTO.setProductType(productType.name());
        placeOrderRequestDTO.setSide(order.getType());
        placeOrderRequestDTO.setType(orderType.getType());
        placeOrderRequestDTO.setValidity(validity.name());
        if(orderType==OrderType.LIMIT_ORDER || orderType== OrderType.STOP_LOSS_LIMIT){
            placeOrderRequestDTO.setLimitPrice(price);
        }
        if(productType==ProductType.BO || productType==ProductType.CO){
            placeOrderRequestDTO.setStopLoss(stopLoss);
        }
        if(orderType==OrderType.STOP_LOSS_MARKET || orderType== OrderType.STOP_LOSS_LIMIT){
            placeOrderRequestDTO.setStopPrice(stopLoss);
        }
        if(productType==ProductType.BO){
            placeOrderRequestDTO.setStopLoss(stopLoss);
        }
        return placeOrderRequestDTO;
    }
    public CancelRequestDTO prepareCancelRequest(long orderId){
        CancelRequestDTO cancelRequestDTO=new CancelRequestDTO();
        cancelRequestDTO.setId(orderId);
        return cancelRequestDTO;
    }
}
