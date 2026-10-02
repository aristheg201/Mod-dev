package vn.svarcade.tcg.performance;
import com.google.gson.Gson;
import java.nio.file.*;
public record PerformanceConfig(int cpuWorkers,int ioConcurrency,int serializationWorkers,int maxQueuedCpuTasks,int maxQueuedIoTasks,int maxQueuedSerializationTasks,boolean snapshotCoalesce,boolean perfMetrics) {
 public PerformanceConfig { if(cpuWorkers<1||cpuWorkers>32||ioConcurrency<1||ioConcurrency>8||serializationWorkers<1||serializationWorkers>8||maxQueuedCpuTasks<1||maxQueuedCpuTasks>4096||maxQueuedIoTasks<1||maxQueuedIoTasks>4096||maxQueuedSerializationTasks<1||maxQueuedSerializationTasks>4096)throw new IllegalArgumentException("Invalid bounded Card Worlds performance configuration"); }
 public static PerformanceConfig defaults(){return new PerformanceConfig(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()-1)),1,2,128,64,64,true,false);}
 public static PerformanceConfig load(Path file){try{if(!Files.exists(file)){Files.createDirectories(file.toAbsolutePath().getParent());var c=defaults();Files.writeString(file,new Gson().toJson(c));return c;}return java.util.Objects.requireNonNull(new Gson().fromJson(Files.readString(file),PerformanceConfig.class));}catch(java.io.IOException e){throw new IllegalStateException("Cannot load Card Worlds performance configuration",e);}}
}
