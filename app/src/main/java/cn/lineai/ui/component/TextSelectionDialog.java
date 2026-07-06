package cn.lineai.ui.component;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.ScrollView;

public final class TextSelectionDialog {

    private TextSelectionDialog() {}

    public static void show(Context context, String content) {
        EditText editText = new EditText(context);
        editText.setText(content);
        editText.setTextSize(15);
        editText.setTextColor(Color.WHITE);
        editText.setBackgroundColor(0xFF1E1E2E);
        editText.setPadding(30, 30, 30, 30);
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setKeyListener(null);          // Read-only
        editText.setTextIsSelectable(true);     // Dialog has its own Window, doesn't steal touch
        editText.selectAll();

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(editText);

        new AlertDialog.Builder(context)
                .setTitle("长按选中文字")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }
}
