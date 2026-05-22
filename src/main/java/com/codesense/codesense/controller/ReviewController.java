package com.codesense.codesense.controller;

import com.codesense.codesense.model.CodeReview;
import com.codesense.codesense.service.CodeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final CodeReviewService codeReviewService;

    /**
     * Full structured code review.
     *
     * curl -X POST http://localhost:8080/api/review \
     *   -H "Content-Type: application/json" \
     *   -d '{"code":"public void save(String input){repo.save(input);}","context":"Spring Data JPA service"}'
     */
    @PostMapping
    public ResponseEntity<CodeReview> review(@RequestBody ReviewRequest req) {
        CodeReview review = codeReviewService.review(req.code(), req.context());
        return ResponseEntity.ok(review);
    }

    /**
     * Quick merge-check — is this code safe to ship?
     *
     * curl -X POST http://localhost:8080/api/review/quick \
     *   -H "Content-Type: application/json" \
     *   -d '{"code":"..."}'
     */
    @PostMapping("/quick")
    public ResponseEntity<String> quickCheck(@RequestBody ReviewRequest req) {
        String verdict = codeReviewService.quickCheck(req.code());
        return ResponseEntity.ok(verdict);
    }

    record ReviewRequest(String code, String context) {}
}