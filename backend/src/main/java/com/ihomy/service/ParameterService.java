package com.ihomy.service;

import com.ihomy.common.AesUtil;
import com.ihomy.entity.SysParameter;
import com.ihomy.mapper.SysParameterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统参数服务:读写 sys_parameter 表。
 * 当前核心用途:提供 AES 加密盐值(aes-salt),供外挂配置解密。
 *
 * 盐值首次启动时自动生成(16 字节 Base64)并入库,后续每次从 DB 读取并缓存。
 * 解密失败(盐值不匹配)会抛运行时异常,启动日志可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParameterService {

    private final SysParameterMapper parameterMapper;
    private static final String SALT_KEY = "aes-salt";

    private volatile String cachedSalt;

    /**
     * 获取 AES 盐值:优先从环境变量 IHOMY_AES_SALT 读(与 ExternalConfigLoader 共用),
     * 环境变量未设则从 DB 读,DB 无则生成并入库。
     * 首次调用后缓存,避免每次解密都查库/读环境变量。
     */
    public String getAesSalt() {
        if (cachedSalt != null) return cachedSalt;
        // 优先环境变量(与 ExternalConfigLoader 启动期解密共用同一盐值)
        String envSalt = System.getenv("IHOMY_AES_SALT");
        if (envSalt != null && !envSalt.isBlank()) {
            cachedSalt = envSalt;
            // 同步到 DB(若 DB 无记录),保证 OPS 加密接口用同一盐值
            try {
                SysParameter p = new SysParameter();
                p.setName(SALT_KEY);
                p.setValue(envSalt);
                p.setDescription("AES-GCM 盐值(来自环境变量 IHOMY_AES_SALT)");
                parameterMapper.upsert(p);
            } catch (Exception e) {
                log.warn("Sync env salt to DB failed: {}", e.getMessage());
            }
            return cachedSalt;
        }
        // 环境变量未设:从 DB 读
        SysParameter param = parameterMapper.findByName(SALT_KEY);
        if (param != null && param.getValue() != null && !param.getValue().isBlank()) {
            cachedSalt = param.getValue();
            return cachedSalt;
        }
        // DB 无:生成并入库
        cachedSalt = AesUtil.generateSalt();
        SysParameter newParam = new SysParameter();
        newParam.setName(SALT_KEY);
        newParam.setValue(cachedSalt);
        newParam.setDescription("AES-GCM 加密盐值(PBKDF2 派生密钥用),首次启动自动生成");
        try {
            parameterMapper.upsert(newParam);
            log.info("AES salt generated and persisted to sys_parameter");
        } catch (Exception e) {
            SysParameter existing = parameterMapper.findByName(SALT_KEY);
            if (existing != null) cachedSalt = existing.getValue();
        }
        return cachedSalt;
    }

    /** 解密 ENC(...) 密文;非加密格式原样返回。盐值从 DB 读取。 */
    public String decrypt(String value) {
        if (!AesUtil.isEncrypted(value)) return value;
        return AesUtil.decrypt(value, getAesSalt());
    }

    /** 加密明文为 ENC(...) 格式(供运维生成密文用) */
    public String encrypt(String plaintext) {
        return AesUtil.encrypt(plaintext, getAesSalt());
    }

    /** 读任意系统参数(不存在返回 null) */
    public String getString(String name) {
        SysParameter p = parameterMapper.findByName(name);
        return p == null ? null : p.getValue();
    }

    /** 写系统参数(upsert) */
    public void put(String name, String value, String description) {
        SysParameter p = new SysParameter();
        p.setName(name);
        p.setValue(value);
        p.setDescription(description);
        parameterMapper.upsert(p);
    }
}
