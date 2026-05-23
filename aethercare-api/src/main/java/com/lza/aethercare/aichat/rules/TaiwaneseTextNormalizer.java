package com.lza.aethercare.aichat.rules;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

/**
 * Spec § AI_Care_Chat：LLM 回覆的字級正規化。
 *
 * <p>強制把可能出現的簡體字轉成繁體（OpenCC s2t）。Qwen / Llama 等模型偶爾會在繁中輸出裡
 * 混入幾個簡體字（如「没/沒」），此 normalizer 是最後一道把關。
 *
 * <p>純字符對應，不變更字義或詞序；台灣特有詞彙（如「兜底→保底」「視頻→影片」）仍仰賴
 * system prompt 約束。
 */
public final class TaiwaneseTextNormalizer {

    private TaiwaneseTextNormalizer() {}

    public static String normalize(String text) {
        if (text == null || text.isBlank()) return text;
        return ZhConverterUtil.toTraditional(text);
    }
}
