package com.life.rag.controller;

import com.life.rag.entity.MessageVo;
import com.life.rag.service.SseEmitterService;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;


/**
 * SSE长链接
 */
@RestController
@RequestMapping("/sse")
public class SseEmitterController {

    @Resource
    private SseEmitterService sseEmitterService;

    @Resource
    VectorStore vectorStore;

    @CrossOrigin
    @GetMapping("chat")
    public SseEmitter test(String msg) {
        SseEmitter sseEmitter =sseEmitterService.createConnect("test");
        List<Document> results = vectorStore.similaritySearch(SearchRequest.query(msg).withTopK(3));
        new Thread(() -> sseEmitterService.clientCall("test",msg,results)).start();
        return sseEmitter;
    }

}

