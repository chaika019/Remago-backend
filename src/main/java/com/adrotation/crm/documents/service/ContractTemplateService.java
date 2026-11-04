package com.adrotation.crm.documents.service;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.OrganizationProfile;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class ContractTemplateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] render(OrganizationProfile executor, Contract contract) {
        Company customer = contract.getCompany();

        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(14);
            titleRun.setText("ДОГОВОР ОКАЗАНИЯ УСЛУГ № " + contract.getNumber());

            XWPFParagraph meta = document.createParagraph();
            meta.setAlignment(ParagraphAlignment.BOTH);
            addText(meta, "г. Минск", true);
            addText(meta, "    " + DATE.format(contract.getContractDate()), false);

            paragraph(document,
                    value(executor.getLegalName()) + ", именуемое в дальнейшем «Исполнитель», с одной стороны, и "
                            + value(customer.getCanonicalName())
                            + ", именуемое в дальнейшем «Заказчик», с другой стороны, совместно именуемые «Стороны», заключили настоящий договор о нижеследующем.");

            heading(document, "1. Предмет договора");
            paragraph(document,
                    "1.1. Исполнитель обязуется по заявкам Заказчика проводить рекламные кампании путем размещения рекламных и информационных материалов на рекламных конструкциях, а Заказчик обязуется принять и оплатить оказанные услуги.");
            paragraph(document,
                    "1.2. Конкретные города, форматы, сроки, объем и стоимость размещения определяются в спецификациях, являющихся неотъемлемой частью настоящего договора.");

            heading(document, "2. Права и обязанности сторон");
            paragraph(document,
                    "2.1. Заказчик своевременно предоставляет согласованные материалы, необходимые сведения и документы, а также оплачивает услуги в порядке, установленном настоящим договором и спецификациями.");
            paragraph(document,
                    "2.2. Исполнитель размещает рекламные материалы в согласованные сроки, контролирует состояние мест размещения и информирует Заказчика о ходе оказания услуг.");
            paragraph(document,
                    "2.3. Стороны обязуются соблюдать требования законодательства Республики Беларусь, включая требования законодательства о рекламе и интеллектуальной собственности.");

            heading(document, "3. Стоимость и порядок расчетов");
            paragraph(document,
                    "3.1. Стоимость каждой рекламной кампании указывается в соответствующей спецификации. Оплата производится в безналичной форме на расчетный счет Исполнителя в сроки, согласованные Сторонами.");

            heading(document, "4. Ответственность сторон");
            paragraph(document,
                    "4.1. За неисполнение или ненадлежащее исполнение обязательств Стороны несут ответственность в соответствии с настоящим договором и законодательством Республики Беларусь.");
            paragraph(document,
                    "4.2. Споры и разногласия разрешаются путем переговоров, а при недостижении соглашения — в порядке, предусмотренном законодательством Республики Беларусь.");

            heading(document, "5. Срок действия");
            String validity = contract.getValidUntil() == null
                    ? "до полного исполнения обязательств Сторонами"
                    : "до " + DATE.format(contract.getValidUntil());
            paragraph(document,
                    "5.1. Настоящий договор вступает в силу с даты подписания и действует " + validity + "."
                            + (contract.isAutoRenewal()
                            ? " При отсутствии письменного отказа одной из Сторон договор автоматически продлевается на следующий календарный год на тех же условиях."
                            : ""));

            heading(document, "6. Реквизиты сторон");
            XWPFTable table = document.createTable(1, 2);
            table.setWidth("100%");
            XWPFTableRow row = table.getRow(0);
            fillPartyCell(row.getCell(0), "ЗАКАЗЧИК", customer.getCanonicalName(), customer.getInn(),
                    customer.getLegalAddress(), customer.getPostalAddress(), customer.getSettlementAccount(),
                    customer.getBankName(), customer.getBankBic(), null);
            fillPartyCell(row.getCell(1), "ИСПОЛНИТЕЛЬ", executor.getLegalName(), executor.getUnp(),
                    executor.getLegalAddress(), executor.getPostalAddress(), executor.getSettlementAccount(),
                    executor.getBankName(), executor.getBankBic(), executor.getSignatoryName());

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать DOCX договора", e);
        }
    }

    private void fillPartyCell(
            XWPFTableCell cell,
            String caption,
            String name,
            String unp,
            String legalAddress,
            String postalAddress,
            String account,
            String bankName,
            String bic,
            String signatory
    ) {
        cell.removeParagraph(0);
        XWPFParagraph header = cell.addParagraph();
        addText(header, caption, true);
        line(cell, name);
        line(cell, "УНП: " + value(unp));
        line(cell, "Юридический адрес: " + value(legalAddress));
        line(cell, "Почтовый адрес: " + value(postalAddress));
        line(cell, "р/с: " + value(account));
        line(cell, "Банк: " + value(bankName));
        line(cell, "БИК: " + value(bic));
        if (signatory != null && !signatory.isBlank()) {
            line(cell, "Подписант: " + signatory);
        }
        line(cell, "");
        line(cell, "______________________________");
    }

    private void line(XWPFTableCell cell, String text) {
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingAfter(0);
        addText(paragraph, text, false);
    }

    private void heading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setText(text);
    }

    private void paragraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setIndentationFirstLine(500);
        addText(paragraph, text, false);
    }

    private void addText(XWPFParagraph paragraph, String text, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setBold(bold);
        run.setFontSize(11);
        run.setText(text == null ? "" : text);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
