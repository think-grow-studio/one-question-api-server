package site.one_question.api.publicquestion.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;
import site.one_question.api.publicquestion.domain.exception.PublicDailyQuestionAnswerAlreadyExistsException;
import site.one_question.api.publicquestion.domain.exception.PublicDailyQuestionAnswerNotFoundException;

@Service
@RequiredArgsConstructor
public class PublicDailyQuestionAnswerService {

    private final PublicDailyQuestionAnswerRepository publicDailyQuestionAnswerRepository;

    public PublicDailyQuestionAnswer createAndSave(
            PublicDailyQuestion pdq,
            Member member,
            String content,
            String timezone
    ) {
        PublicDailyQuestionAnswer answer = PublicDailyQuestionAnswer.create(pdq, member, content, timezone);
        return publicDailyQuestionAnswerRepository.save(answer);
    }

    public void validateAnswerNotExists(PublicDailyQuestion pdq, Member member) {
        if (existsBy(pdq, member)) {
            throw new PublicDailyQuestionAnswerAlreadyExistsException(pdq.getId(), member.getId());
        }
    }

    public boolean existsBy(PublicDailyQuestion publicDailyQuestion, Member member) {
        return publicDailyQuestionAnswerRepository.existsByPublicDailyQuestionAndMember(publicDailyQuestion, member);
    }

    public Optional<PublicDailyQuestionAnswer> findBy(PublicDailyQuestion publicDailyQuestion, Member member) {
        return publicDailyQuestionAnswerRepository.findByPublicDailyQuestionAndMember(publicDailyQuestion, member);
    }

    public PublicDailyQuestionAnswer findByIdOrThrow(Long answerId) {
        return publicDailyQuestionAnswerRepository.findById(answerId)
                .orElseThrow(() -> new PublicDailyQuestionAnswerNotFoundException(answerId));
    }

    public PublicDailyQuestionAnswer findByIdAndPdqIdOrThrow(Long answerId, Long pdqId) {
        PublicDailyQuestionAnswer answer = findByIdOrThrow(answerId);
        if (!answer.getPublicDailyQuestion().getId().equals(pdqId)) {
            throw new PublicDailyQuestionAnswerNotFoundException(answerId);
        }
        return answer;
    }

    public PublicDailyQuestionAnswer findOwnedByIdAndPdqIdOrThrow(Long answerId, Long pdqId, Long memberId) {
        PublicDailyQuestionAnswer answer = findByIdAndPdqIdOrThrow(answerId, pdqId);
        if (!answer.isOwnedBy(memberId)) {
            throw new PublicDailyQuestionAnswerNotFoundException(answerId);
        }
        return answer;
    }

    public void delete(PublicDailyQuestionAnswer answer) {
        publicDailyQuestionAnswerRepository.delete(answer);
    }
}
