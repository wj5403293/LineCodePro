package cn.lineai.ui.component;

import android.content.Context;
import cn.lineai.R;

public final class UserAgreementDialog {

    private UserAgreementDialog() {
    }

    public static void show(Context context, Runnable onAgree, Runnable onDisagree) {
        if (context == null) {
            return;
        }
        LegalDialog.show(
                context,
                context.getString(R.string.user_agreement_title),
                context.getString(R.string.user_agreement_text),
                context.getString(R.string.user_agreement_agree),
                context.getString(R.string.user_agreement_disagree),
                onAgree,
                onDisagree
        );
    }
}
