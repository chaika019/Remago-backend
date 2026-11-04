package com.adrotation.crm.search.dto;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() {}
    public record Result(String type, Long id, String title, String subtitle, String route) {}
    public record Response(String query, List<Result> results) {}
}
