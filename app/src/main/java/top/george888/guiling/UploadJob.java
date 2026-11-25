package top.george888.guiling;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
import org.json.*;

public final class UploadJob extends JobService {
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Object LOCK=new Object();
    static void schedule(Context c){JobScheduler j=c.getSystemService(JobScheduler.class);j.schedule(new JobInfo.Builder(1001,new ComponentName(c,UploadJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    @Override public boolean onStartJob(JobParameters p){IO.execute(()->jobFinished(p,upload(getApplicationContext())));return true;}
    @Override public boolean onStopJob(JobParameters p){return true;}
    static boolean upload(Context c){
        synchronized(LOCK){
            if(Vault.prefs(c).getBoolean("pause_upload",false))return false;
            try(Store store=new Store(c)) {
                String key=Vault.read(c);if(key.isEmpty())return false;
                for(JSONObject row:store.list("queued",100)) {
                    String id=row.getString("id");
                    try {
                        JSONObject body=new JSONObject(row.getString("body"));
                        JSONObject result=Api.call(key,"expenses",body,id);
                        JSONObject saved=result.optJSONObject("record");
                        long cents=new java.math.BigDecimal(body.getString("amount")).movePointRight(2).longValueExact();
                        if(!result.optBoolean("ok")||saved==null||saved.optLong("amount",-1)!=cents)throw new java.io.IOException("返回记录不匹配，保留原编号重试核对");
                        store.finish(id,"done","",saved.getLong("id"));
                    } catch(Api.Failure failure){
                        if(failure.code==400||failure.code==403||failure.code==404||failure.code==409){store.finish(id,"blocked",failure.getMessage(),0);}
                        else {store.finish(id,"queued",failure.code==401?"密钥已失效，请在原账本生成新密钥，并联系维护者核对待上传记录":failure.getMessage(),0);return true;}
                    } catch(Exception error){store.finish(id,"queued","网络或响应异常，将使用同一编号重试",0);return true;}
                }
                return store.count("queued")>0;
            }catch(Exception e){return true;}
        }
    }
}
