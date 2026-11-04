package com.adrotation.crm.documents.service;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.OrganizationProfile;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.entity.SpecificationItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecificationTemplateServiceTest {

    private final SpecificationTemplateService service =
            new SpecificationTemplateService(new MoneyInWordsService());

    @Test
    void rendersEveryPlacementFromPristineTemplateRow() throws Exception {
        OrganizationProfile executor = executor();
        Specification specification = specification();

        byte[] docx = service.render(executor, specification);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))
            ));

            String rendered = text.toString();
            assertTrue(rendered.contains("Минск"), () -> "Не отрисована первая строка спецификации. Текст документа:\n" + rendered);
            assertTrue(rendered.contains("Брест"), () -> "Не отрисована вторая строка спецификации. Текст документа:\n" + rendered);
            assertTrue(rendered.contains("Спецификация № 101") || rendered.contains("101"),
                    () -> "Не подставлен номер спецификации. Текст документа:\n" + rendered);
            assertTrue(!rendered.contains("{{ITEM_NO}}"),
                    () -> "В итоговом DOCX остались шаблонные маркеры строки: \n" + rendered);
        }
    }

    private OrganizationProfile executor() {
        OrganizationProfile profile = new OrganizationProfile();
        profile.setLegalName("Общество с ограниченной ответственностью \"Ремаго\"");
        profile.setUnp("691341415");
        profile.setSettlementAccount("BY14ALFA30122A56360010270000");
        profile.setBankName("ЗАО «Альфа-Банк»");
        profile.setBankBic("ALFABY2X");
        profile.setBankCity("Минск");
        profile.setLegalAddress("Минская область, г. Березино, ул. Новобазарная, д. 4а");
        profile.setPostalAddress("г. Минск, ул. Короля, 2, помещение 215");
        profile.setSignatoryName("Д.А. Лабкович");
        return profile;
    }

    private Specification specification() {
        Company company = new Company();
        company.setCanonicalName("ООО «Тестовый клиент»");
        company.setInn("123456789");
        company.setLegalAddress("г. Минск, ул. Тестовая, 1");
        company.setPostalAddress("г. Минск, ул. Тестовая, 1");
        company.setSettlementAccount("BY00TEST00000000000000000000");
        company.setBankName("Тестовый банк");
        company.setBankBic("TESTBY2X");

        Contract contract = new Contract();
        contract.setCompany(company);
        contract.setNumber("40/2026");
        contract.setContractDate(LocalDate.of(2026, 1, 10));

        Specification specification = new Specification();
        specification.setNumber(101L);
        specification.setSpecificationDate(LocalDate.of(2026, 9, 12));
        specification.setCompany(company);
        specification.setContract(contract);
        specification.setTotalBeforeDiscount(new BigDecimal("300.00"));
        specification.setTotalAfterDiscount(new BigDecimal("300.00"));
        specification.setVatAmount(BigDecimal.ZERO.setScale(2));

        SpecificationItem first = item(
                specification,
                1,
                "Минск",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 28)
        );
        SpecificationItem second = item(
                specification,
                2,
                "Брест",
                LocalDate.of(2026, 9, 29),
                LocalDate.of(2026, 10, 26)
        );

        specification.setItems(new java.util.ArrayList<>(List.of(first, second)));
        return specification;
    }

    private SpecificationItem item(
            Specification specification,
            int line,
            String city,
            LocalDate start,
            LocalDate end
    ) {
        SpecificationItem item = new SpecificationItem();
        item.setSpecification(specification);
        item.setLineNumber(line);
        item.setCityName(city);
        item.setStandFormat("A4");
        item.setPeriodStart(start);
        item.setPeriodEnd(end);
        item.setQuantity(10);
        item.setUnitPrice(new BigDecimal("15.00"));
        item.setDiscountPercent(BigDecimal.ZERO.setScale(2));
        item.setVatExempt(true);
        item.setVatRate(BigDecimal.ZERO.setScale(2));
        item.setVatAmount(BigDecimal.ZERO.setScale(2));
        item.setTotalBeforeDiscount(new BigDecimal("150.00"));
        item.setTotalAfterDiscount(new BigDecimal("150.00"));
        return item;
    }
}
