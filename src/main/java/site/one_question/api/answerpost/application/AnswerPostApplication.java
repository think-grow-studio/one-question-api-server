package site.one_question.api.answerpost.application;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostLike;
import site.one_question.api.answerpost.domain.AnswerPostLikeService;
import site.one_question.api.answerpost.domain.AnswerPostService;
import site.one_question.api.answerpost.domain.exception.AnswerPostNotOwnedException;
import site.one_question.api.answerpost.presentation.response.AnswerPostFeedItemDto;
import site.one_question.api.answerpost.presentation.response.AnswerPostFeedResponse;

import site.one_question.api.answerpost.presentation.response.ToggleLikeResponse;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.DailyQuestionAnswerService;

@Service
@Transactional
@RequiredArgsConstructor
public class AnswerPostApplication {

    private final AnswerPostService answerPostService;
    private final AnswerPostLikeService answerPostLikeService;
    private final DailyQuestionAnswerService questionAnswerService;
    private final MemberService memberService;
    public void publish(Long memberId, Long questionAnswerId) {
        DailyQuestionAnswer questionAnswer = questionAnswerService.findById(questionAnswerId);

        if (!questionAnswer.isOwnedBy(memberId)) {
            throw new AnswerPostNotOwnedException(questionAnswerId);
        }

        var existing = answerPostService.findByQuestionAnswer(questionAnswer);

        if (existing.isPresent()) {
            answerPostService.republish(existing.get());
            return;
        }

        Member member = memberService.findById(memberId);
        answerPostService.publish(questionAnswer, member);
    }

    public void unpublish(Long memberId, Long answerPostId) {
        AnswerPost answerPost = answerPostService.findByIdOrThrow(answerPostId);

        if (!answerPost.isOwnedBy(memberId)) {
            throw new AnswerPostNotOwnedException(answerPostId);
        }

        answerPost.unpublish();
    }

    @Transactional(readOnly = true)
    public AnswerPostFeedResponse getFeed(Long memberId, Instant cursor, int size, String timezone) {
        if (cursor == null) {
            cursor = Instant.now();
        }

        Member member = memberService.findById(memberId);
        List<AnswerPost> posts = answerPostService.getFeed(cursor, size + 1); // 원하는 size 보다 1 크게 하여 next 유무 판단

        boolean hasNext = posts.size() > size;
        List<AnswerPost> feedPosts = hasNext ? posts.subList(0, size) : posts;

        List<AnswerPostFeedItemDto> items = feedPosts.stream()
                .map(post -> {
                    long likeCount = answerPostLikeService.countByAnswerPost(post); // N + 1 개선 필요
                    boolean liked = answerPostLikeService.existsByAnswerPostAndMember(post, member);
                    boolean mine = post.isOwnedBy(memberId);
                    return AnswerPostFeedItemDto.from(post, timezone, likeCount, liked, mine);
                })
                .toList();

        Instant nextCursor = hasNext ? feedPosts.getLast().getPostedAt() : null;

        return new AnswerPostFeedResponse(items, hasNext, nextCursor);
    }

    public ToggleLikeResponse toggleLike(Long memberId, Long answerPostId) {
        AnswerPost answerPost = answerPostService.findByIdOrThrow(answerPostId);
        Member member = memberService.findById(memberId);

        var existingLike = answerPostLikeService.findByAnswerPostAndMember(answerPost, member);

        boolean liked;
        if (existingLike.isPresent()) {
            answerPostLikeService.delete(existingLike.get());
            liked = false;
        } else {
            answerPostLikeService.save(AnswerPostLike.create(answerPost, member));
            liked = true;
        }

        return new ToggleLikeResponse(liked);
    }
}
