package top.george888.guiling;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class Api {
    static final String BASE="https://tasks.george888.top/debt/api/v1/";
    static final class Failure extends IOException {final int code;Failure(int code,String message){super(message);this.code=code;}}
    static JSONObject call(String key,String path,JSONObject body,String id) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);
        c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Accept","application/json");
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Idempotency-Key",id);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();InputStream stream=status>=200&&status<300?c.getInputStream():c.getErrorStream();
            String raw="";if(stream!=null)try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>2_000_000)throw new IOException("响应过大");}raw=out.toString("UTF-8");}
            JSONObject result;try{result=new JSONObject(raw);}catch(JSONException e){throw new Failure(status,"服务器返回无法识别的响应（"+status+"）");}
            if(status!=200)throw new Failure(status,result.optString("error","请求失败（"+status+"）"));
            return result;
        } finally {c.disconnect();}
    }
}
