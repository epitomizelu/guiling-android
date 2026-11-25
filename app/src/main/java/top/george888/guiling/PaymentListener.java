package top.george888.guiling;
import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.time.*;

public final class PaymentListener extends NotificationListenerService {
    static volatile boolean connected=false;
    @Override public void onListenerConnected(){connected=true;Vault.prefs(this).edit().putLong("connected_at",System.currentTimeMillis()).apply();UploadJob.schedule(this);}
    @Override public void onListenerDisconnected(){connected=false;}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(!Vault.prefs(this).getBoolean("capture",false))return;
        String pkg=sbn.getPackageName();if(!pkg.equals("com.tencent.mm")&&!pkg.equals("com.eg.android.AlipayGphone"))return;
        Notification n=sbn.getNotification();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle e=n.extras;if(e==null)return;
        String title=string(e.getCharSequence(Notification.EXTRA_TITLE));
        String text=string(e.getCharSequence(Notification.EXTRA_BIG_TEXT));if(text.isEmpty())text=string(e.getCharSequence(Notification.EXTRA_TEXT));
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(lines!=null&&lines.length>0){StringBuilder b=new StringBuilder();for(CharSequence line:lines)b.append(line).append('\n');text=b.toString();}
        if(text.length()>6000)text=text.substring(0,6000);
        Parser.Result parsed=Parser.parse(pkg,title,text,Store.accounts(this));
        if(!parsed.candidate())return;
        long received=sbn.getPostTime(),time=n.when>0&&n.when<=received+60000?n.when:received;
        // An old timestamp is not silently converted into a new transaction.
        boolean recent=Math.abs(received-time)<300000;
        String event=Parser.hash(pkg+"|"+sbn.getKey()+"|"+time);
        String signature=Parser.hash(pkg+"|"+title+"|"+text);
        String date=Instant.ofEpochMilli(time).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        try(Store store=new Store(this)){
            store.add(event,signature,received,date,title+"\n"+text,parsed,recent&&(lines==null||lines.length<=1)&&Vault.prefs(this).getBoolean("automatic",false));
            Vault.prefs(this).edit().putLong("last_capture",received).apply();
        }catch(Exception ignored){Vault.prefs(this).edit().putString("capture_error","通知保存失败，请打开助手检查存储空间").apply();}
        UploadJob.schedule(this);
    }
    private static String string(CharSequence s){return s==null?"":s.toString();}
}
