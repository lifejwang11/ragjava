package com.life.rag.controller;

import com.life.rag.entity.Question;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("question")
public class QuestionController {

    @Autowired
    VectorStore vectorStore;

    @RequestMapping("/add")
    @ResponseBody
    public String addQuestion(@RequestBody Question question){
        List <Document> documents = List.of(
                new Document(
                        question.getQuestion(), Map.of(question.getQuestion(), question.getAnswer())));
        // Add the documents to Qdrant
        vectorStore.add(documents);
        return documents.get(0).getId();
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(@RequestBody List<String> ids){
        vectorStore.delete(ids);
        return "true";
    }


}
