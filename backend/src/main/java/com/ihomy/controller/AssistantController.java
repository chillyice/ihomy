package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.service.assistant.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "家庭 AI 助手")
@RestController
@RequestMapping("/ai/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final AssistantService assistant;

    public record PageContext(@Size(max=200) String path, @Size(max=80) String name) {}
    public record Chat(@NotBlank @Size(max=4000) String text, @Positive Long conversationId, @Valid PageContext pageContext) {}
    public record Settings(@NotNull Boolean enabled) {}
    public record Confirm(@Pattern(regexp="PRIVATE|FAMILY") String visibility) {}

    @GetMapping("/status")
    public Result<?> status() { return Result.success(assistant.status()); }

    @PutMapping("/settings")
    @RequirePermission("family:manage")
    @OperationLog(module="AI", operationType="UPDATE", description="设置家庭助手开关")
    public Result<?> settings(@Valid @RequestBody Settings request) {
        assistant.setEnabled(request.enabled());
        return Result.success();
    }

    @Operation(summary="向家庭助手提问")
    @PostMapping("/chat")
    @OperationLog(module="AI", operationType="CREATE", description="与家庭助手对话", saveArgs=false)
    public Result<?> chat(@Valid @RequestBody Chat request) {
        return Result.success(assistant.chat(request.text().strip(), request.conversationId(),
                request.pageContext() == null ? null : request.pageContext().path()));
    }

    @GetMapping("/conversation")
    public Result<?> conversation(@RequestParam(required=false) Long beforeId) {
        return Result.success(assistant.conversation(beforeId));
    }

    @DeleteMapping("/conversation")
    @OperationLog(module="AI", operationType="DELETE", description="清空本人的助手对话", saveArgs=false)
    public Result<?> clear() {
        assistant.clear();
        return Result.success();
    }

    @GetMapping("/actions/pending")
    public Result<?> pending() { return Result.success(Map.of("count", assistant.pending())); }

    @PostMapping("/actions/{id}/confirm")
    @OperationLog(module="AI", operationType="UPDATE", description="确认家庭助手操作", saveArgs=false)
    public Result<?> confirm(@PathVariable long id, @Valid @RequestBody(required=false) Confirm body) {
        return Result.success(assistant.decide(id, true, body == null ? null : body.visibility()));
    }

    @PostMapping("/actions/{id}/cancel")
    @OperationLog(module="AI", operationType="UPDATE", description="取消家庭助手操作", saveArgs=false)
    public Result<?> cancel(@PathVariable long id) { return Result.success(assistant.decide(id, false, null)); }

    @PostMapping("/transcribe")
    @OperationLog(module="AI", operationType="CREATE", description="转写助手语音", saveArgs=false)
    public Result<?> transcribe(@RequestParam("file") MultipartFile file, @RequestParam(required=false) String language,
                               @RequestParam(required=false) Integer rate) {
        return Result.success(assistant.transcribe(file, language, rate));
    }

    @GetMapping("/tools")
    public Result<?> tools() { return Result.success(Map.of("tools", assistant.catalogForOps())); }
}
