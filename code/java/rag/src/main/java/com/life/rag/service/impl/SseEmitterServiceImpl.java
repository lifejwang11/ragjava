package com.life.rag.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.life.rag.entity.RequestDto;
import com.life.rag.entity.MessageVo;
import com.life.rag.service.SseEmitterService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class SseEmitterServiceImpl implements SseEmitterService {

    /**
     * 容器，保存连接，用于输出返回 ;可使用其他方法实现
     */
    private static final Map<String, SseEmitter> sseCache = new ConcurrentHashMap<>();




    /**
     * 根据客户端id获取SseEmitter对象
     *
     * @param clientId 客户端ID
     */
    @Override
    public SseEmitter getSseEmitterByClientId(String clientId) {
        return sseCache.get(clientId);
    }


    /**
     * 创建连接
     *
     * @param clientId 客户端ID
     */
    @Override
    public SseEmitter createConnect(String clientId) {
        // 设置超时时间，0表示不过期。默认30秒，超过时间未完成会抛出异常：AsyncRequestTimeoutException
        SseEmitter sseEmitter = new SseEmitter(0L);
        // 是否需要给客户端推送ID
        if (StrUtil.isBlank(clientId)) {
            clientId = IdUtil.simpleUUID();
        }
        // 注册回调
        sseEmitter.onCompletion(completionCallBack(clientId));     // 长链接完成后回调接口(即关闭连接时调用)
        sseEmitter.onTimeout(timeoutCallBack(clientId));        // 连接超时回调
        sseEmitter.onError(errorCallBack(clientId));          // 推送消息异常时，回调方法
        sseCache.put(clientId, sseEmitter);
        log.info("创建新的sse连接，当前用户：{}    累计用户:{}", clientId, sseCache.size());
        try {
            // 注册成功返回用户信息
            sseEmitter.send(SseEmitter.event().id(String.valueOf(HttpStatus.HTTP_CREATED))
                    .data(clientId, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.error("创建长链接异常，客户端ID:{}   异常信息:{}", clientId, e.getMessage());
        }
        return sseEmitter;
    }

    /**
     * 发送消息给所有客户端
     *
     * @param msg 消息内容
     */
    @Override
    public void sendMessageToAllClient(String msg) {
        if (MapUtil.isEmpty(sseCache)) {
            return;
        }
        // 判断发送的消息是否为空

        for (Map.Entry<String, SseEmitter> entry : sseCache.entrySet()) {
            MessageVo messageVo = new MessageVo();
            messageVo.setClientId(entry.getKey());
            messageVo.setData(msg);
            sendMsgToClientByClientId(entry.getKey(), messageVo, entry.getValue());
        }

    }

    /**
     * 给指定客户端发送消息
     *
     * @param clientId 客户端ID
     * @param msg      消息内容
     */
    @Override
    public void sendMessageToOneClient(String clientId, String msg) {
        MessageVo messageVo = new MessageVo(clientId, msg);
        sendMsgToClientByClientId(clientId, messageVo, sseCache.get(clientId));
    }

    /**
     * 关闭连接
     *
     * @param clientId 客户端ID
     */
    @Override
    public void closeConnect(String clientId) {
        SseEmitter sseEmitter = sseCache.get(clientId);
        if (sseEmitter != null) {
            sseEmitter.complete();
            removeUser(clientId);
        }
    }

    /**
     * 推送消息到客户端 此处做了推送失败后，重试推送机制，可根据自己业务进行修改
     *
     * @param clientId  客户端ID
     * @param messageVo 推送信息，此处结合具体业务，定义自己的返回值即可
     **/
    private void sendMsgToClientByClientId(String clientId, MessageVo messageVo,
            SseEmitter sseEmitter) {
        if (sseEmitter == null) {
            log.error("推送消息失败：客户端{}未创建长链接,失败消息:{}",
                    clientId, messageVo.toString());
            return;
        }
        SseEmitter.SseEventBuilder sendData = SseEmitter.event()
                .id(String.valueOf(HttpStatus.HTTP_OK))
                .data(messageVo, MediaType.APPLICATION_JSON);
        try {
            sseEmitter.send(sendData);
        } catch (IOException e) {
            // 推送消息失败，记录错误日志，进行重推
            log.error("推送消息失败：{},尝试进行重推", messageVo.toString());
            closeConnect(clientId);
        }
    }


    /**
     * 长链接完成后回调接口(即关闭连接时调用)
     *
     * @param clientId 客户端ID
     **/
    private Runnable completionCallBack(String clientId) {
        return () -> {
            log.info("结束连接：{}", clientId);
            removeUser(clientId);
        };
    }

    /**
     * 连接超时时调用
     *
     * @param clientId 客户端ID
     **/
    private Runnable timeoutCallBack(String clientId) {
        return () -> {
            log.info("连接超时：{}", clientId);
            removeUser(clientId);
        };
    }

    /**
     * 推送消息异常时，回调方法
     *
     * @param clientId 客户端ID
     **/
    private Consumer<Throwable> errorCallBack(String clientId) {
        return throwable -> {
            log.error("SseEmitterServiceImpl[errorCallBack]：连接异常,客户端ID:{}", clientId);

            // 推送消息失败后，每隔10s推送一次，推送5次
            for (int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(10000);
                    SseEmitter sseEmitter = sseCache.get(clientId);
                    if (sseEmitter == null) {
                        log.error(
                                "SseEmitterServiceImpl[errorCallBack]：第{}次消息重推失败,未获取到 {} 对应的长链接",
                                i + 1, clientId);
                        continue;
                    }
                    sseEmitter.send("失败后重新推送");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    /**
     * 移除用户连接
     *
     * @param clientId 客户端ID
     **/
    private void removeUser(String clientId) {
        sseCache.remove(clientId);
        log.info("SseEmitterServiceImpl[removeUser]:移除用户：{}", clientId);
    }

    public void clientCall(String clientId, String msg, List<Document> results) {
        String txt = """
            ${user_input}
                        
            以下是可能相关的内容：
            问题：${question1} 答案：${answer1}
            问题：${question2} 答案：${answer2}
            问题：${question3} 答案：${answer3}
            """;
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.DAYS)
                .readTimeout(1, TimeUnit.DAYS)//这边需要将超时显示设置长一点，不然刚连上就断开，之前以为调用方式错误被坑了半天
                .build();
        String url = "http://192.168.1.10:5000/sse";
        RequestDto requestDto = new RequestDto();
        txt = txt.replace("${user_input}", msg);
        for (int i = 0; i < results.size(); i++) {
            txt = txt.replace("${question"+(i+1)+"}", results.get(i).getContent());
            txt = txt.replace("${answer"+(i+1)+"}",
                    results.get(i).getMetadata().getOrDefault(results.get(i).getContent(),"").toString());
        }
        requestDto.setContent(txt);
        RequestBody body = RequestBody.create(JSONUtil.toJsonStr(requestDto),
                okhttp3.MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        EventSource.Factory factory = EventSources.createFactory(okHttpClient);
        EventSourceListener eventSourceListener = new EventSourceListener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onOpen(final EventSource eventSource, final Response
                    response) {
                System.out.println("建立sse连接...");
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void onEvent(final EventSource eventSource, final String
                    id, final String type, final String data) {
                sendMessageToOneClient(clientId, data);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void onClosed(final EventSource eventSource) {
                closeConnect(clientId);
                System.out.println("sse连接关闭...");
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void onFailure(final EventSource eventSource, final
            Throwable t, final Response response) {
                closeConnect(clientId);
                System.out.println("使用事件源时出现异常... [响应：{}]...");
            }
        };
        //创建事件
        factory.newEventSource(request, eventSourceListener);
    }


}

