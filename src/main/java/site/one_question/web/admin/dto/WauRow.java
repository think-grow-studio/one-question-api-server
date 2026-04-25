package site.one_question.web.admin.dto;

import java.time.LocalDate;

public record WauRow(Long memberId, String fullName, LocalDate joinedDate, Long answerCount) {}
