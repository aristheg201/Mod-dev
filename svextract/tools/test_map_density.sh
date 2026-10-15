#!/usr/bin/env bash
# Build lightweight API stubs so deterministic blueprints can be compile-checked
# and validated even when Mojang/Fabric Maven repositories are inaccessible.
set -euo pipefail
cd "$(dirname "$0")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/stubs/net/minecraft/block/enums" "$work/stubs/net/minecraft/block" "$work/stubs/net/minecraft/util/math" "$work/stubs/dev/svextract"
python3 - "$work/stubs/net/minecraft/block/Blocks.java" <<'PY'
from pathlib import Path
import re,sys
base=Path('src/main/java/dev/svextract/world')
files=[base/'BlackglassBlueprint.java',base/'StationBlueprint.java',base/'DenseMapDecorator.java']
blocks=sorted(set().union(*(set(re.findall(r'Blocks\.([A-Z][A-Z_0-9]+)',f.read_text())) for f in files)))
Path(sys.argv[1]).write_text('package net.minecraft.block; public final class Blocks {\n'+''.join('  public static final Block '+b+' = new Block("'+b+'");\n' for b in blocks)+'}\n')
PY
cat > "$work/stubs/net/minecraft/block/Block.java" <<'JAVA'
package net.minecraft.block;
public class Block {
  public final String name;
  public Block(String value){this.name=value;}
  public BlockState getDefaultState(){return new BlockState(this);}
}
JAVA
cat > "$work/stubs/net/minecraft/block/BlockState.java" <<'JAVA'
package net.minecraft.block;
public record BlockState(Block block) {
  public <T> BlockState with(Object property,T value){return this;}
}
JAVA
cat > "$work/stubs/net/minecraft/block/RailBlock.java" <<'JAVA'
package net.minecraft.block;
public final class RailBlock {public static final Object SHAPE=new Object();}
JAVA
cat > "$work/stubs/net/minecraft/block/enums/RailShape.java" <<'JAVA'
package net.minecraft.block.enums;
public enum RailShape {EAST_WEST}
JAVA
cat > "$work/stubs/net/minecraft/util/math/BlockPos.java" <<'JAVA'
package net.minecraft.util.math;
public record BlockPos(int x,int y,int z) {}
JAVA
cat > "$work/stubs/dev/svextract/Content.java" <<'JAVA'
package dev.svextract;
import net.minecraft.block.Block;
public final class Content {
  public static final Block SUPPLY_CRATE=new Block("SUPPLY_CRATE");
  public static final Block STASH_TERMINAL=new Block("STASH_TERMINAL");
}
JAVA
cat > "$work/MapDensitySmoke.java" <<'JAVA'
import dev.svextract.world.*;
import dev.svextract.mission.RaidPatrols;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public class MapDensitySmoke {
  static void assertTrue(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static Map<BlockPos,BlockState> indexed(List<StationBlueprint.Change> changes){
    Map<BlockPos,BlockState> map=new HashMap<>();
    for(var c:changes){assertTrue(map.put(c.position(),c.state())==null,"duplicate changed cell");}
    return map;
  }
  static void verify(String label,List<StationBlueprint.Change> base,List<StationBlueprint.Change> decorated,int bx,int bz){
    var originals=indexed(base);var cells=indexed(decorated);
    assertTrue(cells.size()>originals.size()+100,"no meaningful environmental detail added");
    for(var p:cells.keySet())assertTrue(Math.abs(p.x())<=bx&&Math.abs(p.z())<=bz&&p.y()>=0&&p.y()<256,"invalid coordinate "+p);
    for(var c:RaidLootLocations.ALL){
      var p=new BlockPos(c[0],65,c[1]);
      assertTrue(cells.containsKey(p)&&cells.get(p).block().name.equals("SUPPLY_CRATE"),"missing loot cache "+p);
    }
    for(var p:List.of(new BlockPos(-12,65,8),new BlockPos(16,65,16),new BlockPos(24,65,16),new BlockPos(-19,65,-12)))
      assertTrue(cells.containsKey(p),"required gameplay POI missing "+p);
    assertTrue(cells.get(new BlockPos(0,65,0))==null||cells.get(new BlockPos(0,65,0)).block().name.equals("AIR"),"spawn obstructed");
    int walkable=0;
    String mapId=label.equals("blackglass")?"blackglass_industrial":"redline_station";
    for(var wp:RaidPatrols.spawnPoints(mapId)){
      var foot=cells.get(new BlockPos(wp.x(),65,wp.z()));
      var head=cells.get(new BlockPos(wp.x(),66,wp.z()));
      var ground=cells.get(new BlockPos(wp.x(),64,wp.z()));
      boolean clear=(foot==null||foot.block().name.equals("AIR"))&&(head==null||head.block().name.equals("AIR"));
      boolean solid=ground!=null&&!ground.block().name.equals("AIR")&&!ground.block().name.equals("WATER");
      if(clear&&solid)walkable++;
      else System.out.printf("WARN %s waypoint blocked: (%d,%d), foot=%s head=%s ground=%s%n",label,wp.x(),wp.z(),foot==null?"null":foot.block().name,head==null?"null":head.block().name,ground==null?"null":ground.block().name);
    }
    assertTrue(walkable>=RaidPatrols.spawnPoints(mapId).size()/2,"Insufficient physically walkable bot spawn candidates");
    System.out.printf("PASS %s: original %d cells, decorated %d cells (+%d), 20 caches, %d/%d patrol spawns walkable%n",label,originals.size(),cells.size(),cells.size()-originals.size(),walkable,RaidPatrols.spawnPoints(mapId).size());
  }
  public static void main(String[] unused){
    var black=BlackglassBlueprint.create();
    verify("blackglass",black,DenseMapDecorator.blackglass(black),52,52);
    var red=StationBlueprint.redline();
    verify("redline",red,DenseMapDecorator.redline(red),36,28);
    var ids=new HashSet<String>();
    for(String map:List.of("blackglass_industrial","redline_station"))for(var p:RaidLandmarks.forMap(map)){
      assertTrue(ids.add(p.id()),"duplicated POI name");
      assertTrue(p.contains(p.x(),p.z()),"bad discover radius");
      assertTrue(!p.contains(p.x()+p.radius()*2,p.z()),"oversized discover radius");
    }
    System.out.println("PASS "+ids.size()+" distinct POIs validated");
  }
}
JAVA
javac --release 21 -d "$work/classes" $(find "$work/stubs" -name '*.java') \
  src/main/java/dev/svextract/world/StationBlueprint.java \
  src/main/java/dev/svextract/world/BlackglassBlueprint.java \
  src/main/java/dev/svextract/world/DenseMapDecorator.java \
  src/main/java/dev/svextract/world/RaidLandmarks.java \
  src/main/java/dev/svextract/world/RaidLootLocations.java \
  src/main/java/dev/svextract/mission/RaidPatrols.java \
  "$work/MapDensitySmoke.java"
java -ea -cp "$work/classes" MapDensitySmoke
