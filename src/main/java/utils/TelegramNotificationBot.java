package utils;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


/**
 * Telegram机器人通知
 *
 * @author loks666
 * @date 2023-05-09-10:16
 */
@Slf4j
public class TelegramNotificationBot {
    public static void main(String[] args) {
        System.out.println();
    }
    private static final String TELEGRAM_API_TOKEN = "";
    private static final long CHAT_ID = 1L;

    public void sendMessage(String message, String title) {

        TelegramBot bot = new TelegramBot(TELEGRAM_API_TOKEN);
        String messageText = "*【" + title + "】*\n\n" + message;
        SendMessage sendMessageRequest =
                new SendMessage(CHAT_ID, messageText).parseMode(ParseMode.Markdown);
        bot.execute(sendMessageRequest);
    }

    public void sendMessageWithList(String message, List<String> list, String title) {
        StringBuilder builder = new StringBuilder();
        for (String s : list) {
            builder.append(s).append("\n");
        }
        String trim = builder.toString().trim() + "\n\n";
        TelegramBot bot = new TelegramBot(TELEGRAM_API_TOKEN);
        String messageText = "*【" + title + "】*\n\n" + trim + message;
        SendMessage sendMessageRequest =
                new SendMessage(CHAT_ID, messageText).parseMode(ParseMode.Markdown);
        bot.execute(sendMessageRequest);
    }

    public void sendWarningMessage(String message, String title) {
        TelegramBot bot = new TelegramBot(TELEGRAM_API_TOKEN);
        String messageText = "*⚠️WARNING:*\n*【" + title + "】*\n\n" + message;
        SendMessage sendMessageRequest =
                new SendMessage(CHAT_ID, messageText).parseMode(ParseMode.Markdown);
        bot.execute(sendMessageRequest);
    }

    public void sendWarningMessageWithList(String message, List<String> list, String title) {
        StringBuilder builder = new StringBuilder();
        for (String s : list) {
            builder.append(s).append("\n");
        }
        String trim = builder.toString().trim() + "\n\n";
        TelegramBot bot = new TelegramBot(TELEGRAM_API_TOKEN);
        String messageText = "*⚠️WARNING:*\\n*【\" + title + \"】*\\n\\n" + trim + message;
        SendMessage sendMessageRequest =
                new SendMessage(CHAT_ID, messageText).parseMode(ParseMode.Markdown);
        bot.execute(sendMessageRequest);
    }
}
