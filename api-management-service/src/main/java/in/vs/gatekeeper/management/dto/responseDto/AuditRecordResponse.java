package in.vs.gatekeeper.management.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

import in.vs.gatekeeper.management.entity.ControlPlaneAuditRecord;

@Getter
@AllArgsConstructor
public class AuditRecordResponse {

    private Long id;
    private String actor;
    private String action;
    private String resourceType;
    private String resourceId;
    private String correlationId;
    private Long durationMs;
    private String result;
    private String details;
    private LocalDateTime createdAt;

    public static AuditRecordResponse from(ControlPlaneAuditRecord record) {
        return new AuditRecordResponse(record.getId(), record.getActor(), record.getAction(),
                record.getResourceType(), record.getResourceId(), record.getCorrelationId(),
                record.getDurationMs(), record.getResult(), record.getDetails(), record.getCreatedAt());
    }
}
