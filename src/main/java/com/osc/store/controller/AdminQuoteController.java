package com.osc.store.controller;

import com.osc.store.model.QuoteStatus;
import com.osc.store.model.dto.*;
import com.osc.store.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/quotes")
public class AdminQuoteController {

    private final QuoteService quotes;

    public AdminQuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    public PageResponse<QuoteSummaryDto> list(@RequestParam(required = false) QuoteStatus status,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return quotes.list(status, q, page, size);
    }

    @GetMapping("/stats")
    public QuoteStatsDto stats() {
        return quotes.stats();
    }

    @GetMapping("/{id}")
    public QuoteDetailDto get(@PathVariable Long id) {
        return quotes.get(id);
    }

    /** Save pricing and terms without notifying the customer. */
    @PutMapping("/{id}")
    public QuoteDetailDto save(@PathVariable Long id, @Valid @RequestBody QuoteUpdateRequest req) {
        return quotes.saveDraft(id, req);
    }

    /** Save, mark as processed and email the formal quotation with the PDF attached. */
    @PostMapping("/{id}/process")
    public ProcessResponse process(@PathVariable Long id, @Valid @RequestBody QuoteUpdateRequest req,
                                   Authentication authentication) {
        return quotes.process(id, req, authentication.getName());
    }

    @PostMapping("/{id}/resend")
    public ProcessResponse resend(@PathVariable Long id) {
        return quotes.resend(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] bytes = quotes.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("Quotation-" + quotes.reference(id) + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<InputStreamResource> attachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        QuoteService.AttachmentFile file = quotes.attachment(id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.data().size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.data().stream()));
    }
}