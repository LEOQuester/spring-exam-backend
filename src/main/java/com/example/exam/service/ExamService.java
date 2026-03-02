package com.example.exam.service;

import com.example.exam.dto.CreateExamRequest;
import com.example.exam.dto.SubmitExamRequest;
import com.example.exam.entity.*;
import com.example.exam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;

    @Transactional
    public Exam createExam(User teacher, CreateExamRequest request) {
        Exam exam = Exam.builder()
                .title(request.title())
                .description(request.description())
                .teacher(teacher)
                .durationMinutes(request.durationMinutes())
                .build();
        examRepository.save(exam);

        List<Question> questions = questionRepository.findByIdIn(request.questionIds());
        if (questions.size() != request.questionIds().size()) {
            throw new RuntimeException("Some questions were not found");
        }

        // Verify all questions belong to this teacher
        for (Question q : questions) {
            if (!q.getQuestionSet().getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Question " + q.getId() + " does not belong to you");
            }
        }

        List<ExamQuestion> examQuestions = new ArrayList<>();
        for (int i = 0; i < request.questionIds().size(); i++) {
            Long qId = request.questionIds().get(i);
            Question q = questions.stream()
                    .filter(x -> x.getId().equals(qId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Question not found"));

            ExamQuestion eq = ExamQuestion.builder()
                    .exam(exam)
                    .question(q)
                    .questionOrder(i + 1)
                    .build();
            examQuestions.add(eq);
        }
        examQuestionRepository.saveAll(examQuestions);
        exam.setExamQuestions(examQuestions);
        return exam;
    }

    @Transactional(readOnly = true)
    public List<Exam> getTeacherExams(Long teacherId) {
        return examRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public List<Exam> getAllExams() {
        return examRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Exam getExamById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
    }

    @Transactional
    public void deleteExam(Long id, Long teacherId) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        if (!exam.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("Not authorized to delete this exam");
        }
        examRepository.delete(exam);
    }

    @Transactional
    public ExamAttempt submitExam(User student, Long examId, SubmitExamRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);

        ExamAttempt attempt = ExamAttempt.builder()
                .exam(exam)
                .student(student)
                .totalQuestions(examQuestions.size())
                .submittedAt(LocalDateTime.now())
                .build();
        examAttemptRepository.save(attempt);

        int score = 0;
        List<StudentAnswer> answers = new ArrayList<>();

        for (SubmitExamRequest.AnswerSubmission sub : request.answers()) {
            ExamQuestion eq = examQuestions.stream()
                    .filter(x -> x.getId().equals(sub.examQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Exam question not found: " + sub.examQuestionId()));

            boolean isCorrect = eq.getQuestion().getCorrectAnswer()
                    .trim().equalsIgnoreCase(sub.selectedAnswer().trim());

            if (isCorrect) score++;

            StudentAnswer answer = StudentAnswer.builder()
                    .attempt(attempt)
                    .examQuestion(eq)
                    .selectedAnswer(sub.selectedAnswer())
                    .correct(isCorrect)
                    .build();
            answers.add(answer);
        }

        studentAnswerRepository.saveAll(answers);
        attempt.setScore(score);
        attempt.setAnswers(answers);
        examAttemptRepository.save(attempt);
        return attempt;
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getStudentAttempts(Long studentId) {
        return examAttemptRepository.findByStudentIdOrderByStartedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public ExamAttempt getAttemptById(Long id) {
        return examAttemptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attempt not found"));
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getExamAttempts(Long examId) {
        return examAttemptRepository.findByExamId(examId);
    }
}
