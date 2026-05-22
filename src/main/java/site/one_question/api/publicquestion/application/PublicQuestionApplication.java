package site.one_question.api.publicquestion.application;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerService;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionService;
import site.one_question.api.publicquestion.presentation.response.CreatePublicDailyQuestionAnswerResponse;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionResponse;
import site.one_question.api.publicquestion.presentation.response.UpdatePublicDailyQuestionAnswerResponse;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicQuestionApplication {

    private final MemberService memberService;
    private final PublicDailyQuestionService publicDailyQuestionService;
    private final PublicDailyQuestionAnswerService publicDailyQuestionAnswerService;

    public GetPublicDailyQuestionResponse getPublicDailyQuestion(Long memberId, LocalDate date) {
        Member member = memberService.findById(memberId);
        PublicDailyQuestion pdq = publicDailyQuestionService.findByDateAndMember(date, member);
        PublicDailyQuestionAnswer answer = publicDailyQuestionAnswerService.findBy(pdq, member).orElse(null);
        return GetPublicDailyQuestionResponse.from(pdq, answer);
    }

    @Transactional
    public CreatePublicDailyQuestionAnswerResponse createAnswer(
            Long memberId,
            Long pdqId,
            String content,
            String timezone
    ) {
        Member member = memberService.findById(memberId);
        PublicDailyQuestion pdq = publicDailyQuestionService.findById(pdqId);

        publicDailyQuestionAnswerService.validateAnswerNotExists(pdq, member);
        PublicDailyQuestionAnswer saved = publicDailyQuestionAnswerService.createAndSave(pdq, member, content, timezone);

        return CreatePublicDailyQuestionAnswerResponse.from(saved, timezone);
    }

    @Transactional
    public UpdatePublicDailyQuestionAnswerResponse updateAnswer(
            Long memberId,
            Long pdqId,
            String content,
            String timezone
    ) {
        Member member = memberService.findById(memberId);
        PublicDailyQuestion pdq = publicDailyQuestionService.findById(pdqId);
        PublicDailyQuestionAnswer answer = publicDailyQuestionAnswerService.update(pdq, member, content);
        return UpdatePublicDailyQuestionAnswerResponse.from(answer, timezone);
    }
}
