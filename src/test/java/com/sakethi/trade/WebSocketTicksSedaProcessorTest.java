package com.sakethi.trade;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.entity.UserSubscription;
import com.sakthi.trade.entity.UserSubscriptions;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.seda.WebSocketTicksSedaProcessor;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.zerodha.account.User;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import com.sakthi.trade.domain.TradeData;
public class WebSocketTicksSedaProcessorTest {

    @Mock
    private BrokerWorker brokerWorker;

    @Mock
    private TradeSedaQueue tradeSedaQueue;

    @Mock
    private User user;

    @InjectMocks
    private WebSocketTicksSedaProcessor tradeProcessor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcessRangeBuy() throws IOException, KiteException {
        // Arrange
        TradeData tradeData = new TradeData();

        tradeData.setEntryOrderId(null);
        tradeData.setOrderPlaced(false);
        tradeData.setRange(true);
        tradeData.setEntryType("BUY");
        tradeData.setRangeHigh(new BigDecimal("100.0"));
        tradeData.setStockName("TEST_STOCK");

        double lastTradedPrice = 105.0;

        TradeStrategy strategy = new TradeStrategy();
        strategy.setIndex("SS");
        strategy.setTradeValidity("MIS");
        strategy.setOrderType("BUY");
        strategy.setLotSize(1);
        strategy.setUserId("user123");

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUserId("user123");
        userSubscription.setLotSize(1);

        UserSubscriptions userSubscriptions = new UserSubscriptions();
        userSubscriptions.setUserSubscriptionList(List.of(userSubscription));
        strategy.setUserSubscriptions(userSubscriptions);

        Order order = new Order();
        order.orderId = "order123";

        when(brokerWorker.placeOrder(any(OrderParams.class), eq(user), eq(tradeData))).thenReturn(order);

        // Act
        tradeProcessor.processRangeBuy(tradeData, lastTradedPrice, strategy, brokerWorker, user, tradeSedaQueue);

        // Assert
        assertEquals("order123", tradeData.getEntryOrderId());
        assertTrue(tradeData.isOrderPlaced());
        verify(brokerWorker, times(1)).placeOrder(any(OrderParams.class), eq(user), eq(tradeData));
        verify(tradeSedaQueue, times(1)).sendTelemgramSeda(anyString(), eq("exp-trade"));
    }
}