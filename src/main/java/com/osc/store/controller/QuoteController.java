package com.osc.store.controller;

import com.osc.store.model.dto.QuoteSubmitRequest;
import com.osc.store.model.dto.QuoteSubmitResponse;
import com.osc.store.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Public endpoint a customer uses to submit their quote basket. */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteSubmitResponse submit(@RequestPart("quote") @Valid QuoteSubmitRequest quote,
                                      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return quotes.submit(quote, files);
    }
}