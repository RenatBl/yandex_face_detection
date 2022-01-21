package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Msg {

    public List<Message> messages;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        public EventMetadata event_metadata;
        public Details details;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message2 {
        public String message_id;
        public String md5_of_body;
        public String body;
        public Attributes attributes;
        public MessageAttributes message_attributes;
        public String md5_of_message_attributes;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Details {
        public String queue_id;
        public Message2 message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageAttributes {
        public MessageAttributeKey messageAttributeKey;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageAttributeKey {
        public String dataType;
        public String stringValue;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Attributes {
        @JsonProperty("SentTimestamp")
        public String sentTimestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventMetadata {
        public String event_id;
        public String event_type;
        public Date created_at;
    }
}
