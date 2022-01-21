import lombok.SneakyThrows;
import models.Msg;
import models.UserFace;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Handler2 implements Function<String, String> {

    public static final List<UserFace> USER_FACES = new CopyOnWriteArrayList<>();
    public static volatile boolean MONITOR = true;

    private final Telebot telebot = new Telebot();

    @SneakyThrows
    @Override
    public String apply(String stringRequest) {
        // get message queue response and parse it
        Msg msg = Handler.parseJson(stringRequest, Msg.class);
        List<String> fileIds = getFileIdsFromMessage(msg);
        // download images from request
        Map<String, BufferedImage> images = fileIds.stream()
                .collect(Collectors.toMap(Function.identity(), Handler::downloadImage));
        // send image to Telegram Bot
        for (Map.Entry<String, BufferedImage> imageEntry : images.entrySet()) {
            MONITOR = true;
            // save pairs "image" - "user" to list
            // after getting response "who is it" send next image
            File file = convertBufferedImageToFile(imageEntry.getValue(), imageEntry.getKey());
            SendPhoto sendPhoto = new SendPhoto();
            InputFile inputFile = new InputFile().setMedia(file);
            sendPhoto.setPhoto(inputFile);
            telebot.sendPhoto(sendPhoto);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setText("Who is it? (send username with \"/user\" command)");
            telebot.sendMessage(sendMessage);

            Telebot.LAST_IMAGE_ID.set(imageEntry.getKey());

            // wait for receiving
            while (MONITOR) Thread.onSpinWait();
        }

        return "Finish";
    }

    private List<String> getFileIdsFromMessage(Msg msg) {
        String message = msg.getMessages().get(0).getDetails().getMessage().getBody();

        return Arrays.stream(message.split(";"))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public static File convertBufferedImageToFile(BufferedImage image, String name) {
        File outputFile = new File(name);
        ImageIO.write(image, "jpg", outputFile);

        return outputFile;
    }
}
