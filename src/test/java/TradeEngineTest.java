import com.sakthi.trade.TradeEngine;
import com.sakthi.trade.domain.TradeData;
import com.sakthi.trade.entity.TradeStrategy;
import com.sakthi.trade.seda.TradeSedaQueue;
import com.sakthi.trade.zerodha.TransactionService;
import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import org.junit.Before;
import org.junit.Test;

import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
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

   // @DisplayName("Candle Monitor - Happy Path")
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
        verify(transactionService, times(1)).callAPI(any(), any(), any());
        verify(tradeSedaQueue, times(1)).sendOrderPlaceSeda(any());
    }

   // @DisplayName("Candle Monitor - Websocket SL Not Enabled")
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
}