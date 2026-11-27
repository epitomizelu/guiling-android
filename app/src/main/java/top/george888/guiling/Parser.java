package top.george888.guiling;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Conservative parser: notification text is data, never executable instructions. */
public final class Parser {
    public record Account(String value, String name) {}
    public record Result(boolean candidate, String channel, String amount, String category, String account, String merchant, String reason, boolean automatic) {}
    private static final Pattern AMOUNT = Pattern.compile("(?:支付金额|付款金额|交易金额|实付金额|支出金额|成功付款|成功支付|支付成功|付款成功|消费成功|支付|付款|消费|支出)\\s*[:：]?\\s*[¥￥]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元");
    private static final Pattern REVERSE = Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元\\s*(?:支付成功|付款成功)");
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile("(?:已支付|已付款|支付成功|付款成功|成功支付|成功付款|支付金额|付款金额|实付金额|支付|付款)\\s*[:：]?\\s*[¥￥]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?:\\s*元)?");
    private static final Pattern GROUPED = Pattern.compile("[\\[【]\\s*[2-9][0-9]*\\s*条[\\]】]");
    private static final Pattern ACCOUNT = Pattern.compile("(?:支付方式|付款方式|扣款账户|付款账户)\\s*[:：]\\s*([^\\n；;]+)");
    public static Result parse(String pkg, String title, String text, List<Account> accounts) {
        boolean wx=pkg.equals("com.tencent.mm"), ali=pkg.equals("com.eg.android.AlipayGphone");
        String channel=wx?"微信":ali?"支付宝":"";
        String t=(title==null?"":title).trim(), body=(text==null?"":text).trim();
        if(Pattern.compile("退款|退回|退还").matcher(t+"\n"+body).find())
            return new Result(false,channel,"","其他","","","退款通知已忽略",false);
        if(Pattern.compile("记账日报|账单日报").matcher(t+"\n"+body).find())
            return new Result(false,channel,"","其他","","","记账日报已忽略",false);
        boolean service=wx ? t.matches("^(微信支付|微信支付凭证|微信支付服务)(?:[· ·：:].*)?$") : ali && t.matches("^(支付宝|支付助手|支付宝支付助手|付款成功|支付成功)(?:[· ·：:].*)?$");
        String all=t+"\n"+body;
        // Both payment apps use multiple notification titles. Unknown titles may be reviewed,
        // but must never inherit the trusted-title automatic submission policy.
        boolean reviewOnly=(wx || ali) && !service && (
            Pattern.compile("支付成功|付款成功|消费成功|成功支付|成功付款").matcher(all).find()
            || AMOUNT.matcher(body).find() || REVERSE.matcher(body).find() || CURRENCY_AMOUNT.matcher(body).find());
        if (!service && !reviewOnly) return new Result(false,channel,"","其他","","","标题未匹配支付服务，正文也无明确付款信息",false);
        if (!Pattern.compile("支付|付款|消费|扣款|还款|转账|收款").matcher(body).find()) return new Result(false,channel,"","其他","","","非交易消息",false);
        boolean excluded=Pattern.compile("退款|还款|转账|收款(?!方)|到账|充值|提现|红包|失败|取消|关闭|待支付|待付款|验证码").matcher(all).find();
        Set<String> amounts=new LinkedHashSet<>();
        for (Pattern p:List.of(AMOUNT,REVERSE,CURRENCY_AMOUNT)) {
            Matcher m=p.matcher(all);
            while(m.find())try { String v=normalize(m.group(1).replace(",","")); if(!v.isEmpty())amounts.add(v); } catch(Exception ignored) {}
        }
        String amount=amounts.size()==1?amounts.iterator().next():"";
        Matcher acct=ACCOUNT.matcher(body); String method=acct.find()?acct.group(1).trim():"";
        List<Account> matched=new ArrayList<>();
        for(Account a:accounts) {
            boolean hit=!method.isEmpty() && method.contains(a.name());
            if (a.name().equals("微信余额")) hit=wx && method.matches("(?:微信)?零钱(?:[（(].*)?");
            if (a.name().equals("支付宝余额")) hit=ali && method.matches("(?:支付宝)?余额(?:[（(].*)?");
            if (a.name().equals("京东")) hit=method.contains("京东白条");
            if (hit)matched.add(a);
        }
        String account=matched.size()==1?matched.get(0).value():"";
        Matcher merchantPattern=Pattern.compile("(?:收款方|商户名称|商户|商品说明|商品)\\s*[:：]\\s*([^\\n；;]+)").matcher(body);
        String merchant=merchantPattern.find()?merchantPattern.group(1).trim():"通知消费";
        if(merchant.length()>100)merchant=merchant.substring(0,100);
        String category=merchant.matches(".*(早餐|午餐|晚餐|餐饮|饭|咖啡|奶茶|面馆).* ".trim())?"餐饮":merchant.matches(".*(打车|滴滴|出租|公交|地铁).* ".trim())?"交通":"其他";
        boolean success=Pattern.compile("支付成功|付款成功|消费成功|成功支付|成功付款").matcher(all).find();
        String reason=excluded?"退款、转账、还款或非成功交易，禁止自动按消费提交":amount.isEmpty()?"未找到唯一明确的付款金额":account.isEmpty()?"付款账户未明确或匹配不唯一":!success?"未明确标记支付成功":"信息完整，可确认";
        boolean grouped=GROUPED.matcher(all).find();
        if(grouped&&!excluded)reason="合并通知可能包含多笔，请逐笔核对；"+reason;
        else if(reviewOnly&&!excluded)reason=channel+"通知标题未验证，请核对是否为真实消费后手动确认；"+reason;
        return new Result(true,channel,amount,category,account,merchant,reason,!grouped && !reviewOnly && !excluded && !amount.isEmpty() && !account.isEmpty() && success);
    }
    public static String normalize(String value) {
        if(!value.matches("\\d{1,8}(?:\\.\\d{1,2})?"))throw new IllegalArgumentException("金额需为正数，最多两位小数");
        BigDecimal n=new BigDecimal(value);
        if(n.signum()<=0)throw new IllegalArgumentException("金额必须大于零");
        return n.setScale(2).toPlainString();
    }
    public static String hash(String s) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}
    }
}
