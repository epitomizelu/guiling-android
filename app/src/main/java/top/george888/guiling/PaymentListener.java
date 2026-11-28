package top.george888.guiling;
import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.time.*;

public final class PaymentListener extends NotificationListenerService {
    static volatile boolean connected=false;
    @Override public void onListenerConnected(){connected=true;CaptureLog.add(this,"系统","已连接","通知监听服务已连接，等待新通知");Vault.prefs(this).edit().putLong("connected_at",System.currentTimeMillis()).apply();UploadJob.schedule(this);}
    @Override public void onListenerDisconnected(){
        connected=false;
        CaptureLog.add(this,"系统","已断开","通知监听服务断开，已请求系统重连");
        try{requestRebind(new ComponentName(this,PaymentListener.class));}
        catch(RuntimeException e){CaptureLog.add(this,"系统","重连失败","请在系统设置中重新开启通知使用权");}
    }
    @Override public void onDestroy(){connected=false;super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        String pkg=sbn.getPackageName();if(!pkg.equals("com.tencent.mm")&&!pkg.equals("com.eg.android.AlipayGphone"))return;
        String source=pkg.equals("com.tencent.mm")?"微信":"支付宝";
        CaptureLog.add(this,source,"已收到","系统已将通知交给助手");
        if(!Vault.prefs(this).getBoolean("capture",false)){CaptureLog.add(this,source,"已忽略","采集开关关闭");return;}
        try { process(sbn,source); }
        catch(Exception e){CaptureLog.add(this,source,"处理失败","通知处理异常："+e.getClass().getSimpleName());}
    }
    private void process(StatusBarNotification sbn,String source){
        String pkg=sbn.getPackageName();
        Notification n=sbn.getNotification();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0){CaptureLog.add(this,source,"已忽略","分组汇总通知，等待单条通知");return;}
        Bundle e=n.extras;if(e==null){CaptureLog.add(this,source,"已忽略","通知没有可读取的内容");return;}
        String title=string(e.getCharSequence(Notification.EXTRA_TITLE));
        String text=string(e.getCharSequence(Notification.EXTRA_BIG_TEXT));if(text.isEmpty())text=string(e.getCharSequence(Notification.EXTRA_TEXT));
        CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(lines!=null&&lines.length>0){StringBuilder b=new StringBuilder();for(CharSequence line:lines)b.append(line).append('\n');text=b.toString();}
        if(text.length()>6000)text=text.substring(0,6000);
        Parser.Result parsed=Parser.parse(pkg,title,text,Store.accounts(this));
        if(!parsed.candidate()){CaptureLog.add(this,source,"未匹配",parsed.reason());return;}
        long received=sbn.getPostTime(),time=n.when>0&&n.when<=received+60000?n.when:received;
        // An old timestamp is not silently converted into a new transaction.
        boolean recent=Math.abs(received-time)<300000;
        String event=Parser.hash(pkg+"|"+sbn.getKey()+"|"+time);
        String signature=Parser.hash(pkg+"|"+title+"|"+text);
        String date=Instant.ofEpochMilli(time).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        try(Store store=new Store(this)){
            String id=store.add(event,signature,received,date,title+"\n"+text,parsed,recent&&(lines==null||lines.length<=1)&&Vault.prefs(this).getBoolean("automatic",false));
            CaptureLog.add(this,source,id==null?"已去重":"已保存",id==null?"相同通知事件已处理":"已识别并保存到记账记录；"+parsed.reason());
            Vault.prefs(this).edit().putLong("last_capture",received).apply();
        }catch(Exception ignored){CaptureLog.add(this,source,"保存失败","通知未保存，请检查存储空间");Vault.prefs(this).edit().putString("capture_error","通知保存失败，请打开助手检查存储空间").apply();}
        UploadJob.schedule(this);
    }
    private static String string(CharSequence s){return s==null?"":s.toString();}
}
