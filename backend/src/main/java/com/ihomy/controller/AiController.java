package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.AiChatDTO;
import com.ihomy.dto.AiImageDTO;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AiService;
import com.ihomy.service.FamilyAiConfigService;
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
 * AI 接入接口(V9.40;按功能解析模型 V9.48):图片生成 / 语音识别(OpenAI 兼容协议)。
 * 配置按当前家庭模型池+功能绑定(无全局兜底);仅登录即可用(家庭内共享),
 * 能力未配置友好提示。模型池与功能绑定(/ai/models、/ai/features)family:manage 仅家长,密钥不回传。
 */
@Tag(name = "AI 接入")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final FamilyAiConfigService familyAiConfigService;
    private final SecurityHelper securityHelper;

    /** 语音识别单文件上限:multipart 需整包进内存,短语音场景 20MB 足够(生产 -Xmx384m 占比 ~5%) */
    private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;

    private Long currentFamilyId() {
        return securityHelper.current().getFamilyId();
    }

    @Operation(summary = "AI 能力状态(Playground,按当前家庭)")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        securityHelper.current();
        return Result.success(aiService.status(currentFamilyId()));
    }

    @Operation(summary = "家庭 AI 模型池列表(密钥不回传)")
    @RequirePermission("family:manage")
    @GetMapping("/models")
    public Result<List<Map<String, Object>>> models() {
        return Result.success(familyAiConfigService.listModels(currentFamilyId()));
    }

    @Operation(summary = "新增家庭 AI 模型(密钥加密入库)")
    @OperationLog(module = "AI", operationType = "CREATE", description = "新增 AI 模型", saveArgs = false)
    @RequirePermission("family:manage")
    @PostMapping("/models")
    public Result<Long> addModel(@RequestBody Map<String, String> body) {
        return Result.success(familyAiConfigService.saveModel(currentFamilyId(), null, body));
    }

    @Operation(summary = "更新家庭 AI 模型(密钥留空保留原值)")
    @OperationLog(module = "AI", operationType = "UPDATE", description = "更新 AI 模型", saveArgs = false)
    @RequirePermission("family:manage")
    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        familyAiConfigService.saveModel(currentFamilyId(), id, body);
        return Result.success();
    }

    @Operation(summary = "删除家庭 AI 模型(引用它的功能解绑停用)")
    @OperationLog(module = "AI", operationType = "DELETE", description = "删除 AI 模型", saveArgs = false)
    @RequirePermission("family:manage")
    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        familyAiConfigService.deleteModel(currentFamilyId(), id);
        return Result.success();
    }

    @Operation(summary = "家庭 AI 功能绑定列表")
    @RequirePermission("family:manage")
    @GetMapping("/features")
    public Result<List<Map<String, Object>>> features() {
        return Result.success(familyAiConfigService.getFeatures(currentFamilyId()));
    }

    @Operation(summary = "绑定功能到模型(modelId 为空=停用该功能;fallbackModelId 可选兜底)")
    @OperationLog(module = "AI", operationType = "UPDATE", description = "绑定 AI 功能模型", saveArgs = false)
    @RequirePermission("family:manage")
    @PutMapping("/features/{featureCode}")
    public Result<Void> bindFeature(@PathVariable String featureCode, @RequestBody(required = false) Map<String, Object> body) {
        Long modelId = body == null || body.get("modelId") == null ? null : ((Number) body.get("modelId")).longValue();
        Long fallbackModelId = body == null || body.get("fallbackModelId") == null ? null : ((Number) body.get("fallbackModelId")).longValue();
        familyAiConfigService.bindFeature(currentFamilyId(), featureCode, modelId, fallbackModelId);
        return Result.success();
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
        String content = aiService.chat(currentFamilyId(), dto.getMessages(), dto.getTemperature());
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
        return Result.success(aiService.images(currentFamilyId(), dto));
    }

    @Operation(summary = "AI 语音识别")
    @OperationLog(module = "AI", operationType = "CREATE", description = "AI 语音识别", saveArgs = false)
    @PostMapping("/transcribe")
    public Result<Map<String, Object>> transcribe(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false) String language,
                                                  @RequestParam(required = false) Integer rate) throws IOException {
        securityHelper.current();
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "音频文件过大(上限 20MB)");
        }
        return Result.success(aiService.transcribe(currentFamilyId(), file.getBytes(), file.getOriginalFilename(),
                file.getContentType(), language, rate));
    }
}
