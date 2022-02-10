/*
package com.sakthi.trade.websocket.truedata;


import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.AutomationController;
import com.sakthi.trade.domain.OrbTradeData;
import com.sakthi.trade.domain.PreOpenDetails;
import com.sakthi.trade.domain.TradeData;

import com.sakthi.trade.options.banknifty.BankNiftyShortStraddleOI;
import com.sakthi.trade.truedata.HistoricRequestDTO;
import com.sakthi.trade.truedata.HistoricResponseDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.websocket.ContainerProvider;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Calendar.DAY_OF_MONTH;

@Component
@Slf4j
public class HistoricWebsocketTest {
    @Value("${truedata.wss}")
    String truedataWss;
    @Value("${truedata.username}")
    String truedataUsername;
    @Value("${truedata.password}")
    String truedataPassword;
    @Value("${truedata.historic.port}")
    String truedataHistoricDataPort;
    @Value("${filepath.trend}")
    String trendPath;
    @Value("${backtest.enabled}")
    boolean backtestEnabled;
    public Session session = null;
    String truedataURL = null;
    public Map<Integer, TradeData> orbTradePriceDTOS = null;
    public Map<String, PreOpenDetails> preOpenDetailsMap = new HashMap<>();
*/
/*
    @Autowired
    OrbScheduler orbScheduler;*//*



    @Autowired
    AutomationController automationController;

    @Autowired
    BankNiftyShortStraddleOI bankNiftyShortStraddle;
    */
/*
    @Autowired
    TrendScheduler trendScheduler;
    @Autowired
    NiftyShortStraddle niftyShortStraddle;*//*



    AtomicInteger atomicInteger = new AtomicInteger();

    public ObservableList<HistoricResponseDTO> historicResponseDTOObservableList = FXCollections.observableArrayList();
    ExecutorService executorService = Executors.newFixedThreadPool(10);
  //  @Scheduled(cron = "${truedata.websocket.scheduler.start}")
    public Session createHistoricWebSocket() throws IOException {
        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("ddMMyyyy");
        LocalDate localDate=LocalDate.now();
        CSVWriter csvWriter=new CSVWriter(new FileWriter(trendPath+"/trend_"+dtf1.format(localDate)+".csv",false));
        String[] dataHeader={"StockName","PerChange","Closing Price","PreOpenPrice","Date"};
        CSVWriter csvWriterNeg=new CSVWriter(new FileWriter(trendPath+"/trend_negative_"+dtf1.format(localDate)+".csv",false));
        csvWriter.writeNext(dataHeader);
        csvWriter.flush();
        csvWriterNeg.writeNext(dataHeader);
        csvWriterNeg.flush();
        Map<String, Double> trendStockMap = new HashMap<>();
        if (session == null) {
            truedataURL = truedataWss.replace("port", truedataHistoricDataPort);
            truedataURL = truedataURL.replace("input_username", truedataUsername);
            truedataURL = truedataURL.replace("input_password", truedataPassword);
            WebSocketContainer webSocketContain = null;
            try {
                webSocketContain = ContainerProvider.getWebSocketContainer();
                webSocketContain.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
                webSocketContain.setDefaultMaxTextMessageBufferSize(1024 * 1024);
                session = webSocketContain.connectToServer(WebSocketClientEndPoint.class, new URI(truedataURL));
                // Optional<OrbConfigEntity> orbConfigEntity=orbConfigRepo.findById("test");
                BigDecimal amount = new BigDecimal("20000");
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @SneakyThrows
                    @Override
                    public void onMessage(String message) {

                        System.out.println(message);
                        if (message.contains("data")) {

                            HistoricResponseDTO historicResponseDTO = new Gson().fromJson(message, HistoricResponseDTO.class);
                            if(historicResponseDTO.getSymbol().equals("NIFTY BANK") && historicResponseDTO.getInterval().equals("5min")){
                                bankNiftyShortStraddle.shortStraddleTradeBackTest(historicResponseDTO);
                            }
                            if(historicResponseDTO.getSymbol().contains("BANKNIFTY")){
                                System.out.println(message);
                                try { executorService.execute(new Runnable() {
                                    @SneakyThrows
                                    public void run() {
                                        Date currentDate =new Date();
                                        SimpleDateFormat sdfo = new SimpleDateFormat("dd-MM-yyyy");
                                        SimpleDateFormat sdfrom = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
                                        Date fromDate = sdfrom.parse(sdfo.format(currentDate)+"T15:30:00");
                                        Date toDate = sdfrom.parse(sdfo.format(currentDate)+"T09:00:00");
                                        if(backtestEnabled || (currentDate.after(fromDate) || currentDate.before(toDate))) {
                                            bankNiftyShortStraddle.shortStraddleTradeProcessLongBackTest(historicResponseDTO);
                                            //bankNiftyShortStraddle.longShortBackTest(historicResponseDTO);
                                        }
                                        // enable for realtime
                                        if(!backtestEnabled) {
                                            bankNiftyShortStraddle.shortStraddleTradeProcessor(historicResponseDTO);
                                        }
                                    }});
                                }catch(Exception e){
                                    e.printStackTrace();
                                }

                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return session;
    }
}*/
