package in.vs.gatekeeper.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import in.vs.gatekeeper.management.dto.responseDto.HealthResponse;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public String getHealth() {
        String databaseStatus = "DOWN";

         try {
             Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

             if(Integer.valueOf(1).equals(result)) {
                 databaseStatus = "UP";
             }
        }
         catch(Exception e) {
             System.out.println("MySQL Health check failed " + e.getMessage());
         }
         return databaseStatus;
    }
}
