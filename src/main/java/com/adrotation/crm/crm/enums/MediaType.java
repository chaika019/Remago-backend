package com.adrotation.crm.crm.enums;

public enum MediaType {
    STATIC, LED;

    public static MediaType orDefault(MediaType value) {
        return value == null ? STATIC : value;
    }

    public String label() {
        return this == LED ? "LED-борд" : "Статический баннер / печатный стенд";
    }
}
