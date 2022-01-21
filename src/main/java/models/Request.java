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
public class Request {

    @JsonProperty("messages")
    private List<Message> messages;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TracingContext {

        @JsonProperty("trace_id")
        private String traceId;

        @JsonProperty("span_id")
        private String spanId;

        @JsonProperty("parent_span_id")
        private String parentSpanId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventMetadata {

        @JsonProperty("event_id")
        private String eventId;

        @JsonProperty("event_type")
        private String eventType;

        @JsonProperty("created_at")
        private Date createdAt;

        @JsonProperty("tracing_context")
        private TracingContext tracingContext;

        @JsonProperty("cloud_id")
        private String cloudId;

        @JsonProperty("folder_id")
        private String folderId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Details {

        @JsonProperty("bucket_id")
        private String bucketId;

        @JsonProperty("object_id")
        private String objectId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {

        @JsonProperty("event_metadata")
        private EventMetadata eventMetadata;

        @JsonProperty("details")
        private Details details;
    }
}
