package com.lifelog.common.storage;

/** Validates the bucket-relative paths stored in business records. */
public final class MediaObjectPath {
    private MediaObjectPath() {}

    public static boolean isValid(String path) {
        if (path == null || path.length() > 2048 || !path.startsWith("/") || path.startsWith("//")
                || path.contains("://") || path.contains("\\") || path.contains("..")
                || path.contains("?") || path.contains("#") || path.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        int keyStart = path.indexOf('/', 1);
        return keyStart > 1 && keyStart < path.length() - 1 && path.charAt(keyStart + 1) != '/';
    }
}
