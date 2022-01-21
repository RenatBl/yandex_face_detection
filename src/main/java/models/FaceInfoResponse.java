package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FaceInfoResponse {

    @JsonProperty("results")
    private List<Result> results;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Vertex {

        @JsonProperty("x")
        private String x;

        @JsonProperty("y")
        private String y;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BoundingBox {

        @JsonProperty("vertices")
        private List<Vertex> vertices;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Face {

        @JsonProperty("boundingBox")
        private BoundingBox boundingBox;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FaceDetection {

        @JsonProperty("faces")
        private List<Face> faces;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Result {

        @JsonProperty("results")
        private List<Result2> results;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Result2 {

        @JsonProperty("faceDetection")
        public FaceDetection faceDetection;
    }
}
