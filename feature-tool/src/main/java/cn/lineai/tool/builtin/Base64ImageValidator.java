package cn.lineai.tool.builtin;

import java.util.Arrays;

/**
 * 校验图片数据是否为可用图片。负责 Base64 格式校验、前缀解码与已知图片签名匹配。
 */
final class Base64ImageValidator {
    boolean isUsableBase64ImagePayload(String value) {
        String payload = value == null ? "" : value.trim();
        if (payload.length() == 0 || isNullLikeString(payload)) {
            return false;
        }
        return hasKnownImageSignature(decodedBase64Prefix(payload, 16));
    }

    boolean isUsableImageDataUrl(String value) {
        String dataUrl = value == null ? "" : value.trim();
        if (!isImageDataUrl(dataUrl)) {
            return false;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return false;
        }
        String metadata = dataUrl.substring(0, comma).toLowerCase(java.util.Locale.ROOT);
        return metadata.contains(";base64")
                && isUsableBase64ImagePayload(dataUrl.substring(comma + 1));
    }

    boolean isImageDataUrl(String value) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).startsWith("data:image/");
    }

    boolean isNullLikeString(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return "null".equals(normalized) || "undefined".equals(normalized);
    }

    private byte[] decodedBase64Prefix(String value, int maxBytes) {
        byte[] output = new byte[Math.max(0, maxBytes)];
        int count = 0;
        int buffer = 0;
        int bits = 0;
        boolean sawPadding = false;
        for (int i = 0; i < value.length() && count < output.length; i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '=') {
                sawPadding = true;
                continue;
            }
            int sixBits = base64Value(c);
            if (sixBits < 0 || sawPadding) {
                return new byte[0];
            }
            buffer = (buffer << 6) | sixBits;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output[count] = (byte) ((buffer >> bits) & 0xff);
                count++;
            }
        }
        return Arrays.copyOf(output, count);
    }

    private int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '+' || c == '-') {
            return 62;
        }
        if (c == '/' || c == '_') {
            return 63;
        }
        return -1;
    }

    private boolean hasKnownImageSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return false;
        }
        return startsWith(bytes, new int[] {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})
                || startsWith(bytes, new int[] {0xff, 0xd8, 0xff})
                || startsWith(bytes, new int[] {'G', 'I', 'F', '8'})
                || startsWith(bytes, new int[] {'B', 'M'})
                || (bytes.length >= 12
                && startsWith(bytes, new int[] {'R', 'I', 'F', 'F'})
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P')
                || (bytes.length >= 12
                && bytes[4] == 'f'
                && bytes[5] == 't'
                && bytes[6] == 'y'
                && bytes[7] == 'p'
                && bytes[8] == 'a'
                && bytes[9] == 'v'
                && bytes[10] == 'i'
                && bytes[11] == 'f');
    }

    private boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
