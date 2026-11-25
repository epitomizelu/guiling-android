package top.george888.guiling;

import android.content.Context;
import org.json.*;

/** Local bounded diagnostic history. Never contains notification bodies or credentials. */
final class CaptureLog {
    static synchronized void add(Context c,String source,String result,String reason){
        try {
            JSONArray old=read(c), rows=new JSONArray();
            rows.put(new JSONObject().put("time",System.currentTimeMillis()).put("source",source).put("result",result).put("reason",reason));
            for(int i=0;i<old.length()&&i<199;i++)rows.put(old.get(i));
            c.getSharedPreferences("capture_log",0).edit().putString("rows",rows.toString()).apply();
        } catch(JSONException ignored) { }
    }
    static synchronized JSONArray read(Context c){
        try{return new JSONArray(c.getSharedPreferences("capture_log",0).getString("rows","[]"));}
        catch(JSONException e){return new JSONArray();}
    }
    static synchronized void clear(Context c){c.getSharedPreferences("capture_log",0).edit().clear().apply();}
}
