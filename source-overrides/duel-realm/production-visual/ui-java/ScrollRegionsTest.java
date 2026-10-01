package vn.svarcade.tcg.client.component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollRegionsTest {
 @Test void wheelMovesOnlyTheHoveredPanelAndClampsAtBothEnds(){
  var s=new ScrollRegions();s.register("packs",new Rect(0,0,200,100),1000);s.register("rates",new Rect(300,0,200,100),500);
  assertTrue(s.wheel(40,40,-100));assertEquals(900,s.offset("packs"));assertEquals(0,s.offset("rates"));
  assertFalse(s.wheel(250,40,-1));assertFalse(s.wheel(40,40,0));
  s.wheel(350,40,-100);assertEquals(400,s.offset("rates"));s.wheel(40,40,100);assertEquals(0,s.offset("packs"));
 }
 @Test void nestedPanelConsumesWheelBeforeOuterPage(){
  var s=new ScrollRegions();s.register("page",new Rect(0,0,800,600),1000);s.register("inner",new Rect(50,50,200,100),500);
  s.wheel(60,60,-1);assertEquals(38,s.offset("inner"));assertEquals(0,s.offset("page"));
  s.wheel(400,400,-1);assertEquals(38,s.offset("page"));
 }
 @Test void scrollbarCanBeDraggedToTheLastRowAndReleased(){
  var s=new ScrollRegions();var r=s.register("list",new Rect(10,20,200,100),1000);
  assertTrue(s.press(r.thumb().x()+2,r.thumb().y()+2));assertTrue(s.drag(500));assertEquals(900,s.offset("list"));
  assertTrue(s.release());assertFalse(s.drag(20));
  s.beginFrame();r=s.register("list",new Rect(10,20,200,100),1000);assertTrue(s.press(r.track().x()+2,r.box().y()+1));assertEquals(800,s.offset("list"));
 }
 @Test void ResizeFilteringAndPageChangesCannotLeaveStaleTargets(){
  var s=new ScrollRegions();s.register("list",new Rect(0,0,200,100),1000);s.wheel(10,10,-100);
  s.beginFrame();assertFalse(s.wheel(10,10,-1));var r=s.register("list",new Rect(0,0,200,200),220);assertEquals(20,r.offset());
  s.beginFrame();r=s.register("list",new Rect(0,0,200,300),220);assertEquals(0,r.max());assertEquals(0,r.offset());
  s.reset();assertEquals(0,s.offset("list"));
 }
}
