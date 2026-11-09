package com.finantrack.report.controller;

import com.finantrack.common.exception.InvalidParameterException;
import com.finantrack.common.security.CurrentUser;
import com.finantrack.report.dto.ReportFilter;
import com.finantrack.report.dto.ReportResponse;
import com.finantrack.report.dto.ReportType;
import com.finantrack.report.service.ReportExporter;
import com.finantrack.report.service.ReportService;
import com.finantrack.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tipos: monthly, annual, by-category, by-period, income-vs-expense, evolution e by-card. */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Relatórios")
public class ReportController {

    private final ReportService service;
    private final ReportExporter exporter;
    private final UserService userService;
    private final CurrentUser currentUser;

    public ReportController(ReportService service, ReportExporter exporter, CurrentUser currentUser,
            UserService userService) {
        this.userService = userService;
        this.service = service;
        this.exporter = exporter;
        this.currentUser = currentUser;
    }

    @GetMapping("/{type}")
    public ReportResponse get(@PathVariable String type, @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.generate(currentUser.id(), ReportType.fromSlug(type), new ReportFilter(month, year, from, to));
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam String format,
            @RequestParam(required = false) String month, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportType reportType = ReportType.fromSlug(type);
        boolean csv = "csv".equals(format);
        if (!csv && !"pdf".equals(format)) {
            throw new InvalidParameterException("Formato de exportação inválido.");
        }

        ReportResponse report = service.generate(currentUser.id(), reportType, new ReportFilter(month, year, from, to));
        byte[] file = csv ? exporter.toCsv(report) : exporter.toPdf(report, userService.getById(currentUser.id()).currency());
        String fileName = "relatorio-%s-%s-a-%s.%s".formatted(reportType.slug(), report.from(), report.to(), format);
        return ResponseEntity.ok()
                .contentType(csv ? new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8) : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(file);
    }
}
