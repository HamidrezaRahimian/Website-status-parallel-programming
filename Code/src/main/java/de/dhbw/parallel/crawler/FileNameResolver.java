package de.dhbw.parallel.crawler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves safe target file names and handles duplicate file names per target directory.
 */
public final class FileNameResolver {

    private final Map<Path, DirectoryNameState> assignedNamesByDirectory = new HashMap<>();

    /**
     * Resolves a target path for the given image URI. Duplicate names in the same directory are suffixed with
     * {@code _2}, {@code _3} and so on.
     *
     * @param imageUri the image URI
     * @param targetDirectory the directory where the file will be written
     * @return a unique target path
     */
    public synchronized Path resolveTargetPath(final URI imageUri, final Path targetDirectory) {
        Objects.requireNonNull(imageUri, "imageUri must not be null");
        Objects.requireNonNull(targetDirectory, "targetDirectory must not be null");

        final Path normalizedDirectory = targetDirectory.toAbsolutePath().normalize();
        final String originalName = extractFileName(imageUri);
        final DirectoryNameState directoryNameState = assignedNamesByDirectory.computeIfAbsent(
                normalizedDirectory,
                ignored -> new DirectoryNameState()
        );

        int nextIndex = directoryNameState.occurrencesByOriginalName.merge(originalName, 1, Integer::sum);
        String resolvedName = nextIndex == 1 ? originalName : addDuplicateSuffix(originalName, nextIndex);
        while (directoryNameState.assignedFileNames.contains(resolvedName)
                || Files.exists(normalizedDirectory.resolve(resolvedName))) {
            nextIndex++;
            resolvedName = addDuplicateSuffix(originalName, nextIndex);
            directoryNameState.occurrencesByOriginalName.put(originalName, nextIndex);
        }
        directoryNameState.assignedFileNames.add(resolvedName);
        return normalizedDirectory.resolve(resolvedName);
    }

    private String extractFileName(final URI imageUri) {
        final String rawPath = imageUri.getRawPath();
        if (rawPath == null || rawPath.isBlank() || rawPath.endsWith("/")) {
            return "image";
        }

        final int slashIndex = rawPath.lastIndexOf('/');
        final String rawName = slashIndex >= 0 ? rawPath.substring(slashIndex + 1) : rawPath;
        final String decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        final String sanitizedName = decodedName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitizedName.isBlank() || sanitizedName.equals(".") || sanitizedName.equals("..")) {
            return "image";
        }
        return sanitizedName;
    }

    private String addDuplicateSuffix(final String fileName, final int duplicateIndex) {
        final int extensionIndex = findExtensionIndex(fileName);
        if (extensionIndex <= 0) {
            return fileName + "_" + duplicateIndex;
        }
        return fileName.substring(0, extensionIndex) + "_" + duplicateIndex + fileName.substring(extensionIndex);
    }

    private int findExtensionIndex(final String fileName) {
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return -1;
        }
        return dotIndex;
    }

    private static final class DirectoryNameState {

        private final Map<String, Integer> occurrencesByOriginalName = new HashMap<>();
        private final Set<String> assignedFileNames = new HashSet<>();
    }
}
