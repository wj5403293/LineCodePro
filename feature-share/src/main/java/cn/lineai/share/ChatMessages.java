package cn.lineai.share;

import android.graphics.Paint;
import cn.lineai.model.ChatMessage;
import java.util.List;

public final class ChatMessages {
    private ChatMessages() {}

    public static final String FOOTER_MD = "*—— From LineCode Pro*";
    public static final String FOOTER_PLAIN = "—— From LineCode Pro";

    public static String toMarkdown(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("## ").append(label(msg)).append("\n\n");
            sb.append(msg.getContent()).append("\n\n---\n\n");
        }
        sb.append(FOOTER_MD);
        return sb.toString();
    }

    public static String toPlainText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("【").append(label(msg)).append("】\n");
            sb.append(msg.getContent()).append("\n\n");
        }
        sb.append(FOOTER_PLAIN);
        return sb.toString();
    }

    public static String wrapText(String text, Paint paint, int maxWidth) {
        // Split text into lines that fit within maxWidth when measured by paint
        // Handle existing newlines - preserve them
        // For each segment between newlines, break into sub-lines that fit
        StringBuilder result = new StringBuilder();
        String[] paragraphs = text.split("\n", -1);
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) result.append("\n");
            String para = paragraphs[i];
            if (para.isEmpty()) continue;
            // Break paragraph into lines that fit maxWidth
            int start = 0;
            while (start < para.length()) {
                int end = start;
                int lastBreak = start;
                for (int j = start + 1; j <= para.length(); j++) {
                    if (paint.measureText(para.substring(start, j)) > maxWidth) {
                        break;
                    }
                    lastBreak = j;
                    if (j < para.length() && Character.isWhitespace(para.charAt(j))) {
                        // prefer breaking at whitespace
                    }
                }
                if (lastBreak <= start) lastBreak = start + 1; // at least 1 char
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append("\n");
                }
                result.append(para.substring(start, lastBreak));
                start = lastBreak;
                // skip trailing space
                while (start < para.length() && Character.isWhitespace(para.charAt(start))) start++;
            }
        }
        return result.toString();
    }

    private static String label(ChatMessage msg) {
        return msg.getRole() == ChatMessage.Role.USER ? "Me" : "AI";
    }
}
