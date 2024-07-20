package com.sakethi.trade;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.sakthi.trade.cache.GlobalTickCache;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.entity.UserSubscription;
import com.sakthi.trade.entity.UserSubscriptions;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.seda.WebSocketTicksSedaProcessor;
import com.sakthi.trade.service.TradingStrategyAndTradeData;
import com.sakthi.trade.worker.BrokerWorker;
import com.sakthi.trade.worker.BrokerWorkerFactory;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Tick;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.sakthi.trade.domain.TradeData;
public class WebSocketTicksSedaProcessorTest {

    @Mock
    private BrokerWorker brokerWorker;

    @Mock
    private BrokerWorkerFactory brokerWorkerFactory;

    @Mock
    private TradeSedaQueue tradeSedaQueue;
    @Mock
    TradingStrategyAndTradeData tradingStrategyAndTradeData;


    @InjectMocks
    private WebSocketTicksSedaProcessor tradeProcessor;

    @InjectMocks
    GlobalTickCache globalTickCache;


    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);
    }
@Test
    public void testTick(){
    tradeProcessor.tradingStrategyAndTradeData=tradingStrategyAndTradeData;
    tradeProcessor.globalTickCache=globalTickCache;
globalTickCache.globalTickCache=
        CacheBuilder.newBuilder().maximumSize(1000).build();
    tradingStrategyAndTradeData.openTrade=new HashMap<>();
        String samplePayload="{\n" +
                "  \"mode\": \"full\",\n" +
                "  \"tradable\": true,\n" +
                "  \"token\": 12345678,\n" +
                "  \"lastTradedPrice\": 1250.75,\n" +
                "  \"highPrice\": 1275.50,\n" +
                "  \"lowPrice\": 1230.25,\n" +
                "  \"openPrice\": 1240.00,\n" +
                "  \"closePrice\": 1245.50,\n" +
                "  \"change\": 5.25,\n" +
                "  \"lastTradeQuantity\": 100,\n" +
                "  \"averageTradePrice\": 1248.60,\n" +
                "  \"volumeTradedToday\": 1000000,\n" +
                "  \"totalBuyQuantity\": 500000,\n" +
                "  \"totalSellQuantity\": 450000,\n" +
                "  \"lastTradedTime\": \"2024-07-08T10:30:00Z\",\n" +
                "  \"oi\": 150000,\n" +
                "  \"openInterestDayHigh\": 160000,\n" +
                "  \"openInterestDayLow\": 140000,\n" +
                "  \"tickTimestamp\": \"2024-07-08T10:30:01Z\",\n" +
                "  \"depth\": {\n" +
                "    \"buy\": [\n" +
                "      {\n" +
                "        \"price\": 1250.50,\n" +
                "        \"quantity\": 100,\n" +
                "        \"orders\": 5\n" +
                "      },\n" +
                "      {\n" +
                "        \"price\": 1250.25,\n" +
                "        \"quantity\": 150,\n" +
                "        \"orders\": 7\n" +
                "      }\n" +
                "    ],\n" +
                "    \"sell\": [\n" +
                "      {\n" +
                "        \"price\": 1251.00,\n" +
                "        \"quantity\": 75,\n" +
                "        \"orders\": 3\n" +
                "      },\n" +
                "      {\n" +
                "        \"price\": 1251.25,\n" +
                "        \"quantity\": 125,\n" +
                "        \"orders\": 6\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
    TradeData tradeData = new TradeData();

    tradeData.setEntryOrderId("12345");
    tradeData.setOrderPlaced(true);
    tradeData.setRange(true);
    tradeData.setEntryType("BUY");
    tradeData.setSlPrice(new BigDecimal(1550));
    tradeData.setRangeHigh(new BigDecimal("100.0"));
    tradeData.setStockName("TEST_STOCK");
    tradeData.setZerodhaStockId(12345678);
    List<TradeData> tradeDataList=new ArrayList<>();
    tradeDataList.add(tradeData);
    tradingStrategyAndTradeData.openTrade.put("user123",tradeDataList);
    tradeProcessor.workerFactory=brokerWorkerFactory;
    User user=new User();
    user.setName("user123");
    user.setEnabled(true);
    List<User> userLs=new ArrayList<>();
    userLs.add(user);
    UserList userList=new UserList();
    userList.setUser(userLs);
    tradeProcessor.userList=userList;
        Tick tick= new Gson().fromJson(samplePayload,Tick.class);
        List<Tick> tickList=new ArrayList<>();
        tickList.add(tick);
        tradeProcessor.tickMessageProcessing(new Gson().toJson(tickList));
    tick.setLastTradedPrice(1300);
    List<Tick> tickList1=new ArrayList<>();
    tickList1.add(tick);
    tradeProcessor.tickMessageProcessing(new Gson().toJson(tickList1));
    tick.setLastTradedPrice(1500);
    List<Tick> tickList2=new ArrayList<>();
    tickList2.add(tick);
    tradeProcessor.tickMessageProcessing(new Gson().toJson(tickList2));

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
        User user=new User();
        user.setName("user123");
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