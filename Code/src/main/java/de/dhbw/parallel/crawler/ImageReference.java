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

    /**
     * Creates a new image reference.
     *
     * @param imageUri the URI of the image to download
     * @param targetDirectory the directory where the image should be stored
     */
    public ImageReference(final URI imageUri, final Path targetDirectory) {
        this.imageUri = Objects.requireNonNull(imageUri, "imageUri must not be null");
        this.targetDirectory = Objects.requireNonNull(targetDirectory, "targetDirectory must not be null");
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
}
