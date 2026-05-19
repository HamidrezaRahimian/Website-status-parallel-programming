package de.dhbw.parallel.crawler;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates website analysis and image downloads with separate parallelism limits.
 */
public final class ImageCrawler implements IImageCrawler, AutoCloseable {

    private final IImageCrawlerConfig config;
    private final WebsiteAnalyzer websiteAnalyzer;
    private final ImageDownloader imageDownloader;
    private final ExecutorService websiteExecutor;
    private final ExecutorService imageExecutor;
    private final AtomicInteger crawlFolderCounter = new AtomicInteger();
    private final AtomicInteger pendingWebsiteTasks = new AtomicInteger();
    private final AtomicInteger activeWebsiteTasks = new AtomicInteger();
    private final AtomicInteger pendingImageTasks = new AtomicInteger();
    private final AtomicInteger activeImageTasks = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates a crawler using default analyzer and downloader instances.
     *
     * @param config the crawler configuration
     */
    public ImageCrawler(final IImageCrawlerConfig config) {
        this(config, new WebsiteAnalyzer(), new ImageDownloader());
    }

    /**
     * Creates a crawler with explicitly supplied collaborators.
     *
     * @param config the crawler configuration
     * @param websiteAnalyzer the website analyzer
     * @param imageDownloader the image downloader
     */
    public ImageCrawler(
            final IImageCrawlerConfig config,
            final WebsiteAnalyzer websiteAnalyzer,
            final ImageDownloader imageDownloader
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        validateConfig(config);
        this.websiteAnalyzer = Objects.requireNonNull(websiteAnalyzer, "websiteAnalyzer must not be null");
        this.imageDownloader = Objects.requireNonNull(imageDownloader, "imageDownloader must not be null");
        this.websiteExecutor = Executors.newFixedThreadPool(
                config.getNumberOfAllowedParallelWebsiteScans(),
                namedThreadFactory("website-scan")
        );
        this.imageExecutor = Executors.newFixedThreadPool(
                config.getNumberOfAllowedParallelImageDownloads(),
                namedThreadFactory("image-download")
        );
    }

    /**
     * Adds a website URI to the crawler and schedules its analysis.
     *
     * @param uri the website URI to crawl
     */
    @Override
    public void crawl(final URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        if (closed.get()) {
            throw new IllegalStateException("Crawler is already closed.");
        }

        final int folderNumber = crawlFolderCounter.incrementAndGet();
        final Path targetDirectory = config.getDownloadPath().resolve(Integer.toString(folderNumber));
        pendingWebsiteTasks.incrementAndGet();
        try {
            websiteExecutor.submit(() -> runWebsiteTask(uri, targetDirectory));
        } catch (final RejectedExecutionException exception) {
            pendingWebsiteTasks.decrementAndGet();
            throw exception;
        }
    }

    /**
     * Returns whether the crawler has no queued or running website and image work.
     *
     * @return {@code true} if there is no pending or active work, otherwise {@code false}
     */
    @Override
    public boolean isIdle() {
        return pendingWebsiteTasks.get() == 0
                && activeWebsiteTasks.get() == 0
                && pendingImageTasks.get() == 0
                && activeImageTasks.get() == 0;
    }

    /**
     * Stops both executor services. Already running tasks are interrupted if possible.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            websiteExecutor.shutdownNow();
            imageExecutor.shutdownNow();
        }
    }

    /**
     * Runs one website scan and schedules one image task for each extracted image URI.
     *
     * @param websiteUri the website URI to scan
     * @param targetDirectory the directory assigned to this website
     */
    private void runWebsiteTask(final URI websiteUri, final Path targetDirectory) {
        moveTaskFromPendingToActive(pendingWebsiteTasks, activeWebsiteTasks);
        try {
            final List<URI> imageUris = websiteAnalyzer.analyze(websiteUri);
            for (final URI imageUri : imageUris) {
                submitImageTask(imageDownloader.reserveTargetPath(imageUri, targetDirectory));
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (final Exception ignored) {
            // A failed website must not stop other queued or running crawler work.
        } finally {
            activeWebsiteTasks.decrementAndGet();
        }
    }

    /**
     * Submits one image download task to the image executor.
     *
     * @param imageReference the image download description
     */
    private void submitImageTask(final ImageReference imageReference) {
        pendingImageTasks.incrementAndGet();
        try {
            imageExecutor.submit(() -> runImageTask(imageReference));
        } catch (final RejectedExecutionException exception) {
            pendingImageTasks.decrementAndGet();
        }
    }

    /**
     * Downloads one image and updates the task counters afterwards.
     *
     * @param imageReference the image download description
     */
    private void runImageTask(final ImageReference imageReference) {
        moveTaskFromPendingToActive(pendingImageTasks, activeImageTasks);
        try {
            imageDownloader.download(imageReference);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (final Exception ignored) {
            // A single broken image should not affect the rest of the crawl.
        } finally {
            activeImageTasks.decrementAndGet();
        }
    }

    /**
     * Moves a task from the pending counter to the active counter.
     *
     * @param pendingCounter the pending task counter
     * @param activeCounter the active task counter
     */
    private void moveTaskFromPendingToActive(final AtomicInteger pendingCounter, final AtomicInteger activeCounter) {
        activeCounter.incrementAndGet();
        pendingCounter.decrementAndGet();
    }

    /**
     * Validates the crawler configuration before executor services are created.
     *
     * @param config the crawler configuration
     */
    private void validateConfig(final IImageCrawlerConfig config) {
        if (config.getNumberOfAllowedParallelWebsiteScans() < 1) {
            throw new IllegalArgumentException("At least one parallel website scan must be allowed.");
        }
        if (config.getNumberOfAllowedParallelImageDownloads() < 1) {
            throw new IllegalArgumentException("At least one parallel image download must be allowed.");
        }
        Objects.requireNonNull(config.getDownloadPath(), "download path must not be null");
    }

    /**
     * Creates a daemon thread factory with readable worker thread names.
     *
     * @param poolName the logical pool name
     * @return the named thread factory
     */
    private ThreadFactory namedThreadFactory(final String poolName) {
        final AtomicInteger threadCounter = new AtomicInteger();
        return runnable -> {
            final Thread thread = new Thread(runnable, "image-crawler-" + poolName + "-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
