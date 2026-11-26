package com.example.baocaosuco.resident.photo;

/** C01-LOCAL: one JPEG/PNG evidence photo; limits are not a Firebase contract. */
public final class IncidentPhotoPolicy {
    public static final long MAX_INPUT_BYTES = 15L * 1024L * 1024L;
    public static final long MAX_OUTPUT_BYTES = 2L * 1024L * 1024L;
    public static final int MAX_OUTPUT_SIDE = 1600;
    public static final int MAX_SOURCE_SIDE = 16000;

    private IncidentPhotoPolicy() { }

    public static boolean supportsMime(String mime) {
        return mime == null || "image/jpeg".equalsIgnoreCase(mime)
                || "image/png".equalsIgnoreCase(mime);
    }

    public static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0
                && width <= MAX_SOURCE_SIDE && height <= MAX_SOURCE_SIDE;
    }

    /** BitmapFactory sampling uses powers of two to cap decode memory. */
    public static int sampleSize(int width, int height) {
        if (!validDimensions(width, height)) {
            throw new IllegalArgumentException("Invalid photo dimensions");
        }
        int sample = 1;
        while (Math.max(width / sample, height / sample) > MAX_OUTPUT_SIDE) {
            sample *= 2;
        }
        return sample;
    }

    public static int targetSide(int sourceSide, int largestSourceSide) {
        if (sourceSide <= 0 || largestSourceSide <= 0) {
            throw new IllegalArgumentException("Invalid size");
        }
        if (largestSourceSide <= MAX_OUTPUT_SIDE) return sourceSide;
        return Math.max(1, (int) Math.round(
                sourceSide * (MAX_OUTPUT_SIDE / (double) largestSourceSide)));
    }
}
