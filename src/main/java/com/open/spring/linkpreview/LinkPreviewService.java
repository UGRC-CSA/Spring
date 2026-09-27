package com.open.spring.linkpreview;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import com.open.spring.scraper.security.UrlSafetyValidator;

import lombok.RequiredArgsConstructor;

/**
 * Slack-style link unfurling: fetches a URL server-side (the browser can't do this itself for
 * arbitrary third-party sites due to CORS) and reads its Open Graph tags. Reuses the same
 * SSRF-safe fetch pattern already used by {@link com.open.spring.scraper.GenericScraperService}.
 */
@Service
@RequiredArgsConstructor
public class LinkPreviewService {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 5000;
    private static final long CACHE_TTL_MINUTES = 30;

    private final UrlSafetyValidator urlSafetyValidator;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(LinkPreview preview, Instant expiresAt) {
    }

    /**
     * @throws IllegalArgumentException if the url is missing, malformed, or points at a
     *                                   local/internal/private-network target (via {@link UrlSafetyValidator}).
     */
    public LinkPreview fetch(String url) {
        String trimmed = url == null ? null : url.trim();
        urlSafetyValidator.validateTargetUrl(trimmed);

        CacheEntry cached = cache.get(trimmed);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.preview();
        }

        LinkPreview preview = fetchUncached(trimmed);
        cache.put(trimmed, new CacheEntry(preview, Instant.now().plus(CACHE_TTL_MINUTES, ChronoUnit.MINUTES)));
        return preview;
    }

    private LinkPreview fetchUncached(String url) {
        String host = safeHost(url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(2 * 1024 * 1024)
                    .get();

            String title = firstNonBlank(
                    metaContent(doc, "meta[property=og:title]"),
                    doc.title(),
                    host);
            String description = firstNonBlank(
                    metaContent(doc, "meta[property=og:description]"),
                    metaContent(doc, "meta[name=description]"),
                    "");
            String image = toAbsoluteUrl(url, metaContent(doc, "meta[property=og:image]"));
            String siteName = firstNonBlank(metaContent(doc, "meta[property=og:site_name]"), host);

            return new LinkPreview(url, title, description, image, siteName);
        } catch (IOException | RuntimeException ex) {
            // Unreachable, timed out, not HTML, etc. — degrade to a plain-link fallback rather
            // than surfacing an error; the frontend just skips rendering a preview card.
            return new LinkPreview(url, host, "", null, host);
        }
    }

    private String metaContent(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        if (el == null) {
            return null;
        }
        String content = el.attr("content");
        return content == null || content.isBlank() ? null : content.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String toAbsoluteUrl(String base, String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        try {
            URI baseUri = URI.create(base);
            if (pathOrUrl.startsWith("/")) {
                return baseUri.getScheme() + "://" + baseUri.getHost() + pathOrUrl;
            }
            return baseUri.resolve(pathOrUrl).toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String safeHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (RuntimeException ex) {
            return url;
        }
    }
}
