package com.be.certificate.service;

import com.be.certificate.dto.CertificateResponse;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CertificatePdfRenderer {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
    private static final Color INK = new Color(29, 47, 70);
    private static final float WIDTH = PDRectangle.A4.getWidth();
    private static final float TEXT_WIDTH = WIDTH - 140;
    private final Resource regular;
    private final Resource bold;

    public CertificatePdfRenderer() {
        this(new ClassPathResource("fonts/NanumGothic-Regular.ttf"),
                new ClassPathResource("fonts/NanumGothic-Bold.ttf"));
    }

    CertificatePdfRenderer(Resource regular, Resource bold) {
        this.regular = regular;
        this.bold = bold;
    }

    public byte[] render(CertificateResponse certificate) {
        // PDFBox 문서와 폰트는 요청마다 생성하며, 설치 폰트/파일 시스템 경로를 사용하지 않는다.
        try (var document = new PDDocument();
             var regularStream = regular.getInputStream();
             var boldStream = bold.getInputStream();
             var output = new ByteArrayOutputStream()) {
            var font = PDType0Font.load(document, regularStream, true);
            var heading = PDType0Font.load(document, boldStream, true);
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.setStrokingColor(INK);
                content.setLineWidth(1.2f);
                content.addRect(40, 40, WIDTH - 80, PDRectangle.A4.getHeight() - 80);
                content.stroke();
                content.setNonStrokingColor(INK);
                centered(content, heading, 34, 725, "수료증");
                centered(content, font, 11, 655, "직원 이름");
                fittedBlock(content, heading, certificate.memberName(), 622, 112, 24);
                centered(content, font, 11, 476, "과정명");
                fittedBlock(content, heading, certificate.courseTitle(), 445, 155, 22);
                centered(content, font, 12, 255, "위 교육과정을 수료하였음을 증명합니다.");
                centered(content, font, 12, 210, "수료일  " + date(certificate.completedAt()));
                centered(content, font, 12, 183, "발급일  " + date(certificate.issuedAt()));
                centered(content, font, 10, 120, "수료증 번호");
                centered(content, font, 10, 98, certificate.certificateNumber());
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            log.error("수료증 PDF 생성 실패", exception);
            throw new BusinessException(ErrorCode.CERTIFICATE_PDF_GENERATION_FAILED);
        }
    }

    private static String date(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(SEOUL).format(DATE);
    }

    // 최대 길이의 한글/공백 없는 문자열도 생략하지 않고 줄바꿈과 축소로 정해진 영역에 배치한다.
    private static void fittedBlock(PDPageContentStream content, PDType0Font font, String value,
                                    float top, float height, int maximumSize) throws IOException {
        String text = Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("\\s+", " ").strip();
        for (int size = maximumSize; size >= 10; size--) {
            List<String> lines = wrap(font, text, size);
            float leading = size * 1.5f;
            if (lines.size() * leading <= height) {
                for (int i = 0; i < lines.size(); i++) centered(content, font, size, top - i * leading, lines.get(i));
                return;
            }
        }
        throw new IOException("Certificate text exceeds layout capacity");
    }

    private static List<String> wrap(PDType0Font font, String text, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        var line = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            String next = new String(Character.toChars(point));
            if (!line.isEmpty() && font.getStringWidth(line + next) * size / 1000 > TEXT_WIDTH) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            if (!line.isEmpty() || !next.isBlank()) line.append(next);
            offset += Character.charCount(point);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static void centered(PDPageContentStream content, PDType0Font font, float size,
                                 float y, String text) throws IOException {
        float width = font.getStringWidth(text) * size / 1000;
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset((WIDTH - width) / 2, y);
        content.showText(text);
        content.endText();
    }
}
