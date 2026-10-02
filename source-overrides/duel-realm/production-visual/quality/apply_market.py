from pathlib import Path
import shutil,json
here=Path(__file__).resolve().parent.parent
base=Path("src/main")
# Market payments use the provider wallet, never CardStore's legacy coin column.
shutil.copyfile(here / 'quality/MarketCheckout.java', base / 'java/vn/svarcade/tcg/economy/MarketCheckout.java')
p = base / 'java/vn/svarcade/tcg/economy/CardStore.java'
s = p.read_text()
s = s.replace('public record Listing(String id,String seller,String serial,String name,long price) {}',
              'public record Listing(String id,String seller,String serial,String name,long price,String currency) { public Listing(String id,String seller,String serial,String name,long price){this(id,seller,serial,name,price,"");} }')
anchor = '        // Duels are ephemeral.'
assert anchor in s
schema = '''        try(var migration=db.createStatement()) {
            for(String table:List.of("listings","sales")) {
                boolean found=false;try(var columns=migration.executeQuery("PRAGMA table_info("+table+")")){while(columns.next())if(columns.getString("name").equals("currency"))found=true;}
                if(!found)migration.execute("ALTER TABLE "+table+" ADD COLUMN currency TEXT NOT NULL DEFAULT ''");
            }
            migration.execute("CREATE TABLE IF NOT EXISTS market_payments(request TEXT PRIMARY KEY,listing TEXT NOT NULL,buyer TEXT NOT NULL,seller TEXT NOT NULL,serial TEXT NOT NULL,currency TEXT NOT NULL,price INTEGER NOT NULL,tax INTEGER NOT NULL,state TEXT NOT NULL)");
            migration.execute("CREATE UNIQUE INDEX IF NOT EXISTS market_payment_listing ON market_payments(listing) WHERE state!='DECLINED'");
        }
'''
s = s.replace(anchor, schema + anchor)
s = s.replace('INSERT INTO listings VALUES(?,?,?,?)', 'INSERT INTO listings(id,seller,serial,price) VALUES(?,?,?,?)')
s = s.replace('INSERT INTO sales VALUES(?,?,?,?,?,?,?)', 'INSERT INTO sales(id,card,seller,buyer,price,tax,at) VALUES(?,?,?,?,?,?,?)')
s = s.replace('r.getLong("price")));return out;', 'r.getLong("price"),r.getString("currency")));return out;')
anchor = 'public void cancelListing(String owner,String id){tx(()->{'
assert anchor in s
s = s.replace(anchor, anchor + 'check(scalar("SELECT COUNT(*) FROM market_payments WHERE listing=? AND state NOT IN (\'DECLINED\',\'COMPLETE\')",id)==0,"This listing has a payment in progress.");')
s = s.rstrip()[:-1] + (here / 'quality/market-store.txt').read_text() + '\n}\n'
p.write_text(s)

p = base / 'java/vn/svarcade/tcg/fabric/TcgMod.java'
s = p.read_text()
s = s.replace('store.list(owner,a.get(0),Long.parseLong(a.get(1)))', 'store.listEconomy(owner,a.get(0),Long.parseLong(a.get(1)))')
old = 'case "buy" -> {store.buy(owner,a.getFirst());notice="Purchase complete.";}'
assert old in s
s = s.replace(old, '''case "buy" -> {
                    check(a.size()==2,"Choose a listing and payment receipt.");
                    check(p.getServer().isDedicated(),"Market payments require the dedicated server economy.");
                    vn.svarcade.tcg.economy.MarketCheckout.buy(store,new vn.svarcade.tcg.economy.MarketCheckout.Wallet(){
                        public boolean debit(String buyer,long amount,String currency){return vn.svarcade.tcg.integration.BEconomyCardWorlds.debitMarket(buyer,amount,currency);}
                        public void credit(String seller,long amount,String currency){vn.svarcade.tcg.integration.BEconomyCardWorlds.creditMarket(seller,amount,currency);}
                    },owner,a.get(0),a.get(1));notice="Purchase complete.";
                }''')
p.write_text(s)

p = base / 'java/vn/svarcade/tcg/integration/BEconomyCardWorlds.java'
s = p.read_text()
anchor = '    private static Object api() {'
assert anchor in s
s = s.replace(anchor, '''    private static Object marketApi(String currency) {
        if(!BEAST.equals(currency))throw new IllegalArgumentException("Invalid market currency.");
        Object api=api();requireCurrency(api,currency);
        try {
            var server=(net.minecraft.server.MinecraftServer)find(api.getClass(),"getServer",0).invoke(api);
            if(!server.isDedicated()||!server.isOnThread())throw new IllegalStateException("Economy mutations require the dedicated server thread.");
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Cannot validate economy thread ownership",e);}
        return api;
    }
    public static boolean debitMarket(String owner,long amount,String currency) {
        Object api=marketApi(currency);
        try{return Boolean.TRUE.equals(find(api.getClass(),"subtractBalance",3).invoke(api,UUID.fromString(owner),BigDecimal.valueOf(amount),currency));}
        catch(Exception e){throw new IllegalStateException("Market debit outcome requires reconciliation",e);}
    }
    public static void creditMarket(String owner,long amount,String currency) {
        Object api=marketApi(currency);
        try{find(api.getClass(),"addBalance",3).invoke(api,UUID.fromString(owner),BigDecimal.valueOf(amount),currency);}
        catch(Exception e){throw new IllegalStateException("Market seller credit outcome requires reconciliation",e);}
    }
''' + anchor)
p.write_text(s)
# Legacy CardStore callers cannot accidentally purchase a provider-priced listing.
p=base/'java/vn/svarcade/tcg/economy/CardStore.java';s=p.read_text()
start=s.index('public void buy(');end=s.index('public ',start+10)
part=s[start:end];anchor='check(r.next(),'
# Put the currency check after the existing statement has advanced to its row.
i=part.index(anchor);stop=part.index(';',i)+1
part=part[:stop]+'check(r.getString("currency").isEmpty(),"This listing requires the dedicated economy checkout.");'+part[stop:]
s=s[:start]+part+s[end:];p.write_text(s)
# Client presentation uses shared descriptors and snapshot display balances only.
p=base/'java/vn/svarcade/tcg/client/screens/MarketScreen.java';s=p.read_text()
s=s.replace('import java.util.*;','import java.util.*;import vn.svarcade.tcg.economy.CardWorldsCurrency;')
s=s.replace('"cardworlds.ui.sale_fee_5_minimum_1_coin"','"cardworlds.market.beast_fee"')
s=s.replace('"Price in HunterCoin"','"cardworlds.market.beast_price"')
old='u.text(Long.toString(l.price()),row.right()-175,y+23,18,Ui.GOLD);boolean mine=l.seller().equals(MinecraftClient.getInstance().player.getUuidAsString());u.button(mine?"Cancel":"Buy",new Rect(row.right()-93,y+18,82,29),!mine,mine||a.state.profile().coins()>=l.price(),()->a.send(mine?"cancel_listing":"buy",l.id()));'
assert old in s
new='''boolean priced=CardWorldsCurrency.BEAST.equals(l.currency());
 if(priced){CurrencyPurchaseUi.draw(u,CurrencyPurchaseUi.coin(CardWorldsCurrency.BEAST),new Rect(row.right()-192,y+20,20,20));u.text(Long.toString(l.price()),row.right()-168,y+23,18,Ui.GOLD);}
 else u.fit("cardworlds.market.reprice",new Rect(row.right()-193,y+16,96,32),12,Ui.MUTED);
 boolean mine=l.seller().equals(MinecraftClient.getInstance().player.getUuidAsString());var balances=a.state.currencyBalances();
 boolean funded=priced&&balances!=null&&balances.available()&&balances.beast()!=null&&balances.beast().compareTo(java.math.BigDecimal.valueOf(l.price()))>=0;
 u.button(mine?"Cancel":"Buy",new Rect(row.right()-93,y+18,82,29),!mine,mine||funded,()->{if(mine)a.send("cancel_listing",l.id());else a.send("buy",l.id(),UUID.randomUUID().toString());});'''
s=s.replace(old,new);p.write_text(s)
translations={
 'en_us':{'cardworlds.market.beast_price':'Price in Beast Coin','cardworlds.market.beast_fee':'Sale fee: 5% (minimum 1 Beast Coin)','cardworlds.market.reprice':'Seller must update price'},
 'vi_vn':{'cardworlds.market.beast_price':'Giá bằng Beast Coin','cardworlds.market.beast_fee':'Phí bán: 5% (tối thiểu 1 Beast Coin)','cardworlds.market.reprice':'Người bán cần cập nhật giá'},
}
for lang,entries in translations.items():
 p=base/f'resources/assets/svarcade_tcg/lang/{lang}.json';data=json.loads(p.read_text());data.update(entries)
 for key in ('cardworlds.ui.price_in_huntercoin',):data.pop(key,None)
 p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
p=base/'resources/data/svarcade_tcg/ui_translation_aliases.json';data=json.loads(p.read_text());data.pop('Price in HunterCoin',None);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
for f in (here/'quality/test').glob('*.java'):
 target=Path('src/test/java/vn/svarcade/tcg/economy')/f.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,target)
print('CARDWORLDS_MARKET_PATCH_READY currency=beastcoin legacy_prices=require_relist wallet=provider')
p=base/'java/vn/svarcade/tcg/client/component/CurrencyPurchaseUi.java';s=p.read_text();assert 'private static void draw(Ui u,ItemStack stack,Rect r)' in s;s=s.replace('private static void draw(Ui u,ItemStack stack,Rect r)','public static void draw(Ui u,ItemStack stack,Rect r)');p.write_text(s)
