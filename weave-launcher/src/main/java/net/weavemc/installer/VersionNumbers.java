package net.weavemc.installer;

import java.math.BigInteger;

final class VersionNumbers {
    private VersionNumbers() {
    }

    static int compare(String left, String right) {
        String[] leftParts = normalize(left).split("\\.");
        String[] rightParts = normalize(right).split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            BigInteger leftPart = part(leftParts, index);
            BigInteger rightPart = part(rightParts, index);
            int result = leftPart.compareTo(rightPart);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static String normalize(String version) {
        if (version == null) {
            throw new IllegalArgumentException("Version is missing.");
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int suffix = normalized.indexOf('-');
        if (suffix >= 0) {
            normalized = normalized.substring(0, suffix);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Version is empty.");
        }
        return normalized;
    }

    private static BigInteger part(String[] parts, int index) {
        String value = index < parts.length ? parts[index] : "0";
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid numeric version component: " + value);
        }
        return new BigInteger(value);
    }
}
