package com.be.certificate.service;

import com.be.certificate.dto.CertificateResponse;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CertificatePdfRendererTest {
    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer();

    @Test void koreanPdfIsSingleA4WithEmbeddedFontsAndSeoulDates() throws Exception {
        byte[] bytes = renderer.render(response("홍길동", "개인정보 보호 및 보안 교육"));
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(document.getPage(0).getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
            for (var name : document.getPage(0).getResources().getFontNames()) {
                assertThat(document.getPage(0).getResources().getFont(name).isEmbedded()).isTrue();
            }
            assertThat(new PDFTextStripper().getText(document)).contains("수료증", "홍길동", "개인정보 보호 및 보안 교육",
                    "2026년 09월 21일", "2026년 09월 30일", response("", "").certificateNumber());
            assertInsidePage(document);
        }
        saveForVisualQa("certificate-korean.pdf", bytes);
    }

    @Test void maximumLengthKoreanAndUnbrokenLatinStringsFitWithoutTruncation() throws Exception {
        for (String character : new String[]{"한", "W"}) {
            String member = character.repeat(100);
            String course = character.repeat(200);
            byte[] bytes = renderer.render(response(member, course));
            try (var document = Loader.loadPDF(bytes)) {
                assertThat(document.getNumberOfPages()).isEqualTo(1);
                String text = new PDFTextStripper().getText(document).replaceAll("\\s", "");
                assertThat(text).contains(member, course);
                assertInsidePage(document);
            }
            saveForVisualQa("certificate-long-" + (character.equals("한") ? "korean" : "latin") + ".pdf", bytes);
        }
    }

    @Test void whitespaceAndDecomposedHangulAreNormalizedForDisplay() throws Exception {
        try (var document = Loader.loadPDF(renderer.render(response("한\n글", "보안\t교육\r\n과정")))) {
            assertThat(new PDFTextStripper().getText(document)).contains("한 글", "보안 교육 과정");
        }
    }

    @Test void missingFontFailsWithSanitizedError() {
        var missing = new ClassPathResource("fonts/not-installed.ttf");
        var broken = new CertificatePdfRenderer(missing, missing);
        assertThatThrownBy(() -> broken.render(response("직원", "과정")))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CERTIFICATE_PDF_GENERATION_FAILED);
                    assertThat(error.getMessage()).doesNotContain("fonts", "not-installed");
                });
    }

    private static CertificateResponse response(String member, String course) {
        return new CertificateResponse(3L, "LF-CERT-0123456789abcdef0123456789abcdef", member, course,
                LocalDateTime.of(2026, 9, 20, 16, 30), LocalDateTime.of(2026, 9, 29, 16, 30));
    }

    private static void assertInsidePage(PDDocument document) throws Exception {
        new PDFTextStripper() {
            @Override protected void processTextPosition(TextPosition text) {
                assertThat(text.getXDirAdj()).isGreaterThanOrEqualTo(60);
                assertThat(text.getXDirAdj() + text.getWidthDirAdj()).isLessThan(PDRectangle.A4.getWidth() - 60);
                assertThat(text.getYDirAdj()).isBetween(70f, PDRectangle.A4.getHeight() - 70);
                super.processTextPosition(text);
            }
        }.getText(document);
    }

    private static void saveForVisualQa(String name, byte[] bytes) throws Exception {
        if (Boolean.getBoolean("certificate.visualQa")) {
            var directory = Path.of("target", "certificate-qa");
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), bytes);
        }
    }
}
