package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FaceInfoRequest {

    @JsonProperty("folderId")
    private String folderId;

    @JsonProperty("analyze_specs")
    private List<AnalyzeSpec> analyzeSpecs;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnalyzeSpec {

        @JsonProperty("content")
        private String content;

        private List<Feature> features = List.of(new Feature());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Feature {

        @JsonProperty("type")
        private String type = "FACE_DETECTION";
    }
}
