package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionRepository;
import site.one_question.question.domain.QuestionStatus;

@Component
@RequiredArgsConstructor
public class TestQuestionUtils {

    private final QuestionRepository questionRepository;
    private static int uniqueId = 0;

    public Question createSave() {
        Question question = createQuestion(
                "테스트 질문 내용 " + uniqueId,
                "테스트 질문 설명 " + uniqueId,
                "ko-KR",
                uniqueId++
        );
        return questionRepository.save(question);
    }

    public Question createSave_With_Content(String content) {
        Question question = createQuestion(
                content,
                "테스트 질문 설명 " + uniqueId,
                "ko-KR",
                uniqueId++
        );
        return questionRepository.save(question);
    }

    public Question createSave_With_Locale(String locale) {
        Question question = createQuestion(
                "테스트 질문 내용 " + uniqueId,
                "테스트 질문 설명 " + uniqueId,
                locale,
                uniqueId++
        );
        return questionRepository.save(question);
    }

    private Question createQuestion(String content, String description, String locale, int number) {
        Question question = BeanUtils.instantiateClass(Question.class);
        ReflectionTestUtils.setField(question, "content", content);
        ReflectionTestUtils.setField(question, "description", description);
        ReflectionTestUtils.setField(question, "locale", locale);
        ReflectionTestUtils.setField(question, "status", QuestionStatus.ACTIVE);
        ReflectionTestUtils.setField(question, "number", number);
        return question;
    }
}
