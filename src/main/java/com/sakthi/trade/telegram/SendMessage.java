package com.sakthi.trade.telegram;

import com.sakthi.trade.http.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

@Component
public class SendMessage {
    @Value("${telegram.straddle.bot.chatid}")
    public String chatId;
    @Value("${telegram.bot.sendmessage.url}")
    public String sendmessageURL;
    @Autowired
    HttpClientBuilder httpClientBuilder;
    // to send message curl -X POST "https://api.telegram.org/bot1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o/sendMessage" -d "chat_id=-713214125&text=my sample text"
    // to get bot related group id's to send alert https://api.telegram.org/bot1162339611:AAGTezAs6970OmLwhcBuTlef_-dsfcoQi_o/getUpdates
    // ex: https://stackoverflow.com/questions/32423837/telegram-bot-how-to-get-a-group-chat-id
        public void sendToTelegram(String message,String token){
        try {

            String escapedMsg =message.replace("&","");
            String messageText = URLEncoder.encode(escapedMsg, "UTF-8");
            String urlString =String.format(sendmessageURL, token, chatId, messageText);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendToTelegram(String message,String token,String groupChatId){
        try {

            String escapedMsg =message.replace("&","");
            String messageText = URLEncoder.encode(escapedMsg, "UTF-8");
            String urlString =String.format(sendmessageURL, token, groupChatId, messageText);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}