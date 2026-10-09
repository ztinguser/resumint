package com.resumint.export;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class PlaywrightPdfRenderer implements PdfRenderer {
    private final ExportLimits limits;

    public PlaywrightPdfRenderer(ExportLimits limits) {
        this.limits = limits;
    }

    @Override
    public void render(ExportSnapshot snapshot, Path output) {
        renderPage("single-column", snapshot.document().toString(), output);
    }

    public void renderFixture(String fixtureId, Path output) {
        renderPage(fixtureId, null, output);
    }

    private void renderPage(String fixtureId, String documentJson, Path output) {
        URI base = limits.printBaseUrl();
        String fixture = URLEncoder.encode(fixtureId, StandardCharsets.UTF_8);
        String target = base.resolve("/print?fixture=" + fixture).toString();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext()) {
            context.route("**/*", route -> {
                if (isTrusted(route.request().url(), base)) route.resume();
                else route.abort();
            });
            Page page = context.newPage();
            page.setDefaultTimeout(limits.renderTimeout().toMillis());
            page.navigate(target);
            page.waitForFunction("() => window.__RESUMINT_READY__ === true");
            if (documentJson != null) {
                page.evaluate("json => window.renderResume(JSON.parse(json))", documentJson);
                page.waitForFunction("() => window.__RESUMINT_READY__ === true");
            }
            page.evaluate("() => document.fonts.ready");
            page.waitForFunction("() => Array.from(document.images).every(image => image.complete && image.naturalWidth > 0)");
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(com.microsoft.playwright.options.Media.PRINT));
            page.pdf(new Page.PdfOptions()
                    .setPath(output)
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setPreferCSSPageSize(true));
        }
    }

    private static boolean isTrusted(String rawUrl, URI base) {
        URI candidate = URI.create(rawUrl);
        if ("data".equalsIgnoreCase(candidate.getScheme())) return true;
        return same(candidate.getScheme(), base.getScheme())
                && same(candidate.getHost(), base.getHost())
                && effectivePort(candidate) == effectivePort(base);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
