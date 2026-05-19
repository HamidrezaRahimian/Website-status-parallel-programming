package de.dhbw.parallel.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Downloads image bytes and stores them in the configured target directory.
 */
public final class ImageDownloader {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final FileNameResolver fileNameResolver;

    /**
     * Creates an image downloader using a default HTTP client and filename resolver.
     */
    public ImageDownloader() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new FileNameResolver());
    }

    /**
     * Creates an image downloader.
     *
     * @param httpClient the HTTP client used for image requests
     * @param fileNameResolver the resolver used to assign unique target file names
     */
    public ImageDownloader(final HttpClient httpClient, final FileNameResolver fileNameResolver) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.fileNameResolver = Objects.requireNonNull(fileNameResolver, "fileNameResolver must not be null");
    }

    /**
     * Creates an image reference with a target path that is already reserved for this image.
     *
     * @param imageUri the image URI
     * @param targetDirectory the directory where the image should be stored
     * @return an image reference containing the reserved target path
     */
    ImageReference reserveTargetPath(final URI imageUri, final Path targetDirectory) {
        return new ImageReference(imageUri, targetDirectory, fileNameResolver.resolveTargetPath(imageUri, targetDirectory));
    }

    /**
     * Downloads an image and stores it in the reference's target directory. Failed downloads are ignored gracefully.
     *
     * @param imageReference the image download description
     * @return the written file path, or {@code null} if the image could not be downloaded
     * @throws InterruptedException if the current thread is interrupted while waiting for the response
     */
    public Path download(final ImageReference imageReference) throws InterruptedException {
        Objects.requireNonNull(imageReference, "imageReference must not be null");
        final URI imageUri = imageReference.getImageUri();
        if (!isSupportedHttpUri(imageUri)) {
            return null;
        }

        try {
            final HttpRequest request = HttpRequest.newBuilder(imageUri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .header("User-Agent", "DHBW-Parallel-ImageCrawler/1.0")
                    .build();
            final HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            final Path targetPath = resolveTargetPath(imageReference);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, response.body());
            return targetPath;
        } catch (final IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Returns the reserved target path or resolves a new one if the reference was created externally.
     *
     * @param imageReference the image reference
     * @return the target path for the file
     */
    private Path resolveTargetPath(final ImageReference imageReference) {
        if (imageReference.getReservedTargetPath() != null) {
            return imageReference.getReservedTargetPath();
        }
        return fileNameResolver.resolveTargetPath(imageReference.getImageUri(), imageReference.getTargetDirectory());
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
