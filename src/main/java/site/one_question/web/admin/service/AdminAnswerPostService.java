package site.one_question.web.admin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.member.domain.AuthSocialProvider;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.web.admin.repository.AdminAnswerPostRepository;
import site.one_question.web.admin.repository.AdminDailyQuestionAnswerRepository;
import site.one_question.web.admin.repository.AdminDailyQuestionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnswerPostService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminDailyQuestionRepository adminDailyQuestionRepository;
    private final AdminDailyQuestionAnswerRepository adminAnswerRepository;
    private final AdminAnswerPostRepository adminAnswerPostRepository;

    public Map<LocalDate, List<AiPersonaAnswerPostRow>> getAiPersonaAnswerPosts(
            LocalDate startDate, LocalDate endDate) {

        List<DailyQuestion> dailyQuestions =
                adminDailyQuestionRepository.findAllByProviderAndDateBetween(
                        AuthSocialProvider.AI_PERSONA, startDate, endDate);

        return dailyQuestions.stream()
                .map(dq -> {
                    DailyQuestionAnswer answer = dq.getAnswer();
                    String postStatus = "NO_ANSWER";
                    LocalDateTime postedAt = null;

                    if (answer != null) {
                        AnswerPost post = answer.getAnswerPost();
                        if (post != null) {
                            postStatus = post.getStatus().name();
                            postedAt = post.getPostedAt() != null
                                    ? LocalDateTime.ofInstant(post.getPostedAt(), KST) : null;
                        } else {
                            postStatus = "NOT_POSTED";
                        }
                    }

                    return new AiPersonaAnswerPostRow(
                            answer != null ? answer.getId() : null,
                            dq.getQuestionDate(),
                            dq.getMember().getFullName(),
                            dq.getQuestion().getContent(),
                            answer != null ? answer.getContent() : null,
                            postStatus,
                            postedAt
                    );
                })
                .sorted(Comparator.comparing(AiPersonaAnswerPostRow::fullName))
                .collect(Collectors.groupingBy(
                        AiPersonaAnswerPostRow::questionDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    @Transactional
    public void publishAiPersonaAnswer(Long answerId, Instant postedAt) {
        DailyQuestionAnswer answer = adminAnswerRepository.findById(answerId)
                .orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다. id=" + answerId));
        var existing = adminAnswerPostRepository.findByQuestionAnswer(answer);

        if (existing.isPresent()) {
            AnswerPost post = existing.get();
            post.publishWithCustomTime(postedAt != null ? postedAt : Instant.now());
        } else {
            AnswerPost post = AnswerPost.createPublish(answer, answer.getMember());
            if (postedAt != null) {
                post.publishWithCustomTime(postedAt);
            }
            adminAnswerPostRepository.save(post);
        }
    }

    @Transactional
    public void unpublishAiPersonaAnswer(Long answerId) {
        DailyQuestionAnswer answer = adminAnswerRepository.findById(answerId)
                .orElseThrow(() -> new IllegalArgumentException("답변을 찾을 수 없습니다. id=" + answerId));
        adminAnswerPostRepository.findByQuestionAnswer(answer)
                .ifPresent(AnswerPost::unpublish);
    }

    public record AiPersonaAnswerPostRow(
            Long answerId,
            LocalDate questionDate,
            String fullName,
            String questionContent,
            String answerContent,
            String postStatus,
            LocalDateTime postedAt
    ) {}
}
