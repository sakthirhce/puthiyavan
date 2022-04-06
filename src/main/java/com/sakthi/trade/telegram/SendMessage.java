package com.sakthi.trade.telegram;

import com.github.badoualy.telegram.api.Kotlogram;
import com.github.badoualy.telegram.api.TelegramApp;
import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.*;
import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.api.auth.TLAuthorization;
import com.github.badoualy.telegram.tl.api.auth.TLSentCode;
import com.github.badoualy.telegram.tl.core.TLBool;
import com.github.badoualy.telegram.tl.core.TLBytes;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import com.sakthi.trade.http.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.UUID;
import java.util.logging.Logger;

@Component
public class SendMessage {
    @Value("${telegram.straddle.bot.chatid}")
    public String chatId;
    @Value("${telegram.bot.sendmessage.url}")
    public String sendmessageURL;
    @Autowired
    HttpClientBuilder httpClientBuilder;
    @Value("${profile:false}")
    boolean profile;
    // Get them from Telegram's console
    public static final int API_ID = 17305734;
    public static final String API_HASH = "5d6e1be11aa26b4a66dc8ae4e81c4c02";

    // What you want to appear in the "all sessions" screen
    public static final String APP_VERSION = "AppVersion";
    public static final String MODEL = "Model";
    public static final String SYSTEM_VERSION = "SysVer";
    public static final String LANG_CODE = "en";

    public static TelegramApp application = new TelegramApp(API_ID, API_HASH, MODEL, SYSTEM_VERSION, APP_VERSION, LANG_CODE);

    // Phone number used for tests
    public static final String PHONE_NUMBER = "+918892356038"; // International format
   // private static final Logger logger = Logger.getLogger(TelegramBot.class.getName());
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
            }
    }/*
    public void sendDocumentToTelegram(File file,String token,String groupChatId){
        try {

            TelegramBot telegramBot=new TelegramBot(token);
            telegramBot.start();
            telegramBot.sendDocument(Long.valueOf(groupChatId),file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void sendToChannel(TLInputPeerChannel tlInputPeerChannel, String name, long fileId, int totalParts) {
        TelegramCl//ient client = Kotlogram.getDefaultClient(application, new ApiStorage());

// You can start making requests
        try {
            // Send code to account
            TLSentCode sentCode = client.authSendCode(false, PHONE_NUMBER, true);
            System.out.println("Authentication code: ");
            String code = new Scanner(System.in).nextLine();

            // Auth with the received code
            TLAuthorization authorization = client.authSignIn(PHONE_NUMBER, sentCode.getPhoneCodeHash(), code);
            TLUser self = authorization.getUser().getAsUser();
            System.out.println("You are now signed in as " + self.getFirstName() + " " + self.getLastName() + " @" + self.getUsername());
        } catch (RpcErrorException | IOException e) {
            e.printStackTrace();
        } finally {
            client.close(); // Important, do not forget this, or your process won't finish
        }
        try {
            String mimeType = name.substring(name.indexOf(".") + 1);

            TLVector<TLAbsDocumentAttribute> attributes = new TLVector<>();
            attributes.add(new TLDocumentAttributeFilename(name));

            TLInputFileBig inputFileBig = new TLInputFileBig(fileId, totalParts, name);
            TLInputMediaUploadedDocument document = new TLInputMediaUploadedDocument(inputFileBig, mimeType, attributes, "", null);
            TLAbsUpdates tlAbsUpdates = client.messagesSendMedia(false, false, false,
                    tlInputPeerChannel, null, document, UUID.randomUUID().clockSequence(), null);
        } catch (Exception e) {
            System.out.println("Error sending file by id into channel"+ e);
        } finally {
            client.close();
        }
    }*/

}