package com.example.exam.controller;

import com.example.exam.dto.SubmitExamRequest;
import com.example.exam.entity.*;
import com.example.exam.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final ExamService examService;

    /**
     * List all available exams (gigs).
     */
    @GetMapping("/exams")
    public ResponseEntity<?> getAvailableExams() {
        List<Exam> exams = examService.getAllExams();
        return ResponseEntity.ok(exams.stream().map(this::mapExamForStudent).toList());
    }

    /**
     * Get exam details with questions (without correct answers).
     */
    @GetMapping("/exams/{id}")
    public ResponseEntity<?> getExam(@PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        Map<String, Object> result = mapExamForStudent(exam);
        // Include questions WITHOUT correct answers
        result.put("questions", exam.getExamQuestions().stream().map(eq -> {
            Map<String, Object> qmap = new LinkedHashMap<>();
            qmap.put("examQuestionId", eq.getId());
            qmap.put("questionOrder", eq.getQuestionOrder());
            qmap.put("questionNumber", eq.getQuestion().getQuestionNumber());
            qmap.put("questionText", eq.getQuestion().getQuestionText());
            qmap.put("options", eq.getQuestion().getOptions());
            qmap.put("imageUrl", eq.getQuestion().getImageUrl());
            qmap.put("hasSharedReference", eq.getQuestion().isHasSharedReference());
            qmap.put("sharedReferenceId", eq.getQuestion().getSharedReferenceId());
            qmap.put("hasVisualOptions", eq.getQuestion().isHasVisualOptions());
            return qmap;
        }).toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Submit exam answers and get score.
     */
    @PostMapping("/exams/{id}/submit")
    public ResponseEntity<?> submitExam(
            @AuthenticationPrincipal User student,
            @PathVariable Long id,
            @RequestBody SubmitExamRequest request) {
        ExamAttempt attempt = examService.submitExam(student, id, request);
        return ResponseEntity.ok(mapAttemptResult(attempt));
    }

    /**
     * List all my past attempts.
     */
    @GetMapping("/attempts")
    public ResponseEntity<?> getMyAttempts(@AuthenticationPrincipal User student) {
        List<ExamAttempt> attempts = examService.getStudentAttempts(student.getId());
        return ResponseEntity.ok(attempts.stream().map(this::mapAttemptSummary).toList());
    }

    /**
     * Get detailed result of a specific attempt.
     */
    @GetMapping("/attempts/{id}")
    public ResponseEntity<?> getAttempt(@AuthenticationPrincipal User student,
                                        @PathVariable Long id) {
        ExamAttempt attempt = examService.getAttemptById(id);
        if (!attempt.getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(mapAttemptResult(attempt));
    }

    // ==================== Response Mappers ====================

    private Map<String, Object> mapExamForStudent(Exam exam) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exam.getId());
        map.put("title", exam.getTitle());
        map.put("description", exam.getDescription());
        map.put("teacherName", exam.getTeacher().getUsername());
        map.put("durationMinutes", exam.getDurationMinutes());
        map.put("questionCount", exam.getExamQuestions().size());
        map.put("createdAt", exam.getCreatedAt());
        return map;
    }

    private Map<String, Object> mapAttemptSummary(ExamAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", attempt.getId());
        map.put("examId", attempt.getExam().getId());
        map.put("examTitle", attempt.getExam().getTitle());
        map.put("score", attempt.getScore());
        map.put("totalQuestions", attempt.getTotalQuestions());
        map.put("percentage", attempt.getTotalQuestions() > 0
                ? Math.round(attempt.getScore() * 100.0 / attempt.getTotalQuestions() * 100.0) / 100.0
                : 0);
        map.put("submittedAt", attempt.getSubmittedAt());
        return map;
    }

    private Map<String, Object> mapAttemptResult(ExamAttempt attempt) {
        Map<String, Object> map = mapAttemptSummary(attempt);
        map.put("answers", attempt.getAnswers().stream().map(a -> {
            Map<String, Object> amap = new LinkedHashMap<>();
            amap.put("examQuestionId", a.getExamQuestion().getId());
            amap.put("questionText", a.getExamQuestion().getQuestion().getQuestionText());
            amap.put("options", a.getExamQuestion().getQuestion().getOptions());
            amap.put("selectedAnswer", a.getSelectedAnswer());
            amap.put("correctAnswer", a.getExamQuestion().getQuestion().getCorrectAnswer());
            amap.put("correct", a.isCorrect());
            amap.put("explanation", a.getExamQuestion().getQuestion().getAnswerExplanation());
            return amap;
        }).toList());
        return map;
    }
}
