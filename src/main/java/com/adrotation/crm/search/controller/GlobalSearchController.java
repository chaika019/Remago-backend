package com.adrotation.crm.search.controller;

import com.adrotation.crm.search.dto.SearchDtos;
import com.adrotation.crm.search.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {
    private final GlobalSearchService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','BUH','DELO','LAWYER','SHIFT_WORKER')")
    public SearchDtos.Response search(@RequestParam String q) { return service.search(q); }
}
