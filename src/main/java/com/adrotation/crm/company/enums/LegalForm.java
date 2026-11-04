package com.adrotation.crm.company.enums;

public enum LegalForm {
    OOO("ООО"),
    ZAO("ЗАО"),
    UP("УП"),
    IP("ИП"),
    OAO("ОАО"),
    CHUP("ЧУП"),
    OTHER("Организация");

    private final String displayName;

    LegalForm(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
