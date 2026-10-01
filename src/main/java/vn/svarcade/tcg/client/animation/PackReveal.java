package vn.svarcade.tcg.client.animation;

import vn.svarcade.tcg.client.*;
import vn.svarcade.tcg.client.component.*;
import vn.svarcade.tcg.client.card.CardRenderer;
import vn.svarcade.tcg.client.screens.PacksScreen;
import vn.svarcade.tcg.economy.CardStore;
import java.util.*;

/** Pack presentation timeline: enter -> shake -> burst -> sequential card flips -> summary. */
public final class PackReveal {
 private String previewId="",packName="CARD WORLDS";
 private List<CardStore.Pull> results=List.of();private Map<String,Integer> before=Map.of();private long started,waiting;private int revealed;
 public void await(){await("","CARD WORLDS");}
 public void await(String card,String title){previewId=card==null?"":card;packName=title;waiting=System.currentTimeMillis();}
 public void start(List<CardStore.Pull> pulls,Map<String,Integer> counts){results=List.copyOf(pulls);before=Map.copyOf(counts);started=System.currentTimeMillis();waiting=0;revealed=0;}
 public boolean active(){return !results.isEmpty()||waiting>0&&System.currentTimeMillis()-waiting<12000;}

 public void render(CardWorldsScreen a,Ui u,Rect b){
  u.hits.clear();u.fill(b,0xF406101E);
  if(results.isEmpty()){u.text("Opening pack...",b.w()/2-90,b.h()/2,25,Ui.GOLD);return;}
  double elapsed=(System.currentTimeMillis()-started)/1000.0;
  if(elapsed<2.05){intro(a,u,b,elapsed);u.button("Skip",new Rect(b.right()-104,b.bottom()-50,76,28),false,true,()->{started=System.currentTimeMillis()-2300;revealed=Math.max(revealed,1);});return;}

  u.text("YOUR CARDS",b.w()/2-90,47,30,Ui.GOLD);
  int cols=Math.min(5,results.size()),rows=(results.size()+cols-1)/cols,cw=Math.min(145,Math.min((b.w()-110)/Math.max(1,cols)-13,(int)((b.h()-195-rows*45)/1.4/rows))),ch=(int)(cw*1.4),total=cols*(cw+13)-13,start=(b.w()-total)/2,y=100;
  int automatic=Math.max(0,(int)((elapsed-2.15)/0.62)+1);revealed=Math.min(results.size(),Math.max(revealed,automatic));
  for(int i=0;i<results.size();i++){
   var pull=results.get(i);var card=a.state.definitions().get(pull.card());
   Rect r=new Rect(start+i%cols*(cw+13),y+i/cols*(ch+45),cw,ch);
   double cardAge=elapsed-(2.15+i*0.62);
   if(i<revealed){
    boolean rare=!card.rarity().equals("Common");
    if(rare&&cardAge>0.45){double pulse=(Math.sin(elapsed*5)+1)*0.5;u.frame(new Rect(r.x()-5,r.y()-5,r.w()+10,r.h()+10),((int)(130+pulse*125)<<24)|0xE9BE67);for(int p=0;p<14;p++){int px=r.x()+(int)((Math.sin(p*4.6+elapsed)*.5+.5)*r.w()),py=r.y()+(int)((p/14.0+elapsed*.2)%1*r.h());u.fill(new Rect(px,py,2,3),Ui.GOLD);}}
    flip(u,card,r,cardAge,"reveal"+i);
    if(cardAge>0.55){int count=before.getOrDefault(card.id(),0);for(int j=0;j<=i;j++)if(results.get(j).card().equals(card.id()))count++;u.text(before.getOrDefault(card.id(),0)==0?"NEW":"Owned x"+count,r.x()+7,r.bottom()+12,16,before.getOrDefault(card.id(),0)==0?Ui.CYAN:Ui.GOLD);}
   }else{CardRenderer.back(u,r);u.click(r,()->revealed=Math.min(results.size(),revealed+1));}
  }
  u.button(revealed<results.size()?"Reveal All":"Continue",new Rect(b.w()/2-110,b.h()-67,220,39),true,true,()->{if(revealed<results.size()){revealed=results.size();started=System.currentTimeMillis()-(long)(Math.max(3.0,2.15+results.size()*.62)*1000);}else{results=List.of();waiting=0;}});
 }

 private void intro(CardWorldsScreen a,Ui u,Rect b,double elapsed){
  double enter=Math.min(1,elapsed/.72);int x=b.w()/2-112;int targetY=b.h()/2-158;int y=(int)(b.h()+ (targetY-b.h())*(1-Math.pow(1-enter,3)));
  double shake=elapsed<.85?0:Math.min(1,(elapsed-.85)/.78);float angle=(float)(Math.sin(elapsed*28)*shake*.055);
  u.c.getMatrices().push();u.c.getMatrices().translate(x+112,y+158,0);u.c.getMatrices().multiply(new org.joml.Quaternionf().rotationZ(angle));u.c.getMatrices().translate(-x-112,-y-158,0);
  PacksScreen.pack(u,new Rect(x,y,224,316),packName,previewId.isBlank()?PacksScreen.preview(a):a.state.definitions().get(previewId),"opening/"+a.banner);u.c.getMatrices().pop();
  if(elapsed>1.55){double p=Math.min(1,(elapsed-1.55)/.5);int alpha=(int)(Math.sin(p*Math.PI)*230);u.fill(b,(alpha<<24)|0xD5EEFF);int rays=12;for(int i=0;i<rays;i++){int len=(int)(p*(90+i%3*18));int rx=b.w()/2+(int)(Math.cos(i*Math.PI*2/rays)*len),ry=b.h()/2+(int)(Math.sin(i*Math.PI*2/rays)*len);u.fill(new Rect(rx-2,ry-2,4,4),Ui.GOLD);}}
 }

 private void flip(Ui u,vn.svarcade.tcg.data.Catalog.Card card,Rect r,double age,String key){
  double t=Math.max(0,Math.min(1,age/.55));float sx=(float)Math.max(.04,Math.abs(1-2*t));
  u.c.getMatrices().push();u.c.getMatrices().translate(r.x()+r.w()/2f,r.y()+r.h()/2f,0);u.c.getMatrices().scale(sx,1,1);u.c.getMatrices().translate(-r.x()-r.w()/2f,-r.y()-r.h()/2f,0);
  if(t<.5)CardRenderer.back(u,r);else CardRenderer.draw(u,card,r,0,false,true,key);
  u.c.getMatrices().pop();
 }
}
