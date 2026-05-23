package site.one_question.api.publicquestion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
        validateAnswerNotExists(pdq, member);
        PublicDailyQuestionAnswer answer = PublicDailyQuestionAnswer.create(pdq, member, content, timezone);
        return publicDailyQuestionAnswerRepository.save(answer);
    }

    private void validateAnswerNotExists(PublicDailyQuestion pdq, Member member) {
        boolean exists = publicDailyQuestionAnswerRepository
                .existsByPublicDailyQuestionAndMember(pdq, member);
        if (exists) {
            throw new PublicDailyQuestionAnswerAlreadyExistsException(pdq.getId(), member.getId());
        }
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

    public void deleteByMemberId(Long memberId) {
        publicDailyQuestionAnswerRepository.deleteByMemberId(memberId);
    }

    public List<PublicDailyQuestionAnswer> findFeed(
            Long pdqId, Long excludeMemberId, Instant cursorAnsweredAt, Long cursorId, int limit) {
        return publicDailyQuestionAnswerRepository.findFeed(
                pdqId, excludeMemberId, cursorAnsweredAt, cursorId, PageRequest.of(0, limit));
    }
}
