package de.dhbw.parallel.crawler;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable description of a single image download task.
 */
public final class ImageReference {

    private final URI imageUri;
    private final Path targetDirectory;
    private final Path reservedTargetPath;

    /**
     * Creates a new image reference.
     *
     * @param imageUri the URI of the image to download
     * @param targetDirectory the directory where the image should be stored
     */
    public ImageReference(final URI imageUri, final Path targetDirectory) {
        this(imageUri, targetDirectory, null);
    }

    ImageReference(final URI imageUri, final Path targetDirectory, final Path reservedTargetPath) {
        this.imageUri = Objects.requireNonNull(imageUri, "imageUri must not be null");
        this.targetDirectory = Objects.requireNonNull(targetDirectory, "targetDirectory must not be null");
        this.reservedTargetPath = reservedTargetPath;
    }

    /**
     * Returns the image URI.
     *
     * @return the image URI
     */
    public URI getImageUri() {
        return imageUri;
    }

    /**
     * Returns the target directory for the image.
     *
     * @return the target directory
     */
    public Path getTargetDirectory() {
        return targetDirectory;
    }

    /**
     * Returns the reserved target path, if one was assigned before the download task was started.
     *
     * @return the reserved target path, or {@code null} if no path has been reserved yet
     */
    Path getReservedTargetPath() {
        return reservedTargetPath;
    }
}
