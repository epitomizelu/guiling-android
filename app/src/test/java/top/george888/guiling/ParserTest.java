package top.george888.guiling;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class ParserTest {
    private final List<Parser.Account> accounts=List.of(new Parser.Account("asset:2","招商银行"),new Parser.Account("asset:1","微信余额"),new Parser.Account("debt:8","花呗"),new Parser.Account("asset:6","支付宝余额"));
    private Parser.Result wx(String text){return Parser.parse("com.tencent.mm","微信支付",text,accounts);}
    @Test public void explicitPaymentAndAccount(){Parser.Result r=wx("支付成功\n支付金额：10.10元\n收款方：早餐店\n支付方式：招商银行（1234）");assertTrue(r.automatic());assertEquals("10.10",r.amount());assertEquals("asset:2",r.account());assertEquals("餐饮",r.category());}
    @Test public void chatIgnored(){assertFalse(Parser.parse("com.tencent.mm","张三","支付成功10元",accounts).candidate());assertFalse(Parser.parse("com.fake.app","微信支付","支付成功10元",accounts).candidate());}
    @Test public void unknownAccountStaysPending(){Parser.Result r=wx("支付成功10.10元");assertTrue(r.candidate());assertFalse(r.automatic());assertEquals("",r.account());}
    @Test public void excludedTypesCannotAutoSubmit(){for(String s:List.of("退款","还款","转账","收款","到账","充值","提现","失败","取消","待支付","验证码"))assertFalse(s,wx("支付成功10元\n付款方式：招商银行\n"+s).automatic());}
    @Test public void multipleAmountsStayPending(){assertEquals("",wx("支付成功10元\n支付成功20元\n付款方式：招商银行").amount());}
    @Test public void accountNameInMerchantIsNotPaymentMethod(){assertEquals("",wx("支付成功10元\n收款方：招商银行附近便利店").account());}
    @Test public void walletAndCreditMappings(){assertEquals("asset:1",wx("支付成功2元\n支付方式：零钱").account());Parser.Result r=Parser.parse("com.eg.android.AlipayGphone","支付助手","支付成功13.50元\n付款方式：花呗\n商户：滴滴",accounts);assertTrue(r.automatic());assertEquals("debt:8",r.account());assertEquals("交通",r.category());}
    @Test public void noSuccessNoAutomatic(){assertFalse(wx("支付金额：10元\n支付方式：招商银行").automatic());}
    @Test public void ambiguousAccount(){List<Parser.Account> two=new ArrayList<>(accounts);two.add(new Parser.Account("asset:7","招商银行"));assertFalse(Parser.parse("com.tencent.mm","微信支付","支付成功10元\n支付方式：招商银行",two).automatic());}
    @Test public void invalidMoney(){for(String s:List.of("-1","0","0.00","1.001","NaN","1e5","100000000")){try{Parser.normalize(s);fail(s);}catch(IllegalArgumentException expected){}}assertEquals("13.50",Parser.normalize("13.5"));}
    @Test public void fingerprint(){assertEquals(Parser.hash("a"),Parser.hash("a"));assertNotEquals(Parser.hash("a"),Parser.hash("b"));assertEquals(64,Parser.hash("a").length());}
}
