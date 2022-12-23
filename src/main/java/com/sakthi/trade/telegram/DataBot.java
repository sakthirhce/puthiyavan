package com.sakthi.trade.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
@Component
public class DataBot extends TelegramLongPollingBot {
    private static final String BOT_USERNAME = "YOUR_BOT_USERNAME";
    @Value("${telegram.data.bot.chatid}")
    public String chatId;
    @PostConstruct
    public void registerBot() throws TelegramApiException {

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            botsApi.registerBot(this);
          //  log.info("==================================TelegramBotService.afterPropertiesSet:registerBot finish");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onUpdateReceived(Update update) {

    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return chatId;
    }

    public void sendZipFile(String filePath,String fileName,long chatId) throws FileNotFoundException {
        SendDocument sendDocumentRequest = new SendDocument();
        sendDocumentRequest.setChatId(chatId);
        sendDocumentRequest.setDocument(new InputFile(new File(filePath)));
        sendDocumentRequest.setCaption(fileName);
        try {
            execute(sendDocumentRequest);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message,long chatId) throws FileNotFoundException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
