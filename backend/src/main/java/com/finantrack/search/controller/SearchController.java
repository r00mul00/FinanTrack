package com.finantrack.search.controller;

import com.finantrack.common.security.CurrentUser;
import com.finantrack.search.dto.SearchResult;
import com.finantrack.search.service.SearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Pesquisa")
public class SearchController {

    private final SearchService service;
    private final CurrentUser currentUser;

    public SearchController(SearchService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<SearchResult> search(@RequestParam(defaultValue = "") String q) {
        return service.search(currentUser.id(), q);
    }
}
