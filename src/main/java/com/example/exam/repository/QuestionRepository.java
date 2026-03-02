package com.example.exam.repository;

import com.example.exam.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuestionSetId(Long questionSetId);
    List<Question> findByIdIn(List<Long> ids);
}
