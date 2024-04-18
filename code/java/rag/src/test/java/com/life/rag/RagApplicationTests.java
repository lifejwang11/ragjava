package com.life.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class RagApplicationTests {

    @Autowired
    EmbeddingClient embeddingClient;

    @Autowired
    VectorStore vectorStore;

    @Test
    void contextLoads() {
        List<List<Double>> embeddings = embeddingClient.embed(List.of("Hello world"));
        System.out.println(embeddings);
    }

    @Test
    void test(){
        List <Document> documents = List.of(
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));

        // Add the documents to Qdrant
        vectorStore.add(documents);
    }
}
