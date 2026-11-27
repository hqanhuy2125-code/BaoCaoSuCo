package com.example.baocaosuco.resident.photo;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * C01 G2. Call process() ONLY from a worker thread.
 * Copies a temporary picker/camera URI, validates JPEG/PNG bytes and size,
 * rotates according to EXIF, bounds bitmap memory, and outputs app-cache JPEG.
 */
public final class IncidentPhotoProcessor {
    public enum Failure {
        INVALID_URI, UNSUPPORTED_FORMAT, TOO_LARGE, INVALID_IMAGE,
        OUTPUT_TOO_LARGE, IO_ERROR
    }

    public static final class PhotoException extends Exception {
        private final Failure failure;
        PhotoException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
        public Failure getFailure() { return failure; }
    }

    public static final class PreparedPhoto {
        private final Uri uri;
        private final long bytes;
        private final int width;
        private final int height;

        PreparedPhoto(Uri uri, long bytes, int width, int height) {
            this.uri = uri;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }

        public Uri getUri() { return uri; }
        public long getBytes() { return bytes; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    private final Context context;
    private final IncidentPhotoStore store;

    public IncidentPhotoProcessor(Context context, IncidentPhotoStore store) {
        if (context == null || store == null) {
            throw new IllegalArgumentException("Context and Store required");
        }
        this.context = context.getApplicationContext();
        this.store = store;
    }

    public PreparedPhoto process(Uri input) throws PhotoException {
        if (input == null || !"content".equalsIgnoreCase(input.getScheme())) {
            throw new PhotoException(Failure.INVALID_URI);
        }

        ContentResolver resolver = context.getContentResolver();
        try {
            if (!IncidentPhotoPolicy.supportsMime(resolver.getType(input))) {
                throw new PhotoException(Failure.UNSUPPORTED_FORMAT);
            }
        } catch (SecurityException | IllegalArgumentException error) {
            throw new PhotoException(Failure.INVALID_URI);
        }

        File staged = null;
        File output = null;
        boolean transferred = false;
        try {
            staged = store.newStagingFile();
            copyWithLimit(resolver, input, staged);
            boolean jpeg = detectFormat(staged);
            Bitmap image = decodeAndNormalize(staged, jpeg);
            try {
                output = store.newPreparedFile();
                encodeBoundedJpeg(image, output);
                Uri ownedUri = store.uriFor(output);
                PreparedPhoto result = new PreparedPhoto(ownedUri,
                        output.length(), image.getWidth(), image.getHeight());
                transferred = true;
                return result;
            } finally {
                image.recycle();
            }
        } catch (PhotoException error) {
            throw error;
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            throw new PhotoException(Failure.IO_ERROR);
        } finally {
            store.deleteInternal(staged);
            if (!transferred) store.deleteInternal(output);
            store.deleteCaptured(input);
        }
    }

    private static void copyWithLimit(ContentResolver resolver, Uri source, File target)
            throws IOException, PhotoException {
        long count = 0;
        byte[] buffer = new byte[8192];
        try (InputStream in = resolver.openInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new PhotoException(Failure.INVALID_URI);
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Interrupted");
                count += n;
                if (count > IncidentPhotoPolicy.MAX_INPUT_BYTES) {
                    throw new PhotoException(Failure.TOO_LARGE);
                }
                out.write(buffer, 0, n);
            }
        }
        if (count == 0) throw new PhotoException(Failure.INVALID_IMAGE);
    }

    /** Returns true for JPEG, false for PNG; rejects suffix-only or spoofed files. */
    private static boolean detectFormat(File file) throws IOException, PhotoException {
        byte[] h = new byte[8];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(h) < 8) throw new PhotoException(Failure.INVALID_IMAGE);
        }
        if ((h[0] & 255) == 255 && (h[1] & 255) == 216 && (h[2] & 255) == 255) {
            return true;
        }
        int[] png = {137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < png.length; i++) {
            if ((h[i] & 255) != png[i]) throwIfNotJpeg(i);
        }
        return trueIfPng();
    }

    private static void throwIfNotJpeg(int ignored) throws PhotoException {
        throw new PhotoException(Failure.UNSUPPORTED_FORMAT);
    }

    private static boolean trueIfPng() {
        return false;
    }

    private static Bitmap decodeAndNormalize(File staged, boolean jpeg)
            throws PhotoException {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(staged.getAbsolutePath(), probe);
        if (!IncidentPhotoPolicy.validDimensions(probe.outWidth, probe.outHeight)) {
            throw new PhotoException(Failure.INVALID_IMAGE);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = IncidentPhotoPolicy.sampleSize(
                probe.outWidth, probe.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap result = BitmapFactory.decodeFile(staged.getAbsolutePath(), options);
        if (result == null) throw new PhotoException(Failure.INVALID_IMAGE);

        try {
            if (jpeg) {
                int orientation = ExifInterface.ORIENTATION_NORMAL;
                try {
                    orientation = new ExifInterface(staged.getAbsolutePath())
                            .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL);
                } catch (IOException ignored) {
                    // A valid JPEG can lack readable EXIF.
                }
                Matrix matrix = matrixFor(orientation);
                if (matrix != null) {
                    Bitmap corrected = Bitmap.createBitmap(result, 0, 0,
                            result.getWidth(), result.getHeight(), matrix, true);
                    if (corrected != result) result.recycle();
                    result = corrected;
                }
            }
            int longest = Math.max(result.getWidth(), result.getHeight());
            if (longest > IncidentPhotoPolicy.MAX_OUTPUT_SIDE) {
                int w = IncidentPhotoPolicy.targetSide(result.getWidth(), longest);
                int h = IncidentPhotoPolicy.targetSide(result.getHeight(), longest);
                Bitmap scaled = Bitmap.createScaledBitmap(result, w, h, true);
                if (scaled != result) result.recycle();
                result = scaled;
            }
            if (result.hasAlpha()) {
                Bitmap opaque = Bitmap.createBitmap(
                        result.getWidth(), result.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(opaque);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(result, 0, 0, null);
                result.recycle();
                result = opaque;
            }
            return result;
        } catch (RuntimeException failure) {
            if (!result.isRecycled()) result.recycle();
            throw new PhotoException(Failure.INVALID_IMAGE);
        }
    }

    private static Matrix matrixFor(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90); break;
            default: return null;
        }
        return matrix;
    }

    private static void encodeBoundedJpeg(Bitmap image, File target)
            throws IOException, PhotoException {
        for (int quality : new int[] {85, 75, 65, 55}) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Interrupted");
            try (FileOutputStream out = new FileOutputStream(target, false)) {
                if (!image.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw new PhotoException(Failure.IO_ERROR);
                }
            }
            if (target.length() > 0
                    && target.length() <= IncidentPhotoPolicy.MAX_OUTPUT_BYTES) {
                return;
            }
        }
        throw new PhotoException(Failure.OUTPUT_TOO_LARGE);
    }
}
