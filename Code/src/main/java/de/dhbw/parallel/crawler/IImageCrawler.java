package de.dhbw.parallel.crawler;

import java.net.URI;

/**
 * Defines a crawler that accepts website URIs and downloads the images referenced by their HTML pages.
 */
public interface IImageCrawler {

    /**
     * Adds a website URI to the crawler. The method schedules the work and returns without waiting for all downloads
     * to finish.
     *
     * @param uri the website URI to crawl
     */
    void crawl(final URI uri);

    /**
     * Checks whether the crawler currently has no queued or running website scans and image downloads.
     *
     * @return {@code true} if the crawler is completely idle, otherwise {@code false}
     */
    boolean isIdle();
}
