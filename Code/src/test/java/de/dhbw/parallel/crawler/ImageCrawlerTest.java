package de.dhbw.parallel.crawler;

import de.dhbw.parallel.crawler.ImageCrawler;
import de.dhbw.parallel.crawler.IImageCrawlerConfig;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCrawlerTest {

    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @TempDir
    Path downloadPath;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ImageCrawler crawler;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (crawler != null) {
            crawler.close();
        }
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void crawlDownloadsImagesFromHtmlPage() throws IOException {
        createPage("/page", """
                <html><body>
                    <img src="/images/first.jpg">
                    <img src="/images/second.png">
                </body></html>
                """);
        createImage("/images/first.jpg");
        createImage("/images/second.png");
        crawler = newCrawler(2, 2);

        crawler.crawl(uri("/page"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(Files.exists(downloadPath.resolve("1").resolve("first.jpg")));
        assertTrue(Files.exists(downloadPath.resolve("1").resolve("second.png")));
    }

    @Test
    void crawlResolvesRelativeImageUrls() throws IOException {
        createPage("/gallery/index.html", """
                <html><body>
                    <img src="../images/relative.jpg">
                    <img src="nested/local.png">
                </body></html>
                """);
        createImage("/images/relative.jpg");
        createImage("/gallery/nested/local.png");
        crawler = newCrawler(2, 2);

        crawler.crawl(uri("/gallery/index.html"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(Files.exists(downloadPath.resolve("1").resolve("relative.jpg")));
        assertTrue(Files.exists(downloadPath.resolve("1").resolve("local.png")));
    }

    @Test
    void crawlCreatesNumberedFolderPerWebsite() throws IOException {
        createPage("/one", "<img src=\"/images/a.jpg\">");
        createPage("/two", "<img src=\"/images/b.jpg\">");
        createPage("/three", "<img src=\"/images/c.jpg\">");
        createImage("/images/a.jpg");
        createImage("/images/b.jpg");
        createImage("/images/c.jpg");
        crawler = newCrawler(2, 2);

        crawler.crawl(uri("/one"));
        crawler.crawl(uri("/two"));
        crawler.crawl(uri("/three"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(Files.isDirectory(downloadPath.resolve("1")));
        assertTrue(Files.isDirectory(downloadPath.resolve("2")));
        assertTrue(Files.isDirectory(downloadPath.resolve("3")));
    }

    @Test
    void duplicateImageNamesAreRenamedWithSuffix() throws IOException {
        createPage("/duplicates", """
                <img src="/first/image.jpg">
                <img src="/second/image.jpg">
                <img src="/third/image.jpg">
                """);
        createImage("/first/image.jpg");
        createImage("/second/image.jpg");
        createImage("/third/image.jpg");
        crawler = newCrawler(1, 3);

        crawler.crawl(uri("/duplicates"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(Files.exists(downloadPath.resolve("1").resolve("image.jpg")));
        assertTrue(Files.exists(downloadPath.resolve("1").resolve("image_2.jpg")));
        assertTrue(Files.exists(downloadPath.resolve("1").resolve("image_3.jpg")));
    }

    @Test
    void firstDuplicateKeepsOriginalNameInHtmlOrder() throws Exception {
        final byte[] firstBytes = "first".getBytes(StandardCharsets.UTF_8);
        final byte[] secondBytes = "second".getBytes(StandardCharsets.UTF_8);
        final CountDownLatch firstImageRequested = new CountDownLatch(1);
        final CountDownLatch releaseFirstImage = new CountDownLatch(1);
        createPage("/ordered-duplicates", """
                <img src="/first-slow/image.jpg">
                <img src="/second-fast/image.jpg">
                """);
        createBlockingImage("/first-slow/image.jpg", firstImageRequested, releaseFirstImage, firstBytes);
        createImage("/second-fast/image.jpg", secondBytes);
        crawler = newCrawler(1, 2);

        crawler.crawl(uri("/ordered-duplicates"));

        assertTrue(firstImageRequested.await(5, TimeUnit.SECONDS));
        waitUntilFileExists(downloadPath.resolve("1").resolve("image_2.jpg"), Duration.ofSeconds(5));
        releaseFirstImage.countDown();
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertArrayEquals(firstBytes, Files.readAllBytes(downloadPath.resolve("1").resolve("image.jpg")));
        assertArrayEquals(secondBytes, Files.readAllBytes(downloadPath.resolve("1").resolve("image_2.jpg")));
    }

    @Test
    void queryStringsMissingNamesAndMissingExtensionsUseCleanFileNames() throws IOException {
        createPage("/filename-edge-cases", """
                <img src="/assets/photo.jpg?version=42">
                <img src="/files/download">
                <img src="/folder/">
                """);
        createImage("/assets/photo.jpg");
        createImage("/files/download");
        createImage("/folder/");
        crawler = newCrawler(1, 3);

        crawler.crawl(uri("/filename-edge-cases"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(Files.exists(downloadPath.resolve("1").resolve("photo.jpg")));
        assertTrue(Files.exists(downloadPath.resolve("1").resolve("download")));
        assertTrue(Files.exists(downloadPath.resolve("1").resolve("image")));
    }

    @Test
    void websiteScanParallelLimitIsRespected() throws Exception {
        final int allowedScans = 2;
        final ConcurrencyProbe probe = new ConcurrencyProbe(allowedScans);
        createBlockingPage("/slow-page", probe);
        crawler = newCrawler(allowedScans, 2);

        for (int i = 0; i < 6; i++) {
            crawler.crawl(uri("/slow-page?request=" + i));
        }

        assertTrue(probe.awaitExpectedEntries(Duration.ofSeconds(5)));
        assertEquals(allowedScans, probe.maxConcurrent());
        assertFalse(crawler.isIdle());
        probe.release();
        waitUntilIdle(crawler, Duration.ofSeconds(5));
        assertTrue(probe.maxConcurrent() <= allowedScans);
    }

    @Test
    void imageDownloadParallelLimitIsRespected() throws Exception {
        final int allowedDownloads = 2;
        final ConcurrencyProbe probe = new ConcurrencyProbe(allowedDownloads);
        createPage("/image-limit", """
                <img src="/slow-image/1.jpg">
                <img src="/slow-image/2.jpg">
                <img src="/slow-image/3.jpg">
                <img src="/slow-image/4.jpg">
                """);
        createBlockingImagePrefix("/slow-image/", probe);
        crawler = newCrawler(2, allowedDownloads);

        crawler.crawl(uri("/image-limit"));

        assertTrue(probe.awaitExpectedEntries(Duration.ofSeconds(5)));
        assertEquals(allowedDownloads, probe.maxConcurrent());
        assertFalse(crawler.isIdle());
        probe.release();
        waitUntilIdle(crawler, Duration.ofSeconds(5));
        assertTrue(probe.maxConcurrent() <= allowedDownloads);
    }

    @Test
    void websiteAnalysisAndImageDownloadsCanOverlap() throws Exception {
        final ConcurrencyProbe imageProbe = new ConcurrencyProbe(1);
        final ConcurrencyProbe websiteProbe = new ConcurrencyProbe(1);
        createPage("/page-with-slow-image", "<img src=\"/slow-overlap/image.jpg\">");
        createBlockingImagePrefix("/slow-overlap/", imageProbe);
        createBlockingPage("/independent-page", websiteProbe);
        crawler = newCrawler(2, 1);

        crawler.crawl(uri("/page-with-slow-image"));
        assertTrue(imageProbe.awaitExpectedEntries(Duration.ofSeconds(5)));

        crawler.crawl(uri("/independent-page"));

        assertTrue(websiteProbe.awaitExpectedEntries(Duration.ofSeconds(5)));
        assertFalse(crawler.isIdle());
        imageProbe.release();
        websiteProbe.release();
        waitUntilIdle(crawler, Duration.ofSeconds(5));
    }

    @Test
    void isIdleIsFalseWhileWorkIsPendingOrRunning() throws Exception {
        final ConcurrencyProbe probe = new ConcurrencyProbe(1);
        createBlockingPage("/slow-idle", probe);
        crawler = newCrawler(1, 1);

        crawler.crawl(uri("/slow-idle"));

        assertTrue(probe.awaitExpectedEntries(Duration.ofSeconds(5)));
        assertFalse(crawler.isIdle());
        probe.release();
        waitUntilIdle(crawler, Duration.ofSeconds(5));
        assertTrue(crawler.isIdle());
    }

    @Test
    void concurrentCrawlCallsAreThreadSafe() throws Exception {
        final int crawlCalls = 20;
        createPage("/concurrent", "<img src=\"/images/shared.png\">");
        createImage("/images/shared.png");
        crawler = newCrawler(4, 4);
        final ExecutorService callers = Executors.newFixedThreadPool(8);
        final CountDownLatch start = new CountDownLatch(1);
        final Set<Throwable> failures = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < crawlCalls; i++) {
            callers.submit(() -> {
                try {
                    start.await();
                    crawler.crawl(uri("/concurrent"));
                } catch (final Throwable throwable) {
                    failures.add(throwable);
                }
            });
        }
        start.countDown();
        callers.shutdown();
        assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        waitUntilIdle(crawler, Duration.ofSeconds(10));

        assertTrue(failures.isEmpty());
        for (int i = 1; i <= crawlCalls; i++) {
            assertTrue(Files.exists(downloadPath.resolve(Integer.toString(i)).resolve("shared.png")));
        }
    }

    @Test
    void invalidImageUrlsAreIgnoredGracefully() throws IOException {
        createPage("/invalid-images", """
                <img src="data:image/png;base64,AAAA">
                <img src="ftp://example.invalid/file.jpg">
                <img src="/missing/not-found.jpg">
                <img>
                """);
        crawler = newCrawler(2, 2);

        crawler.crawl(uri("/invalid-images"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(crawler.isIdle());
    }

    @Test
    void failedWebsiteDoesNotBreakCrawler() throws IOException {
        createStatus("/failed", 500, "server error");
        createPage("/working", "<img src=\"/images/ok.jpg\">");
        createImage("/images/ok.jpg");
        crawler = newCrawler(1, 2);

        crawler.crawl(uri("/failed"));
        crawler.crawl(uri("/working"));
        waitUntilIdle(crawler, Duration.ofSeconds(5));

        assertTrue(Files.exists(downloadPath.resolve("2").resolve("ok.jpg")));
    }

    private ImageCrawler newCrawler(final int websiteScans, final int imageDownloads) {
        return new ImageCrawler(new TestImageCrawlerConfig(websiteScans, imageDownloads, downloadPath));
    }

    private void createPage(final String path, final String html) {
        createStatus(path, 200, html, "text/html; charset=utf-8");
    }

    private void createImage(final String path) {
        server.createContext(path, exchange -> send(exchange, 200, "image/png", PNG_BYTES));
    }

    private void createImage(final String path, final byte[] body) {
        server.createContext(path, exchange -> send(exchange, 200, "image/png", body));
    }

    private void createBlockingImage(
            final String path,
            final CountDownLatch requestLatch,
            final CountDownLatch releaseLatch,
            final byte[] body
    ) {
        server.createContext(path, exchange -> {
            requestLatch.countDown();
            try {
                releaseLatch.await();
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            send(exchange, 200, "image/png", body);
        });
    }

    private void createBlockingPage(final String path, final ConcurrencyProbe probe) {
        server.createContext(path, exchange -> {
            probe.enterAndWait();
            send(exchange, 200, "text/html; charset=utf-8", "<html></html>".getBytes(StandardCharsets.UTF_8));
        });
    }

    private void createBlockingImagePrefix(final String pathPrefix, final ConcurrencyProbe probe) {
        server.createContext(pathPrefix, exchange -> {
            probe.enterAndWait();
            send(exchange, 200, "image/png", PNG_BYTES);
        });
    }

    private void createStatus(final String path, final int status, final String body) {
        createStatus(path, status, body, "text/plain; charset=utf-8");
    }

    private void createStatus(final String path, final int status, final String body, final String contentType) {
        server.createContext(path, exchange -> send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8)));
    }

    private void send(final HttpExchange exchange, final int status, final String contentType, final byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private URI uri(final String pathAndQuery) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + pathAndQuery);
    }

    private void waitUntilIdle(final ImageCrawler imageCrawler, final Duration timeout) {
        final long deadline = System.nanoTime() + timeout.toNanos();
        final List<Boolean> observations = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            observations.add(imageCrawler.isIdle());
            if (imageCrawler.isIdle()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for crawler to become idle.", exception);
            }
        }
        throw new AssertionError("Crawler did not become idle within " + timeout + ". Observations: " + observations);
    }

    private void waitUntilFileExists(final Path path, final Duration timeout) {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for file " + path + ".", exception);
            }
        }
        throw new AssertionError("File was not created within " + timeout + ": " + path);
    }

    private record TestImageCrawlerConfig(
            int websiteScans,
            int imageDownloads,
            Path downloadPath
    ) implements IImageCrawlerConfig {

        @Override
        public int getNumberOfAllowedParallelWebsiteScans() {
            return websiteScans;
        }

        @Override
        public int getNumberOfAllowedParallelImageDownloads() {
            return imageDownloads;
        }

        @Override
        public Path getDownloadPath() {
            return downloadPath;
        }
    }

    private static final class ConcurrencyProbe {

        private final CountDownLatch expectedEntries;
        private final CountDownLatch releaseLatch = new CountDownLatch(1);
        private final AtomicInteger current = new AtomicInteger();
        private final AtomicInteger max = new AtomicInteger();

        private ConcurrencyProbe(final int expectedEntries) {
            this.expectedEntries = new CountDownLatch(expectedEntries);
        }

        private void enterAndWait() {
            final int active = current.incrementAndGet();
            max.accumulateAndGet(active, Math::max);
            expectedEntries.countDown();
            try {
                releaseLatch.await();
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                current.decrementAndGet();
            }
        }

        private boolean awaitExpectedEntries(final Duration timeout) throws InterruptedException {
            return expectedEntries.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private int maxConcurrent() {
            return max.get();
        }

        private void release() {
            releaseLatch.countDown();
        }
    }
}
