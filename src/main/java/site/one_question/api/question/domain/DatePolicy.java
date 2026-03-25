package site.one_question.api.question.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import site.one_question.api.question.domain.exception.FutureDateQuestionException;

public class DatePolicy {

    public static void requireNotFuture(LocalDate date, String timezone) {
        LocalDate today = getToday(timezone);
        if (date.isAfter(today)) {
            throw new FutureDateQuestionException();
        }
    }

    public static LocalDate getToday(String timezone) {
        return LocalDate.now(ZoneId.of(timezone));
    }
}
