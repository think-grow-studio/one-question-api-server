package site.one_question.api.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "후보 질문 cycle 중복 확인 응답")
public record CheckCandidateCycleResponse(
        @Schema(description = "같은 사이클 내 과거에 이미 배정된 적이 있는지 여부", example = "true")
        boolean alreadyAssignedInCycle,

        @Schema(description = "같은 사이클 내 과거 배정 날짜 목록", example = "[\"2026-04-07\"]")
        List<LocalDate> previouslyAssignedDates
) {
    public static CheckCandidateCycleResponse from(List<LocalDate> assignedDates) {
        return new CheckCandidateCycleResponse(!assignedDates.isEmpty(), assignedDates);
    }
}
