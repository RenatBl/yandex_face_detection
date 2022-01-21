import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import models.UserFace;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class Telebot extends TelegramWebhookBot {

    public static final String BOT_USERNAME = System.getenv("BOT_USERNAME");
    public static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private final ThreadLocal<String> chatId = new ThreadLocal<>();
    public static AtomicReference<String> LAST_IMAGE_ID = new AtomicReference<>();

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @SneakyThrows
    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            return null;
        }
        Message message = update.getMessage();
        chatId.set(message.getChat().getId().toString());
        if (message.hasText()) {
            String text = message.getText();
            if (text.startsWith("/find")) {
                String user = text.split("/find ")[0];
                // find user faces from list
                List<UserFace> userFaces = Handler2.USER_FACES.stream()
                        .filter(userFace -> userFace.getUsername().equals(user))
                        .collect(Collectors.toList());
                // download them
                if (userFaces.isEmpty()) {
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setText(String.format("Faces for user %s not found", user));
                    sendMessage(sendMessage);
                }
                // send them to chat
                for (UserFace userFace : userFaces) {
                    BufferedImage bufferedImage = Handler.downloadImage(userFace.getImageId());
                    File file = Handler2.convertBufferedImageToFile(bufferedImage, userFace.getUsername() + ".jpg");
                    SendPhoto sendPhoto = new SendPhoto();
                    InputFile inputFile = new InputFile().setMedia(file);
                    sendPhoto.setPhoto(inputFile);
                    sendPhoto(sendPhoto);
                }
            } else if (text.startsWith("/user")) {
                String user = text.split("/user ")[0];
                Handler2.USER_FACES.add(new UserFace(user, LAST_IMAGE_ID.get()));
                Handler2.MONITOR = false;
            }
        }

        return null;
    }

    @Override
    public String getBotPath() {
        return BOT_USERNAME;
    }

    @SneakyThrows
    public void sendMessage(SendMessage sendMessage) {
        sendMessage.setChatId(chatId.get());
        execute(sendMessage);
    }

    @SneakyThrows
    public void sendPhoto(SendPhoto sendPhoto) {
        sendPhoto.setChatId(chatId.get());
        execute(sendPhoto);
    }

    @Override
    protected void finalize() {
        chatId.remove();
    }
}
