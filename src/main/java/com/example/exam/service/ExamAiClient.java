package com.example.exam.service;

import com.example.exam.dto.ExamAiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExamAiClient {

    private final RestClient restClient;

    public ExamAiClient(@Value("${exam-ai.base-url:http://localhost:5001}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ExamAiResponse processPdf(MultipartFile file, String geminiApiKey) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("pdf_file", resource);

            if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                body.add("gemini_api_key", geminiApiKey);
            }

            return restClient.post()
                    .uri("/process-pdf")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ExamAiResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process PDF with exam-ai service: " + e.getMessage(), e);
        }
    }
}
