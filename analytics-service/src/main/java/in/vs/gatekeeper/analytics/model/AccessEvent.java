package in.vs.gatekeeper.analytics.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "access_events")
public class AccessEvent {

    @Id
    private String id;

    private Long organizationId;

    private String routeId;

    private String requestMethod;

    private String requestPath;

    private Integer responseStatus;

    private Long responseTimeMs;

    private LocalDateTime timestamp;

    private String clientIp;

    private String userAgent;
}