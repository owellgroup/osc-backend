package com.osc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osc.store.service.EmailService;
import com.osc.store.service.FileStorageService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:osc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class QuoteFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.osc.store.service.QuoteDocumentService documents;
    @MockBean JavaMailSender mailSender;
    @MockBean FileStorageService storage;
    @SpyBean EmailService emailService;

    private String login() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joel@wayaspot.com\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void fullQuoteFlow() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
        when(storage.storeDocument(any())).thenReturn(new FileStorageService.StoredFile("documents/x.pdf", "BOM.pdf", 12));
        String token = login();

        // public catalogue is seeded and has no prices
        MvcResult cat = mvc.perform(get("/api/catalog/products?size=50")).andExpect(status().isOk()).andReturn();
        JsonNode items = json.readTree(cat.getResponse().getContentAsString()).get("items");
        assertTrue(items.size() >= 8);
        assertFalse(cat.getResponse().getContentAsString().toLowerCase().contains("price"));
        long productId = 0, solutionId = 0;
        for (JsonNode n : items) {
            if (productId == 0 && "PRODUCT".equals(n.get("type").asText()) && n.get("options").size() > 0) productId = n.get("id").asLong();
            if (solutionId == 0 && "SOLUTION".equals(n.get("type").asText())) solutionId = n.get("id").asLong();
        }

        // admin endpoints reject anonymous callers
        mvc.perform(get("/api/admin/quotes")).andExpect(status().isUnauthorized());

        // company submits a request with a document
        String body = """
                {"customerType":"COMPANY","companyName":"Acme Mining (Pty) Ltd","companyEmail":"procurement@acme.test",
                 "companyAddress":"1 Main Rd, Sandton","companyLocation":"Johannesburg, Gauteng",
                 "contactName":"Jane Doe","contactEmail":"jane@acme.test","contactJobTitle":"Procurement",
                 "deliveryAddress":"Plot 5, Industrial Park","deliveryLocation":"Pretoria","questions":"Can you install?",
                 "items":[{"productId":%d,"quantity":2,"options":{}},{"productId":%d,"quantity":1}]}
                """.formatted(productId, solutionId);
        MockMultipartFile quotePart = new MockMultipartFile("quote", "", "application/json", body.getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "BOM.pdf", "application/pdf", "pdf".getBytes());
        MvcResult sub = mvc.perform(multipart("/api/quotes").file(quotePart).file(file)).andExpect(status().isCreated()).andReturn();
        JsonNode created = json.readTree(sub.getResponse().getContentAsString());
        assertEquals("procurement@acme.test", created.get("email").asText());
        verify(emailService, timeout(3000)).sendAsync(eq("procurement@acme.test"), anyString(), anyString());
        verify(emailService, timeout(3000)).sendAsync(eq("joel@wayaspot.com"), anyString(), anyString());

        // admin sees it
        MvcResult list = mvc.perform(get("/api/admin/quotes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = json.readTree(list.getResponse().getContentAsString()).get("items").get(0);
        assertEquals(1, first.get("productCount").asInt());
        assertEquals(1, first.get("solutionCount").asInt());
        long id = first.get("id").asLong();

        JsonNode detail = json.readTree(mvc.perform(get("/api/admin/quotes/" + id).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, detail.get("attachments").size());

        // processing without prices is rejected
        String unpriced = "{\"items\":[]}";
        mvc.perform(post("/api/admin/quotes/" + id + "/process").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(unpriced)).andExpect(status().isBadRequest());

        // price it and process
        long i1 = detail.get("items").get(0).get("id").asLong();
        long i2 = detail.get("items").get(1).get("id").asLong();
        String priced = """
                {"deliveryFee":500,"discount":100,"vatPercent":15,"warranty":"3 years onsite","deliveryTimeline":"2-3 weeks",
                 "availability":"In stock","sourcing":"Local distributor","paymentTerms":"Full payment upfront","additionalNotes":"Thanks & regards",
                 "items":[{"id":%d,"unitPrice":12500.50,"warranty":"3 years"},{"id":%d,"unitPrice":45000,"notes":"Includes install"}]}
                """.formatted(i1, i2);
        MvcResult processed = mvc.perform(post("/api/admin/quotes/" + id + "/process").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(priced)).andExpect(status().isOk()).andReturn();
        JsonNode pr = json.readTree(processed.getResponse().getContentAsString());
        assertTrue(pr.get("emailSent").asBoolean(), pr.toString());
        assertEquals("PROCESSED", pr.get("quote").get("status").asText());
        // (2 * 12500.50 + 45000 + 500 - 100) * 1.15 = 80,961.15
        assertEquals(0, new java.math.BigDecimal("80961.15").compareTo(pr.get("quote").get("total").decimalValue()),
                pr.get("quote").get("total").asText());
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        // pdf
        byte[] pdf = mvc.perform(get("/api/admin/quotes/" + id + "/pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4));

        // keep previews for a visual check
        Path out = Path.of("target", "preview");
        Files.createDirectories(out);
        Files.write(out.resolve("quote.pdf"), pdf);
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                javax.imageio.ImageIO.write(renderer.renderImageWithDPI(i, 80), "png", out.resolve("quote-" + (i + 1) + ".png").toFile());
            }
        }
        var quoteEntity = json.readTree(processed.getResponse().getContentAsString());
        assertNotNull(quoteEntity);
    }

    @Test
    void superAdminIsProtected() throws Exception {
        String token = login();
        MvcResult list = mvc.perform(get("/api/admin/admins").header("Authorization", "Bearer " + token)).andReturn();
        JsonNode admins = json.readTree(list.getResponse().getContentAsString());
        long superId = admins.get(0).get("id").asLong();

        mvc.perform(delete("/api/admin/admins/" + superId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/admins/" + superId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"email\":\"x@x.com\",\"password\":\"password123\"}")).andExpect(status().isForbidden());

        // super admin creates a normal admin, who cannot manage admins but can use the catalogue
        mvc.perform(post("/api/admin/admins").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sam\",\"email\":\"sam@osc.test\",\"password\":\"password123\"}")).andExpect(status().isCreated());
        String samToken = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sam@osc.test\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get("/api/admin/admins").header("Authorization", "Bearer " + samToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/quotes").header("Authorization", "Bearer " + samToken)).andExpect(status().isOk());
    }

    @Test
    void bannerLimitIsFour() throws Exception {
        String token = login();
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/admin/banners").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Banner " + i + "\",\"description\":\"A short slide\",\"backgroundColor\":\"#D71920\",\"imagePath\":\"/api/files/images/a" + i + ".png\",\"ctaText\":\"Shop\",\"linkUrl\":\"/product/1\",\"ctaText2\":\"Learn more\",\"linkUrl2\":\"/products\",\"active\":true}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/admin/banners").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Too many\",\"active\":true}")).andExpect(status().isConflict());
        mvc.perform(get("/api/catalog/banners")).andExpect(status().isOk());
    }
}
