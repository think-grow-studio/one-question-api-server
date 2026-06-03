package site.one_question.api.health;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController implements HealthCheckApi {

    @GetMapping
    @Override
    public ResponseEntity<Map<String, String>> healthCheck() {
      log.debug("[API] Health Check API 시작");
      Map<String, String> response = new HashMap<>();
      response.put("status", "healthy");
      log.debug("[API] Health Check API 종료");
      return ResponseEntity.ok(response);
    }
  }
