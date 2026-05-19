package de.dhbw.parallel.crawler;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Command line entry point for the image crawler.
 */
public final class Main {

    private static final int DEFAULT_WEBSITE_SCANS = 2;
    private static final int DEFAULT_IMAGE_DOWNLOADS = 4;
    private static final Duration WAIT_INTERVAL = Duration.ofMillis(100);

    /**
     * Prevents instantiation of the utility-style command line entry point.
     */
    private Main() {
    }

    /**
     * Runs the crawler from the command line.
     *
     * @param args website URL, download directory, optional website scan count, optional image download count
     * @throws InterruptedException if the wait loop is interrupted
     * @throws IOException if the download summary cannot be read
     */
    public static void main(final String[] args) throws InterruptedException, IOException {
        final String[] effectiveArgs = args.length == 0
                ? new String[] {"https://example.com", "downloads-demo"}
                : args;
        if (args.length == 0) {
            System.out.println("No arguments were provided. Running a small demo crawl.");
            System.out.println("Use program arguments to crawl a different website.");
            System.out.println();
        }

        if (effectiveArgs.length < 2 || effectiveArgs.length > 4) {
            printUsage();
            System.exit(1);
            return;
        }

        final URI websiteUri = parseWebsiteUri(effectiveArgs[0]);
        final Path downloadPath = Path.of(effectiveArgs[1]).toAbsolutePath().normalize();
        final int websiteScans = effectiveArgs.length >= 3
                ? parsePositiveInteger(effectiveArgs[2], "website scans")
                : DEFAULT_WEBSITE_SCANS;
        final int imageDownloads = effectiveArgs.length >= 4
                ? parsePositiveInteger(effectiveArgs[3], "image downloads")
                : DEFAULT_IMAGE_DOWNLOADS;

        System.out.println("Image Crawler");
        System.out.println("Website: " + websiteUri);
        System.out.println("Download folder: " + downloadPath);
        System.out.println("Parallel website scans: " + websiteScans);
        System.out.println("Parallel image downloads: " + imageDownloads);
        System.out.println();
        System.out.println("Preparing crawler...");

        try (ImageCrawler crawler = new ImageCrawler(new CommandLineConfig(websiteScans, imageDownloads, downloadPath))) {
            System.out.println("Submitting website scan...");
            crawler.crawl(websiteUri);
            System.out.println("Website scan is queued. Downloads will start when images are found.");
            System.out.println("Waiting until all scan and download tasks are finished...");
            waitUntilIdle(crawler);
        }

        final List<Path> downloadedFiles = listDownloadedFiles(downloadPath);
        System.out.println();
        System.out.println("Finished.");
        System.out.println("Downloaded files: " + downloadedFiles.size());
        if (downloadedFiles.isEmpty()) {
            System.out.println("No downloadable images were found, or all image downloads failed.");
        } else {
            System.out.println("Saved files:");
            for (final Path file : downloadedFiles) {
                System.out.println("- " + downloadPath.relativize(file));
            }
        }
    }

    /**
     * Parses and validates the website URI argument.
     *
     * @param value the raw command line value
     * @return the parsed website URI
     */
    private static URI parseWebsiteUri(final String value) {
        try {
            final URI uri = URI.create(value);
            final String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Only http and https URLs are supported.");
            }
            return uri;
        } catch (final IllegalArgumentException exception) {
            System.err.println("Invalid website URL: " + value);
            System.err.println(exception.getMessage());
            System.exit(1);
            return URI.create("http://localhost");
        }
    }

    /**
     * Parses a positive integer command line option.
     *
     * @param value the raw command line value
     * @param name the option name for error messages
     * @return the parsed positive integer
     */
    private static int parsePositiveInteger(final String value, final String name) {
        try {
            final int parsedValue = Integer.parseInt(value);
            if (parsedValue < 1) {
                throw new NumberFormatException("Value must be at least 1.");
            }
            return parsedValue;
        } catch (final NumberFormatException exception) {
            System.err.println("Invalid " + name + ": " + value);
            System.err.println("Use a positive integer.");
            System.exit(1);
            return 1;
        }
    }

    /**
     * Waits until the crawler has no queued or running work.
     *
     * @param crawler the crawler to observe
     * @throws InterruptedException if the wait is interrupted
     */
    private static void waitUntilIdle(final ImageCrawler crawler) throws InterruptedException {
        long lastStatusUpdate = System.nanoTime();
        while (!crawler.isIdle()) {
            Thread.sleep(WAIT_INTERVAL.toMillis());
            final long now = System.nanoTime();
            if (Duration.ofNanos(now - lastStatusUpdate).toSeconds() >= 1) {
                System.out.println("Still working...");
                lastStatusUpdate = now;
            }
        }
    }

    /**
     * Lists downloaded files below the configured download directory.
     *
     * @param downloadPath the download root directory
     * @return the downloaded files in stable order
     * @throws IOException if the directory cannot be read
     */
    private static List<Path> listDownloadedFiles(final Path downloadPath) throws IOException {
        if (!Files.exists(downloadPath)) {
            return List.of();
        }
        try (var stream = Files.walk(downloadPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    /**
     * Prints the command line usage.
     */
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar target/image-crawler-1.0.0.jar <website-url> <download-folder> [website-scans] [image-downloads]");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar target/image-crawler-1.0.0.jar https://example.com downloads");
    }

    private record CommandLineConfig(
            int websiteScans,
            int imageDownloads,
            Path downloadPath
    ) implements IImageCrawlerConfig {

        private CommandLineConfig {
            Objects.requireNonNull(downloadPath, "downloadPath must not be null");
        }

        /**
         * Returns the configured number of parallel website scans.
         *
         * @return the website scan limit
         */
        @Override
        public int getNumberOfAllowedParallelWebsiteScans() {
            return websiteScans;
        }

        /**
         * Returns the configured number of parallel image downloads.
         *
         * @return the image download limit
         */
        @Override
        public int getNumberOfAllowedParallelImageDownloads() {
            return imageDownloads;
        }

        /**
         * Returns the configured download root directory.
         *
         * @return the download root directory
         */
        @Override
        public Path getDownloadPath() {
            return downloadPath;
        }
    }
}
