package site.one_question.test_config.initializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionRepository;
import site.one_question.question.domain.QuestionStatus;

@Component
@RequiredArgsConstructor
public class TestDataInitializer {

    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @EventListener(ContextRefreshedEvent.class)
    public void loadQuestions() throws IOException {
        if (questionRepository.count() > 0) {
            return; // 이미 로드됨
        }

        InputStream inputStream = getClass().getResourceAsStream("/questions/question_kr.json");
        List<QuestionJsonDto> dtos = objectMapper.readValue(
            inputStream,
            new TypeReference<List<QuestionJsonDto>>() {}
        );

        List<Question> questions = dtos.stream()
            .map(this::createQuestion)
            .toList();

        questionRepository.saveAll(questions);
    }

    private Question createQuestion(QuestionJsonDto dto) {
        Question question = BeanUtils.instantiateClass(Question.class);
        ReflectionTestUtils.setField(question, "content", dto.text());
        ReflectionTestUtils.setField(question, "description", dto.description());
        ReflectionTestUtils.setField(question, "locale", "ko-KR");
        ReflectionTestUtils.setField(question, "status", QuestionStatus.ACTIVE);
        ReflectionTestUtils.setField(question, "number", Integer.parseInt(dto.id()));
        return question;
    }
}
