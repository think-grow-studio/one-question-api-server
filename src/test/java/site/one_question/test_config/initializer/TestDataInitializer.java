package site.one_question.test_config.initializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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
            .map(dto -> Question.create(
                dto.text(),
                dto.description(),
                "ko-KR",
                Integer.parseInt(dto.id())
            ))
            .toList();

        questionRepository.saveAll(questions);
    }
}
