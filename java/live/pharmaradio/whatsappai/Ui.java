package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(24, 38, 41);
    static final int MUTED = Color.rgb(88, 108, 115);
    static final int PRIMARY = Color.rgb(16, 85, 80);
    static final int BACKGROUND = Color.rgb(232, 239, 249);
    static final int BORDER = Color.rgb(204, 221, 225);
    static final int SOFT = Color.rgb(222, 248, 242);
    static final int LIME = Color.rgb(120, 250, 158);
    static final int YELLOW = Color.rgb(255, 248, 119);
    static final int LAVENDER = Color.rgb(226, 205, 242);
    static final int DANGER = Color.rgb(173, 46, 66);
    static final int AMBER = Color.rgb(143, 88, 19);

    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    static GradientDrawable rounded(int color, int radius, Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, radius));
        return background;
    }

    static GradientDrawable atmosphere(Context context) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(174, 243, 231), Color.rgb(229, 244, 247), BACKGROUND});
        return background;
    }

    static Drawable stripes(Context context) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float radius = dp(context, 28);
                RectF rect = new RectF(bounds);
                Path clip = new Path();
                clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                paint.setColor(Color.rgb(216, 232, 242));
                canvas.drawRect(rect, paint);
                paint.setColor(0x8892ADB9);
                paint.setStrokeWidth(dp(context, 2));
                for (float x = bounds.left; x < bounds.right; x += dp(context, 9))
                    canvas.drawLine(x, bounds.top, x, bounds.bottom, paint);
                canvas.restore();
            }
            @Override public void setAlpha(int value) { invalidateSelf(); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { invalidateSelf(); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    static LinearLayout root(Activity activity) {
        activity.getWindow().setStatusBarColor(Color.rgb(174, 243, 231));
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackground(atmosphere(activity));
        activity.setContentView(root);
        return root;
    }

    static void header(LinearLayout root, String title, String subtitle) {
        Context context = root.getContext();
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 22), dp(context, 19), dp(context, 22), dp(context, 12));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row);
        TextView back = new TextView(context);
        back.setText("←");
        back.setTextSize(25);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setBackground(rounded(Color.WHITE, 100, context));
        back.setContentDescription("Retour");
        back.setOnClickListener(view -> ((Activity) context).finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(23);
        heading.setTextColor(INK);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingSpace = new LinearLayout.LayoutParams(0, -2, 1);
        headingSpace.leftMargin = dp(context, 16);
        row.addView(heading, headingSpace);
        TextView seal = new TextView(context);
        seal.setText("AI");
        seal.setTextSize(16);
        seal.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        seal.setTextColor(INK);
        seal.setGravity(Gravity.CENTER);
        seal.setBackground(rounded(Color.rgb(255, 202, 177), 100, context));
        seal.setContentDescription("WhatsApp AI");
        row.addView(seal, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        if (!subtitle.isEmpty()) {
            TextView description = text(header, subtitle, 14);
            description.setTextColor(MUTED);
            LinearLayout.LayoutParams space = (LinearLayout.LayoutParams) description.getLayoutParams();
            space.topMargin = dp(context, 15);
            description.setLayoutParams(space);
        }
        root.addView(header);
    }

    static LinearLayout content(LinearLayout root) {
        ScrollView scroll = new ScrollView(root.getContext());
        scroll.setFillViewport(true);
        scroll.setBackground(rounded(Color.WHITE, 34, root.getContext()));
        scroll.setClipToOutline(true);
        LinearLayout content = new LinearLayout(root.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(root.getContext(), 22);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(-1, 0, 1);
        placement.leftMargin = dp(root.getContext(), 12);
        placement.rightMargin = dp(root.getContext(), 12);
        root.addView(scroll, placement);
        return content;
    }

    static LinearLayout page(Activity activity, String title, String subtitle) {
        LinearLayout root = root(activity);
        header(root, title, subtitle);
        pageRoutes(root, activity, title);
        return content(root);
    }

    private static void pageRoutes(LinearLayout root, Activity activity, String title) {
        LinearLayout routes = new LinearLayout(activity);
        routes.setGravity(Gravity.CENTER_VERTICAL);
        routes.setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5));
        routes.setBackground(rounded(0xAAFFFFFF, 36, activity));
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(-1, dp(activity, 62));
        placement.leftMargin = dp(activity, 22);
        placement.rightMargin = dp(activity, 22);
        placement.topMargin = dp(activity, 8);
        placement.bottomMargin = dp(activity, 12);
        root.addView(routes, placement);
        String[] icons = {"⌂", "↗", "▤", "⚙"};
        String[] labels = {"Accueil", "Appeler", "Dialogues", "Réglages"};
        Class<?>[] targets = {MainActivity.class, OutgoingCallActivity.class, HistoryActivity.class, MoreActivity.class};
        for (int index = 0; index < labels.length; index++) {
            final int target = index;
            TextView link = new TextView(activity);
            link.setText(icons[index]);
            link.setTextSize(22);
            link.setTextColor(INK);
            link.setGravity(Gravity.CENTER);
            link.setMinHeight(dp(activity, 50));
            link.setContentDescription(labels[index]);
            boolean selected = title.equals(labels[index]) || (index == 1 && title.contains("appel"));
            if (selected) {
                GradientDrawable active = rounded(Color.WHITE, 100, activity);
                active.setStroke(dp(activity, 1), BORDER);
                link.setBackground(active);
            }
            link.setOnClickListener(view -> {
                if (activity.getClass().equals(targets[target])) return;
                activity.startActivity(new Intent(activity, targets[target]));
            });
            routes.addView(link, new LinearLayout.LayoutParams(0, -1, 1));
        }
    }

    static LinearLayout card(LinearLayout parent, String title, String subtitle) {
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(parent.getContext(), 22);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(rounded(Color.rgb(241, 249, 249), 28, parent.getContext()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(parent.getContext(), 16);
        parent.addView(card, params);
        if (!title.isEmpty()) text(card, title, 23).setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (!subtitle.isEmpty()) text(card, subtitle, 15).setTextColor(MUTED);
        return card;
    }

    static TextView text(LinearLayout parent, String value, int size) {
        TextView label = new TextView(parent.getContext());
        label.setText(value);
        label.setTextColor(INK);
        label.setTextSize(size);
        label.setLineSpacing(dp(parent.getContext(), 3), 1);
        label.setPadding(0, dp(parent.getContext(), 5), 0, dp(parent.getContext(), 8));
        parent.addView(label);
        return label;
    }

    static Button button(LinearLayout parent, String label, boolean primary, Runnable action) {
        Button button = new Button(parent.getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setElevation(0);
        button.setMinHeight(dp(parent.getContext(), 56));
        GradientDrawable shape = rounded(primary ? LIME : Color.WHITE, 24, parent.getContext());
        if (!primary) shape.setStroke(dp(parent.getContext(), 1), BORDER);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22136960), shape, null));
        int[][] states = new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}};
        button.setTextColor(new android.content.res.ColorStateList(states, new int[]{MUTED, INK}));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(parent.getContext(), 8);
        parent.addView(button, params);
        return button;
    }

    static void danger(Button button) {
        int[][] states = new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}};
        button.setTextColor(new android.content.res.ColorStateList(states, new int[]{MUTED, Color.WHITE}));
        button.setBackground(rounded(DANGER, 14, button.getContext()));
    }

    static EditText field(LinearLayout parent, String hint, boolean multiline, int maxLength) {
        EditText input = new EditText(parent.getContext());
        input.setHint(hint);
        input.setTextSize(17);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setSaveEnabled(false);
        GradientDrawable background = rounded(Color.WHITE, 18, parent.getContext());
        background.setStroke(dp(parent.getContext(), 1), BORDER);
        input.setBackground(background);
        input.setPadding(dp(parent.getContext(), 13), dp(parent.getContext(), 12), dp(parent.getContext(), 13), dp(parent.getContext(), 12));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) input.setMinLines(2);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxLength)});
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(parent.getContext(), 7);
        params.bottomMargin = dp(parent.getContext(), 7);
        parent.addView(input, params);
        return input;
    }

    static void navigation(LinearLayout root, String selected, java.util.function.Consumer<String> callback) {
        LinearLayout bar = new LinearLayout(root.getContext());
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(rounded(Color.WHITE, 28, root.getContext()));
        bar.setPadding(dp(root.getContext(), 6), dp(root.getContext(), 6), dp(root.getContext(), 6), dp(root.getContext(), 6));
        bar.setElevation(dp(root.getContext(), 3));
        for (String tab : new String[]{"Accueil", "Appeler", "Dialogues", "Réglages"}) {
            TextView item = new TextView(root.getContext());
            item.setText(tab);
            item.setTextSize(13);
            item.setGravity(Gravity.CENTER);
            item.setMinHeight(dp(root.getContext(), 50));
            item.setTextColor(tab.equals(selected) ? INK : MUTED);
            if (tab.equals(selected)) item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (tab.equals(selected)) item.setBackground(rounded(SOFT, 22, root.getContext()));
            item.setContentDescription(tab);
            item.setOnClickListener(view -> callback.accept(tab));
            bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
        position.leftMargin = dp(root.getContext(), 16);
        position.rightMargin = dp(root.getContext(), 16);
        position.bottomMargin = dp(root.getContext(), 12);
        root.addView(bar, position);
    }
}
