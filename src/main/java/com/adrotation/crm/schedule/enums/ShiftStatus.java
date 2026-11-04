package com.adrotation.crm.schedule.enums;

public enum ShiftStatus {
    PLANNED(0, "Запланирована", "#E5E7EB"),
    WHITE_SHIFT(1, "Смена", "#FFFFFF"),
    SHIFT_AND_SALE(2, "Смена и продажа", "#3B82F6"),
    NOT_OCCURRED(3, "Не состоялась", "#EF4444"),
    MINUS(4, "Минус", "#DC2626"),
    SHIFT_AND_NOTIFICATION(5, "Смена и уведомление", "#22C55E"),
    PLUS_MINUS(6, "Плюс/минус", "#FACC15"),
    AUTO_SHIFT(7, "Автосмена", "#FDE047"),
    AUTO_AND_SHIFT(8, "Авто и смена", "#F97316"),
    UNDEFINED_9(9, "Статус 9 — уточнить", "#9CA3AF"),
    ACTIVE_SELL(10, "Активно продавать", "#FEF08A"),
    REPAIR_AND_RECONSTRUCTION(11, "Ремонт и реконструкция", "#A78BFA");

    private final int legendNumber;
    private final String displayName;
    private final String colorHex;

    ShiftStatus(int legendNumber, String displayName, String colorHex) {
        this.legendNumber = legendNumber;
        this.displayName = displayName;
        this.colorHex = colorHex;
    }

    public int getLegendNumber() {
        return legendNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }
}
