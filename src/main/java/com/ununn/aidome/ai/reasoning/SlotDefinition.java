package com.ununn.aidome.ai.reasoning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数槽位定义
 * 描述每个意图需要的参数及其约束
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlotDefinition {

    /** 参数名，如 floor、zone、bookingDate */
    private String name;

    /** 参数中文名，如 楼层、区域、预约日期 */
    private String displayName;

    /** 是否必填 */
    private boolean required;

    /** 参数值描述/约束，如 "1楼|2楼|3楼|4楼" 或 "yyyy-MM-dd" */
    private String valueDescription;

    /** 追问模板，如 "请提供图书馆楼层，例如：1楼、2楼" */
    private String followUpTemplate;

    public static SlotDefinition of(String name, String displayName, boolean required, String valueDescription) {
        return new SlotDefinition(name, displayName, required, valueDescription, "请提供" + displayName + "，例如：" + valueDescription);
    }

    public static SlotDefinition of(String name, String displayName, boolean required, String valueDescription, String followUpTemplate) {
        return new SlotDefinition(name, displayName, required, valueDescription, followUpTemplate);
    }
}
