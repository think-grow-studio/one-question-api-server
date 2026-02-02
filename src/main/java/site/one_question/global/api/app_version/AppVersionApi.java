package site.one_question.global.api.app_version;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "App Version", description = "앱 버전 및 서버 상태 관련 API")
public interface AppVersionApi {

    @Operation(
            summary = "앱 버전 및 서버 상태 조회",
            description = "지원하는 최소 버전과 최신 버전을 제공하여 클라이언트가 업데이트 필요 여부를 결정할 수 있도록 합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "버전 정보 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppVersionResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                \"minVersion\": \"1.2.0\",
                                                \"latestVersion\": \"1.5.3\",
                                                \"serverLive\": true
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<AppVersionResponse> version();
}
