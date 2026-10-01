package vn.svarcade.tcg.client.component;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.*;

/** Custom rendering and hit-testing in one logical coordinate space. */
public final class Ui {
    public static final int WHITE=0xFFF1F5F9,MUTED=0xFF92AABD,GOLD=0xFFE5B865,CYAN=0xFF49C9F5,LINE=0xFF29475D,BG=0xFF06121E;
    public record Hit(Rect box,Runnable action){}
    private final Deque<Rect> clips=new ArrayDeque<>();
    private final Deque<String> clipTooltips=new ArrayDeque<>();
    public final DrawContext c;public final double mx,my;public final List<Hit> hits;public String tooltip="";
    public Ui(DrawContext c,double mx,double my,List<Hit> hits){this.c=c;this.mx=mx;this.my=my;this.hits=hits;}
    public Text styled(String s){return Text.literal(CardWorldsLanguage.translate(s));}
    public void text(String s,int x,int y,int size,int color){c.getMatrices().push();c.getMatrices().translate(x,y,0);float scale=size/12f;c.getMatrices().scale(scale,scale,1);c.drawText(MinecraftClient.getInstance().textRenderer,styled(s),0,0,color,false);c.getMatrices().pop();}
    public int textWidth(String s,int size){return Math.round(MinecraftClient.getInstance().textRenderer.getWidth(styled(s))*size/12f);}
    public void fit(String s,Rect r,int size,int color){s=CardWorldsLanguage.translate(s);while(s.length()>1&&textWidth(s,size)>r.w())s=s.substring(0,s.length()-1);text(s,r.x(),r.y(),size,color);}
    public int paragraph(String s,Rect r,int size,int color){int yy=r.y();for(var line:MinecraftClient.getInstance().textRenderer.wrapLines(styled(s),Math.max(1,(int)(r.w()*12f/size)))){if(yy+size>r.bottom())break;c.getMatrices().push();c.getMatrices().translate(r.x(),yy,0);float f=size/12f;c.getMatrices().scale(f,f,1);c.drawText(MinecraftClient.getInstance().textRenderer,line,0,0,color,false);c.getMatrices().pop();yy+=size+4;}return yy;}
    public void fill(Rect r,int color){c.fill(r.x(),r.y(),r.right(),r.bottom(),color);}
    public void frame(Rect r,int color){c.fill(r.x(),r.y(),r.right(),r.y()+1,color);c.fill(r.x(),r.bottom()-1,r.right(),r.bottom(),color);c.fill(r.x(),r.y(),r.x()+1,r.bottom(),color);c.fill(r.right()-1,r.y(),r.right(),r.bottom(),color);}
    public void panel(Rect r){c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xF215293B,0xF0081724);frame(r,LINE);}
    public void ornament(Rect r,int color){frame(r,color);int k=7;c.fill(r.x(),r.y(),r.x()+k,r.y()+2,color);c.fill(r.right()-k,r.bottom()-2,r.right(),r.bottom(),color);}
    /** Procedural arena background: deterministic, resolution independent, and not a fake artwork asset. */
    public void arena(Rect r){
        c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF132C42,0xFF040B14);
        int horizon=r.y()+(int)(r.h()*0.58f);fill(new Rect(r.x(),horizon,r.w(),1),0x6654B7D6);
        for(int i=1;i<8;i++){int x=r.x()+r.w()*i/8;fill(new Rect(x,r.y(),1,r.h()),0x182C6A86);}
        for(int i=1;i<6;i++){int y=r.y()+r.h()*i/6;fill(new Rect(r.x(),y,r.w(),1),0x142C6A86);}
        for(int i=0;i<7;i++){int w=Math.max(20,r.w()/12),x=r.x()+i*(r.w()-w)/6;int h=10+(i%3)*8;fill(new Rect(x,horizon-h,w,h),0x44243852);}
        fill(new Rect(r.x(),r.bottom()-Math.max(18,r.h()/8),r.w(),Math.max(18,r.h()/8)),0x3300A6C8);
    }
    public void button(String label,Rect r,boolean primary,boolean enabled,Runnable action){boolean over=r.contains(mx,my)&&enabled;int edge=enabled?(primary?GOLD:over?CYAN:LINE):0xFF263541;
        c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),primary?(over?0xFFFFD382:0xFFD9A54F):(over?0xFF163E60:0xFF0E273A),primary?0xFF8B591E:0xFF091825);ornament(r,edge);int color=enabled?(primary?0xFF1B1720:WHITE):0xFF667686;text(label,r.x()+(r.w()-textWidth(label,15))/2,r.y()+(r.h()-15)/2,15,color);if(enabled)click(r,action);}
    public void chip(String label,Rect r,boolean selected,Runnable action){button(label,r,false,true,action);if(selected){frame(r,CYAN);c.fill(r.x()+1,r.bottom()-3,r.right()-1,r.bottom()-1,CYAN);}}
    public Rect clipped(Rect r){return clips.isEmpty()?r:intersection(r,clips.peek());}
    private static Rect intersection(Rect a,Rect b){int x=Math.max(a.x(),b.x()),y=Math.max(a.y(),b.y());return new Rect(x,y,Math.max(0,Math.min(a.right(),b.right())-x),Math.max(0,Math.min(a.bottom(),b.bottom())-y));}
    public boolean visible(Rect r){Rect v=clipped(r);return v.w()>0&&v.h()>0;}
    public void clip(Rect r){r=clipped(r);clips.push(r);clipTooltips.push(tooltip);var matrix=c.getMatrices().peek().getPositionMatrix();
        var tl=matrix.transformPosition(new org.joml.Vector3f(r.x(),r.y(),0));var br=matrix.transformPosition(new org.joml.Vector3f(r.right(),r.bottom(),0));
        c.enableScissor((int)Math.floor(tl.x),(int)Math.floor(tl.y),(int)Math.ceil(br.x),(int)Math.ceil(br.y));}
    public void endClip(){if(!clips.peek().contains(mx,my))tooltip=clipTooltips.peek();clipTooltips.pop();clips.pop();c.disableScissor();}
    public int paragraphHeight(String s,int width,int size){return MinecraftClient.getInstance().textRenderer.wrapLines(styled(s),Math.max(1,(int)(width*12f/size))).size()*(size+4);}
    public void click(Rect r,Runnable action){Rect hit=clipped(r);if(hit.w()>0&&hit.h()>0)hits.add(new Hit(hit,action));}
    public void item(Item item,Rect r){c.getMatrices().push();c.getMatrices().translate(r.x(),r.y(),0);c.getMatrices().scale(r.w()/16f,r.h()/16f,1);c.drawItem(new ItemStack(item),0,0);c.getMatrices().pop();}
    public void meter(Rect r,double value,int color){fill(r,0xFF06111C);fill(new Rect(r.x(),r.y(),(int)(r.w()*Math.clamp(value,0,1)),r.h()),color);frame(r,LINE);}
    public void tooltip(){if(tooltip.isBlank())return;int w=370,h=Math.min(440,Math.max(90,MinecraftClient.getInstance().textRenderer.wrapLines(styled(tooltip),300).size()*18+24)),x=Math.min((int)mx+12,1260-w),y=Math.max(8,Math.min((int)my-70,700-h));Rect r=new Rect(x,y,w,h);panel(r);paragraph(tooltip,r.inset(9),14,WHITE);}
}
