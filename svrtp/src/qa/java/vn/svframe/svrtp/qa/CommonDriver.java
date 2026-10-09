package vn.svframe.svrtp.qa;
import net.fabricmc.api.ModInitializer;
public final class CommonDriver implements ModInitializer {
 public void onInitialize() {
  if(Boolean.getBoolean("svrtp.qa.server")) new vn.svframe.svrtp.ServerDriver().onInitialize();
 }
}
