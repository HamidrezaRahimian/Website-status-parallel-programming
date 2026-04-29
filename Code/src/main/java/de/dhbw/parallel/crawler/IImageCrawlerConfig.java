package de.dhbw.parallel.crawler;

import java.nio.file.Path;

/**
 * Provides the runtime configuration for an {@link IImageCrawler}.
 */
public interface IImageCrawlerConfig {

    /**
     * Returns the maximum number of website scans that may run at the same time.
     *
     * @return the allowed number of parallel website scans
     */
    int getNumberOfAllowedParallelWebsiteScans();

    /**
     * Returns the maximum number of image downloads that may run at the same time.
     *
     * @return the allowed number of parallel image downloads
     */
    int getNumberOfAllowedParallelImageDownloads();

    /**
     * Returns the root directory under which the crawler stores downloaded images.
     *
     * @return the download root directory
     */
    Path getDownloadPath();
}
