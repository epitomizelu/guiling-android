package top.george888.guiling;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdate {
    private static final String BASE="https://tasks.george888.top/guiling-android/";
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private static void message(Activity a,String s){a.runOnUiThread(()->{if(!a.isFinishing())Toast.makeText(a,s,Toast.LENGTH_LONG).show();});}
    private static HttpURLConnection open(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setUseCaches(false);
        int status=c.getResponseCode();if(status!=200){c.disconnect();throw new IOException(status==404?"尚未发布更新，请稍后再试":"更新服务器返回 "+status);}return c;
    }
    static void check(Activity a){
        if(!busy.compareAndSet(false,true)){message(a,"正在检查或下载更新，请稍候");return;}
        message(a,"正在检查更新…");
        worker.execute(()->{
            try{
                HttpURLConnection c=open(BASE+"latest.json");byte[] bytes;
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("更新信息过大");out.write(b,0,n);}bytes=out.toByteArray();
                }finally{c.disconnect();}
                JSONObject info=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                int code=info.getInt("versionCode");
                if(code<=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionCode){message(a,"已是最新版本");return;}
                String file=info.getString("file"),hash=info.getString("sha256");long size=info.getLong("size");
                if(!file.equals("guiling-"+code+".apk")||!hash.matches("[a-f0-9]{64}")||size<1||size>100*1024*1024)throw new IOException("更新信息格式无效");
                a.runOnUiThread(()->{if(!a.isFinishing())new AlertDialog.Builder(a).setTitle("发现新版 "+info.optString("versionName"))
                    .setMessage(info.optString("notes","修复与功能更新")+"\n\n下载约 "+(size/1024/1024+1)+" MB，完成后由系统确认安装。")
                    .setPositiveButton("下载更新",(d,w)->download(a,file,hash,size,code)).setNegativeButton("暂不更新",null).show();});
            }catch(Exception e){message(a,"检查失败："+e.getMessage());}finally{busy.set(false);}
        });
    }
    private static void download(Activity a,String name,String hash,long size,int code){
        if(!busy.compareAndSet(false,true))return;
        message(a,"正在下载更新，请保持应用打开…");
        worker.execute(()->{
            File directory=new File(a.getCacheDir(),"updates"), file=new File(directory,"download.part");
            try{
                if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建下载目录");
                File[] previous=directory.listFiles();if(previous!=null)for(File p:previous)if(p.isFile())p.delete();
                HttpURLConnection c=open(BASE+name);MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
                    byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>size)throw new IOException("文件大小不符");digest.update(buffer,0,n);out.write(buffer,0,n);}
                }finally{c.disconnect();}
                StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(total!=size||!actual.toString().equals(hash))throw new IOException("文件校验失败，请重试");
                PackageManager pm=a.getPackageManager();
                PackageInfo update=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES), current=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNATURES);
                if(update==null||!a.getPackageName().equals(update.packageName)||update.versionCode!=code)throw new IOException("安装包版本或应用标识不符");
                if(update.signatures==null||current.signatures==null||!Arrays.equals(update.signatures,current.signatures))throw new IOException("安装包签名与当前版本不同，不能覆盖更新，请联系维护者");
                File ready=new File(directory,"update.apk");if(!file.renameTo(ready))throw new IOException("无法保存安装包");
                a.runOnUiThread(()->{if(!a.isFinishing())install(a,ready);});
            }catch(Exception e){file.delete();message(a,"更新失败："+e.getMessage());}finally{busy.set(false);}
        });
    }
    private static void install(Activity a,File file){
        if(!a.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(a).setTitle("允许安装更新").setMessage("请在系统页面允许归零记账助手安装应用。返回后点击“安装已下载更新”。")
                .setPositiveButton("去授权",(d,w)->{try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(Exception e){message(a,"请在系统设置中允许本应用安装未知应用");}}).setNegativeButton("稍后",null).show();return;
        }
        try{Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".updates",file);a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
        catch(Exception e){message(a,"无法打开系统安装页面，请重试");}
    }
    static void resumeInstall(Activity a){
        if(busy.get()){message(a,"下载尚未结束");return;}
        File file=new File(a.getCacheDir(),"updates/update.apk");
        if(file.isFile())install(a,file);else message(a,"尚无已下载更新，请先检查更新");
    }
}
