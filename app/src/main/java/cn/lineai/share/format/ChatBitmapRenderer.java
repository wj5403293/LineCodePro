package cn.lineai.share.format;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ChatMessages;
import java.util.ArrayList;
import java.util.List;

public final class ChatBitmapRenderer {
    private static final int IMG_WIDTH = 720;
    private static final int PADDING = 32;
    private static final int FONT_SIZE = 28;
    private static final int BUBBLE_RADIUS = 24;
    private static final int SPACING = 16;
    private static final int NAME_SIZE = 24;

    public static Bitmap render(List<ChatMessage> messages) {
        ChatLayout layout = new ChatLayout(messages).measure();
        Bitmap bitmap = Bitmap.createBitmap(IMG_WIDTH, layout.totalHeight, Bitmap.Config.ARGB_8888);
        new ChatCanvas(bitmap).draw(layout);
        return bitmap;
    }

    static final class TextBlock {
        final String name;
        final String content;
        final boolean isUser;
        final List<String> lines;
        final int height;
        final int width;

        TextBlock(String name, String content, boolean isUser, List<String> lines, int height, int width) {
            this.name = name;
            this.content = content;
            this.isUser = isUser;
            this.lines = lines;
            this.height = height;
            this.width = width;
        }
    }

    static final class ChatLayout {
        final List<ChatMessage> messages;
        final List<TextBlock> blocks = new ArrayList<>();
        int totalHeight;

        ChatLayout(List<ChatMessage> messages) {
            this.messages = messages;
        }

        ChatLayout measure() {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(FONT_SIZE);
            textPaint.setColor(Color.WHITE);

            Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(NAME_SIZE);
            namePaint.setColor(Color.LTGRAY);

            int maxWidth = IMG_WIDTH - 2 * PADDING;
            int contentMaxWidth = maxWidth - 2 * PADDING;

            totalHeight = PADDING;
            for (ChatMessage msg : messages) {
                if (msg.getContent() == null || msg.getContent().isEmpty()) continue;

                boolean isUser = msg.getRole() == ChatMessage.Role.USER;
                String name = isUser ? "我" : "AI";
                String wrapped = ChatMessages.wrapText(msg.getContent(), textPaint, contentMaxWidth);
                String[] lineArray = wrapped.split("\n");
                List<String> lines = new ArrayList<>();
                for (String line : lineArray) {
                    if (!line.isEmpty()) lines.add(line);
                }

                int blockHeight = NAME_SIZE + SPACING + lines.size() * (FONT_SIZE + 4) + PADDING;
                int blockWidth = maxWidth; // full width for now

                blocks.add(new TextBlock(name, msg.getContent(), isUser, lines, blockHeight, blockWidth));
                totalHeight += blockHeight + SPACING;
            }
            totalHeight += PADDING; // bottom padding
            return this;
        }
    }

    static final class ChatCanvas {
        final Canvas canvas;
        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChatCanvas(Bitmap bitmap) {
            this.canvas = new Canvas(bitmap);
            textPaint.setTextSize(FONT_SIZE);
            textPaint.setColor(Color.WHITE);
            namePaint.setTextSize(NAME_SIZE);
            namePaint.setColor(0xFFAAAAAA);
        }

        void draw(ChatLayout layout) {
            // Background
            canvas.drawColor(0xFF1E1E2E);

            int y = PADDING;
            int maxWidth = IMG_WIDTH - 2 * PADDING;

            for (TextBlock block : layout.blocks) {
                // Name
                canvas.drawText(block.name, PADDING, y + NAME_SIZE, namePaint);
                y += NAME_SIZE + SPACING / 2;

                // Bubble
                RectF bubbleRect = new RectF(PADDING, y, PADDING + maxWidth, y + block.height - NAME_SIZE - SPACING / 2);
                bubblePaint.setColor(block.isUser ? 0xFF3B3B5C : 0xFF2A2A3E);
                canvas.drawRoundRect(bubbleRect, BUBBLE_RADIUS, BUBBLE_RADIUS, bubblePaint);

                // Text
                int textY = y + PADDING + FONT_SIZE;
                for (String line : block.lines) {
                    canvas.drawText(line, PADDING + PADDING / 2, textY, textPaint);
                    textY += FONT_SIZE + 4;
                }

                y += block.height - NAME_SIZE - SPACING / 2 + SPACING;
            }
        }
    }
}
