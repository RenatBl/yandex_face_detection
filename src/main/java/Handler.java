import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import models.FaceInfoRequest;
import models.FaceInfoResponse;
import models.Request;
import models.Response;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Handler implements Function<String, Response> {

    public static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
    public static final String FOLDER_ID = System.getenv("FOLDER_ID");
    public static final String QUEUE_NAME = System.getenv("QUEUE_NAME");
    private static final String ACCESS_KEY = System.getenv("ACCESS_KEY");
    private static final String SECRET_KEY = System.getenv("SECRET_KEY");
    public static final String AUTH_TOKEN = "Bearer " + System.getenv("AUTH_TOKEN");

    @SneakyThrows
    public static void main(String[] args) {
        Request request = new Request();
        Request.Details details = new Request.Details();
        details.setObjectId("docs.jpg");
        Request.Message message = new Request.Message();
        message.setDetails(details);
        request.setMessages(List.of(message));

        new Handler().apply(new ObjectMapper().writeValueAsString(request));
    }

    @SneakyThrows
    @Override
    public Response apply(String stringRequest) {
        System.out.println("Environments");
        System.out.println("BUCKET_NAME " + BUCKET_NAME);
        System.out.println("FOLDER_ID " + FOLDER_ID);
        System.out.println("QUEUE_NAME " + QUEUE_NAME);
        System.out.println("ACCESS_KEY " + ACCESS_KEY);
        System.out.println("SECRET_KEY " + SECRET_KEY);
        System.out.println("AUTH_TOKEN " + AUTH_TOKEN);

        System.out.println("String request: " + stringRequest);

        Request request = parseJson(stringRequest, Request.class);
        // download image
        String fileId = request.getMessages().stream()
                .map(Request.Message::getDetails)
                .map(Request.Details::getObjectId)
                .findFirst()
                .orElseThrow();

        System.out.println("File ID: " + fileId);

        BufferedImage originalImage = downloadImage(fileId);

        FaceInfoRequest.AnalyzeSpec analyzeSpec = new FaceInfoRequest.AnalyzeSpec();
        FaceInfoRequest faceRequest = new FaceInfoRequest();
        faceRequest.setFolderId(FOLDER_ID);
        faceRequest.setAnalyzeSpecs(List.of(analyzeSpec));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(originalImage, "jpg", outputStream);
        analyzeSpec.setContent(Base64.encodeBase64String(outputStream.toByteArray()));

        // 1) send image to Face Detection service
        FaceInfoResponse faceInfoResponse = sendImageToFaceDetectionService(faceRequest);
        System.out.println("Face info response: " + faceInfoResponse);

        // if face detected, 2) then download and 3) cut it
        List<BufferedImage> images = downloadImageAndCutFaces(faceInfoResponse, originalImage);

        // 3) cut faces upload to Object Storage
        List<PutObjectResult> uploadResults = uploadImagesToObjectStorage(images);
        System.out.println("Put object results: " + uploadResults);

        // 4) write info about saved faces, link them with original image ID and send message to Message Queue
        List<String> faceImagesFilenames = uploadResults.stream()
                .map(PutObjectResult::getETag)
                .collect(Collectors.toList());
        sendMessageToMessageQueue(faceImagesFilenames);
        System.out.println("Face images names: " + faceImagesFilenames);

        return new Response(200, "OK");
    }

    @SneakyThrows
    public static BufferedImage downloadImage(String fileId) {
        AmazonS3 s3 = connectToAws();

        ListObjectsV2Result result = s3.listObjectsV2(BUCKET_NAME);
        List<S3ObjectSummary> objects = result.getObjectSummaries();

        S3ObjectSummary objectSummary = objects.stream()
                .filter(s3ObjectSummary -> s3ObjectSummary.getKey().contains(fileId))
                .findFirst()
                .orElseThrow();
        S3Object object = s3.getObject(BUCKET_NAME, objectSummary.getKey());

        byte[] bytes = object.getObjectContent().readAllBytes();

        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @SneakyThrows
    private FaceInfoResponse sendImageToFaceDetectionService(FaceInfoRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", AUTH_TOKEN);

        ResponseEntity<FaceInfoResponse> response = restTemplate().exchange(
                "https://vision.api.cloud.yandex.net/vision/v1/batchAnalyze",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                FaceInfoResponse.class
        );

        return response.getBody();
    }

    private List<PutObjectResult> uploadImagesToObjectStorage(List<BufferedImage> images) {
        AmazonS3 s3 = connectToAws();

        return images.stream()
                .map(image -> {
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", outputStream);
                        byte[] bytes = outputStream.toByteArray();
                        InputStream inputStream = new ByteArrayInputStream(bytes);

                        return s3.putObject(BUCKET_NAME, UUID.randomUUID().toString(), inputStream, new ObjectMetadata());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private List<BufferedImage> downloadImageAndCutFaces(FaceInfoResponse response, BufferedImage originalImage) {
        if (response == null || response.getResults() == null) {
            return new ArrayList<>();
        }

        List<BufferedImage> images = new ArrayList<>();

        response.getResults().stream()
                .map(FaceInfoResponse.Result::getResults)
                .flatMap(List::stream)
                .map(FaceInfoResponse.Result2::getFaceDetection)
                .map(FaceInfoResponse.FaceDetection::getFaces)
                .flatMap(List::stream)
                .map(FaceInfoResponse.Face::getBoundingBox)
                .forEach(boundingBox -> {
                    int minX = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE;
                    int minY = Integer.MAX_VALUE;
                    int maxY = Integer.MIN_VALUE;
                    for (FaceInfoResponse.Vertex vertex : boundingBox.getVertices()) {
                        minX = Integer.min(Integer.parseInt(vertex.getX()), minX);
                        maxX = Integer.max(Integer.parseInt(vertex.getX()), maxX);
                        minY = Integer.min(Integer.parseInt(vertex.getY()), minY);
                        maxY = Integer.max(Integer.parseInt(vertex.getY()), maxY);
                    }
                    BufferedImage subImage = originalImage.getSubimage(minX, minY, maxX, maxY);
                    images.add(subImage);
                });

        return images;
    }

    @SneakyThrows
    private void sendMessageToMessageQueue(List<String> faceImagesFilenames) {
        AWSCredentialsProvider credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY,
                SECRET_KEY));

        SQSConnectionFactory connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClientBuilder.standard()
                        .withCredentials(credentials)
                        .withRegion("ru-central1")
                        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                                "https://message-queue.api.cloud.yandex.net",
                                "ru-central1"
                        ))
        );

        SQSConnection connection = connectionFactory.createConnection();

        AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();

        if (!client.queueExists(QUEUE_NAME)) {
            client.createQueue(QUEUE_NAME);
        }

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // create queue
        javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
        MessageProducer producer = session.createProducer(queue);

        // send message with files info
        String msg = String.join(";", faceImagesFilenames);
        Message message = session.createTextMessage(msg);
        producer.send(message);
    }

    public static AmazonS3 connectToAws() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY);

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(
                        new AmazonS3ClientBuilder.EndpointConfiguration(
                                "storage.yandexcloud.net", "ru-central1"
                        )
                )
                .build();
    }

    private static RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        messageConverter.setObjectMapper(objectMapper);
        restTemplate.getMessageConverters().add(messageConverter);

        return restTemplate;
    }

    @SneakyThrows
    public static <T> T parseJson(String json, Class<T> toClass) {
        return new ObjectMapper().readValue(json, toClass);
    }
}