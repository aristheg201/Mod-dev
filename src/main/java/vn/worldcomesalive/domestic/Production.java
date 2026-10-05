package vn.worldcomesalive.domestic;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.domestic.DomesticContent.Recipe;
import java.util.*;

/** Atomic ingredient reservation; output exists only after the persisted production completion event. */
public final class Production {
    public static boolean canMake(Map<String,Integer> stock,Recipe recipe){return recipe.inputs().entrySet().stream().allMatch(e->stock.getOrDefault(e.getKey(),0)>=e.getValue());}
    public static DomesticState.Batch reserve(DomesticState state,Settlement s,Building b,Recipe recipe,long clock,double skill){
        if(!canMake(b.stock,recipe)||state.batches.values().stream().anyMatch(job->job.building.equals(b.id)&&job.recipe.equals(recipe.id())))return null;
        recipe.inputs().forEach((id,count)->b.stock.merge(id,-count,Integer::sum));var job=new DomesticState.Batch();job.id=UUID.randomUUID();job.settlement=s.id;job.building=b.id;job.recipe=recipe.id();job.output=recipe.output();job.count=recipe.count();job.quality=Math.max(0,Math.min(3,recipe.quality()+(skill>=.7?1:0)));job.due=clock+recipe.ticks();state.batches.put(job.id,job);return job;
    }
    private Production(){}
}
