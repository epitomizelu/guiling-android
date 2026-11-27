package top.george888.guiling;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

final class Store extends SQLiteOpenHelper {
    Store(Context c){super(c,"notifications.db",null,1);}
    public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY,event TEXT UNIQUE,signature TEXT,received INTEGER,date TEXT,channel TEXT,raw TEXT,amount TEXT,category TEXT,account TEXT,note TEXT,reason TEXT,status TEXT,body TEXT DEFAULT '',remote_id INTEGER DEFAULT 0,error TEXT DEFAULT '')");d.execSQL("CREATE INDEX entries_status ON entries(status)");}
    public void onUpgrade(SQLiteDatabase d,int old,int next){throw new IllegalStateException("Unsupported migration");}
    static List<Parser.Account> accounts(Context c){List<Parser.Account> out=new ArrayList<>();try{JSONArray a=new JSONArray(Vault.prefs(c).getString("accounts","[]"));for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);out.add(new Parser.Account(x.getString("value"),x.getString("name")));}}catch(JSONException ignored){}return out;}
    synchronized String add(String event,String signature,long received,String date,String raw,Parser.Result r,boolean auto) {
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try {
            try(Cursor c=d.rawQuery("SELECT id FROM entries WHERE event=?",new String[]{event})){if(c.moveToFirst())return null;}
            boolean duplicate;
            try(Cursor c=d.rawQuery("SELECT id FROM entries WHERE signature=? AND received>=? LIMIT 1",new String[]{signature,Long.toString(received-120000)})){duplicate=c.moveToFirst();}
            String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("event",event);v.put("signature",signature);v.put("received",received);v.put("date",date);v.put("channel",r.channel());v.put("raw",raw);v.put("amount",r.amount());v.put("category",r.category());v.put("account",r.account());v.put("note",r.merchant());v.put("reason",duplicate?"可能与近期通知重复，请核对后决定":r.reason());v.put("status","pending");
            d.insertOrThrow("entries",null,v);
            if(auto&&r.automatic()&&!duplicate)queueInternal(d,id,date,r.amount(),r.category(),r.account(),r.merchant());
            d.setTransactionSuccessful();return id;
        } finally {d.endTransaction();}
    }
    synchronized void queue(String id,String date,String amount,String category,String account,String note) throws Exception {
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{queueInternal(d,id,date,amount,category,account,note);d.setTransactionSuccessful();}finally{d.endTransaction();}
    }
    private void queueInternal(SQLiteDatabase d,String id,String date,String amount,String category,String account,String note) {
        try {
            JSONObject row=get(id);if(row==null||!row.getString("status").equals("pending"))throw new IllegalStateException("记录已提交，不能更改请求内容");
            java.time.LocalDate day=java.time.LocalDate.parse(date);if(day.isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))))throw new IllegalArgumentException("不能记录未来日期");
            String n=Parser.normalize(amount);if(!account.matches("(asset|debt):[1-9]\\d*"))throw new IllegalArgumentException("请选择付款账户");
            if(!List.of("餐饮","交通","购物","居住","健康","娱乐","其他").contains(category))throw new IllegalArgumentException("分类无效");
            String memo=note.length()>450?note.substring(0,450):note;
            JSONObject body=new JSONObject().put("date",date).put("amount",n).put("category",category).put("payment_account",account).put("payment_channel",row.getString("channel")).put("note",memo+"（手机通知记账）");
            ContentValues v=new ContentValues();v.put("date",date);v.put("amount",n);v.put("category",category);v.put("account",account);v.put("note",memo);v.put("body",body.toString());v.put("status","queued");v.put("error","");d.update("entries",v,"id=? AND status='pending'",new String[]{id});
        } catch(JSONException e){throw new IllegalStateException(e);}
    }
    synchronized JSONObject get(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM entries WHERE id=?",new String[]{id})){return c.moveToFirst()?object(c):null;}}
    synchronized List<JSONObject> list(String status,int limit){List<JSONObject> out=new ArrayList<>();String sql="SELECT * FROM entries"+(status==null?"":" WHERE status=?")+" ORDER BY received DESC LIMIT "+limit;try(Cursor c=getReadableDatabase().rawQuery(sql,status==null?null:new String[]{status})){while(c.moveToNext())out.add(object(c));}return out;}
    synchronized int count(String status){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries WHERE status=?",new String[]{status})){c.moveToFirst();return c.getInt(0);}}
    synchronized void finish(String id,String status,String error,long remote){ContentValues v=new ContentValues();v.put("status",status);v.put("error",error);v.put("remote_id",remote);getWritableDatabase().update("entries",v,"id=?",new String[]{id});}
    synchronized void ignore(String id){ContentValues v=new ContentValues();v.put("status","ignored");getWritableDatabase().update("entries",v,"id=? AND status='pending'",new String[]{id});}
    synchronized boolean restore(String id){ContentValues v=new ContentValues();v.put("status","pending");return getWritableDatabase().update("entries",v,"id=? AND status='ignored' AND body=''",new String[]{id})==1;}
    synchronized void clear(){getWritableDatabase().delete("entries",null,null);}
    private static JSONObject object(Cursor c){JSONObject o=new JSONObject();try{for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.getString(i));return o;}catch(JSONException e){throw new IllegalStateException(e);}}
}
