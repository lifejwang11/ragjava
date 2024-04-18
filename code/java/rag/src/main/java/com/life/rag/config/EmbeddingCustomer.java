package com.life.rag.config;

import cn.hutool.json.JSONUtil;
import com.life.rag.entity.RequestEmbedding;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingClient;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class EmbeddingCustomer extends AbstractEmbeddingClient {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Double> resultEmbeddings = new ArrayList<>();
        RequestEmbedding requestEmbedding = new RequestEmbedding();
        requestEmbedding.setSentence(request.getInstructions().get(0));
        try {
            resultEmbeddings = sendPostWithJson("http://47.100.229.242:5000/get_embedding",
                    JSONUtil.toJsonStr(requestEmbedding));
            var indexCounter = new AtomicInteger(0);
            return new EmbeddingResponse(
                    Collections.singletonList(new Embedding(resultEmbeddings, indexCounter.incrementAndGet())));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public List<Double> embed(Document document) {
        return this.embed(document.getFormattedContent(MetadataMode.NONE));
    }

    /**
     * HTTP接口-POST方式，请求参数形式为body-json形式
     *
     * @param url
     * @param jsonString
     * @return String
     */
    public static List sendPostWithJson(String url, String jsonString) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonString);
        Request request = new Request.Builder()
                .post(body)
                .url(url)
                .build();
        Call call = client.newCall(request);
        //返回请求结果
        try {
            Response response = call.execute();
            Map<String,List> map = JSONUtil.toBean(response.body().string(), Map.class);
            List<Double> doubles = new ArrayList<>();
            for (Object key : map.get("embedding")) {
                if (key instanceof BigDecimal) {
                    doubles.add(((BigDecimal) key).doubleValue());
                }
            }
            return doubles;
        } catch (IOException e) {
            throw new IOException(e);
        }
    }


}
