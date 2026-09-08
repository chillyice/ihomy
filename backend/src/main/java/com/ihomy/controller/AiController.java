package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.AiChatDTO;
import com.ihomy.dto.AiImageDTO;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 接入接口(V9.40):图片生成 / 语音识别(OpenAI 兼容协议,复用 app.ai 配置)。
 * 仅登录即可用(家庭内共享);模型名 app.ai.image-model / asr-model 配置后启用,未配置友好提示。
 */
@Tag(name = "AI 接入")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final SecurityHelper securityHelper;

    /** 语音识别单文件上限:multipart 需整包进内存,短语音场景 20MB 足够(生产 -Xmx384m 占比 ~5%) */
    private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;

    @Operation(summary = "AI 能力状态(Playground)")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        securityHelper.current();
        return Result.success(aiService.status());
    }

    @Operation(summary = "AI 对话测试(Playground)")
    @OperationLog(module = "AI", operationType = "QUERY", description = "AI 对话测试", saveArgs = false)
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody AiChatDTO dto) {
        securityHelper.current();
        if (dto.getMessages() == null || dto.getMessages().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写对话内容");
        }
        long start = System.currentTimeMillis();
        String content = aiService.chat(dto.getMessages(), dto.getTemperature());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.success(out);
    }

    @Operation(summary = "AI 图片生成")
    @OperationLog(module = "AI", operationType = "CREATE", description = "AI 图片生成")
    @PostMapping("/image")
    public Result<List<Map<String, Object>>> image(@RequestBody AiImageDTO dto) {
        securityHelper.current();
        return Result.success(aiService.images(dto.getPrompt(), dto.getSize(), dto.getN()));
    }

    @Operation(summary = "AI 语音识别")
    @OperationLog(module = "AI", operationType = "CREATE", description = "AI 语音识别", saveArgs = false)
    @PostMapping("/transcribe")
    public Result<Map<String, Object>> transcribe(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false) String language) throws IOException {
        securityHelper.current();
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "音频文件过大(上限 20MB)");
        }
        return Result.success(aiService.transcribe(file.getBytes(), file.getOriginalFilename(),
                file.getContentType(), language));
    }
}
