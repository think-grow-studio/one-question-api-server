package site.one_question.global.api.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;

@Tag(name = "Health", description = "서버 상태 확인 API")
public interface HealthCheckApi {

    @Operation(
            summary = "서버 헬스체크",
            description = "서버가 정상 운영 중인지 확인합니다. 배포 후 자동 헬스체크에 사용됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "서버 정상 운영 중",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "status": "healthy"
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<Map<String, String>> healthCheck();
}
