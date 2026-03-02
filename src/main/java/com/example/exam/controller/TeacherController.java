package com.example.exam.controller;

import com.example.exam.dto.CreateExamRequest;
import com.example.exam.dto.ImportJsonRequest;
import com.example.exam.entity.*;
import com.example.exam.service.ExamService;
import com.example.exam.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final QuestionBankService questionBankService;
    private final ExamService examService;

    // ==================== Question Sets ====================

    /**
     * Import questions by uploading PDF → calls exam-ai → saves as question set.
     */
    @PostMapping("/question-sets/import-pdf")
    public ResponseEntity<?> importFromPdf(
            @AuthenticationPrincipal User teacher,
            @RequestParam("file") MultipartFile file,
            @RequestParam("setName") String setName,
            @RequestParam(value = "geminiApiKey", required = false) String geminiApiKey) {
        QuestionSet set = questionBankService.importFromPdf(teacher, setName, file, geminiApiKey);
        return ResponseEntity.ok(mapQuestionSet(set));
    }

    /**
     * Import questions from JSON (exam-ai output format) directly.
     */
    @PostMapping("/question-sets/import-json")
    public ResponseEntity<?> importFromJson(
            @AuthenticationPrincipal User teacher,
            @RequestBody ImportJsonRequest request) {
        QuestionSet set = questionBankService.importFromJson(teacher, request.setName(), request.data());
        return ResponseEntity.ok(mapQuestionSet(set));
    }

    @GetMapping("/question-sets")
    public ResponseEntity<?> getQuestionSets(@AuthenticationPrincipal User teacher) {
        List<QuestionSet> sets = questionBankService.getTeacherSets(teacher.getId());
        return ResponseEntity.ok(sets.stream().map(this::mapQuestionSetSummary).toList());
    }

    @GetMapping("/question-sets/{id}")
    public ResponseEntity<?> getQuestionSet(@AuthenticationPrincipal User teacher,
                                            @PathVariable Long id) {
        QuestionSet set = questionBankService.getSetById(id);
        if (!set.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        return ResponseEntity.ok(mapQuestionSet(set));
    }

    @DeleteMapping("/question-sets/{id}")
    public ResponseEntity<?> deleteQuestionSet(@AuthenticationPrincipal User teacher,
                                               @PathVariable Long id) {
        questionBankService.deleteSet(id, teacher.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Exams ====================

    /**
     * Create an exam by selecting question IDs from the question bank.
     */
    @PostMapping("/exams")
    public ResponseEntity<?> createExam(@AuthenticationPrincipal User teacher,
                                        @RequestBody CreateExamRequest request) {
        Exam exam = examService.createExam(teacher, request);
        return ResponseEntity.ok(mapExam(exam));
    }

    @GetMapping("/exams")
    public ResponseEntity<?> getExams(@AuthenticationPrincipal User teacher) {
        List<Exam> exams = examService.getTeacherExams(teacher.getId());
        return ResponseEntity.ok(exams.stream().map(this::mapExamSummary).toList());
    }

    @GetMapping("/exams/{id}")
    public ResponseEntity<?> getExam(@AuthenticationPrincipal User teacher,
                                     @PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        if (!exam.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        Map<String, Object> result = mapExam(exam);
        // Include attempt statistics
        List<ExamAttempt> attempts = examService.getExamAttempts(id);
        result.put("totalAttempts", attempts.size());
        result.put("attempts", attempts.stream().map(this::mapAttemptSummary).toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/exams/{id}")
    public ResponseEntity<?> deleteExam(@AuthenticationPrincipal User teacher,
                                        @PathVariable Long id) {
        examService.deleteExam(id, teacher.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Response Mappers ====================

    private Map<String, Object> mapQuestionSetSummary(QuestionSet set) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", set.getId());
        map.put("name", set.getName());
        map.put("questionCount", set.getQuestions().size());
        map.put("createdAt", set.getCreatedAt());
        return map;
    }

    private Map<String, Object> mapQuestionSet(QuestionSet set) {
        Map<String, Object> map = mapQuestionSetSummary(set);
        map.put("questions", set.getQuestions().stream().map(this::mapQuestion).toList());
        return map;
    }

    private Map<String, Object> mapQuestion(Question q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", q.getId());
        map.put("questionNumber", q.getQuestionNumber());
        map.put("pageNumber", q.getPageNumber());
        map.put("questionText", q.getQuestionText());
        map.put("options", q.getOptions());
        map.put("correctAnswer", q.getCorrectAnswer());
        map.put("answerExplanation", q.getAnswerExplanation());
        map.put("imageUrl", q.getImageUrl());
        map.put("hasSharedReference", q.isHasSharedReference());
        map.put("sharedReferenceId", q.getSharedReferenceId());
        map.put("hasVisualOptions", q.isHasVisualOptions());
        return map;
    }

    private Map<String, Object> mapExamSummary(Exam exam) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", exam.getId());
        map.put("title", exam.getTitle());
        map.put("description", exam.getDescription());
        map.put("durationMinutes", exam.getDurationMinutes());
        map.put("questionCount", exam.getExamQuestions().size());
        map.put("createdAt", exam.getCreatedAt());
        return map;
    }

    private Map<String, Object> mapExam(Exam exam) {
        Map<String, Object> map = mapExamSummary(exam);
        map.put("questions", exam.getExamQuestions().stream().map(eq -> {
            Map<String, Object> qmap = mapQuestion(eq.getQuestion());
            qmap.put("examQuestionId", eq.getId());
            qmap.put("questionOrder", eq.getQuestionOrder());
            return qmap;
        }).toList());
        return map;
    }

    private Map<String, Object> mapAttemptSummary(ExamAttempt attempt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", attempt.getId());
        map.put("studentName", attempt.getStudent().getUsername());
        map.put("score", attempt.getScore());
        map.put("totalQuestions", attempt.getTotalQuestions());
        map.put("submittedAt", attempt.getSubmittedAt());
        return map;
    }
}
