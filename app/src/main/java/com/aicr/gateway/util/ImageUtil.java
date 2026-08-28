package com.aicr.gateway.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * Image utility class for the AICR API Gateway.
 * Provides helper methods for Bitmap-to-Base64 conversion, Base64-to-Bitmap
 * decoding, and image compression. Primarily used for encoding segmentation
 * masks in API responses.
 */
public class ImageUtil {

    private static final String TAG = "ImageUtil";

    /** Default compression quality for PNG encoding (0-100). */
    private static final int DEFAULT_QUALITY = 100;

    /** Compression quality when encoding masks as JPEG (used when size matters). */
    private static final int JPEG_QUALITY = 85;

    private ImageUtil() {
        // Utility class; prevent instantiation
    }

    // =========================================================================
    // Bitmap <-> Base64
    // =========================================================================

    /**
     * Converts a {@link Bitmap} to a Base64-encoded string (PNG format).
     *
     * @param bitmap the bitmap to encode; must not be null
     * @return the Base64-encoded string representation of the bitmap
     * @throws IllegalArgumentException if bitmap is null
     */
    public static String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap must not be null");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, DEFAULT_QUALITY, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /**
     * Decodes a Base64-encoded string back into a {@link Bitmap}.
     *
     * @param base64 the Base64 string to decode
     * @return the decoded Bitmap, or null if decoding fails
     * @throws IllegalArgumentException if base64 is null or empty
     */
    public static Bitmap base64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) {
            throw new IllegalArgumentException("Base64 string must not be null or empty");
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            LogUtil.e(TAG, "Failed to decode Base64 to Bitmap: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Compression
    // =========================================================================

    /**
     * Compresses a bitmap so that its largest dimension does not exceed
     * {@code maxSize} pixels, preserving aspect ratio.
     *
     * <p>If the bitmap is already within the limit it is returned as-is.
     *
     * @param bitmap  the source bitmap
     * @param maxSize the maximum width or height in pixels
     * @return a scaled-down bitmap, or the original if already small enough
     */
    public static Bitmap compressBitmap(Bitmap bitmap, int maxSize) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap must not be null");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return bitmap;
        }

        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        // Ensure at least 1x1
        newWidth = Math.max(newWidth, 1);
        newHeight = Math.max(newHeight, 1);

        LogUtil.d(TAG, "Compressing bitmap from " + width + "x" + height
                + " to " + newWidth + "x" + newHeight);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    /**
     * Compresses a bitmap to JPEG byte array with the given quality (0-100).
     * Useful when the raw bytes are needed (e.g., for network transmission).
     *
     * @param bitmap  the source bitmap
     * @param quality JPEG compression quality (0-100)
     * @return the compressed byte array
     */
    public static byte[] compressToJpeg(Bitmap bitmap, int quality) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap must not be null");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }

    /**
     * Converts a Bitmap to a Base64-encoded JPEG string.
     * Produces smaller output than PNG for photographic content.
     *
     * @param bitmap  the source bitmap
     * @param quality JPEG compression quality (0-100)
     * @return the Base64-encoded JPEG string
     */
    public static String bitmapToBase64Jpeg(Bitmap bitmap, int quality) {
        byte[] bytes = compressToJpeg(bitmap, quality);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
