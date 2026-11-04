package com.adrotation.crm.company.service;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.enums.LegalForm;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.company.repository.CompanyRepository;
import com.adrotation.crm.shared.service.TextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CompanyNormalizationService {
    private final CompanyRepository companyRepository;
    private final TextSanitizer textSanitizer;

    public NormalizedCompany normalizeAndCheck(LegalForm legalForm, String rawName, String inn, Long excludeId) {
        String shortName = normalizeShortName(rawName, legalForm);
        String canonical = canonical(legalForm, shortName);
        String cleanInn = textSanitizer.plain(inn);
        if (cleanInn != null && cleanInn.isBlank()) cleanInn = null;

        companyRepository.findByCanonicalNameIgnoreCase(canonical)
                .filter(c -> excludeId == null || !c.getId().equals(excludeId))
                .ifPresent(c -> { throw new BusinessException("Компания уже существует: " + c.getCanonicalName()); });

        if (cleanInn != null) {
            companyRepository.findByInn(cleanInn)
                    .filter(c -> excludeId == null || !c.getId().equals(excludeId))
                    .ifPresent(c -> { throw new BusinessException("Компания с таким УНП/ИНН уже существует: " + c.getCanonicalName()); });
        }

        List<Company> candidates = companyRepository.findTop50ByShortNameContainingIgnoreCaseOrderByShortNameAsc(firstSearchToken(shortName));
        for (Company candidate : candidates) {
            if (excludeId != null && candidate.getId().equals(excludeId)) continue;
            double score = similarity(key(shortName), key(candidate.getShortName()));
            if (score >= 0.88d) {
                throw new BusinessException("Возможный дубликат компании: " + candidate.getCanonicalName() + " (сходство " + Math.round(score * 100) + "%)");
            }
        }
        return new NormalizedCompany(shortName, canonical, cleanInn);
    }

    public String normalizeShortName(String rawName, LegalForm legalForm) {
        String name = textSanitizer.plain(rawName);
        if (name == null || name.isBlank()) throw new BusinessException("Название компании не может быть пустым");

        String legal = legalForm.getDisplayName();
        name = name.replace('«', ' ').replace('»', ' ').replace('"', ' ');
        name = name.replaceAll("(?iu)^\\s*(ООО|ЗАО|УП|ИП|ОАО|ЧУП)\\s+", "");
        name = name.replaceAll("\\s+", " ").trim();
        if (name.equalsIgnoreCase(legal)) throw new BusinessException("Укажите название компании, а не только юридическую форму");
        return name;
    }

    public String canonical(LegalForm legalForm, String shortName) {
        if (legalForm == LegalForm.IP) return legalForm.getDisplayName() + " " + shortName;
        return legalForm.getDisplayName() + " «" + shortName + "»";
    }

    private String firstSearchToken(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts.length == 0 ? name : parts[0];
    }

    private String key(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int distance = levenshtein(a, b);
        return 1.0 - (double) distance / Math.max(a.length(), b.length());
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    public record NormalizedCompany(String shortName, String canonicalName, String inn) {}

    public LegalForm detectLegalForm(String rawName) {
        String value = textSanitizer.plain(rawName);
        if (value == null) return LegalForm.OTHER;
        String upper = value.trim().toUpperCase(Locale.ROOT).replace('«', ' ').replace('»', ' ').replace('"', ' ');
        if (upper.matches("^\\s*ООО\\b.*")) return LegalForm.OOO;
        if (upper.matches("^\\s*ЗАО\\b.*")) return LegalForm.ZAO;
        if (upper.matches("^\\s*ОАО\\b.*")) return LegalForm.OAO;
        if (upper.matches("^\\s*ЧУП\\b.*")) return LegalForm.CHUP;
        if (upper.matches("^\\s*УП\\b.*")) return LegalForm.UP;
        if (upper.matches("^\\s*ИП\\b.*")) return LegalForm.IP;
        return LegalForm.OTHER;
    }

    public RecognizedCompany recognize(String rawName) {
        LegalForm form = detectLegalForm(rawName);
        String shortName = normalizeShortName(rawName, form);
        return new RecognizedCompany(form, shortName, canonical(form, shortName));
    }

    public record RecognizedCompany(LegalForm legalForm, String shortName, String canonicalName) {}
}
