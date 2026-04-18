package site.one_question.web.admin.dto;

import java.time.Instant;
import java.time.LocalDate;

public record LeaderboardRow(Long memberId, String fullName, LocalDate joinedDate, Long answerCount, Instant lastAnsweredAt) {}
