package com.osc.store.service;

import com.osc.store.config.ApiException;
import com.osc.store.config.AppProperties;
import com.osc.store.model.*;
import com.osc.store.model.dto.*;
import com.osc.store.repository.AdminUserRepository;
import com.osc.store.repository.ProductRepository;
import com.osc.store.repository.QuoteRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final int MAX_ATTACHMENTS = 5;

    private final QuoteRepository quotes;
    private final ProductRepository products;
    private final AdminUserRepository admins;
    private final FileStorageService storage;
    private final QuoteDocumentService documents;
    private final EmailService email;
    private final AppProperties props;

    public QuoteService(QuoteRepository quotes, ProductRepository products, AdminUserRepository admins,
                        FileStorageService storage, QuoteDocumentService documents, EmailService email,
                        AppProperties props) {
        this.quotes = quotes;
        this.products = products;
        this.admins = admins;
        this.storage = storage;
        this.documents = documents;
        this.email = email;
        this.props = props;
    }

    // =====================================================================
    // Customer: submit a request
    // =====================================================================

    @Transactional
    public QuoteSubmitResponse submit(QuoteSubmitRequest req, List<MultipartFile> files) {
        validateSubmission(req);
        List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (uploads.size() > MAX_ATTACHMENTS) {
            throw ApiException.badRequest("You can upload up to " + MAX_ATTACHMENTS + " documents");
        }

        Quote q = new Quote();
        q.setCustomerType(req.customerType());
        if (req.customerType() == CustomerType.COMPANY) {
            q.setCompanyName(trim(req.companyName()));
            q.setRegistrationNumber(trim(req.registrationNumber()));
            q.setVatNumber(trim(req.vatNumber()));
            q.setCompanyEmail(trim(req.companyEmail()));
            q.setCompanyPhone(trim(req.companyPhone()));
            q.setCompanyAddress(trim(req.companyAddress()));
            q.setCompanyLocation(trim(req.companyLocation()));
        }
        q.setContactName(req.contactName().trim());
        q.setContactEmail(req.contactEmail().trim());
        q.setContactPhone(trim(req.contactPhone()));
        q.setContactJobTitle(req.customerType() == CustomerType.COMPANY ? trim(req.contactJobTitle()) : null);
        q.setDeliveryAddress(req.deliveryAddress().trim());
        q.setDeliveryLocation(trim(req.deliveryLocation()));
        q.setQuestions(trim(req.questions()));

        // merge repeated product + option combinations into a single line
        Map<String, QuoteItem> lines = new LinkedHashMap<>();
        for (QuoteSubmitRequest.Item it : req.items()) {
            Product p = products.findById(it.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.badRequest("One of the items in your basket is no longer available"));
            String optionsText = resolveOptions(p, it.options());
            String key = p.getId() + "|" + optionsText;
            QuoteItem line = lines.get(key);
            if (line == null) {
                line = new QuoteItem();
                line.setProductId(p.getId());
                line.setName(p.getName());
                line.setSku(p.getSku());
                line.setBrandName(p.getBrand() == null ? null : p.getBrand().getName());
                line.setType(p.getType());
                line.setImagePath(p.getImages().isEmpty() ? null : p.getImages().get(0));
                line.setSelectedOptions(optionsText.isEmpty() ? null : optionsText);
                line.setQuantity(0);
                lines.put(key, line);
            }
            line.setQuantity(line.getQuantity() + it.quantity());
        }
        lines.values().forEach(q::addItem);

        for (MultipartFile f : uploads) {
            FileStorageService.StoredFile stored = storage.storeDocument(f);
            QuoteAttachment a = new QuoteAttachment();
            a.setOriginalName(stored.name());
            a.setStoragePath(stored.path());
            a.setContentType(f.getContentType());
            a.setSizeBytes(stored.size());
            q.addAttachment(a);
        }

        quotes.saveAndFlush(q);
        q.setReference(String.format("OSC-Q-%d-%05d", Year.now().getValue(), q.getId()));
        quotes.save(q);

        // render now (lazy collections are available) and send once the data is safely committed
        String ackHtml = documents.acknowledgementHtml(q);
        String teamHtml = documents.teamNotificationHtml(q);
        String recipient = q.recipientEmail();
        String subject = "We received your quote request " + q.getReference();
        String teamSubject = "New quote request " + q.getReference() + " - " + q.customerDisplayName();
        Set<String> team = new LinkedHashSet<>();
        admins.findAll().stream()
                .map(AdminUser::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .forEach(team::add);
        String notifyTo = props.getMail().getNotifyTo();
        if (notifyTo != null && !notifyTo.isBlank()) {
            team.add(notifyTo.trim().toLowerCase(Locale.ROOT));
        }
        runAfterCommit(() -> {
            email.sendAsync(recipient, subject, ackHtml);
            team.forEach(addr -> email.sendAsync(addr, teamSubject, teamHtml));
        });

        return new QuoteSubmitResponse(q.getReference(), recipient);
    }

    // =====================================================================
    // Admin: view and price quotes
    // =====================================================================

    @Transactional(readOnly = true)
    public PageResponse<QuoteSummaryDto> list(QuoteStatus status, String search, int page, int size) {
        Specification<Quote> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("reference"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("companyName"), "")), like),
                        cb.like(cb.lower(root.<String>get("contactName")), like),
                        cb.like(cb.lower(root.<String>get("contactEmail")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Quote> result = quotes.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.getContent().stream().map(this::toSummary).toList(),
                result.getTotalElements(), safePage, safeSize, result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public QuoteStatsDto stats() {
        long n = quotes.countByStatus(QuoteStatus.NEW);
        long p = quotes.countByStatus(QuoteStatus.PROCESSED);
        return new QuoteStatsDto(n + p, n, p);
    }

    @Transactional(readOnly = true)
    public QuoteDetailDto get(Long id) {
        return toDetail(find(id));
    }

    /** Saves pricing and terms without sending anything to the customer. */
    @Transactional
    public QuoteDetailDto saveDraft(Long id, QuoteUpdateRequest req) {
        Quote q = find(id);
        applyPricing(q, req);
        return toDetail(quotes.save(q));
    }

    /** Saves pricing and terms, marks the quote as processed and emails the formal quotation + PDF. */
    @Transactional
    public ProcessResponse process(Long id, QuoteUpdateRequest req, String adminEmail) {
        Quote q = find(id);
        applyPricing(q, req);

        List<String> unpriced = q.getItems().stream()
                .filter(i -> i.getUnitPrice() == null).map(QuoteItem::getName).toList();
        if (!unpriced.isEmpty()) {
            throw ApiException.badRequest("Enter a price for every item before processing. Missing: " + String.join(", ", unpriced));
        }
        if (q.getValidUntil() == null) {
            q.setValidUntil(LocalDate.now().plusDays(14));
        }
        q.setStatus(QuoteStatus.PROCESSED);
        q.setProcessedAt(Instant.now());
        q.setProcessedBy(adminEmail);
        quotes.saveAndFlush(q);

        String error = sendQuotation(q);
        return new ProcessResponse(toDetail(quotes.save(q)), error == null, error);
    }

    /** Re-sends the formal quotation of an already processed quote. */
    @Transactional
    public ProcessResponse resend(Long id) {
        Quote q = find(id);
        if (q.getStatus() != QuoteStatus.PROCESSED) {
            throw ApiException.badRequest("Process the quote first");
        }
        String error = sendQuotation(q);
        return new ProcessResponse(toDetail(quotes.save(q)), error == null, error);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        return documents.quotePdf(find(id));
    }

    @Transactional(readOnly = true)
    public String reference(Long id) {
        return find(id).getReference();
    }

    public record AttachmentFile(String name, FileStorageService.FileData data) {
    }

    @Transactional(readOnly = true)
    public AttachmentFile attachment(Long quoteId, Long attachmentId) {
        QuoteAttachment a = find(quoteId).getAttachments().stream()
                .filter(x -> x.getId().equals(attachmentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        return new AttachmentFile(a.getOriginalName(), storage.openDocument(a.getStoragePath()));
    }

    // =====================================================================
    // Internals
    // =====================================================================

    /** Builds and sends the quotation email. Returns null on success or a short error message. */
    private String sendQuotation(Quote q) {
        try {
            String html = documents.quoteHtml(q, false);
            byte[] pdf = documents.quotePdf(q);
            email.send(q.recipientEmail(), "Your quotation " + q.getReference() + " from " + props.getCompany().getName(), html,
                    new EmailService.Attachment("Quotation-" + q.getReference() + ".pdf", pdf, "application/pdf"));
            q.setEmailSentAt(Instant.now());
            q.setEmailError(null);
            return null;
        } catch (Exception ex) {
            log.error("Could not email quotation {}", q.getReference(), ex);
            String msg = "Could not send the email: " + rootMessage(ex);
            q.setEmailError(msg.length() > 480 ? msg.substring(0, 480) : msg);
            return q.getEmailError();
        }
    }

    private void applyPricing(Quote q, QuoteUpdateRequest req) {
        Map<Long, QuoteUpdateRequest.ItemUpdate> updates = new HashMap<>();
        if (req.items() != null) {
            req.items().stream().filter(u -> u.id() != null).forEach(u -> updates.put(u.id(), u));
        }
        BigDecimal products = BigDecimal.ZERO;
        BigDecimal solutions = BigDecimal.ZERO;
        for (QuoteItem item : q.getItems()) {
            QuoteUpdateRequest.ItemUpdate u = updates.get(item.getId());
            if (u != null) {
                item.setUnitPrice(u.unitPrice() == null ? null : u.unitPrice().setScale(2, RoundingMode.HALF_UP));
                item.setAvailability(trim(u.availability()));
                item.setWarranty(trim(u.warranty()));
                item.setDeliveryTimeline(trim(u.deliveryTimeline()));
                item.setSourcing(trim(u.sourcing()));
                item.setNotes(trim(u.notes()));
            }
            if (item.getUnitPrice() == null) {
                item.setLineTotal(null);
            } else {
                BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                item.setLineTotal(line);
                if (item.getType() == ProductType.SOLUTION) {
                    solutions = solutions.add(line);
                } else {
                    products = products.add(line);
                }
            }
        }

        BigDecimal fee = orZero(req.deliveryFee());
        BigDecimal discount = orZero(req.discount());
        BigDecimal vatPercent = req.vatPercent() == null ? q.getVatPercent() : req.vatPercent();
        if (vatPercent == null) {
            vatPercent = new BigDecimal("15");
        }
        BigDecimal net = products.add(solutions).add(fee);
        if (discount.compareTo(net) > 0) {
            throw ApiException.badRequest("The discount cannot be more than the quote value");
        }
        net = net.subtract(discount);
        BigDecimal vat = net.multiply(vatPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        q.setProductsSubtotal(products);
        q.setSolutionsSubtotal(solutions);
        q.setDeliveryFee(fee);
        q.setDiscount(discount);
        q.setVatPercent(vatPercent);
        q.setVatAmount(vat);
        q.setTotal(net.add(vat));

        q.setValidUntil(req.validUntil());
        q.setWarranty(trim(req.warranty()));
        q.setDeliveryTimeline(trim(req.deliveryTimeline()));
        q.setAvailability(trim(req.availability()));
        q.setSourcing(trim(req.sourcing()));
        q.setPaymentTerms(trim(req.paymentTerms()));
        q.setAdditionalNotes(trim(req.additionalNotes()));
    }

    private void validateSubmission(QuoteSubmitRequest req) {
        if (req.customerType() == CustomerType.COMPANY) {
            if (isBlank(req.companyName())) {
                throw ApiException.badRequest("Company name is required");
            }
            if (isBlank(req.companyEmail())) {
                throw ApiException.badRequest("Company email is required");
            }
            if (isBlank(req.companyAddress())) {
                throw ApiException.badRequest("Company address is required");
            }
        }
    }

    private String resolveOptions(Product p, Map<String, String> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (OptionDefinition def : p.getOptions()) {
            String value = chosen.get(def.getName());
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!def.getOptionValues().contains(value)) {
                throw ApiException.badRequest("\"" + value + "\" is not an available " + def.getName() + " option for " + p.getName());
            }
            parts.add(def.getName() + ": " + value);
        }
        return String.join(", ", parts);
    }

    private Quote find(Long id) {
        return quotes.findById(id).orElseThrow(() -> ApiException.notFound("Quote"));
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private QuoteSummaryDto toSummary(Quote q) {
        int products = (int) q.getItems().stream().filter(i -> i.getType() == ProductType.PRODUCT).count();
        int solutions = q.getItems().size() - products;
        int qty = q.getItems().stream().mapToInt(QuoteItem::getQuantity).sum();
        return new QuoteSummaryDto(q.getId(), q.getReference(), q.getCustomerType(), q.customerDisplayName(),
                q.recipientEmail(), q.getStatus(), products, solutions, qty,
                q.getStatus() == QuoteStatus.PROCESSED ? q.getTotal() : null, q.getCreatedAt(), q.getProcessedAt());
    }

    private QuoteDetailDto toDetail(Quote q) {
        return new QuoteDetailDto(
                q.getId(), q.getReference(), q.getCustomerType(), q.getStatus(),
                q.getCompanyName(), q.getRegistrationNumber(), q.getVatNumber(), q.getCompanyEmail(), q.getCompanyPhone(),
                q.getCompanyAddress(), q.getCompanyLocation(),
                q.getContactName(), q.getContactEmail(), q.getContactPhone(), q.getContactJobTitle(),
                q.getDeliveryAddress(), q.getDeliveryLocation(), q.getQuestions(),
                q.getCurrency(), q.getProductsSubtotal(), q.getSolutionsSubtotal(), q.getDeliveryFee(),
                q.getDiscount(), q.getVatPercent(), q.getVatAmount(), q.getTotal(),
                q.getValidUntil(), q.getWarranty(), q.getDeliveryTimeline(), q.getAvailability(), q.getSourcing(),
                q.getPaymentTerms(), q.getAdditionalNotes(),
                q.getCreatedAt(), q.getProcessedAt(), q.getProcessedBy(), q.getEmailSentAt(), q.getEmailError(),
                q.getItems().stream().map(i -> new QuoteItemDto(i.getId(), i.getProductId(), i.getName(), i.getSku(),
                        i.getBrandName(), i.getType(), i.getImagePath(), i.getSelectedOptions(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal(), i.getAvailability(), i.getWarranty(),
                        i.getDeliveryTimeline(), i.getSourcing(), i.getNotes())).toList(),
                q.getAttachments().stream().map(a -> new QuoteAttachmentDto(a.getId(), a.getOriginalName(),
                        a.getContentType(), a.getSizeBytes())).toList());
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getMessage() == null ? r.getClass().getSimpleName() : r.getMessage();
    }
}
