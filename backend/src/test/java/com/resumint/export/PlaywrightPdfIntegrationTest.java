package com.resumint.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "resumint.pdf.it", matches = "true")
class PlaywrightPdfIntegrationTest {
    private static final URI PRINT_BASE = URI.create("http://127.0.0.1:3200");
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("single-column", 1, List.of("林知夏", "工作经历", "同济大学"), List.of("https://example.com/portfolio", "https://example.com/case-study")),
            new Fixture("multi-column", 1, List.of("核心经历", "技能", "异步 PDF 验证"), List.of("https://example.com/portfolio")),
            new Fixture("portrait-decoration", 1, List.of("头像、色块和分隔线"), List.of("https://example.com/portfolio")),
            new Fixture("long-content", 3, List.of("复杂项目阶段 1", "复杂项目阶段 12", "中文可复制文本"), List.of("https://example.com/portfolio")),
            new Fixture("long-link", 1, List.of("连续链接必须换行"), List.of("https://example.com/projects/resumint/verification/this-is-a-deliberately-long-link-that-must-wrap-without-overflow")),
            new Fixture("image-overflow", 1, List.of("受控头像槽位"), List.of("https://example.com/portfolio")),
            new Fixture("font-failure", 1, List.of("中文仍可读取"), List.of("https://example.com/portfolio"))
    );

    private Process frontend;

    @BeforeAll
    void startFrontend() throws Exception {
        Path frontendDirectory = Path.of("..").resolve("frontend").toAbsolutePath().normalize();
        Path next = frontendDirectory.resolve(Path.of("node_modules", "next", "dist", "bin", "next"));
        frontend = new ProcessBuilder("node", next.toString(), "start", "--port", "3200")
                .directory(frontendDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (!frontend.isAlive()) throw new IllegalStateException("Next.js print server stopped during startup");
            try {
                var connection = (HttpURLConnection) PRINT_BASE.resolve("/print").toURL().openConnection();
                connection.setConnectTimeout(500);
                connection.setReadTimeout(500);
                if (connection.getResponseCode() == 200) return;
            } catch (IOException ignored) { }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Next.js print server did not become ready");
    }

    @AfterAll
    void stopFrontend() throws Exception {
        if (frontend == null) return;
        frontend.destroy();
        if (!frontend.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) frontend.destroyForcibly();
    }

    @Test
    void generalTemplateHasNoBlankTrailingPage(@TempDir Path outputDirectory) throws Exception {
        Path pdf=outputDirectory.resolve("general-default.pdf");
        new PlaywrightPdfRenderer(ExportLimits.m1Defaults(PRINT_BASE)).renderFixture("general-default",pdf);
        try(PDDocument document=Loader.loadPDF(pdf.toFile())){
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            PDFTextStripper stripper=new PDFTextStripper();
            assertThat(stripper.getText(document).replaceAll("\\s+","")).contains("林清远","个人简介","个人总结");
        }
    }

    @Test
    void fixedFixturesHaveStablePagesTextLinksAndScreenshots(@TempDir Path outputDirectory) throws Exception {
        var renderer = new PlaywrightPdfRenderer(ExportLimits.m1Defaults(PRINT_BASE));
        for (Fixture fixture : FIXTURES) {
            Path pdf = outputDirectory.resolve(fixture.id() + ".pdf");
            renderer.renderFixture(fixture.id(), pdf);
            verifyPdf(fixture, pdf);
        }
    }

    @Test
    void queuedSnapshotIsRenderedByTheWorkerAsynchronously(@TempDir Path outputDirectory) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var repository = new InMemoryExportJobRepository();
        var limits = ExportLimits.m1Defaults(PRINT_BASE);
        var storage = new ExportStorage(outputDirectory, objectMapper);
        var coordinator = new ExportCoordinator(repository, new ResumeDocumentSchema(objectMapper), limits, objectMapper, Clock.systemUTC(), storage,
                new ExportCredentialService("integration-export-credential-secret-32-chars"));
        var document = objectMapper.readTree("""
                {
                  "schemaVersion":"1.0",
                  "documentId":"async-export",
                  "template":{"id":"benchmark-template","version":1},
                  "locale":"zh-CN",
                  "profile":{"fullName":"异步快照","headline":"Java Playwright worker","email":"worker@example.com","website":{"label":"导出链接","href":"https://example.com/export"}},
                  "layout":{"kind":"single","page":{"size":"A4","marginMm":16},"theme":{"fontFamily":"Arial","accentColor":"#244b45"},"decorations":[]},
                  "sections":[{"id":"async-section","type":"custom","title":"不可变文档","column":"main","visible":true,"items":[{"id":"async-item","title":"后台生成","summary":"worker 从队列租约中取得提交快照。"}]}]
                }
                """);
        ExportJob job = coordinator.submit("async-worker-key", new ExportSnapshot(document)).job();
        var worker = new ExportWorker(repository, new PlaywrightPdfRenderer(limits), limits, Clock.systemUTC(), storage);

        assertThat(CompletableFuture.supplyAsync(() -> worker.runOnce("worker-integration")).join()).isTrue();
        ExportJob completed = repository.findById(job.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(ExportStatus.SUCCEEDED);
        assertThat(storage.exists(completed.resultRef())).isTrue();
        try (PDDocument pdf = Loader.loadPDF(storage.resultPath(completed.resultRef()).toFile())) {
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+", ""))
                    .contains("异步快照", "不可变文档", "worker从队列租约中取得提交快照。");
            assertThat(extractLinks(pdf)).contains("https://example.com/export");
        }
    }

    private void verifyPdf(Fixture fixture, Path pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertThat(document.getNumberOfPages()).as(fixture.id() + " page count").isEqualTo(fixture.pages());
            PDFTextStripper stripper = new PDFTextStripper();
            String allText = stripper.getText(document);
            String normalizedText = allText.replaceAll("\\s+", "");
            assertThat(normalizedText).as(fixture.id() + " extractable text")
                    .contains(fixture.text().stream().map(text -> text.replaceAll("\\s+", "")).toArray(String[]::new));
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(document).strip()).as(fixture.id() + " page " + page + " must not be blank").hasSizeGreaterThan(20);
            }
            Set<String> links = extractLinks(document);
            assertThat(links).as(fixture.id() + " links").containsAll(fixture.links());
            verifyScreenshots(fixture, document);
        }
    }

    private Set<String> extractLinks(PDDocument document) throws IOException {
        Set<String> links = new HashSet<>();
        for (var page : document.getPages()) {
            for (var annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action) {
                    links.add(action.getURI());
                }
            }
        }
        return links;
    }

    private void verifyScreenshots(Fixture fixture, PDDocument document) throws IOException {
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        for (int index = 0; index < document.getNumberOfPages(); index++) {
            BufferedImage actual = pdfRenderer.renderImageWithDPI(index, 96, ImageType.RGB);
            String name = fixture.id() + "-page-" + (index + 1) + ".png";
            Path baseline = Path.of("src", "test", "resources", "pdf-baselines", name);
            if (Boolean.getBoolean("updatePdfSnapshots")) {
                Files.createDirectories(baseline.getParent());
                ImageIO.write(actual, "png", baseline.toFile());
                continue;
            }
            assertThat(baseline).as("baseline " + name).exists();
            BufferedImage expected = ImageIO.read(baseline.toFile());
            assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
            assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
            assertThat(pixelDifference(expected, actual)).as(name + " pixel difference").isLessThanOrEqualTo(0.01);
        }
    }

    private double pixelDifference(BufferedImage expected, BufferedImage actual) {
        long different = 0;
        long pixels = (long) expected.getWidth() * expected.getHeight();
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (expected.getRGB(x, y) != actual.getRGB(x, y)) different++;
            }
        }
        return different / (double) pixels;
    }

    private record Fixture(String id, int pages, List<String> text, List<String> links) { }
}
