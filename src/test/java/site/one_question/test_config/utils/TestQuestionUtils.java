package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionRepository;

@Component
@RequiredArgsConstructor
public class TestQuestionUtils {

    private final QuestionRepository questionRepository;
    private static int uniqueId = 0;

    public Question createSave() {
        Question question = Question.create(
                "테스트 질문 내용 " + uniqueId,
                "테스트 질문 설명 " + uniqueId,
                "ko-KR",
                uniqueId++
        );
        return questionRepository.save(question);
    }

    public Question createSave_With_Content(String content) {
        Question question = Question.create(
                content,
                "테스트 질문 설명 " + uniqueId,
                "ko-KR",
                uniqueId++
        );
        return questionRepository.save(question);
    }

    public Question createSave_With_Locale(String locale) {
        Question question = Question.create(
                "테스트 질문 내용 " + uniqueId,
                "테스트 질문 설명 " + uniqueId,
                locale,
                uniqueId++
        );
        return questionRepository.save(question);
    }
}
