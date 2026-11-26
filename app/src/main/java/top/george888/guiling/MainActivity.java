package top.george888.guiling;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;

public final class MainActivity extends Activity {
    private LinearLayout root,content;
    private int page=0;
    private final int green=Color.rgb(22,125,101),ink=Color.rgb(29,45,39),muted=Color.rgb(104,119,110);
    @Override public void onCreate(Bundle state){super.onCreate(state);show();}
    @Override public void onResume(){super.onResume();show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));t.setLineSpacing(dp(3),1);return t;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private LinearLayout stack(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=stack();l.setPadding(dp(18),dp(14),dp(18),dp(14));l.setBackground(bg(Color.WHITE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);content.addView(l,p);return l;}
    private Button button(LinearLayout parent,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextColor(green);b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void show(){
        if(isFinishing())return;
        root=stack();root.setBackgroundColor(Color.rgb(244,247,243));root.setPadding(dp(20),dp(10),dp(20),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(10),dp(20),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);
        TextView eyebrow=text("GUI LING  /  每一笔，都清楚",12,green);root.addView(eyebrow);
        TextView title=text("归零记账助手",28,ink);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        LinearLayout nav=new LinearLayout(this);String[] labels={"待确认","记录","设置"};for(int i=0;i<labels.length;i++){final int tab=i;Button b=new Button(this);b.setText(labels[i]);b.setTextColor(page==i?green:muted);b.setOnClickListener(v->{page=tab;show();});nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(nav);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=stack();content.setPadding(0,dp(12),0,dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(page==3)captureHistory();else if(page==2)settings();else entries();
    }
    private boolean bound(){return !Vault.prefs(this).getString("token","").isEmpty();}
    private void captureHistory(){
        LinearLayout header=card();header.addView(text("采集日志",22,ink));
        header.addView(text("通知服务："+(PaymentListener.connected?"已连接":"未连接")+"\n记录微信、支付宝通知的接收与处理结果，以及服务连接状态。仅保留本机最近200条，不保存通知正文，不上传服务器。日志不是消费记录，也不表示已入账。",14,muted));
        button(header,"刷新日志",this::show);
        button(header,"返回设置",()->{page=2;show();});
        button(header,"清空日志",()->new AlertDialog.Builder(this).setTitle("清空采集日志？").setMessage("不会删除待确认消费或已记账记录。").setPositiveButton("清空",(d,w)->{CaptureLog.clear(this);show();}).setNegativeButton("取消",null).show());
        JSONArray rows=CaptureLog.read(this);
        if(rows.length()==0)header.addView(text("暂无日志。更新前的通知无法补记；保持采集开启，等待下一条新通知。",14,muted));
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            LinearLayout item=card();
            item.addView(text(Instant.ofEpochMilli(row.optLong("time")).atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime().toString().replace('T',' '),12,muted));
            item.addView(text(row.optString("source")+" · "+row.optString("result"),17,ink));
            item.addView(text(row.optString("reason"),14,muted));
        }
    }
    private boolean notificationAccess(){String allowed=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");if(allowed==null)return false;ComponentName ours=new ComponentName(this,PaymentListener.class);for(String s:allowed.split(":"))if(ours.equals(ComponentName.unflattenFromString(s)))return true;return false;}
    private void entries(){
        try(Store db=new Store(this)){
            LinearLayout overview=card();overview.addView(text(page==0?"等你确认，再安心入账":"记录与同步",20,ink));
            overview.addView(text("待确认 "+db.count("pending")+"    待上传 "+db.count("queued")+"    已记账 "+db.count("done"),14,green));
            overview.addView(text("采集"+(Vault.prefs(this).getBoolean("capture",false)?"已开启":"已暂停")+" · "+(PaymentListener.connected?"通知服务已连接":"通知服务未连接")+"\n只有服务器确认成功，才显示已记账。",13,muted));
            if(!bound())button(overview,"先绑定你的账本",()->{page=2;show();});
            else button(overview,"立即同步 / 刷新",()->{UploadJob.schedule(this);UploadJob.IO.execute(()->{UploadJob.upload(getApplicationContext());runOnUiThread(()->{toast("同步检查完成");show();});});});
            List<JSONObject> rows=db.list(page==0?"pending":null,200);
            if(rows.isEmpty()){LinearLayout empty=card();empty.addView(text(page==0?"现在没有待确认消费":"还没有通知记录",20,ink));empty.addView(text("开启通知使用权和采集后，下一笔支付通知会出现在这里。看不到记录时，到设置检查授权与采集状态。",15,muted));}
            for(JSONObject r:rows){
                LinearLayout c=card();String status=r.optString("status");String label=switch(status){case "pending"->"待确认";case "queued"->"待上传";case "done"->"已记账";case "blocked"->"提交被拒绝";default->"已忽略";};
                c.addView(text(r.optString("date")+" · "+r.optString("channel")+" · "+label,12,muted));
                TextView amount=text(r.optString("amount").isEmpty()?"金额待补充":"¥ "+r.optString("amount"),26,ink);amount.setTypeface(null,Typeface.BOLD);c.addView(amount);
                c.addView(text(r.optString("note"),16,ink));c.addView(text(accountLabel(r.optString("account")),13,muted));
                if(status.equals("pending")){c.addView(text(r.optString("reason"),13,muted));button(c,"查看并处理",()->review(r));}
                else {if(!r.optString("error").isEmpty())c.addView(text(r.optString("error"),13,Color.rgb(168,75,44)));button(c,"查看通知原文",()->new AlertDialog.Builder(this).setTitle(label).setMessage(r.optString("raw")+"\n\n"+(status.equals("done")?"服务器记录编号："+r.optString("remote_id"):"提交后请求内容已锁定，重试不会重复扣款。")).setPositiveButton("知道了",null).show());}
            }
        }
    }
    private String accountLabel(String value){for(Parser.Account a:Store.accounts(this))if(a.value().equals(value))return a.name();return "付款账户待确认";}
    private EditText field(LinearLayout form,String name,String value,int type){form.addView(text(name,13,muted));EditText e=new EditText(this);e.setText(value);e.setInputType(type);e.setTextColor(ink);e.setSingleLine(true);form.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Spinner choices(LinearLayout form,String name,List<String> values,int selected){form.addView(text(name,13,muted));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);s.setSelection(selected);form.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    private void review(JSONObject row){
        String id=row.optString("id");LinearLayout form=stack();form.setPadding(dp(20),dp(10),dp(20),dp(16));
        TextView raw=text(row.optString("raw"),14,muted);raw.setTextIsSelectable(true);form.addView(raw);
        if(row.optString("reason").startsWith("退款、")){
            new AlertDialog.Builder(this).setTitle("非普通消费消息").setMessage(row.optString("raw")+"\n\n此版本不自动处理退款、还款或转账，请在归零手账核对登记。")
                .setPositiveButton("忽略此通知",(d,w)->{try(Store s=new Store(this)){s.ignore(id);}show();}).setNegativeButton("返回",null).show();return;
        }
        EditText date=field(form,"消费日期（北京时间）",row.optString("date"),InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText amount=field(form,"金额（元）",row.optString("amount"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        List<String> cats=List.of("餐饮","交通","购物","居住","健康","娱乐","其他");Spinner cat=choices(form,"分类",cats,Math.max(0,cats.indexOf(row.optString("category"))));
        List<Parser.Account> accounts=Store.accounts(this);List<String> names=new ArrayList<>();names.add("请选择付款账户");int selected=0;for(Parser.Account a:accounts){names.add(a.name());if(a.value().equals(row.optString("account")))selected=names.size()-1;}
        Spinner account=choices(form,"付款账户（渠道："+row.optString("channel")+"）",names,selected);
        EditText note=field(form,"备注",row.optString("note"),InputType.TYPE_CLASS_TEXT);
        form.addView(text("提交前请核对是否已通过聊天或网页记过这笔。",13,muted));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("确认这笔消费").setView(scroll).setPositiveButton("确认记账",null).setNeutralButton("忽略",(d,w)->{try(Store s=new Store(this)){s.ignore(id);}show();}).setNegativeButton("返回",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                if(!bound())throw new IllegalArgumentException("请先在设置中绑定账本");
                if(account.getSelectedItemPosition()==0)throw new IllegalArgumentException("请选择付款账户");
                try(Store s=new Store(this)){s.queue(id,date.getText().toString().trim(),amount.getText().toString().trim(),cats.get(cat.getSelectedItemPosition()),accounts.get(account.getSelectedItemPosition()-1).value(),note.getText().toString());}
                dialog.dismiss();UploadJob.schedule(this);show();
            }catch(Exception e){toast(e.getMessage()==null?"请核对输入":e.getMessage());}
        }));dialog.show();
    }
    private void toggle(LinearLayout card,String label,String key,boolean value){Switch s=new Switch(this);s.setText(label);s.setTextColor(ink);s.setPadding(0,dp(12),0,dp(12));s.setChecked(value);s.setOnCheckedChangeListener((b,on)->{if(on&&key.equals("automatic")){s.setChecked(false);new AlertDialog.Builder(this).setTitle("开启明确消费自动入账？").setMessage("仅当通知明确显示支付成功、唯一金额及唯一付款账户时自动提交。新通知可能与手工记账重复，开启后请避免再次手工登记相同消费。未识别的信息仍需确认。").setPositiveButton("开启",(d,w)->{Vault.prefs(this).edit().putBoolean(key,true).apply();show();}).setNegativeButton("取消",null).show();return;}Vault.prefs(this).edit().putBoolean(key,on).apply();if(key.equals("pause_upload")&&!on)UploadJob.schedule(this);});card.addView(s);}
    private void settings(){
        LinearLayout connect=card();connect.addView(text("1 / 绑定归零手账",20,ink));connect.addView(text(bound()?"已绑定 · "+Vault.prefs(this).getString("agent_name",""):"使用独立的手机助手密钥，需 read + write 权限。",14,muted));
        button(connect,bound()?"刷新账户列表":"输入 API Key",()->{if(bound())refreshAccounts();else bind();});
        button(connect,"打开归零手账",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://tasks.george888.top/debt/"))));
        LinearLayout capture=card();capture.addView(text("2 / 通知采集",20,ink));
        capture.addView(text("通知使用权："+(notificationAccess()?"已授权":"未授权")+"\n服务连接："+(PaymentListener.connected?"已连接":"未连接"),14,green));
        button(capture,"打开通知使用权设置",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(capture,"查看采集日志",()->{page=3;show();});
        toggle(capture,"允许采集支付通知","capture",Vault.prefs(this).getBoolean("capture",false));
        toggle(capture,"明确消费自动入账","automatic",Vault.prefs(this).getBoolean("automatic",false));
        toggle(capture,"暂停上传（仍可本地采集）","pause_upload",Vault.prefs(this).getBoolean("pause_upload",false));
        long last=Vault.prefs(this).getLong("last_capture",0);capture.addView(text(last==0?"尚未成功识别并保存支付通知；接收情况请查看采集日志":"最近识别保存："+Instant.ofEpochMilli(last).atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime().toString().replace('T',' '),13,muted));
        String err=Vault.prefs(this).getString("capture_error","");if(!err.isEmpty())capture.addView(text(err,13,muted));
        LinearLayout help=card();help.addView(text("小米14安装与排查",20,ink));help.addView(text("① 安装后打开通知使用权；部分系统需先在应用信息页允许受限制的设置。\n② 在系统设置中搜索本应用的自启动、后台运行或省电设置，允许必要的后台运行。HyperOS版本不同，入口可能不同。\n③ 打开微信/支付宝系统通知及消息详情；微信支付消息不要设为免打扰。\n④ 锁屏、重启后各做一次实际支付通知测试。只在微信内显示的消息无法读取。\n⑤ 没有识别到账户时会停留在待确认，不会猜着扣款。",14,muted));
        button(help,"本地解析演示（不入账）",this::demo);
        LinearLayout update=card();update.addView(text("应用更新 · 0.1.4",20,ink));
        button(update,"检查更新",()->AppUpdate.check(this));
        button(update,"安装已下载更新",()->AppUpdate.resumeInstall(this));
        LinearLayout privacy=card();privacy.addView(text("数据与隐私",20,ink));privacy.addView(text("系统通知权限范围较广，本应用在手机本地仅筛选微信和支付宝的指定支付服务消息。通知原文留在本机；服务器只接收确认后的记账字段。无广告、无分析SDK，不读取聊天数据库。密钥用Android Keystore加密保存，禁止系统备份。\n此版本不自动处理退款、转账或还款。匹配规则需要用你的真实通知验证，不能保证每笔都采集。",14,muted));
        button(privacy,"解除绑定并清空本地记录",()->new AlertDialog.Builder(this).setTitle("解除绑定").setMessage("只清空手机记录和密钥，服务器账本不会删除。为避免已发出但响应丢失的请求被重复记账，有待上传或提交被拒绝的记录时不能解除。先核对处理这些记录。")
            .setPositiveButton("确认解除",(d,w)->{try(Store s=new Store(this)){if(s.count("queued")>0||s.count("blocked")>0){toast("仍有提交中的记录，请先核对，或联系维护者处理");return;}s.clear();CaptureLog.clear(this);Vault.prefs(this).edit().clear().commit();getSystemService(android.app.job.JobScheduler.class).cancel(1001);show();}}).setNegativeButton("取消",null).show());
        content.addView(text("归零记账助手 0.1.4 · Android 8.0+\n支付通知采集测试版",12,muted));
    }
    private void bind(){
        LinearLayout form=stack();form.setPadding(dp(20),dp(8),dp(20),dp(12));EditText key=field(form,"手机助手专用 API Key","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("绑定你的账本").setView(form).setPositiveButton("验证并绑定",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(-1).setOnClickListener(v->{String token=key.getText().toString().trim();if(!token.matches("gz_[A-Za-z0-9_-]{43}")){toast("密钥格式不正确");return;}dialog.getButton(-1).setEnabled(false);
            UploadJob.IO.execute(()->{try{JSONObject me=Api.call(token,"me",null,null);JSONArray scopes=me.getJSONArray("scopes");boolean read=false,write=false;for(int i=0;i<scopes.length();i++){read|=scopes.getString(i).equals("read");write|=scopes.getString(i).equals("write");}if(!read||!write)throw new IllegalArgumentException("密钥需要read和write权限");JSONObject accounts=Api.call(token,"accounts",null,null);Vault.save(getApplicationContext(),token);Vault.prefs(this).edit().putString("accounts",accounts.getJSONArray("payment_accounts").toString()).putString("agent_name",me.getString("agent_name")).commit();runOnUiThread(()->{key.setText("");dialog.dismiss();toast("绑定成功，请开启通知使用权及采集");show();});}catch(Exception e){runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);toast("绑定失败："+(e instanceof Api.Failure?e.getMessage():"请检查网络、密钥或权限"));});}});
        });});dialog.show();
    }
    private void refreshAccounts(){UploadJob.IO.execute(()->{try{JSONObject r=Api.call(Vault.read(this),"accounts",null,null);Vault.prefs(this).edit().putString("accounts",r.getJSONArray("payment_accounts").toString()).commit();runOnUiThread(()->{toast("账户列表已更新");show();});}catch(Exception e){runOnUiThread(()->toast("刷新失败，请检查网络或密钥"));}});}
    private void demo(){
        LinearLayout form=stack();form.setPadding(dp(20),dp(10),dp(20),dp(10));EditText sample=field(form,"模拟微信支付通知（只本地解析）","支付成功\n支付金额：10.10元\n收款方：早餐店\n支付方式：招商银行",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);sample.setSingleLine(false);sample.setMinLines(5);
        new AlertDialog.Builder(this).setTitle("解析演示，不会记账").setView(form).setPositiveButton("解析",(d,w)->{Parser.Result r=Parser.parse("com.tencent.mm","微信支付",sample.getText().toString(),Store.accounts(this));new AlertDialog.Builder(this).setTitle("本地解析结果").setMessage("金额："+r.amount()+"\n分类："+r.category()+"\n账户："+accountLabel(r.account())+"\n结果："+r.reason()+"\n\n本示例未保存、未上传。").setPositiveButton("知道了",null).show();}).setNegativeButton("取消",null).show();
    }
}
