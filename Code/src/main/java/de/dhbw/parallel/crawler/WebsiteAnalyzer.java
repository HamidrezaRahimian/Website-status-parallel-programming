package de.dhbw.parallel.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Downloads HTML pages and extracts image URIs from standard {@code <img src="...">} tags.
 */
public final class WebsiteAnalyzer {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    /**
     * Creates a website analyzer using a default {@link HttpClient}.
     */
    public WebsiteAnalyzer() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /**
     * Creates a website analyzer with the given HTTP client.
     *
     * @param httpClient the HTTP client used for website requests
     */
    public WebsiteAnalyzer(final HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * Downloads the given website and extracts supported image URIs.
     *
     * @param websiteUri the website URI to analyze
     * @return a list of resolved image URIs, possibly empty
     * @throws IOException if the website request fails
     * @throws InterruptedException if the current thread is interrupted while waiting for the response
     */
    public List<URI> analyze(final URI websiteUri) throws IOException, InterruptedException {
        Objects.requireNonNull(websiteUri, "websiteUri must not be null");
        if (!isSupportedHttpUri(websiteUri)) {
            return List.of();
        }

        final HttpRequest request = HttpRequest.newBuilder(websiteUri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("User-Agent", "DHBW-Parallel-ImageCrawler/1.0")
                .build();
        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return List.of();
        }

        return extractImageUris(response.body(), websiteUri);
    }

    /**
     * Extracts image URIs from an HTML string and resolves relative URLs against the website URI.
     *
     * @param html the HTML document as text
     * @param websiteUri the base website URI
     * @return the resolved and supported image URIs
     */
    public List<URI> extractImageUris(final String html, final URI websiteUri) {
        Objects.requireNonNull(html, "html must not be null");
        Objects.requireNonNull(websiteUri, "websiteUri must not be null");

        final Document document = Jsoup.parse(html, websiteUri.toString());
        final List<URI> imageUris = new ArrayList<>();
        for (final Element imageElement : document.select("img[src]")) {
            final String absoluteSource = imageElement.absUrl("src");
            if (absoluteSource.isBlank()) {
                continue;
            }
            try {
                final URI imageUri = URI.create(absoluteSource);
                if (isSupportedHttpUri(imageUri)) {
                    imageUris.add(imageUri);
                }
            } catch (final IllegalArgumentException ignored) {
                // Invalid image references are outside the crawler's supported scope and are skipped.
            }
        }
        return List.copyOf(imageUris);
    }

    /**
     * Checks whether the URI uses a supported HTTP scheme.
     *
     * @param uri the URI to check
     * @return {@code true} for HTTP and HTTPS URIs
     */
    private boolean isSupportedHttpUri(final URI uri) {
        final String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
