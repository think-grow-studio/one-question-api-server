package site.one_question.api.question.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.question.domain.exception.QuestionNotFoundException;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    public Question findByIdOrThrow(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(QuestionNotFoundException::new);
    }
}
