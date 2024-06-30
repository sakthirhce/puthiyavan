package com.sakethi.trade;

import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.util.MathUtils;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.account.StrikeData;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import com.zerodhatech.models.HistoricalData;
import org.junit.Before;
import org.junit.Test;

import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@RunWith(SpringJUnit4ClassRunner.class)
public class TradeEngineTest {

    @InjectMocks
    TradeEngine tradeEngine;

    @Mock
    TransactionService transactionService;

    @Mock
    TradeSedaQueue tradeSedaQueue;

    @Before
    public void setUp() {
        UserList userList=new UserList();
        List<User> userLs=new ArrayList<>();
        User user =new User();
        user.setName("LTK728");
        user.setEnabled(true);
        userLs.add(user);
        userList.setUser(userLs);
        tradeEngine.userList=  userList;
        tradeEngine.transactionService=transactionService;
        tradeEngine.tradeSedaQueue=tradeSedaQueue;
        MockitoAnnotations.openMocks(this);
    }

    @DisplayName("Candle Monitor - Happy Path")
    @Test
    public void candleMonitor_HappyPath() throws IOException {
        // Given
        Map<String, List<TradeData>> openTrade = new HashMap<>();
        List<TradeData> tradeDataList = new ArrayList<>();

        TradeData tradeData = new TradeData();
        tradeData.setSlPrice(new BigDecimal(100));
        tradeData.setStockId(12345);
        tradeData.isOrderPlaced =true;
        tradeData.setUserId("LTK728");
        tradeData.isSlPlaced=true;
        String json = new String(Files.readAllBytes(Paths.get("src/test/resources/json/candle_limit.json")));
        when(transactionService.callAPI(any(),any(),any()))
                .thenReturn(json);

        TradeStrategy tradeStrategy= new TradeStrategy();
        tradeStrategy.setWebsocketSlEnabled(true);
        tradeData.setTradeStrategy(tradeStrategy);
        tradeDataList.add(tradeData);
        openTrade.put("userId", tradeDataList);
        tradeEngine.openTrade=openTrade;
        // When
        tradeEngine.candleMonitor();

        // Then
       // verify(transactionService, times(0)).callAPI(any(), any(), any());
       // verify(tradeSedaQueue, times(0)).sendOrderPlaceSeda(any());
    }

    @DisplayName("Candle Monitor - Websocket SL Not Enabled")
    @Test
   public void candleMonitor_WebsocketSLNotEnabled() {
        // Given
        Map<String, List<TradeData>> openTrade = new HashMap<>();
        List<TradeData> tradeDataList = new ArrayList<>();
        TradeData tradeData = new TradeData();
        tradeData.setSlPrice(new BigDecimal(100));
        tradeData.setStockId(12345);
        TradeStrategy tradeStrategy = new TradeStrategy();
        tradeStrategy.setWebsocketSlEnabled(false);
        tradeData.setTradeStrategy(tradeStrategy);
        tradeDataList.add(tradeData);
        openTrade.put("userId", tradeDataList);
        tradeEngine.openTrade=openTrade;

        // When
        tradeEngine.candleMonitor();

        // Then
        verify(transactionService, never()).callAPI(any(), any(), any());
        verify(tradeSedaQueue, never()).sendTelemgramSeda(any(), any());
    }

   //  @DisplayName("Candle Monitor - Websocket SL Already Modified")
    @Test
    public void candleMonitor_WebsocketSLAlreadyModified() {
        // Given
        Map<String, List<TradeData>> openTrade = new HashMap<>();
        List<TradeData> tradeDataList = new ArrayList<>();
        TradeData tradeData = new TradeData();
        tradeData.setSlPrice(new BigDecimal(100));
        tradeData.setStockId(12345);
        TradeStrategy tradeStrategy = new TradeStrategy();
        tradeStrategy.setWebsocketSlEnabled(true);
        tradeData.setTradeStrategy(tradeStrategy);
        tradeData.setWebsocketSlModified(true);
        tradeDataList.add(tradeData);
        openTrade.put("userId", tradeDataList);
        tradeEngine.openTrade=openTrade;

        // When
        tradeEngine.candleMonitor();

        // Then
        verify(transactionService, never()).callAPI(any(), any(), any());
        verify(tradeSedaQueue, never()).sendTelemgramSeda(any(), any());
    }
    @Mock
    MathUtils mathUtils;

        @DisplayName("Range Break Options - Happy Path")
        @Test
        public void rangeBreakOptions_HappyPath() {
            // Given
            TradeStrategy strategy = new TradeStrategy();
            strategy.setRangeStartTime("09:00");
            HistoricalData historicalData = new HistoricalData();
            HistoricalData lastHistoricalData = new HistoricalData();
            String currentDateStr = "2022-12-01";
            String currentHourMinStr = "09:00";
            String candleHourMinStr = "09:00";
            String index = "NF";

            Map<String, StrikeData> strikeDataEntry = new HashMap<>();
            StrikeData strikeData = new StrikeData();
            strikeDataEntry.put("key", strikeData);

            Map<String, Map<String, StrikeData>> rangeStrikes = new HashMap<>();
            rangeStrikes.put("indexStrikePrice", strikeDataEntry);

            when(mathUtils.strikeSelection(anyString(), any(TradeStrategy.class), anyDouble(), anyString()))
                    .thenReturn(rangeStrikes);

            // When
            tradeEngine.rangeBreakOptions(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr, lastHistoricalData, index);

            // Then
            verify(mathUtils, times(1)).strikeSelection(anyString(), any(TradeStrategy.class), anyDouble(), anyString());
            //verify(transactionService, times(1)).callAPI(any(), any(), any());
        }

        @DisplayName("Range Break Options - Exception Path")
        @Test
        public void rangeBreakOptions_ExceptionPath() {
            // Given
            TradeStrategy strategy = new TradeStrategy();
            strategy.setRangeStartTime("09:00");
            HistoricalData historicalData = new HistoricalData();
            HistoricalData lastHistoricalData = new HistoricalData();
            String currentDateStr = "2022-12-01";
            String currentHourMinStr = "09:00";
            String candleHourMinStr = "09:00";
            String index = "NF";

            when(mathUtils.strikeSelection(anyString(), any(TradeStrategy.class), anyDouble(), anyString()))
                    .thenThrow(new RuntimeException());

            // When
            tradeEngine.rangeBreakOptions(strategy, historicalData, currentDateStr, currentHourMinStr, candleHourMinStr, lastHistoricalData, index);

            // Then
            verify(mathUtils, times(1)).strikeSelection(anyString(), any(TradeStrategy.class), anyDouble(), anyString());
        }



    @DisplayName("SL Immediate - Happy Path")
    @Test
    public void slImmediate_HappyPath() {
        // Given
        Map<String, List<TradeData>> openTrade = new HashMap<>();
        List<TradeData> tradeDataList = new ArrayList<>();

        TradeData tradeData = new TradeData();
        tradeData.setSlPrice(new BigDecimal(100));
        tradeData.setStockId(12345);
        tradeData.isOrderPlaced = true;
        tradeData.setUserId("LTK728");
        tradeData.isSlPlaced = true;

        TradeStrategy tradeStrategy = new TradeStrategy();
        tradeStrategy.setWebsocketSlEnabled(true);
        tradeData.setTradeStrategy(tradeStrategy);

        tradeDataList.add(tradeData);
        openTrade.put("userId", tradeDataList);
        tradeEngine.openTrade = openTrade;

        // When
        tradeEngine.slImmediate();

        // Then
        verify(transactionService, times(0)).callAPI(any(), any(), any());
        verify(tradeSedaQueue, times(0)).sendOrderPlaceSeda(any());
    }

    @DisplayName("SL Immediate - SL Not Placed")
    @Test
    public void slImmediate_SLNotPlaced() {
        // Given
        Map<String, List<TradeData>> openTrade = new HashMap<>();
        List<TradeData> tradeDataList = new ArrayList<>();

        TradeData tradeData = new TradeData();
        tradeData.setSlPrice(new BigDecimal(100));
        tradeData.setStockId(12345);
        tradeData.isOrderPlaced = true;
        tradeData.setUserId("LTK728");
        tradeData.isSlPlaced = false;

        TradeStrategy tradeStrategy = new TradeStrategy();
        tradeStrategy.setWebsocketSlEnabled(true);
        tradeData.setTradeStrategy(tradeStrategy);

        tradeDataList.add(tradeData);
        openTrade.put("userId", tradeDataList);
        tradeEngine.openTrade = openTrade;

        // When
        tradeEngine.slImmediate();

        // Then
        verify(transactionService, never()).callAPI(any(), any(), any());
        verify(tradeSedaQueue, never()).sendOrderPlaceSeda(any());
    }

    @DisplayName("SL Immediate - Order Not Placed")
    @Test
    public void slImmediate_OrderNotPlaced() {
        // Given
        Map<String, List<TradeData>> openTrade = new HashMap<>();
        List<TradeData> tradeDataList = new ArrayList<>();

        TradeData tradeData = new TradeData();
        tradeData.setSlPrice(new BigDecimal(100));
        tradeData.setStockId(12345);
        tradeData.isOrderPlaced = false;
        tradeData.setUserId("LTK728");
        tradeData.isSlPlaced = true;

        TradeStrategy tradeStrategy = new TradeStrategy();
        tradeStrategy.setWebsocketSlEnabled(true);
        tradeData.setTradeStrategy(tradeStrategy);

        tradeDataList.add(tradeData);
        openTrade.put("userId", tradeDataList);
        tradeEngine.openTrade = openTrade;

        // When
        tradeEngine.slImmediate();

        // Then
        verify(transactionService, never()).callAPI(any(), any(), any());
        verify(tradeSedaQueue, never()).sendOrderPlaceSeda(any());
    }

}