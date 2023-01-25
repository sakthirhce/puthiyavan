package com.sakthi.trade.telegram;

import com.sakthi.trade.worker.ZerodhaWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

@Component
public class TelegramMessenger {
    @Value("${telegram.straddle.bot.chatid}")
    public String chatId;
    @Value("${telegram.bot.sendmessage.url}")
    public String sendmessageURL;
    @Value("${profile:false}")
    boolean profile;

    @Autowired
    DataBot myBot;
    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
    @Autowired
    LogBot logBot;
    private static final String BOT_TOKEN = "1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o";
    private static final String BOT_USERNAME = "YOUR_BOT_USERNAME";
    public static final Logger LOGGER = Logger.getLogger(ZerodhaWorker.class.getName());
        public void sendToTelegram(String message,String token){
            try {
            long start1 = System.currentTimeMillis();
            executorService.submit(() -> {
                try {
                        long start = System.currentTimeMillis();
                        String escapedMsg = message.replace("&", "");
                        String messageText = URLEncoder.encode(escapedMsg, "UTF-8");
                        String urlString = String.format(sendmessageURL, token, chatId, messageText);
                        URL url = new URL(urlString);
                        URLConnection conn = url.openConnection();
                        InputStream is = new BufferedInputStream(conn.getInputStream());

                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        String inputLine = "";
                        StringBuilder sb = new StringBuilder();
                        while ((inputLine = br.readLine()) != null) {
                            sb.append(inputLine);
                        }
                        String response = sb.toString();
                        long finish = System.currentTimeMillis();
                        long timeElapsed = finish - start;
                LOGGER.info("telegram message sent in:"+timeElapsed);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                    });

            long end = System.currentTimeMillis();
                long timeElapsed1 = end - start1;
            LOGGER.info("telegram call completed in :"+timeElapsed1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendToTelegram(String message,String token,String groupChatId){
        try {
            long start1 = System.currentTimeMillis();
            executorService.submit(() -> {
            try {
            if (!profile) {
                String escapedMsg = message.replace("&", "");
                String messageText = URLEncoder.encode(escapedMsg, "UTF-8");
                String urlString = String.format(sendmessageURL, token, groupChatId, messageText);
                URL url = new URL(urlString);
                //     System.out.println(url);
                URLConnection conn = url.openConnection();
                InputStream is = new BufferedInputStream(conn.getInputStream());

                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String inputLine = "";
                StringBuilder sb = new StringBuilder();
                while ((inputLine = br.readLine()) != null) {
                    sb.append(inputLine);
                }
                String response = sb.toString();
            }
            } catch(Exception e){
                e.printStackTrace();
            }});

            long end = System.currentTimeMillis();
            long timeElapsed1 = end - start1;
            LOGGER.info("telegram call completed in :"+timeElapsed1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendDocumentToTelegram(String filePath,String fileName){
        try {
            if (!profile) {
                try {
                    long start = System.currentTimeMillis();
                    //botsApi.registerBot(bot);
                  //  long chatId = -713214125; // Replace with the chat ID of the chat where you want to send the file
                 //   String filePath = "/home/hasvanth/Downloads/FINNIFTY/2022/Dec/2022-12-20/FINNIFTY_2022-12-20.zip"; // Replace with the file path of the ZIP file
                    myBot.sendZipFile(filePath,fileName,-849080155);
                    long finish = System.currentTimeMillis();
                    long timeElapsed = finish - start;
                    LOGGER.info("telegram doc sent in:"+timeElapsed);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    public void sendLogToTelegram(String filePath,String fileName){
        try {
            if (!profile) {
                try {
                    long start = System.currentTimeMillis();
                    //botsApi.registerBot(bot);
                    //  long chatId = -713214125; // Replace with the chat ID of the chat where you want to send the file
                    //   String filePath = "/home/hasvanth/Downloads/FINNIFTY/2022/Dec/2022-12-20/FINNIFTY_2022-12-20.zip"; // Replace with the file path of the ZIP file
                    logBot.sendZipFile(filePath,fileName,-848547540);
                    long finish = System.currentTimeMillis();
                    long timeElapsed = finish - start;
                    LOGGER.info("telegram log sent in:"+timeElapsed);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

}