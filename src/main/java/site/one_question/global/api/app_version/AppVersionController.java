package site.one_question.global.api.app_version;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app-version")
public class AppVersionController implements AppVersionApi {

  @Value("${app.version.min}")
  private String MIN_VERSION;

  @Value("${app.version.latest}")
  private String LATEST_VERSION;

  @Value("${app.server.live}")
  private boolean SERVER_LIVE;

  @GetMapping
  @Override
  public ResponseEntity<AppVersionResponse> version() {
    AppVersionResponse response = AppVersionResponse.of(MIN_VERSION, LATEST_VERSION, SERVER_LIVE);
    return ResponseEntity.ok(response);
  }
}


@Schema(description = "앱 버전 및 서버 상태 정보")
record AppVersionResponse(
    @Schema(description = "지원하는 최소 앱 버전", example = "1.2.0")
    String minVersion,

    @Schema(description = "배포된 최신 앱 버전", example = "1.5.3")
    String latestVersion,

    @Schema(description = "서버 점검 여부 (true면 서비스 이용 가능)", example = "true")
    boolean serverLive
) {

  public static AppVersionResponse of(String minVersion, String latestVersion, boolean serverLive) {
    return new AppVersionResponse(minVersion, latestVersion, serverLive);
  }
}

