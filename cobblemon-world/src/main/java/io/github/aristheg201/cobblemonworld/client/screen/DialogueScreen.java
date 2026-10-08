package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.narrative.ConversationService;
import io.github.aristheg201.cobblemonworld.network.ConversationChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** First-party, bottom-anchored conversation; height follows its content. */
public final class DialogueScreen extends Screen {
    private static final int W=560,INK=0xFF3B291F;
    private static final ResourceLocation BOARD=ResourceLocation.fromNamespaceAndPath("cobblemonworld","textures/gui/shop_board.png");
    private ConversationService.Snapshot snapshot;
    private int choiceScroll,textScroll,bodyHeight,choiceY,panelHeight,left,top;
    private float scale;
    private boolean waiting,historyMode;
    public DialogueScreen(String json){super(Component.translatable("narrative.cobblemonworld.conversation"));update(json);}
    public void update(String json){snapshot=new Gson().fromJson(json,ConversationService.Snapshot.class);waiting=false;choiceScroll=0;textScroll=0;if(font!=null)layout();}
    public ConversationService.Snapshot snapshot(){return snapshot;}
    protected void init(){layout();}
    private void layout(){
        int lines=bodyLines().size();
        bodyHeight=Math.max(42,Math.min(10,lines)*12+18);
        choiceY=74+bodyHeight+(snapshot.reply().isBlank() || historyMode?10:38);
        panelHeight=choiceY+(historyMode?0:Math.min(4,snapshot.choices().size()))*23+24+(snapshot.status().isBlank()?0:15);
        scale=Math.min(1f,Math.min((width-12)/(float)W,(height-16)/(float)panelHeight));
        left=(int)((width-W*scale)/2);top=Math.max(6,(int)(height-panelHeight*scale-8));
    }
    private java.util.List<net.minecraft.util.FormattedCharSequence> bodyLines(){
        if(!historyMode)return font.split(Component.translatable(snapshot.text()),488);
        var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        if(snapshot.history()!=null)for(var line:snapshot.history()) {
            lines.addAll(font.split(Component.literal(line.speaker()+": ").append(Component.translatable(line.text())),488));
            lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        }
        if(lines.isEmpty())lines.addAll(font.split(Component.translatable("narrative.cobblemonworld.no_history"),488));
        return lines;
    }
    public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
        int mx=(int)((mouseX-left)/scale),my=(int)((mouseY-top)/scale);
        g.fill(left+4,top+5,left+(int)(W*scale)+4,top+(int)(panelHeight*scale)+5,0x80130F0A);
        g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
        // Original merchant frame is sliced; dialogue is never stretched into a giant empty card.
        g.fill(12,55,W-12,panelHeight-14,0xFFE8D9B7);
        g.blit(BOARD,0,0,0,0,W,55,W,340);
        g.blit(BOARD,0,55,14,panelHeight-69,0,55,14,257,W,340);
        g.blit(BOARD,W-14,55,14,panelHeight-69,W-14,55,14,257,W,340);
        g.blit(BOARD,0,panelHeight-14,0,326,W,14,W,340);
        g.drawString(font,snapshot.speaker(),29,24,0xFFFFE6A7,true);
        g.drawString(font,Component.translatable("narrative.cobblemonworld.conversation"),29,42,0xFFE4C583,false);
        g.drawString(font,"×",522,24,0xFFFFE6A7,false);
        g.fill(408,22,508,42,mx>=408&&mx<508&&my>=22&&my<42?0xFF9B4241:0xFF63302D);
        g.drawString(font,Component.translatable("narrative.cobblemonworld."+(historyMode?"return_dialogue":"history")),417,28,0xFFFFE6A7,false);
        g.fill(24,70,536,74+bodyHeight,0xFFF2E6CD);
        var lines=bodyLines();
        int visibleText=Math.max(1,(bodyHeight-18)/12),maxText=Math.max(0,lines.size()-visibleText);
        textScroll=Math.min(textScroll,maxText);
        for(int i=textScroll;i<Math.min(lines.size(),textScroll+visibleText);i++)g.drawString(font,lines.get(i),32,80+(i-textScroll)*12,INK,false);
        if(maxText>0){int end=64+bodyHeight;g.fill(526,81,529,end,0xFFAC8A50);int y=81+(int)(textScroll/(double)maxText*Math.max(1,end-105));g.fill(525,y,530,y+24,0xFF8C3540);}
        if(!snapshot.reply().isBlank() && !historyMode){
            int replyY=80+bodyHeight;
            g.fill(28,replyY-4,532,replyY+25,0xFFDED0AA);
            g.drawString(font,snapshot.replySpeaker()+":",34,replyY,0xFF8C3540,false);
            g.drawString(font,font.plainSubstrByWidth(Component.translatable(snapshot.reply()).getString(),486),34,replyY+12,INK,false);
        }
        int visible=historyMode?0:Math.min(4,snapshot.choices().size());choiceScroll=Math.min(choiceScroll,Math.max(0,snapshot.choices().size()-visible));
        for(int i=0;i<visible;i++){
            var option=snapshot.choices().get(i+choiceScroll);int y=choiceY+i*23;boolean hover=mx>=30 && mx<519 && my>=y && my<y+21;
            g.fill(30,y,520,y+21,0xFF795735);g.fill(31,y+1,519,y+20,waiting?0xFFC4B38C:hover?0xFF9B4241:0xFFE4D3AA);g.fill(32,y+2,518,y+3,0x88FFFFFF);
            g.drawString(font,font.plainSubstrByWidth(Component.translatable(option.text()).getString(),468),39,y+7,hover?0xFFFFE6BD:INK,false);
        }
        if(!historyMode && snapshot.choices().size()>4){g.fill(527,choiceY,531,choiceY+89,0xFFAA8A57);int y=choiceY+choiceScroll*69/Math.max(1,snapshot.choices().size()-4);g.fill(526,y,532,y+20,0xFF8C3540);}
        if(!snapshot.status().isBlank())g.drawString(font,Component.translatable(snapshot.status()),30,panelHeight-25,0xFF8C3540,false);
        g.pose().popPose();
    }
    public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);int mx=(int)((x-left)/scale),my=(int)((y-top)/scale);
        if(mx>=408 && mx<508 && my>=22 && my<42){historyMode=!historyMode;textScroll=0;layout();return true;}
        if(mx>=512 && mx<538 && my>=15 && my<44){onClose();return true;}
        if(!historyMode && mx>=30 && mx<520 && my>=choiceY && my<choiceY+(historyMode?0:Math.min(4,snapshot.choices().size()))*23){
            int index=choiceScroll+(my-choiceY)/23;if(index<snapshot.choices().size() && (my-choiceY)%23<21 && !waiting){choose(index);return true;}}
        return super.mouseClicked(x,y,button);
    }
    public void qaToggleHistory(){mouseClicked(left+455*scale,top+30*scale,0);}
    public void qaClick(int index){if(index<0 || index>=snapshot.choices().size())throw new IllegalArgumentException("QA choice missing");
        choiceScroll=Math.max(0,index-3);mouseClicked(left+275*scale,top+(choiceY+(index-choiceScroll)*23+10)*scale,0);}
    public void choose(int index){if(index<0 || index>=snapshot.choices().size() || waiting)return;
        var option=snapshot.choices().get(index);waiting=true;ClientPlayNetworking.send(new ConversationChoicePayload(snapshot.session(),snapshot.revision(),option.id()));
        if(option.id().equals("leave") || option.id().equals("finish") || option.id().equals("rematch") || option.id().equals("service"))onClose();
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){int my=(int)((y-top)/scale);
        if(historyMode || my<choiceY)textScroll=Math.max(0,textScroll+(vertical<0?1:-1));else choiceScroll=Math.max(0,Math.min(Math.max(0,snapshot.choices().size()-4),choiceScroll+(vertical<0?1:-1)));return true;}
    public boolean isPauseScreen(){return false;}
}
