package com.ihomy.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 模拟器机种:导入文件的扩展名 ↔ game_info.type,以及「有没有电池存档」的口径。
 * type 存大写机种码(GBA/GB/NES/SNES/SEGA/ARCADE/NDS),前端用同一份口径
 * (utils/games/emulatorSystems.js)映射到 EmulatorJS 核心与虚拟按键,两边取值必须一致。
 * 只收单文件 ROM:光盘镜像与多文件格式(cue+bin/iso)要伴随文件,当前导入接口只收一个文件,故不支持。
 */
public final class GameSystem {

    private GameSystem() {
    }

    public static final String TYPE_GBA = "GBA";
    public static final String TYPE_GB = "GB";
    public static final String TYPE_NES = "NES";
    public static final String TYPE_SNES = "SNES";
    public static final String TYPE_SEGA = "SEGA";
    public static final String TYPE_ARCADE = "ARCADE";
    public static final String TYPE_NDS = "NDS";

    /** 扩展名(小写,不含点)→ 机种码;同一机种的多种格式归到同一个 type */
    private static final Map<String, String> EXTENSION_TYPES = new LinkedHashMap<>();

    static {
        EXTENSION_TYPES.put("gba", TYPE_GBA);
        EXTENSION_TYPES.put("gb", TYPE_GB);
        EXTENSION_TYPES.put("gbc", TYPE_GB);
        EXTENSION_TYPES.put("nes", TYPE_NES);
        EXTENSION_TYPES.put("sfc", TYPE_SNES);
        EXTENSION_TYPES.put("smc", TYPE_SNES);
        EXTENSION_TYPES.put("md", TYPE_SEGA);
        EXTENSION_TYPES.put("gen", TYPE_SEGA);
        EXTENSION_TYPES.put("smd", TYPE_SEGA);
        EXTENSION_TYPES.put("sms", TYPE_SEGA);
        EXTENSION_TYPES.put("gg", TYPE_SEGA);
        // 街机是可执行 romset 压缩包(整份 zip/7z),不是普通压缩的 ROM
        EXTENSION_TYPES.put("zip", TYPE_ARCADE);
        EXTENSION_TYPES.put("7z", TYPE_ARCADE);
        EXTENSION_TYPES.put("nds", TYPE_NDS);
    }

    /** 模拟器机种(SWF/LINK 不是) */
    private static final Set<String> EMULATOR_TYPES = Set.of(
            TYPE_GBA, TYPE_GB, TYPE_NES, TYPE_SNES, TYPE_SEGA, TYPE_ARCADE, TYPE_NDS);

    /** 有电池存档的机种(街机靠即时存档,没有 SRAM,不进存档面板) */
    private static final Set<String> SAVE_TYPES = Set.of(
            TYPE_GBA, TYPE_GB, TYPE_NES, TYPE_SNES, TYPE_SEGA, TYPE_NDS);

    /** 文件名 → 机种码;.swf 返回 SWF(LINK 由接口单独指定),未识别返回 null */
    public static String typeForName(String filename) {
        String ext = extensionOf(filename);
        if (ext == null) return null;
        if ("swf".equals(ext)) return GameConst.TYPE_SWF;
        return EXTENSION_TYPES.get(ext);
    }

    /** 是否模拟器机种(SWF/LINK 不是) */
    public static boolean isEmulator(String type) {
        return type != null && EMULATOR_TYPES.contains(type);
    }

    /** 是否有电池存档(存档接口只服务这些机种) */
    public static boolean hasSave(String type) {
        return type != null && SAVE_TYPES.contains(type);
    }

    /** 导入允许的扩展名清单(错误提示与前端 accept 同口径,按机种顺序) */
    public static String importHint() {
        StringBuilder sb = new StringBuilder();
        for (String ext : EXTENSION_TYPES.keySet()) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append('.').append(ext);
        }
        return sb.toString();
    }

    private static String extensionOf(String filename) {
        if (filename == null) return null;
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
