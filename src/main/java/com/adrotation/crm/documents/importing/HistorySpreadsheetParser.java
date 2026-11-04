package com.adrotation.crm.documents.importing;

import com.adrotation.crm.company.service.CompanyNormalizationService;
import com.adrotation.crm.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class HistorySpreadsheetParser {
    private final CompanyNormalizationService normalizationService;
    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("ru", "RU"));

    public List<RowData> parse(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) throw new BusinessException("В таблице нет листов");
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw new BusinessException("Не найдена строка заголовков");
            Map<String, Integer> headers = headers(headerRow);
            if (!hasAny(headers, "компания", "заказчик", "контрагент", "наименование заказчика")) {
                throw new BusinessException("Не найдена колонка компании/заказчика");
            }
            List<RowData> result = new ArrayList<>();
            for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmpty(row)) continue;
                String company = value(row, headers, "компания", "заказчик", "контрагент", "наименование заказчика");
                if (company.isBlank()) continue;
                String typeRaw = value(row, headers, "тип", "тип документа", "документ");
                String specNumber = value(row, headers, "номер спецификации", "№ спецификации");
                String agreementNumber = value(row, headers, "номер договора", "№ договора");
                String type = detectType(typeRaw, specNumber, agreementNumber, headers);
                String unp = cleanNumber(value(row, headers, "унп", "унн", "инн"));
                String number = !specNumber.isBlank() && "SPECIFICATION".equals(type) ? specNumber
                        : !agreementNumber.isBlank() && "CONTRACT".equals(type) ? agreementNumber
                        : value(row, headers, "номер документа", "№ документа", "номер", "№");
                LocalDate date = date(row, headers, "дата документа", "дата спецификации", "дата договора", "дата");
                String contractNumber = value(row, headers, "основной договор", "договор", "номер договора", "№ договора");
                LocalDate contractDate = date(row, headers, "дата основного договора", "дата договора");
                BigDecimal amount = decimal(value(row, headers, "сумма", "итого", "всего к оплате", "сумма документа"));
                String fileUrl = value(row, headers, "ссылка на документ", "ссылка на файл", "google drive", "файл", "ссылка");
                var recognized = normalizationService.recognize(company);
                result.add(new RowData(i + 1, type, company, recognized.legalForm().name(), recognized.shortName(), recognized.canonicalName(),
                        unp, normalizeDocumentNumber(number), date, normalizeDocumentNumber(contractNumber), contractDate, amount, fileUrl));
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Не удалось прочитать Excel: " + ex.getMessage());
        }
    }

    private Map<String, Integer> headers(Row row) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Cell cell : row) {
            String key = normalize(FORMATTER.formatCellValue(cell));
            if (!key.isBlank()) map.putIfAbsent(key, cell.getColumnIndex());
        }
        return map;
    }

    private String value(Row row, Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            Integer idx = headers.get(normalize(alias));
            if (idx != null) {
                Cell cell = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
            }
        }
        return "";
    }

    private LocalDate date(Row row, Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            Integer idx = headers.get(normalize(alias));
            if (idx == null) continue;
            Cell cell = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            LocalDate parsed = parseDate(FORMATTER.formatCellValue(cell));
            if (parsed != null) return parsed;
        }
        return null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        for (String pattern : List.of("yyyy-MM-dd", "dd.MM.yyyy", "d.M.yyyy", "dd/MM/yyyy", "d/M/yyyy")) {
            try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern)); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        String clean = raw.replace("\u00A0", "").replace(" ", "").replace(',', '.').replaceAll("[^0-9.\\-]", "");
        try { return clean.isBlank() ? BigDecimal.ZERO : new BigDecimal(clean); } catch (NumberFormatException ex) { return BigDecimal.ZERO; }
    }

    private String detectType(String raw, String specNumber, String contractNumber, Map<String, Integer> headers) {
        String upper = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (upper.contains("СПЕЦ")) return "SPECIFICATION";
        if (upper.contains("ДОГОВ")) return "CONTRACT";
        if (specNumber != null && !specNumber.isBlank()) return "SPECIFICATION";
        if (contractNumber != null && !contractNumber.isBlank()) return "CONTRACT";
        boolean hasSpec = headers.keySet().stream().anyMatch(h -> h.contains("спецификац"));
        boolean hasContract = headers.keySet().stream().anyMatch(h -> h.contains("договор"));
        if (hasSpec && !hasContract) return "SPECIFICATION";
        if (hasContract && !hasSpec) return "CONTRACT";
        return "UNKNOWN";
    }

    private boolean isEmpty(Row row) {
        for (Cell cell : row) if (!FORMATTER.formatCellValue(cell).isBlank()) return false;
        return true;
    }
    private boolean hasAny(Map<String,Integer> h, String... aliases) { return Arrays.stream(aliases).map(this::normalize).anyMatch(h::containsKey); }
    private String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("\\s+", " "); }
    private String cleanNumber(String s) { return s == null ? null : s.replaceAll("[^0-9A-Za-zА-Яа-я]", "").trim(); }
    private String normalizeDocumentNumber(String s) { if (s == null) return null; String v=s.trim(); return v.isBlank()?null:v.replaceAll("\\.0$", ""); }

    public record RowData(int rowNumber, String documentType, String rawCompanyName, String legalForm, String shortName,
                          String canonicalName, String unp, String number, LocalDate documentDate, String contractNumber,
                          LocalDate contractDate, BigDecimal amount, String linkedFileUrl) {}
}
