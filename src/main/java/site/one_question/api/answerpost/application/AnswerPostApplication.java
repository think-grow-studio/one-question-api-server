package site.one_question.api.answerpost.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostLike;
import site.one_question.api.answerpost.domain.AnswerPostLikeService;
import site.one_question.api.answerpost.domain.AnswerPostService;
import site.one_question.api.answerpost.presentation.response.AnswerPostFeedItemDto;
import site.one_question.api.answerpost.presentation.response.AnswerPostFeedResponse;

import site.one_question.api.answerpost.presentation.response.ToggleLikeResponse;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;

@Service
@Transactional
@RequiredArgsConstructor
public class AnswerPostApplication {

    private final AnswerPostService answerPostService;
    private final AnswerPostLikeService answerPostLikeService;
    private final MemberService memberService;

    @Transactional(readOnly = true)
    public AnswerPostFeedResponse getFeed(Long memberId, Instant cursor, int size, String timezone) {
        if (cursor == null) {
            cursor = Instant.now();
        }

        Member member = memberService.findById(memberId);
        List<AnswerPost> posts = answerPostService.getFeed(cursor, size + 1); // 원하는 size 보다 1 크게 하여 next 유무 판단

        boolean hasNext = posts.size() > size;
        List<AnswerPost> feedPosts = hasNext ? posts.subList(0, size) : posts;

        List<Long> postIds = feedPosts.stream().map(AnswerPost::getId).toList();
        Map<Long, Long> likeCounts = answerPostLikeService.countByAnswerPostIds(postIds); // <PostId,LikeCount>
        Set<Long> likedPostIds = answerPostLikeService.findLikedPostIdsByMember(postIds, memberId);

        List<AnswerPostFeedItemDto> items = feedPosts.stream()
                .map(post -> {
                    long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
                    boolean liked = likedPostIds.contains(post.getId());
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
