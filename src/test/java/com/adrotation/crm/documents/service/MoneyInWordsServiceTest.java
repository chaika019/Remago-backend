package com.adrotation.crm.documents.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyInWordsServiceTest {

    private final MoneyInWordsService service = new MoneyInWordsService();

    @Test
    void convertsTypicalSpecificationAmount() {
        assertEquals(
                "Одна тысяча двести двадцать белорусских рублей 00 копеек",
                service.belarusianRubles(new BigDecimal("1220.00"))
        );
    }

    @Test
    void roundsKopecks() {
        assertEquals(
                "Два белорусских рубля 35 копеек",
                service.belarusianRubles(new BigDecimal("2.345"))
        );
    }
}
