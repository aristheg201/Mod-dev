package vn.svarcade.tcg.performance;
import vn.svarcade.tcg.fabric.TcgMod;
/** Pure protocol recovery state. The client owner thread alone applies decoded immutable snapshots. */
public final class SnapshotReceiver {
 public record Result(TcgMod.Snapshot state,boolean resync){}
 private SnapshotPipeline.Content content;private TcgMod.Snapshot ui;private String uiKey="";private long generation=-1,sequence=-1;
 public Result accept(SnapshotPipeline.Wire wire){if(wire.sequence()<=sequence)return new Result(null,false);if(wire.content()!=null){content=wire.content();generation=wire.generation();}if(content==null||generation!=wire.generation())return new Result(null,true);TcgMod.Snapshot state=wire.state();if(wire.fullUi()){ui=state;uiKey=wire.uiKey();}else{if(ui==null||!uiKey.equals(wire.uiKey()))return new Result(null,true);state=state.withUi(ui);}sequence=wire.sequence();return new Result(state.withContent(content),false);}
 public void reset(){content=null;ui=null;uiKey="";generation=-1;sequence=-1;}
 public long sequence(){return sequence;}
}
