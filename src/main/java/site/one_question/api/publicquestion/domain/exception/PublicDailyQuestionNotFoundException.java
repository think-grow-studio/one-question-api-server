package site.one_question.api.publicquestion.domain.exception;

import java.time.LocalDate;
import java.util.Map;

public class PublicDailyQuestionNotFoundException extends PublicQuestionException {

    public PublicDailyQuestionNotFoundException(LocalDate date, String locale) {
        super(PublicQuestionExceptionSpec.PDQ_NOT_FOUND, Map.of("date", date, "locale", locale));
    }

    public PublicDailyQuestionNotFoundException(Long pdqId) {
        super(PublicQuestionExceptionSpec.PDQ_NOT_FOUND, Map.of("pdqId", pdqId));
    }
}
