package site.one_question.api.publicquestion.domain;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;
import site.one_question.api.publicquestion.domain.exception.PublicDailyQuestionNotFoundException;

@Service
@RequiredArgsConstructor
public class PublicDailyQuestionService {

    private final PublicDailyQuestionRepository publicDailyQuestionRepository;

    public PublicDailyQuestion findByDateAndMember(LocalDate date, Member member) {
        String locale = member.getLocale();
        return publicDailyQuestionRepository
                .findByQuestionDateAndLocale(date, locale)
                .orElseThrow(() -> new PublicDailyQuestionNotFoundException(date, locale));
    }

    public PublicDailyQuestion findById(Long pdqId) {
        return publicDailyQuestionRepository.findById(pdqId)
                .orElseThrow(() -> new PublicDailyQuestionNotFoundException(pdqId));
    }
}
