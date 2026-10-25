package live.pharmaradio.whatsappai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;

/** Reads a contact portrait on demand. No contact photo is copied into call history. */
final class ContactPortrait {
    static final class Info {
        final String name;
        final Bitmap photo;
        Info(String name, Bitmap photo) { this.name = name; this.photo = photo; }
    }

    static Info lookup(Context context, String number) {
        if (number == null || number.isEmpty()
                || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return new Info("", null);
        Uri lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor rows = context.getContentResolver().query(lookup,
                new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null)) {
            if (rows == null || !rows.moveToFirst()) return new Info("", null);
            long id = rows.getLong(0);
            String name = rows.getString(1);
            while (rows.moveToNext()) if (rows.getLong(0) != id) return new Info("", null);
            Uri contact = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
            Bitmap image = null;
            try (InputStream stream = ContactsContract.Contacts.openContactPhotoInputStream(
                    context.getContentResolver(), contact, true)) {
                if (stream != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    image = BitmapFactory.decodeStream(stream, null, options);
                }
            } catch (Exception ignored) { /* A name is still useful if the portrait is unavailable. */ }
            return new Info(name == null ? "" : name, image);
        } catch (Exception ignored) { return new Info("", null); }
    }

    static LinearLayout add(LinearLayout parent, String number, String savedName) {
        Context context = parent.getContext();
        Info contact = lookup(context, number);
        String name = contact.name.isEmpty() ? (savedName == null || savedName.isEmpty() ? number : savedName) : contact.name;
        if (name == null || name.isEmpty()) name = "Contact inconnu";
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(-1, Ui.dp(context, 78));
        placement.bottomMargin = Ui.dp(context, 10);
        parent.addView(row, placement);
        if (contact.photo != null) {
            ImageView portrait = new ImageView(context);
            portrait.setImageBitmap(contact.photo);
            portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(Ui.LAVENDER);
            portrait.setBackground(circle);
            portrait.setClipToOutline(true);
            portrait.setContentDescription("Photo de " + name);
            row.addView(portrait, new LinearLayout.LayoutParams(Ui.dp(context, 68), Ui.dp(context, 68)));
        } else {
            TextView initials = new TextView(context);
            initials.setText(name.equals(number) ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.getDefault()));
            initials.setTextSize(27);
            initials.setTextColor(Ui.INK);
            initials.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(Ui.LAVENDER);
            initials.setBackground(circle);
            initials.setContentDescription("Photo indisponible pour " + name);
            row.addView(initials, new LinearLayout.LayoutParams(Ui.dp(context, 68), Ui.dp(context, 68)));
        }
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelSpace = new LinearLayout.LayoutParams(0, -2, 1);
        labelSpace.leftMargin = Ui.dp(context, 15);
        row.addView(labels, labelSpace);
        TextView title = Ui.text(labels, name, 21);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (number != null && !number.isEmpty() && !name.equals(number))
            Ui.text(labels, number, 14).setTextColor(Ui.MUTED);
        return row;
    }
}
