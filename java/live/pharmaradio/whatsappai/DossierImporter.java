package live.pharmaradio.whatsappai;

import android.content.Context;
import android.net.Uri;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;

final class DossierImporter {
    static final int MAX_BYTES = 2 * 1024 * 1024;

    static byte[] bounded(InputStream stream) throws java.io.IOException {
        if (stream == null) throw new IllegalArgumentException("Document inaccessible.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.IOException("Lecture annulée");
            if (output.size() + count > MAX_BYTES) throw new IllegalArgumentException("Document limité à 2 Mo.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static String utf8(byte[] bytes) throws java.nio.charset.CharacterCodingException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return new MissionDocument("Document", text, false).text;
    }

    static String read(Context context, Uri uri) throws Exception {
        if (uri == null || !"content".equals(uri.getScheme())) throw new IllegalArgumentException("Choisissez un document via le sélecteur Android.");
        byte[] bytes;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) { bytes = bounded(stream); }
        return extract(context, bytes);
    }

    static String extract(Context context, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Document limité à 2 Mo.");
        boolean pdf = bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
        if (!pdf) return utf8(bytes);
        if (android.os.Build.VERSION.SDK_INT < 35) throw new IllegalArgumentException("L’extraction PDF nécessite Android 15 ou plus. Copiez le texte utile dans une pièce manuelle.");
        return pdf(context, bytes);
    }

    @android.annotation.TargetApi(35)
    private static String pdf(Context context, byte[] bytes) throws Exception {
        File file = File.createTempFile("mission-document-", ".pdf", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
            try (android.os.ParcelFileDescriptor descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                    android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(descriptor)) {
                if (renderer.getPageCount() > 10) throw new IllegalArgumentException("Dix pages PDF maximum. Sélectionnez un extrait plus court.");
                StringBuilder text = new StringBuilder();
                for (int index = 0; index < renderer.getPageCount(); index++) {
                    try (android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(index)) {
                        for (android.graphics.pdf.content.PdfPageTextContent block : page.getTextContents()) {
                            text.append(block.getText()).append('\n');
                            if (text.length() > 6000) throw new IllegalArgumentException("Texte PDF trop long : sélectionnez un extrait de 6 000 caractères maximum.");
                        }
                    }
                }
                if (text.toString().trim().isEmpty()) throw new IllegalArgumentException("Aucun texte extrait. PDF scanné ou non lisible : copiez les informations manuellement, sans OCR automatique.");
                return new MissionDocument("Document PDF", text.toString(), false).text;
            }
        } finally { file.delete(); }
    }
}
