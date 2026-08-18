package com.cardrestricted.ui;

import java.net.URI;
import java.util.Objects;

final class WikiArtworkEntry
{
    private final String cardId;
    private final String runtimeFilename;
    private final URI sourceUri;
    private final String sourceSha256;
    private final String runtimeSha256;
    private final boolean pixelArt;
    private final boolean downloadable;
    private final boolean bundledFallback;

    WikiArtworkEntry(
        String cardId,
        String runtimeFilename,
        URI sourceUri,
        String sourceSha256,
        String runtimeSha256,
        boolean pixelArt,
        boolean downloadable,
        boolean bundledFallback)
    {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.runtimeFilename = Objects.requireNonNull(
            runtimeFilename,
            "runtimeFilename");
        this.sourceUri = Objects.requireNonNull(sourceUri, "sourceUri");
        this.sourceSha256 = Objects.requireNonNull(
            sourceSha256,
            "sourceSha256");
        this.runtimeSha256 = Objects.requireNonNull(
            runtimeSha256,
            "runtimeSha256");
        this.pixelArt = pixelArt;
        this.downloadable = downloadable;
        this.bundledFallback = bundledFallback;
    }

    String getCardId()
    {
        return cardId;
    }

    String getRuntimeFilename()
    {
        return runtimeFilename;
    }

    URI getSourceUri()
    {
        return sourceUri;
    }

    String getSourceSha256()
    {
        return sourceSha256;
    }

    String getRuntimeSha256()
    {
        return runtimeSha256;
    }

    boolean isPixelArt()
    {
        return pixelArt;
    }

    boolean isDownloadable()
    {
        return downloadable;
    }

    boolean isBundledFallback()
    {
        return bundledFallback;
    }
}
