package top.george888.guiling;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class ParserTest {
    private final List<Parser.Account> accounts=List.of(new Parser.Account("asset:2","招商银行"),new Parser.Account("asset:1","微信余额"),new Parser.Account("debt:8","花呗"),new Parser.Account("asset:6","支付宝余额"));
    private Parser.Result wx(String text){return Parser.parse("com.tencent.mm","微信支付",text,accounts);}
    @Test public void explicitPaymentAndAccount(){Parser.Result r=wx("支付成功\n支付金额：10.10元\n收款方：早餐店\n支付方式：招商银行（1234）");assertTrue(r.automatic());assertEquals("10.10",r.amount());assertEquals("asset:2",r.account());assertEquals("餐饮",r.category());}
    @Test public void ordinaryChatAndOtherPackagesIgnored(){assertFalse(Parser.parse("com.tencent.mm","张三","今天一起吃饭吗",accounts).candidate());assertFalse(Parser.parse("com.fake.app","微信支付","支付成功10元",accounts).candidate());}
    @Test public void unknownWechatTitlesBecomeManualCandidates(){
        for(String title:List.of("微信","微信支付（2条消息）","交易提醒","张三")){
            Parser.Result r=Parser.parse("com.tencent.mm",title,"微信支付：支付成功10.10元\n付款方式：招商银行",accounts);
            assertTrue(title,r.candidate());assertEquals("10.10",r.amount());assertEquals("asset:2",r.account());assertFalse(title,r.automatic());
            assertTrue(r.reason().contains("标题未验证"));
        }
    }
    @Test public void unknownWechatIncompleteAndExcludedMessagesStaySafe(){
        Parser.Result missing=Parser.parse("com.tencent.mm","微信","支付成功，查看详情",accounts);
        assertTrue(missing.candidate());assertEquals("",missing.amount());assertFalse(missing.automatic());
        for(String type:List.of("转账","还款","失败")){
            Parser.Result r=Parser.parse("com.tencent.mm","微信","支付成功10元\n付款方式：招商银行\n"+type,accounts);
            assertTrue(r.candidate());assertFalse(r.automatic());assertTrue(r.reason().startsWith("退款、"));
        }
        assertFalse(Parser.parse("com.tencent.mm","优惠提醒","支付有优惠，领10元优惠券",accounts).candidate());
    }
    @Test public void unknownAccountStaysPending(){Parser.Result r=wx("支付成功10.10元");assertTrue(r.candidate());assertFalse(r.automatic());assertEquals("",r.account());}
    @Test public void excludedTypesCannotAutoSubmit(){for(String s:List.of("退款","还款","转账","收款","到账","充值","提现","失败","取消","待支付","验证码"))assertFalse(s,wx("支付成功10元\n付款方式：招商银行\n"+s).automatic());}
    @Test public void multipleAmountsStayPending(){assertEquals("",wx("支付成功10元\n支付成功20元\n付款方式：招商银行").amount());}
    @Test public void accountNameInMerchantIsNotPaymentMethod(){assertEquals("",wx("支付成功10元\n收款方：招商银行附近便利店").account());}
    @Test public void walletAndCreditMappings(){assertEquals("asset:1",wx("支付成功2元\n支付方式：零钱").account());Parser.Result r=Parser.parse("com.eg.android.AlipayGphone","支付助手","支付成功13.50元\n付款方式：花呗\n商户：滴滴",accounts);assertTrue(r.automatic());assertEquals("debt:8",r.account());assertEquals("交通",r.category());}
    @Test public void noSuccessNoAutomatic(){assertFalse(wx("支付金额：10元\n支付方式：招商银行").automatic());}
    @Test public void ambiguousAccount(){List<Parser.Account> two=new ArrayList<>(accounts);two.add(new Parser.Account("asset:7","招商银行"));assertFalse(Parser.parse("com.tencent.mm","微信支付","支付成功10元\n支付方式：招商银行",two).automatic());}
    @Test public void invalidMoney(){for(String s:List.of("-1","0","0.00","1.001","NaN","1e5","100000000")){try{Parser.normalize(s);fail(s);}catch(IllegalArgumentException expected){}}assertEquals("13.50",Parser.normalize("13.5"));}
    @Test public void fingerprint(){assertEquals(Parser.hash("a"),Parser.hash("a"));assertNotEquals(Parser.hash("a"),Parser.hash("b"));assertEquals(64,Parser.hash("a").length());}
    @Test public void unknownAlipayTitleCanBeReviewedButNeverAutoPosted(){
        Parser.Result r=Parser.parse("com.eg.android.AlipayGphone","交易提醒","你已成功付款13.50元\n付款方式：花呗",accounts);
        assertTrue(r.candidate());assertEquals("13.50",r.amount());assertEquals("debt:8",r.account());assertFalse(r.automatic());
        assertTrue(r.reason().contains("标题未验证"));
    }
    @Test public void unknownAlipayTitleWithoutAmountStillNeedsReview(){
        Parser.Result r=Parser.parse("com.eg.android.AlipayGphone","交易提醒","付款成功，点击查看详情",accounts);
        assertTrue(r.candidate());assertEquals("",r.amount());assertFalse(r.automatic());
    }
    @Test public void unknownAlipayTitleDoesNotConvertRefundIntoExpense(){
        Parser.Result r=Parser.parse("com.eg.android.AlipayGphone","交易提醒","付款成功10元，已退款\n付款方式：花呗",accounts);
        assertFalse(r.candidate());assertFalse(r.automatic());assertEquals("退款通知已忽略",r.reason());
    }
    @Test public void refundsAndDailyReportsNeverBecomeExpenses(){
        for(String pkg:List.of("com.tencent.mm","com.eg.android.AlipayGphone")){
            assertFalse(Parser.parse(pkg,"微信支付","退款到账通知",accounts).candidate());
            assertFalse(Parser.parse(pkg,"微信支付","昨日记账日报，点击查看详情",accounts).candidate());
            assertFalse(Parser.parse(pkg,"退款到账","已支付¥10.20",accounts).candidate());
        }
        assertFalse(Parser.parse("com.tencent.mm","微信支付","今天的消息",accounts).candidate());
    }
    @Test public void ordinaryAlipayPromotionsRemainIgnored(){
        assertFalse(Parser.parse("com.eg.android.AlipayGphone","优惠提醒","今天优惠10元，去看看",accounts).candidate());
    }
    @Test public void screenshotGroupedWechatPrefillsYuanWithoutYuanSuffix(){
        Parser.Result r=Parser.parse("com.tencent.mm","微信支付","[2条]微信支付: 已支付¥10.20",accounts);
        assertTrue(r.candidate());assertEquals("10.20",r.amount());assertFalse(r.automatic());
        assertTrue(r.reason().contains("合并通知"));
    }
    @Test public void groupedWechatWithUnknownTitlePrefillsAndNeverAutoPosts(){
        Parser.Result r=Parser.parse("com.tencent.mm","微信","[2条]微信支付: 已支付￥10.20\n付款方式：零钱",accounts);
        assertTrue(r.candidate());assertEquals("10.20",r.amount());assertFalse(r.automatic());
    }
    @Test public void standaloneCurrencyPaymentWithoutYuanSuffixIsDetected(){
        Parser.Result r=Parser.parse("com.eg.android.AlipayGphone","支付助手","付款成功¥13.50\n付款方式：花呗",accounts);
        assertEquals("13.50",r.amount());assertTrue(r.automatic());
    }
    @Test public void plainCurrencyPromotionIsNotPayment(){
        assertFalse(Parser.parse("com.tencent.mm","优惠提醒","可领¥10.20优惠券",accounts).candidate());
    }
}
