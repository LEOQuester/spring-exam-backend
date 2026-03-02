package com.example.exam.service;

import com.example.exam.dto.ExamAiResponse;
import com.example.exam.entity.Question;
import com.example.exam.entity.QuestionSet;
import com.example.exam.entity.User;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.repository.QuestionSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final ExamAiClient examAiClient;

    @Transactional
    public QuestionSet importFromPdf(User teacher, String setName, MultipartFile pdfFile, String geminiApiKey) {
        ExamAiResponse response = examAiClient.processPdf(pdfFile, geminiApiKey);
        if (!response.success()) {
            throw new RuntimeException("Exam AI failed to process the PDF");
        }
        return saveQuestionSet(teacher, setName, response);
    }

    @Transactional
    public QuestionSet importFromJson(User teacher, String setName, ExamAiResponse response) {
        if (!response.success() || response.questions() == null) {
            throw new RuntimeException("Invalid question data");
        }
        return saveQuestionSet(teacher, setName, response);
    }

    private QuestionSet saveQuestionSet(User teacher, String setName, ExamAiResponse response) {
        QuestionSet questionSet = QuestionSet.builder()
                .name(setName)
                .teacher(teacher)
                .build();
        questionSetRepository.save(questionSet);

        List<Question> questions = new ArrayList<>();
        for (ExamAiResponse.ExamAiQuestion q : response.questions()) {
            Question question = Question.builder()
                    .questionSet(questionSet)
                    .questionNumber(q.questionNumber())
                    .pageNumber(q.pageNumber())
                    .questionText(q.questionText())
                    .options(new ArrayList<>(q.options()))
                    .correctAnswer(q.correctAnswer())
                    .answerExplanation(q.answerExplanation())
                    .hasSharedReference(q.hasSharedReference())
                    .sharedReferenceId(q.sharedReferenceId())
                    .imageUrl(q.imageUrl())
                    .hasVisualOptions(q.hasVisualOptions())
                    .build();
            questions.add(question);
        }
        questionRepository.saveAll(questions);
        questionSet.setQuestions(questions);
        return questionSet;
    }

    @Transactional(readOnly = true)
    public List<QuestionSet> getTeacherSets(Long teacherId) {
        return questionSetRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public QuestionSet getSetById(Long id) {
        return questionSetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question set not found"));
    }

    @Transactional
    public void deleteSet(Long id, Long teacherId) {
        QuestionSet set = questionSetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question set not found"));
        if (!set.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Not authorized to delete this set");
        }
        questionSetRepository.delete(set);
    }
}
