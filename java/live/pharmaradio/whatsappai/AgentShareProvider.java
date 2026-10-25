package live.pharmaradio.whatsappai;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class AgentShareProvider extends ContentProvider {
    static final String AUTHORITY = "live.pharmaradio.whatsappai.agent-share";

    @Override public boolean onCreate() { return true; }

    static File directory(android.content.Context context) throws java.io.IOException {
        File directory = new File(context.getCacheDir(), "agent-share");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cache indisponible");
        return directory;
    }

    static void cleanup(android.content.Context context) throws java.io.IOException {
        File[] entries = directory(context).listFiles();
        if (entries != null) for (File file : entries) {
            if (!AgentSharePolicy.fresh(file.lastModified(), System.currentTimeMillis())) file.delete();
        }
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        try {
            String name = uri.getLastPathSegment();
            if (!"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())
                    || uri.getPathSegments().size() != 1 || !AgentSharePolicy.filename(name)
                    || uri.getQuery() != null || uri.getFragment() != null) throw new FileNotFoundException();
            File directory = directory(getContext()).getCanonicalFile();
            File file = new File(directory, name).getCanonicalFile();
            if (!directory.equals(file.getParentFile()) || !file.isFile()
                    || !AgentSharePolicy.fresh(file.lastModified(), System.currentTimeMillis())) throw new FileNotFoundException();
            return file;
        } catch (java.io.IOException error) { throw new FileNotFoundException("Partage expiré ou indisponible"); }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Lecture seule");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        try { return resolve(uri).getName().endsWith(".apk") ? "application/vnd.android.package-archive" : "application/json"; }
        catch (FileNotFoundException error) { throw new IllegalArgumentException("Partage indisponible"); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String order) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor result = new MatrixCursor(columns);
            MatrixCursor.RowBuilder row = result.newRow();
            for (String column : columns) row.add(OpenableColumns.DISPLAY_NAME.equals(column)
                    ? file.getName().endsWith(".apk") ? "whatsapp-ai-" + BuildConfig.VERSION_NAME + ".apk" : "agent-apk-modele.json"
                    : OpenableColumns.SIZE.equals(column) ? file.length() : null);
            return result;
        } catch (FileNotFoundException error) { throw new IllegalArgumentException("Partage indisponible"); }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
}
