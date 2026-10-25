package live.pharmaradio.whatsappai;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

/** Resolves only WhatsApp voice rows linked to the explicitly selected phone number. */
final class WhatsAppContactRoute {
    static Intent resolve(Context c,String number)throws Exception{
        if(number==null||!number.matches("\\+[1-9][0-9]{6,14}"))throw new IllegalArgumentException("Numéro international requis, avec + et indicatif pays.");
        String digits=number.substring(1);
        if(!digits.matches("[1-9][0-9]{6,14}"))throw new IllegalArgumentException("Numéro international requis, avec + et indicatif pays.");
        String mime="vnd.android.cursor.item/vnd.com.whatsapp.voip.call";
        Uri route=null;
        try(Cursor rows=c.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
            new String[]{"_id","data1"},"mimetype=? AND (data1=? OR data1=? OR data1=?)",
            new String[]{mime,digits+"@s.whatsapp.net",digits,"+"+digits},null)){
            if(rows!=null)while(rows.moveToNext()){
                Uri candidate=ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,rows.getLong(0));
                if(route!=null)throw new IllegalStateException("Plusieurs fiches WhatsApp : sélection ambiguë.");route=candidate;
            }
        }
        if(route==null)throw new IllegalStateException("Aucune fiche d’appel WhatsApp trouvée pour ce numéro. Vérifiez le contact et son indicatif.");
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(route,mime).setPackage("com.whatsapp");
        android.content.pm.ResolveInfo info=c.getPackageManager().resolveActivity(intent,0);
        if(info==null||info.activityInfo==null||!"com.whatsapp".equals(info.activityInfo.packageName))throw new IllegalStateException("Entrée d’appel WhatsApp indisponible.");
        return intent.setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name));
    }
}
