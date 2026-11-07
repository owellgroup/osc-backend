package com.osc.store.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.osc.store.config.AppProperties;
import com.osc.store.model.CustomerType;
import com.osc.store.model.ProductType;
import com.osc.store.model.Quote;
import com.osc.store.model.QuoteItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the HTML used for the quotation email, the acknowledgement email and the PDF.
 * The quotation email and the PDF share the same markup so they always look identical.
 * Markup is valid XHTML so it can be fed to the PDF renderer unchanged.
 */
@Service
public class QuoteDocumentService {

    private static final String RED = "#D71920";
    private static final String GREY = "#58595B";
    private static final String INK = "#1C1C1E";
    private static final String MUTED = "#6E6E73";
    private static final String LINE = "#E5E5EA";
    private static final String SOFT = "#F5F5F7";
    private static final String FONT = "-apple-system,'Segoe UI',Helvetica,Arial,sans-serif";
    private static final Logger log = LoggerFactory.getLogger(QuoteDocumentService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final AppProperties props;
    private volatile String logoDataUri;

    public QuoteDocumentService(AppProperties props) {
        this.props = props;
    }

    // =====================================================================
    // Public API
    // =====================================================================

    public byte[] quotePdf(Quote quote) {
        String html = quoteHtml(quote, true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate quotation PDF", ex);
        }
    }

    /** The formal quotation. {@code forPdf} swaps the remote logo for an embedded one and drops the attachment notice. */
    public String quoteHtml(Quote q, boolean forPdf) {
        StringBuilder h = new StringBuilder(8192);
        open(h, forPdf, "Quotation " + q.getReference());

        // ---- header (red)
        h.append("<tr><td style=\"background:").append(RED).append(";padding:22px 28px;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td valign=\"middle\">").append(logo(forPdf)).append("</td>")
                .append("<td align=\"right\" valign=\"middle\" style=\"color:#ffffff;\">")
                .append("<div style=\"font-size:11px;letter-spacing:2px;opacity:0.85;\">QUOTATION</div>")
                .append("<div style=\"font-size:20px;font-weight:bold;margin-top:2px;\">").append(esc(q.getReference())).append("</div>")
                .append("</td></tr></table></td></tr>");

        // ---- intro
        h.append("<tr><td style=\"padding:26px 28px 6px 28px;\">")
                .append("<div style=\"font-size:18px;font-weight:bold;color:").append(INK).append(";\">Dear ")
                .append(esc(greetingName(q))).append(",</div>")
                .append("<div style=\"font-size:13px;line-height:20px;color:").append(MUTED).append(";margin-top:8px;\">")
                .append("Thank you for choosing ").append(esc(props.getCompany().getName()))
                .append(". Here is your formal quotation for the items you requested.");
        if (!forPdf) {
            h.append(" A PDF copy is attached to this email for your records.");
        }
        h.append("</div></td></tr>");

        // ---- key details
        h.append("<tr><td style=\"padding:14px 28px 4px 28px;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:").append(SOFT)
                .append(";border-radius:10px;\"><tr>")
                .append(metaCell("Date", date(q.getProcessedAt() != null ? q.getProcessedAt() : q.getCreatedAt())))
                .append(metaCell("Valid until", q.getValidUntil() == null ? "-" : q.getValidUntil().format(DATE)))
                .append(metaCell("Prepared for", q.customerDisplayName()))
                .append("</tr><tr>")
                .append(metaCell("Contact", q.getContactName()))
                .append(metaCell("Email", q.recipientEmail()))
                .append(metaCell("Deliver to", joinNonBlank(", ", q.getDeliveryAddress(), q.getDeliveryLocation())))
                .append("</tr></table></td></tr>");

        // ---- items
        List<QuoteItem> products = itemsOf(q, ProductType.PRODUCT);
        List<QuoteItem> solutions = itemsOf(q, ProductType.SOLUTION);
        h.append("<tr><td style=\"padding:14px 28px 0 28px;\">");
        if (!products.isEmpty()) {
            itemTable(h, "Products", products, q.getProductsSubtotal(), q);
        }
        if (!solutions.isEmpty()) {
            itemTable(h, "Solutions", solutions, q.getSolutionsSubtotal(), q);
        }
        h.append("</td></tr>");

        // ---- totals
        h.append("<tr><td style=\"padding:6px 28px 0 28px;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td width=\"50%\">&#160;</td><td width=\"50%\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"font-size:13px;\">");
        if (!products.isEmpty()) {
            totalRow(h, "Products total", money(q, q.getProductsSubtotal()), false);
        }
        if (!solutions.isEmpty()) {
            totalRow(h, "Solutions total", money(q, q.getSolutionsSubtotal()), false);
        }
        if (positive(q.getDeliveryFee())) {
            totalRow(h, "Delivery", money(q, q.getDeliveryFee()), false);
        }
        if (positive(q.getDiscount())) {
            totalRow(h, "Discount", "-" + money(q, q.getDiscount()), false);
        }
        totalRow(h, "VAT (" + stripZeros(q.getVatPercent()) + "%)", money(q, q.getVatAmount()), false);
        h.append("<tr><td style=\"padding:10px 12px;background:").append(RED).append(";color:#ffffff;font-weight:bold;border-radius:8px 0 0 8px;\">Total due</td>")
                .append("<td align=\"right\" style=\"padding:10px 12px;background:").append(RED)
                .append(";color:#ffffff;font-weight:bold;font-size:16px;border-radius:0 8px 8px 0;\">")
                .append(esc(money(q, q.getTotal()))).append("</td></tr>")
                .append("</table></td></tr></table></td></tr>");

        // ---- terms
        List<String[]> terms = new ArrayList<>();
        addTerm(terms, "Availability", q.getAvailability());
        addTerm(terms, "Warranty", q.getWarranty());
        addTerm(terms, "Delivery timeline", q.getDeliveryTimeline());
        addTerm(terms, "Sourcing", q.getSourcing());
        addTerm(terms, "Payment terms", q.getPaymentTerms());
        addTerm(terms, "Notes", q.getAdditionalNotes());
        if (!terms.isEmpty()) {
            h.append("<tr><td style=\"padding:20px 28px 0 28px;\">")
                    .append(sectionTitle("Terms &amp; conditions"))
                    .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"font-size:12px;\">");
            for (String[] t : terms) {
                h.append("<tr><td width=\"32%\" valign=\"top\" style=\"padding:6px 0;color:").append(MUTED)
                        .append(";border-bottom:1px solid ").append(LINE).append(";\">").append(esc(t[0]))
                        .append("</td><td valign=\"top\" style=\"padding:6px 0;color:").append(INK)
                        .append(";border-bottom:1px solid ").append(LINE).append(";\">").append(escMultiline(t[1])).append("</td></tr>");
            }
            h.append("</table></td></tr>");
        }

        // ---- banking details
        AppProperties.Bank bank = props.getBank();
        h.append("<tr><td style=\"padding:20px 28px 0 28px;\">")
                .append(sectionTitle("How to pay"))
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:").append(SOFT)
                .append(";border-left:4px solid ").append(RED).append(";font-size:12px;\"><tr><td style=\"padding:14px 16px;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"font-size:12px;\">")
                .append(bankRow("Bank", bank.getBankName()))
                .append(bankRow("Account name", bank.getAccountName()))
                .append(bankRow("Account number", bank.getAccountNumber()))
                .append(bankRow("Account type", bank.getAccountType()))
                .append(bankRow("Branch code", bank.getBranchCode()))
                .append(bankRow("Swift", bank.getSwift()))
                .append(bankRow("Payment reference", q.getReference()))
                .append("</table>")
                .append("<div style=\"margin-top:10px;color:").append(MUTED).append(";font-size:11px;line-height:17px;\">")
                .append("Please use the quote reference as your payment reference and email your proof of payment to ")
                .append(esc(props.getCompany().getEmail())).append(". Your order is confirmed once payment is received.")
                .append("</div></td></tr></table></td></tr>");

        footer(h);
        close(h, forPdf);
        return forPdf ? compactForPrint(h.toString()) : h.toString();
    }

    /** Tightens vertical spacing so a typical quotation fits on a single A4 page. */
    private static String compactForPrint(String html) {
        return html
                .replace("padding:22px 28px;", "padding:14px 28px;")
                .replace("padding:26px 28px 6px 28px;", "padding:16px 28px 2px 28px;")
                .replace("padding:14px 28px 4px 28px;", "padding:10px 28px 2px 28px;")
                .replace("padding:14px 28px 0 28px;", "padding:8px 28px 0 28px;")
                .replace("padding:20px 28px 0 28px;", "padding:10px 28px 0 28px;")
                .replace("padding:20px 28px;", "padding:12px 28px;")
                .replace("padding:9px 8px;", "padding:6px 8px;")
                .replace("padding:6px 0;", "padding:3px 0;")
                .replace("padding:6px 12px;", "padding:4px 12px;")
                .replace("padding:14px 16px;", "padding:10px 16px;");
    }

    /** "We received your request" email sent when a customer submits their basket. */
    public String acknowledgementHtml(Quote q) {
        StringBuilder h = new StringBuilder(4096);
        open(h, false, "We received your quote request");

        h.append("<tr><td style=\"background:").append(RED).append(";padding:22px 28px;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td valign=\"middle\">").append(logo(false)).append("</td>")
                .append("<td align=\"right\" valign=\"middle\" style=\"color:#ffffff;font-size:11px;letter-spacing:2px;\">QUOTE REQUEST</td>")
                .append("</tr></table></td></tr>");

        h.append("<tr><td style=\"padding:28px 28px 8px 28px;\">")
                .append("<div style=\"font-size:22px;font-weight:bold;color:").append(INK).append(";\">Dear ")
                .append(esc(greetingName(q))).append(",</div>")
                .append("<div style=\"font-size:14px;line-height:22px;color:").append(MUTED).append(";margin-top:10px;\">")
                .append("We have received your quote request");
        if (q.getCustomerType() == CustomerType.COMPANY) {
            h.append(" submitted by ").append(esc(q.getContactName()));
        }
        h.append(" and it will be processed shortly. Our team will review your items and send your formal quotation, including pricing, availability, warranty and delivery timelines, to this email address.")
                .append("</div></td></tr>");

        h.append("<tr><td style=\"padding:12px 28px 0 28px;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:").append(SOFT)
                .append(";border-radius:10px;\"><tr>")
                .append(metaCell("Reference", q.getReference()))
                .append(metaCell("Received", date(q.getCreatedAt())))
                .append(metaCell("Items", String.valueOf(q.getItems().size())))
                .append("</tr></table></td></tr>");

        h.append("<tr><td style=\"padding:20px 28px 0 28px;\">").append(sectionTitle("Your request"))
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"font-size:13px;\">");
        for (QuoteItem it : q.getItems()) {
            h.append("<tr><td style=\"padding:9px 0;border-bottom:1px solid ").append(LINE).append(";color:").append(INK).append(";\">")
                    .append("<b>").append(esc(it.getName())).append("</b>");
            if (notBlank(it.getSelectedOptions())) {
                h.append("<div style=\"font-size:11px;color:").append(MUTED).append(";margin-top:2px;\">").append(esc(it.getSelectedOptions())).append("</div>");
            }
            h.append("</td><td align=\"right\" valign=\"top\" style=\"padding:9px 0;border-bottom:1px solid ").append(LINE)
                    .append(";color:").append(MUTED).append(";white-space:nowrap;\">Qty ").append(it.getQuantity()).append("</td></tr>");
        }
        h.append("</table></td></tr>");

        h.append("<tr><td style=\"padding:20px 28px 26px 28px;font-size:13px;line-height:20px;color:").append(MUTED).append(";\">")
                .append("<b style=\"color:").append(INK).append(";\">What happens next</b><br/>")
                .append("1. Our team reviews your request and sources the best pricing.<br/>")
                .append("2. You receive a formal quotation with a PDF copy by email.<br/>")
                .append("3. Pay using the banking details on the quotation and we get your order moving.")
                .append("</td></tr>");

        footer(h);
        close(h, false);
        return h.toString();
    }

    /** Short internal alert for the OSC team. */
    public String teamNotificationHtml(Quote q) {
        StringBuilder h = new StringBuilder();
        h.append("<div style=\"font-family:").append(FONT).append(";font-size:14px;color:").append(INK).append(";\">")
                .append("<h3 style=\"margin:0 0 8px;color:").append(RED).append(";\">New quote request ").append(esc(q.getReference())).append("</h3>")
                .append("<p style=\"margin:0 0 6px;\"><b>").append(esc(q.customerDisplayName())).append("</b> (")
                .append(q.getCustomerType() == CustomerType.COMPANY ? "company" : "individual").append(")</p>")
                .append("<p style=\"margin:0 0 6px;\">Contact: ").append(esc(q.getContactName())).append(" &lt;").append(esc(q.getContactEmail())).append("&gt;</p>")
                .append("<p style=\"margin:0 0 6px;\">Items: ").append(q.getItems().size()).append(", attachments: ").append(q.getAttachments().size()).append("</p>")
                .append("<p style=\"margin:0;\">Open the admin panel to price and process it.</p></div>");
        return h.toString();
    }

    // =====================================================================
    // Layout pieces
    // =====================================================================

    private void open(StringBuilder h, boolean forPdf, String title) {
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>")
                .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>")
                .append("<title>").append(esc(title)).append("</title>");
        if (forPdf) {
            h.append("<style type=\"text/css\">@page { size: A4; margin: 0; } body { margin: 0; padding: 0; }</style>");
        }
        h.append("</head><body style=\"margin:0;padding:0;background:").append(forPdf ? "#ffffff" : "#EDEDF0")
                .append(";font-family:").append(FONT).append(";\">");
        if (forPdf) {
            h.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#ffffff;\">");
        } else {
            h.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#EDEDF0;\"><tr><td align=\"center\" style=\"padding:24px 10px;\">")
                    .append("<table width=\"640\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:640px;max-width:100%;background:#ffffff;border-radius:14px;\">");
        }
    }

    private void close(StringBuilder h, boolean forPdf) {
        h.append("</table>");
        if (!forPdf) {
            // the email variant is wrapped in an outer centring table
            h.append("</td></tr></table>");
        }
        h.append("</body></html>");
    }

    private void footer(StringBuilder h) {
        AppProperties.Company c = props.getCompany();
        h.append("<tr><td style=\"background:").append(GREY).append(";padding:20px 28px;color:#ffffff;font-size:12px;line-height:19px;\">")
                .append("<b style=\"font-size:13px;\">").append(esc(c.getName())).append("</b><br/>")
                .append(esc(c.getEmail())).append(" &#160;|&#160; ").append(esc(c.getPhone())).append(" &#160;|&#160; ").append(esc(c.getWebsite()))
                .append("<div style=\"margin-top:8px;font-size:10px;color:#D1D1D6;\">")
                .append("This message was sent by ").append(esc(c.getName()))
                .append(" in response to your request. Prices are quoted in the currency shown and are subject to availability until the validity date.")
                .append("</div></td></tr>");
    }

    private String logo(boolean forPdf) {
        String src = forPdf ? logoDataUri() : props.getMail().getLogoUrl();
        return "<img src=\"" + esc(src) + "\" alt=\"" + esc(props.getCompany().getName())
                + "\" height=\"42\" style=\"height:42px;width:auto;display:block;border:0;\"/>";
    }

    private String logoDataUri() {
        String cached = logoDataUri;
        if (cached == null) {
            byte[] bytes = loadLogoBytes();
            cached = bytes == null || bytes.length == 0
                    ? ""
                    : "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            logoDataUri = cached;
        }
        return cached;
    }

    private byte[] loadLogoBytes() {
        for (String path : List.of("/branding/logo-white.png", "/branding/logo.png")) {
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in != null) return in.readAllBytes();
            } catch (Exception ignored) {
                /* try next */
            }
        }
        String url = props.getMail().getLogoUrl();
        if (url != null && !url.isBlank()) {
            try {
                var conn = URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(4000);
                try (InputStream in = conn.getInputStream()) {
                    return in.readAllBytes();
                }
            } catch (Exception ex) {
                log.warn("Could not fetch quote logo from {}: {}", url, ex.getMessage());
            }
        }
        return null;
    }

    private String metaCell(String label, String value) {
        return "<td width=\"33%\" valign=\"top\" style=\"padding:10px 14px;\">"
                + "<div style=\"font-size:10px;color:" + MUTED + ";text-transform:uppercase;letter-spacing:1px;\">" + esc(label) + "</div>"
                + "<div style=\"font-size:12px;color:" + INK + ";margin-top:2px;font-weight:bold;\">" + esc(blankToDash(value)) + "</div></td>";
    }

    private String sectionTitle(String htmlText) {
        return "<div style=\"font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;color:" + RED
                + ";margin-bottom:8px;\">" + htmlText + "</div>";
    }

    private void itemTable(StringBuilder h, String title, List<QuoteItem> items, BigDecimal subtotal, Quote q) {
        h.append("<div style=\"font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;color:").append(RED)
                .append(";margin:8px 0;\">").append(esc(title)).append("</div>")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"font-size:12px;margin-bottom:10px;\">")
                .append("<tr style=\"background:").append(SOFT).append(";\">")
                .append(th("Item", "left", null)).append(th("Qty", "center", "46"))
                .append(th("Unit price", "right", "92")).append(th("Total", "right", "98")).append("</tr>");
        for (QuoteItem it : items) {
            h.append("<tr><td valign=\"top\" style=\"padding:9px 8px;border-bottom:1px solid ").append(LINE).append(";color:").append(INK).append(";\">")
                    .append("<b>").append(esc(it.getName())).append("</b>");
            String sub = joinNonBlank(" | ", it.getSku(), it.getBrandName());
            if (!sub.isEmpty()) {
                h.append("<div style=\"font-size:10px;color:").append(MUTED).append(";margin-top:1px;\">").append(esc(sub)).append("</div>");
            }
            if (notBlank(it.getSelectedOptions())) {
                h.append("<div style=\"font-size:10px;color:").append(MUTED).append(";margin-top:1px;\">").append(esc(it.getSelectedOptions())).append("</div>");
            }
            List<String> extra = new ArrayList<>();
            if (notBlank(it.getAvailability())) extra.add("Availability: " + it.getAvailability());
            if (notBlank(it.getWarranty())) extra.add("Warranty: " + it.getWarranty());
            if (notBlank(it.getDeliveryTimeline())) extra.add("Delivery: " + it.getDeliveryTimeline());
            if (notBlank(it.getSourcing())) extra.add("Sourcing: " + it.getSourcing());
            if (!extra.isEmpty()) {
                h.append("<div style=\"font-size:10px;color:").append(MUTED).append(";margin-top:3px;\">").append(esc(String.join("  |  ", extra))).append("</div>");
            }
            if (notBlank(it.getNotes())) {
                h.append("<div style=\"font-size:10px;color:").append(MUTED).append(";margin-top:3px;\">").append(esc(it.getNotes())).append("</div>");
            }
            h.append("</td>")
                    .append(td(String.valueOf(it.getQuantity()), "center", false))
                    .append(td(it.getUnitPrice() == null ? "TBC" : money(q, it.getUnitPrice()), "right", false))
                    .append(td(it.getLineTotal() == null ? "TBC" : money(q, it.getLineTotal()), "right", true))
                    .append("</tr>");
        }
        h.append("</table>");
    }

    private String th(String text, String align, String width) {
        return "<td align=\"" + align + "\"" + (width == null ? "" : " width=\"" + width + "\"")
                + " style=\"padding:7px 8px;font-size:10px;color:" + MUTED + ";text-transform:uppercase;letter-spacing:1px;\">" + esc(text) + "</td>";
    }

    private String td(String text, String align, boolean bold) {
        return "<td align=\"" + align + "\" valign=\"top\" style=\"padding:9px 8px;border-bottom:1px solid " + LINE + ";color:" + INK
                + ";white-space:nowrap;" + (bold ? "font-weight:bold;" : "") + "\">" + esc(text) + "</td>";
    }

    private void totalRow(StringBuilder h, String label, String value, boolean bold) {
        h.append("<tr><td style=\"padding:6px 12px;color:").append(MUTED).append(";\">").append(esc(label))
                .append("</td><td align=\"right\" style=\"padding:6px 12px;color:").append(INK).append(bold ? ";font-weight:bold;" : ";").append("\">")
                .append(esc(value)).append("</td></tr>");
    }

    private String bankRow(String label, String value) {
        return "<tr><td width=\"38%\" style=\"padding:2px 0;color:" + MUTED + ";\">" + esc(label)
                + "</td><td style=\"padding:2px 0;color:" + INK + ";font-weight:bold;\">" + esc(blankToDash(value)) + "</td></tr>";
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<QuoteItem> itemsOf(Quote q, ProductType type) {
        return q.getItems().stream().filter(i -> i.getType() == type).collect(Collectors.toList());
    }

    private void addTerm(List<String[]> terms, String label, String value) {
        if (notBlank(value)) {
            terms.add(new String[]{label, value});
        }
    }

    private String greetingName(Quote q) {
        return q.customerDisplayName();
    }

    private String date(java.time.Instant instant) {
        return instant == null ? "-" : DATE.format(instant.atZone(ZoneId.of("Africa/Johannesburg")));
    }

    private String money(Quote q, BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return moneySymbol(q.getCurrency()) + " " + String.format(Locale.US, "%,.2f", v).replace(',', ' ');
    }

    private String moneySymbol(String currency) {
        if (currency == null || "NAD".equalsIgnoreCase(currency)) return "N$";
        if ("ZAR".equalsIgnoreCase(currency)) return "R";
        return currency;
    }

    private boolean positive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    private String stripZeros(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String blankToDash(String s) {
        return notBlank(s) ? s : "-";
    }

    private String joinNonBlank(String sep, String... parts) {
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            if (notBlank(p)) {
                list.add(p.trim());
            }
        }
        return String.join(sep, list);
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> {
                    // strip control characters that are not valid in XML
                    if (c >= 0x20 || c == '\n' || c == '\t' || c == '\r') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private String escMultiline(String s) {
        return esc(s).replace("\r\n", "\n").replace("\n", "<br/>");
    }
}
