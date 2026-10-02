package vn.svarcade.tcg.performance;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
/** Full history semantics with bounded RAM: immutable chunks spill to disk, one pending IO batch per duel.
 * Normal battle queries use live indexes. Reading an old history page explicitly may perform disk IO.
 */
public final class BoundedHistory<E> extends AbstractList<E> {
 private static final int CHUNK=4096;private static final Gson JSON=new Gson();
 private final Type type;private final String name;private final List<E> tail=new ArrayList<>();private int count,first;
 private Path directory,configured;private CardWorldsExecutors workers;private CompletableFuture<Void> pending;
 private final LinkedHashMap<Integer,List<E>> cache=new LinkedHashMap<>(2,.75f,true){protected boolean removeEldestEntry(Map.Entry<Integer,List<E>> e){return size()>2;}};
 public BoundedHistory(Class<E> element,String name){type=TypeToken.getParameterized(List.class,element).getType();this.name=name;}
 public void configure(CardWorldsExecutors workers,Path directory){this.workers=workers;this.configured=directory.resolve(name);}
 @Override public boolean add(E value){if(tail.size()==CHUNK)spill();tail.add(value);count++;modCount++;return true;}
 private void spill(){flush();List<E> immutable=List.copyOf(tail);int index=first/CHUNK;Path file;
  try{if(directory==null){directory=configured==null?Files.createTempDirectory("cardworlds-"+name+"-"):configured;Files.createDirectories(directory);}file=directory.resolve(index+".json");}catch(java.io.IOException e){throw new IllegalStateException("Cannot prepare duel history",e);}
  java.util.function.Supplier<Void> write=()->{try{Files.writeString(file,JSON.toJson(immutable));return null;}catch(java.io.IOException e){throw new IllegalStateException("Cannot persist duel history chunk "+file,e);}};
  if(workers==null)write.get();else pending=workers.submit(CardWorldsExecutors.Kind.IO,new CardWorldsExecutors.Context("duel-history",directory.toString(),count),write);
  first+=tail.size();tail.clear();
 }
 @Override public E get(int index){Objects.checkIndex(index,count);if(index>=first)return tail.get(index-first);int chunk=index/CHUNK;List<E> values=cache.get(chunk);if(values==null){flush();try{values=List.copyOf(JSON.fromJson(Files.readString(directory.resolve(chunk+".json")),type));cache.put(chunk,values);}catch(java.io.IOException e){throw new IllegalStateException("Cannot read duel history",e);}}return values.get(index%CHUNK);}
 @Override public int size(){return count;}
 public void flush(){if(pending!=null){pending.join();pending=null;}}
 @Override public void clear(){flush();tail.clear();cache.clear();count=0;first=0;modCount++;if(directory!=null)try(var files=Files.list(directory)){for(Path f:files.toList())Files.deleteIfExists(f);}catch(java.io.IOException e){throw new IllegalStateException("Cannot reset QA history",e);}}
}
