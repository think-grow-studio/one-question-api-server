package site.one_question.web.admin.dto;

import java.time.LocalDate;

public record PublicQuestionDailyCountRow(LocalDate date, Long count) {}
