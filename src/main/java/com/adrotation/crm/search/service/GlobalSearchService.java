package com.adrotation.crm.search.service;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.repository.CompanyRepository;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.repository.ContractRepository;
import com.adrotation.crm.documents.repository.SpecificationRepository;
import com.adrotation.crm.search.dto.SearchDtos;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {
    private final CompanyRepository companyRepository;
    private final DealRepository dealRepository;
    private final ContractRepository contractRepository;
    private final SpecificationRepository specificationRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public SearchDtos.Response search(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.length() < 2) return new SearchDtos.Response(q, List.of());
        AppUser user = currentUserService.requireCurrentUser();
        boolean crm = canCrm(user), docs = canDocs(user), managerOnly = isManagerOnly(user);
        List<SearchDtos.Result> out = new ArrayList<>();

        LinkedHashMap<Long, Company> companies = new LinkedHashMap<>();
        companyRepository.findTop20ByCanonicalNameContainingIgnoreCaseOrderByCanonicalNameAsc(q).forEach(c -> companies.put(c.getId(), c));
        companyRepository.findTop50ByShortNameContainingIgnoreCaseOrderByShortNameAsc(q).forEach(c -> companies.put(c.getId(), c));
        companyRepository.findByInn(q.replaceAll("\\s+", "")).ifPresent(c -> companies.put(c.getId(), c));
        companies.values().stream()
                .filter(c -> !managerOnly || dealRepository.existsByCompany_IdAndCreatedBy_Id(c.getId(), user.getId()) || (c.getCreatedBy() != null && c.getCreatedBy().getId().equals(user.getId())))
                .limit(12).forEach(c -> out.add(new SearchDtos.Result("COMPANY", c.getId(), c.getCanonicalName(), c.getInn() == null ? "Компания" : "УНП " + c.getInn(), "/companies/" + c.getId())));

        if (crm) {
            for (Deal d : dealRepository.findTop20ByCompany_CanonicalNameContainingIgnoreCaseOrderByUpdatedAtDesc(q)) {
                if (managerOnly && !d.getCreatedBy().getId().equals(user.getId())) continue;
                out.add(new SearchDtos.Result("DEAL", d.getId(), "CRM · " + d.getCompany().getCanonicalName(), "Менеджер: " + d.getCreatedBy().getFullName(), "/crm?deal=" + d.getId()));
                if (out.size() >= 25) break;
            }
        }
        if (docs) {
            contractRepository.findTop20ByNumberContainingIgnoreCaseOrderByContractDateDesc(q).stream().limit(8).forEach(c ->
                    out.add(new SearchDtos.Result("CONTRACT", c.getId(), "Договор №" + c.getNumber(), c.getCompany().getCanonicalName(), "/documents?contract=" + c.getId())));
            try {
                long number = Long.parseLong(q.replaceAll("[^0-9]", ""));
                specificationRepository.findTop20ByNumberGreaterThanEqualOrderByNumberAsc(number).stream().filter(s -> String.valueOf(s.getNumber()).contains(String.valueOf(number))).limit(8).forEach(s ->
                        out.add(new SearchDtos.Result("SPECIFICATION", s.getId(), "Спецификация №" + s.getNumber(), s.getCompany().getCanonicalName(), "/documents?specification=" + s.getId())));
            } catch (NumberFormatException ignored) {}
        }
        return new SearchDtos.Response(q, out.stream().limit(35).toList());
    }

    private boolean canDocs(AppUser u) { return u.getRoles().stream().anyMatch(r -> r == Role.SUPER_ADMIN || r == Role.BUH || r == Role.DELO); }
    private boolean canCrm(AppUser u) { return u.getRoles().stream().anyMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK || r == Role.MANAGER); }
    private boolean isManagerOnly(AppUser u) { return u.getRoles().contains(Role.MANAGER) && u.getRoles().stream().noneMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK); }
}
