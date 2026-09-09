package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.common.WeatherConst;
import com.ihomy.entity.WeatherCredential;
import com.ihomy.mapper.WeatherCredentialMapper;
import com.ihomy.service.ParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 天气 API 配置(多天气源凭证账本):设置页「天气」标签页使用,family:manage 仅家长。
 * 凭证全局共享(非家庭级),同一时刻仅一条 status=1 生效;和风(QWEATHER)私钥与
 * 其他天气源(OPENWEATHER/AMAP)的 API Key(config_json)均 ENC 加密入库、不回传前端。
 * 运行时 WeatherService.loadCredential 优先读 status=1 记录,缺省回落 external.yml(仅和风)。
 */
@Tag(name = "天气配置")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherCredentialMapper credentialMapper;
    private final ParameterService parameterService;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 凭证列表(私钥/公钥/API Key 内容不回传,只回 privateKeySet/publicKeySet/apiKeySet 布尔) */
    @Operation(summary = "天气 API 凭证列表")
    @RequirePermission("family:manage")
    @GetMapping("/credentials")
    public Result<List<Map<String, Object>>> list() {
        List<WeatherCredential> rows = credentialMapper.selectList(
                new LambdaQueryWrapper<WeatherCredential>().orderByDesc(WeatherCredential::getStatus)
                        .orderByAsc(WeatherCredential::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (WeatherCredential c : rows) {
            out.add(toView(c));
        }
        return Result.success(out);
    }

    @Operation(summary = "新增天气 API 凭证(密钥加密入库)")
    @OperationLog(module = "WEATHER", operationType = "CREATE", description = "新增天气 API 凭证", saveArgs = false)
    @RequirePermission("family:manage")
    @PostMapping("/credentials")
    public Result<Long> create(@RequestBody Map<String, String> body) {
        WeatherCredential c = new WeatherCredential();
        applyFields(c, body, false);
        validate(c);
        if (Integer.valueOf(1).equals(c.getStatus())) {
            // 新增即启用:先停用其余凭证,保证仅一条生效
            disableAll();
        }
        credentialMapper.insert(c);
        return Result.success(c.getId());
    }

    @Operation(summary = "更新天气 API 凭证(密钥留空保留原值)")
    @OperationLog(module = "WEATHER", operationType = "UPDATE", description = "更新天气 API 凭证", saveArgs = false)
    @RequirePermission("family:manage")
    @PutMapping("/credentials/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        WeatherCredential c = require(id);
        applyFields(c, body, true);
        validate(c);
        if (Integer.valueOf(1).equals(c.getStatus())) {
            disableAllExcept(id);
        }
        credentialMapper.updateById(c);
        return Result.success();
    }

    @Operation(summary = "删除天气 API 凭证")
    @OperationLog(module = "WEATHER", operationType = "DELETE", description = "删除天气 API 凭证", saveArgs = false)
    @RequirePermission("family:manage")
    @DeleteMapping("/credentials/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        credentialMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "启用凭证(同时仅一条生效,其余自动停用)")
    @OperationLog(module = "WEATHER", operationType = "UPDATE", description = "启用天气 API 凭证", saveArgs = false)
    @RequirePermission("family:manage")
    @PutMapping("/credentials/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        WeatherCredential c = require(id);
        if (!usable(c)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    WeatherConst.PROVIDER_QWEATHER.equals(c.getProvider())
                            ? "该凭证未填私钥,无法启用(请先编辑填入私钥)"
                            : "该凭证未填 API Key,无法启用(请先编辑填入 API Key)");
        }
        disableAllExcept(id);
        credentialMapper.update(null, new LambdaUpdateWrapper<WeatherCredential>()
                .eq(WeatherCredential::getId, id).set(WeatherCredential::getStatus, 1));
        return Result.success();
    }

    // ---------- 内部 ----------

    private WeatherCredential require(Long id) {
        WeatherCredential c = credentialMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.NOT_FOUND);
        return c;
    }

    /** 回填字段:文本 trim 空串置 NULL;私钥/API Key 显式给出才写(留空保留原值),均 ENC 加密入库。
     *  和风(QWEATHER)写 5 字段,其他天气源把 API Key 整体序列化为 JSON 后加密存 config_json。 */
    private void applyFields(WeatherCredential c, Map<String, String> body, boolean isUpdate) {
        // 天气源:显式给出才写;新建缺省和风
        applyField(c::setProvider, body.get("provider"));
        if (c.getProvider() == null || c.getProvider().isBlank()) {
            c.setProvider(WeatherConst.PROVIDER_QWEATHER);
        }
        if (!WeatherConst.isValidProvider(c.getProvider())) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的天气源:" + c.getProvider());
        }
        applyField(c::setName, body.get("name"));
        applyField(c::setEnv, body.get("env"));
        applyField(c::setRemark, body.get("remark"));
        String status = body.get("status");
        if (status != null && !status.isBlank()) {
            c.setStatus("1".equals(status.trim()) ? 1 : 0);
        } else if (!isUpdate) {
            c.setStatus(0);
        }
        if (WeatherConst.PROVIDER_QWEATHER.equals(c.getProvider())) {
            applyField(c::setApiHost, body.get("apiHost"));
            applyField(c::setProjectId, body.get("projectId"));
            applyField(c::setKeyId, body.get("keyId"));
            // 公钥非机密(对照用),原样存;留空保留原值
            String pub = body.get("publicKey");
            if (pub != null && !pub.isBlank()) {
                c.setPublicKey(pub.trim());
            }
            // 私钥机密:ENC 加密后入库,留空保留原值
            String priv = body.get("privateKey");
            if (priv != null && !priv.isBlank()) {
                String v = priv.trim();
                c.setPrivateKey(v.startsWith("ENC(") && v.endsWith(")") ? v : parameterService.encrypt(v));
            }
        } else {
            // 非和风:单个 API Key → config_json(整体 ENC 加密 JSON);留空保留原值
            String apiKey = body.get("apiKey");
            if (apiKey != null && !apiKey.isBlank()) {
                try {
                    String json = mapper.writeValueAsString(Map.of("apiKey", apiKey.trim()));
                    c.setConfigJson(parameterService.encrypt(json));
                } catch (Exception e) {
                    throw new BizException(ResultCode.BAD_REQUEST, "API Key 序列化失败");
                }
            }
        }
    }

    private void applyField(Consumer<String> setter, String value) {
        if (value == null) return;
        String v = value.trim();
        setter.accept(v.isEmpty() ? null : v);
    }

    private void validate(WeatherCredential c) {
        if (c.getName() == null || c.getName().isBlank()
                || c.getEnv() == null || c.getEnv().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "凭证名称/环境 均为必填");
        }
        if (WeatherConst.PROVIDER_QWEATHER.equals(c.getProvider())) {
            if (c.getApiHost() == null || c.getApiHost().isBlank()
                    || c.getProjectId() == null || c.getProjectId().isBlank()
                    || c.getKeyId() == null || c.getKeyId().isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "凭证名称/环境/API Host/项目ID/凭证ID 均为必填");
            }
        } else {
            if (c.getConfigJson() == null || c.getConfigJson().isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "该天气源需要填写 API Key");
            }
        }
    }

    /** 凭证是否具备可用的密钥字段:和风看私钥,其他天气源看 config_json */
    private boolean usable(WeatherCredential c) {
        if (!WeatherConst.PROVIDER_QWEATHER.equals(c.getProvider())) {
            return c.getConfigJson() != null && !c.getConfigJson().isBlank();
        }
        return c.getPrivateKey() != null && !c.getPrivateKey().isBlank();
    }

    private void disableAll() {
        credentialMapper.update(null, new LambdaUpdateWrapper<WeatherCredential>()
                .set(WeatherCredential::getStatus, 0));
    }

    private void disableAllExcept(Long id) {
        credentialMapper.update(null, new LambdaUpdateWrapper<WeatherCredential>()
                .ne(WeatherCredential::getId, id).set(WeatherCredential::getStatus, 0));
    }

    private Map<String, Object> toView(WeatherCredential c) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", c.getId());
        o.put("env", c.getEnv());
        o.put("name", c.getName());
        o.put("provider", c.getProvider());
        o.put("apiHost", c.getApiHost());
        o.put("projectId", c.getProjectId());
        o.put("keyId", c.getKeyId());
        o.put("status", c.getStatus());
        o.put("privateKeySet", c.getPrivateKey() != null && !c.getPrivateKey().isBlank());
        o.put("publicKeySet", c.getPublicKey() != null && !c.getPublicKey().isBlank());
        o.put("apiKeySet", c.getConfigJson() != null && !c.getConfigJson().isBlank());
        o.put("remark", c.getRemark());
        return o;
    }
}
