package site.one_question.web.admin.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AnswerDateRow(Instant answeredAt, LocalDate joinedDate) {}
