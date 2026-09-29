package com.careerpilot.rag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeService.UploadResult upload(@RequestParam("file") MultipartFile file) {
        return knowledgeService.upload(file);
    }

    @GetMapping("/status")
    public KnowledgeService.KnowledgeStatus status() {
        return knowledgeService.status();
    }

    @DeleteMapping
    public KnowledgeService.KnowledgeStatus clear() {
        return knowledgeService.clear();
    }
}
