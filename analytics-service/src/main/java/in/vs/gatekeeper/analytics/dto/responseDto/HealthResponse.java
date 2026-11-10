package in.vs.gatekeeper.analytics.dto.responseDto;

public class HealthResponse {

    private String serviceName;
    private String serviceDescription;
    private String appStatus;

    public HealthResponse(String serviceName,
                          String serviceDescription,
                          String appStatus) {
        this.serviceName = serviceName;
        this.serviceDescription = serviceDescription;
        this.appStatus = appStatus;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public String getAppStatus() {
        return appStatus;
    }
}