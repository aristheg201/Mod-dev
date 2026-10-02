package vn.svarcade.tcg.performance;
import com.google.gson.Gson;
import vn.svarcade.tcg.fabric.*;
import vn.svarcade.tcg.data.Catalog;
import java.util.*;
/** Complete dynamic state, with static content synchronized once per connection/content generation. */
public final class SnapshotPipeline {
 public record Content(Map<String,Catalog.Card> definitions,Map<String,List<String>> deckTemplates,Catalog.Rules rules){}
 public record Wire(long sequence,long generation,Content content,TcgMod.Snapshot state){}
 public record Encoded(long sequence,long generation,boolean content,byte[] bytes){}
 private static final Gson JSON=new Gson();
 public static Encoded encode(Wire input){long n=CardWorldsPerfStats.start();String json;try{json=JSON.toJson(input);}finally{CardWorldsPerfStats.finish(CardWorldsPerfStats.Path.SNAPSHOT_SERIALIZE,n);}n=CardWorldsPerfStats.start();try{return new Encoded(input.sequence(),input.generation(),input.content()!=null,SnapshotCompression.encode(json));}finally{CardWorldsPerfStats.finish(CardWorldsPerfStats.Path.SNAPSHOT_COMPRESS,n);}}
 public static Wire decode(byte[] bytes){long n=CardWorldsPerfStats.start();try{return Objects.requireNonNull(JSON.fromJson(SnapshotCompression.decode(bytes),Wire.class));}finally{CardWorldsPerfStats.finish(CardWorldsPerfStats.Path.CLIENT_DECODE,n);}}
 public static Wire merge(Wire old,Wire next){return new Wire(next.sequence(),next.generation(),next.content()!=null?next.content():old.generation()==next.generation()?old.content():null,next.state().mergePresentation(old.state()));}
 private SnapshotPipeline(){}
}
