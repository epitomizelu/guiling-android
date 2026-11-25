package top.george888.guiling;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    Context context;Store store;
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=new Store(context);store.clear();Vault.prefs(context).edit().clear().commit();}
    @After public void cleanup(){store.close();}
    private Parser.Result parsed(){return new Parser.Result(true,"微信","10.10","餐饮","asset:2","早餐","信息完整",true);}
    @Test public void queueAndNotificationDedup() throws Exception {
        String id=store.add("event-1","signature",10000,"2026-01-01","raw",parsed(),true);
        assertNotNull(id);assertNull(store.add("event-1","signature",10001,"2026-01-01","raw",parsed(),true));
        assertEquals(1,store.count("queued"));String body=store.get(id).getString("body");assertEquals("10.10",new JSONObject(body).getString("amount"));
        String other=store.add("event-2","signature",10002,"2026-01-01","raw",parsed(),true);
        assertEquals("pending",store.get(other).getString("status"));
        try{store.queue(id,"2026-01-01","20","购物","asset:2","changed");fail();}catch(IllegalStateException expected){}
        assertEquals(body,store.get(id).getString("body"));
        store.finish(id,"done","",123);assertEquals(123,store.get(id).getInt("remote_id"));
    }
    @Test public void pendingDoesNotQueueWithoutConsent() throws Exception {
        String id=store.add("event","sig",1,"2026-01-01","raw",parsed(),false);assertEquals(0,store.count("queued"));
        store.queue(id,"2026-01-01","11.20","餐饮","asset:2","早餐");assertEquals(1,store.count("queued"));
        assertEquals("11.20",new JSONObject(store.get(id).getString("body")).getString("amount"));
    }
    @Test public void secretEncryptedAndRoundTrips() throws Exception {
        String dummy="gz_"+"a".repeat(43);Vault.save(context,dummy);assertEquals(dummy,Vault.read(context));assertFalse(Vault.prefs(context).getString("token","").contains(dummy));
    }
    @Test public void launchActivityWithoutCredentials(){
        Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        assertNotNull(activity);InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }
}
