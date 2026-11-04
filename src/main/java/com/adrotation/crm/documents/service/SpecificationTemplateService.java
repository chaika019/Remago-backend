package com.adrotation.crm.documents.service;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.OrganizationProfile;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.entity.SpecificationItem;
import com.adrotation.crm.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SpecificationTemplateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String TEMPLATE_PATH = "templates/specification-template.docx";
    private static final String ITEM_ROW_MARKER = "{{ITEM_NO}}";

    private final MoneyInWordsService moneyInWordsService;

    public byte[] render(OrganizationProfile executor, Specification specification) {
        try (
                InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
                XWPFDocument document = new XWPFDocument(in);
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            Contract contract = specification.getContract();
            if (contract == null) {
                throw new BusinessException("Для спецификации нужен основной договор: номер и дата");
            }

            Map<String, String> values = documentValues(executor, specification, contract);

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                replaceParagraph(paragraph, values);
            }

            for (XWPFTable table : document.getTables()) {
                renderItems(table, specification.getItems());
                replaceTableParagraphs(table, values);
            }

            document.write(out);
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать DOCX спецификации", e);
        }
    }

    private Map<String, String> documentValues(
            OrganizationProfile executor,
            Specification specification,
            Contract contract
    ) {
        Company customer = specification.getCompany();
        Map<String, String> values = new HashMap<>();

        values.put("{{EXECUTOR_NAME}}", nullToEmpty(executor.getLegalName()));
        values.put("{{EXECUTOR_ACCOUNT}}", nullToEmpty(executor.getSettlementAccount()));
        values.put("{{EXECUTOR_BANK_NAME}}", nullToEmpty(executor.getBankName()));
        values.put("{{EXECUTOR_BANK_BIC}}", nullToEmpty(executor.getBankBic()));
        values.put("{{EXECUTOR_BANK_CITY}}", nullToEmpty(executor.getBankCity()));
        values.put("{{EXECUTOR_LEGAL_ADDRESS}}", nullToEmpty(executor.getLegalAddress()));
        values.put("{{EXECUTOR_POSTAL_ADDRESS}}", nullToEmpty(executor.getPostalAddress()));
        values.put("{{EXECUTOR_UNP}}", nullToEmpty(executor.getUnp()));
        values.put("{{EXECUTOR_SIGNATORY}}", nullToEmpty(executor.getSignatoryName()));

        values.put("{{SPEC_NUMBER}}", String.valueOf(specification.getNumber()));
        values.put("{{SPEC_DATE}}", DATE.format(specification.getSpecificationDate()));
        values.put("{{CONTRACT_NUMBER}}", contract.getNumber());
        values.put("{{CONTRACT_DATE}}", DATE.format(contract.getContractDate()));

        values.put("{{CUSTOMER_NAME}}", customer.getCanonicalName());
        values.put("{{CUSTOMER_LEGAL_ADDRESS}}", nullToEmpty(customer.getLegalAddress()));
        values.put("{{CUSTOMER_POSTAL_ADDRESS}}", nullToEmpty(customer.getPostalAddress()));
        values.put("{{CUSTOMER_ACCOUNT}}", nullToEmpty(customer.getSettlementAccount()));
        values.put("{{CUSTOMER_BANK_NAME}}", nullToEmpty(customer.getBankName()));
        values.put("{{CUSTOMER_BANK_BIC}}", nullToEmpty(customer.getBankBic()));
        values.put("{{CUSTOMER_UNP}}", nullToEmpty(customer.getInn()));

        values.put(
                "{{VAT_SUMMARY}}",
                specification.getVatAmount().compareTo(BigDecimal.ZERO) == 0
                        ? "без НДС согл. ст.326 Налогового кодекса РБ"
                        : money(specification.getVatAmount()) + " руб."
        );
        values.put(
                "{{TOTAL_IN_WORDS}}",
                moneyInWordsService.belarusianRubles(specification.getTotalAfterDiscount())
        );
        values.put("{{TOTAL_VAT}}", money(specification.getVatAmount()));
        values.put(
                "{{TOTAL_BEFORE_DISCOUNT}}",
                money(specification.getTotalBeforeDiscount()) + " руб."
        );
        values.put(
                "{{TOTAL_AFTER_DISCOUNT}}",
                money(specification.getTotalAfterDiscount()) + " руб."
        );

        return values;
    }

    private void renderItems(XWPFTable table, List<SpecificationItem> items) {
        int templateIndex = findItemTemplateRow(table);
        if (templateIndex < 0) {
            return;
        }
        if (items.isEmpty()) {
            throw new BusinessException("В сделке нет строк размещения для спецификации");
        }

        XWPFTableRow templateRow = table.getRow(templateIndex);
        CTRow pristineTemplate = (CTRow) templateRow.getCtRow().copy();

        for (int index = 0; index < items.size(); index++) {
            XWPFTableRow row;

            if (index == 0) {
                row = templateRow;
                replaceRow(row, itemValues(items.get(index)));
            } else {
                CTRow rowCopy = (CTRow) pristineTemplate.copy();
                XWPFTableRow copiedRow = new XWPFTableRow(rowCopy, table);

                // Important for Apache POI 5.4.x: addRow(row, pos) can copy the
                // row XML into the table while keeping the wrapper we pass in
                // as a separate object. Therefore all placeholder replacements
                // must be performed BEFORE the row is added to the table.
                // Then the already-rendered XML is what POI inserts/serializes.
                replaceRow(copiedRow, itemValues(items.get(index)));

                int insertIndex = templateIndex + index;
                if (!table.addRow(copiedRow, insertIndex)) {
                    throw new BusinessException("Не удалось добавить строку спецификации № "
                            + items.get(index).getLineNumber());
                }
            }
        }
    }

    private int findItemTemplateRow(XWPFTable table) {
        for (int index = 0; index < table.getNumberOfRows(); index++) {
            XWPFTableRow row = table.getRow(index);
            String rowText = row.getTableCells()
                    .stream()
                    .map(XWPFTableCell::getText)
                    .reduce("", (left, right) -> left + " " + right);

            if (rowText.contains(ITEM_ROW_MARKER)) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, String> itemValues(SpecificationItem item) {
        return Map.of(
                "{{ITEM_NO}}", String.valueOf(item.getLineNumber()),
                "{{ITEM_SERVICE}}", serviceText(item),
                "{{ITEM_UNIT}}", "шт",
                "{{ITEM_QTY}}", String.valueOf(item.getQuantity()),
                "{{ITEM_UNIT_PRICE}}", money(item.getUnitPrice()),
                "{{ITEM_VAT_RATE}}", item.isVatExempt()
                        ? "Без налога (НДС)"
                        : money(item.getVatRate()),
                "{{ITEM_VAT_AMOUNT}}", money(item.getVatAmount()),
                "{{ITEM_TOTAL}}", money(item.getTotalAfterDiscount())
        );
    }

    private void replaceTableParagraphs(XWPFTable table, Map<String, String> values) {
        for (XWPFTableRow row : table.getRows()) {
            replaceRow(row, values);
        }
    }

    private void replaceRow(XWPFTableRow row, Map<String, String> values) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                replaceParagraph(paragraph, values);
            }
        }
    }

    private String serviceText(SpecificationItem item) {
        String text = "Размещение рекламного модуля формата "
                + item.getStandFormat() + " · " + item.getMediaType().label()
                + " на досках объявлений в г. "
                + item.getCityName()
                + ". Период размещения: с "
                + DATE.format(item.getPeriodStart())
                + " по "
                + DATE.format(item.getPeriodEnd())
                + " гг.";

        if (item.getPromotionName() != null && !item.getPromotionName().isBlank()) {
            text += " Акция: " + item.getPromotionName();
        }

        return text;
    }

    private void replaceParagraph(XWPFParagraph paragraph, Map<String, String> values) {
        String original = paragraph.getText();
        if (original == null || original.isEmpty()) {
            return;
        }

        String replaced = original;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            replaced = replaced.replace(entry.getKey(), nullToEmpty(entry.getValue()));
        }

        if (replaced.equals(original)) {
            return;
        }

        List<XWPFRun> runs = paragraph.getRuns();
        CTRPr firstRunProperties = null;
        if (!runs.isEmpty() && runs.get(0).getCTR().isSetRPr()) {
            firstRunProperties = (CTRPr) runs.get(0).getCTR().getRPr().copy();
        }

        for (int index = runs.size() - 1; index >= 0; index--) {
            paragraph.removeRun(index);
        }

        XWPFRun run = paragraph.createRun();
        if (firstRunProperties != null) {
            run.getCTR().setRPr(firstRunProperties);
        }
        run.setText(replaced);
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
