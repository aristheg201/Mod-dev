package vn.svframe.mmocorefabric.runtime.progression;
import java.util.*;
public final class PlayerProgress {
 private int level=1; private double exp; private int skillPoints; private int attributePoints; private final Map<String,Integer> skillLevels=new HashMap<>(); private final Map<String,Integer> attributes=new HashMap<>(); private final Map<Integer,String> boundSlots=new HashMap<>(); private final Set<UUID> friends=new HashSet<>();
 public int level(){return level;} public double exp(){return exp;} public int skillPoints(){return skillPoints;} public void grantSkillPoints(int n){skillPoints=Math.max(0,Math.addExact(skillPoints,n));} public void setSkillPoints(int n){skillPoints=Math.max(0,n);} public Map<String,Integer> skills(){return skillLevels;} public Map<String,Integer> attributes(){return attributes;}
 public int addExp(double amount, java.util.function.IntToDoubleFunction required){if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException();exp+=amount;int gained=0;while(exp>=required.applyAsDouble(level)&&required.applyAsDouble(level)>0){exp-=required.applyAsDouble(level);level++;gained++;}return gained;}
 public void bind(int slot,String skill){if(slot<0||slot>8)throw new IllegalArgumentException("slot");boundSlots.put(slot,Objects.requireNonNull(skill));} public void unbind(int slot){boundSlots.remove(slot);} public Optional<String> bound(int slot){return Optional.ofNullable(boundSlots.get(slot));} public Map<Integer,String> bindings(){return Map.copyOf(boundSlots);} public boolean addFriend(UUID id){return friends.add(id);} public boolean removeFriend(UUID id){return friends.remove(id);} public boolean isFriend(UUID id){return friends.contains(id);} public Set<UUID> friends(){return Set.copyOf(friends);} public int attributePoints(){return attributePoints;} public void grantAttributePoints(int n){attributePoints=Math.max(0,Math.addExact(attributePoints,n));}
 public boolean spendAttribute(String key,int amount){if(amount<=0||attributePoints<amount)return false;attributes.merge(key.toLowerCase(Locale.ROOT),amount,Integer::sum);attributePoints-=amount;return true;}
 public void restore(int level,double exp,int skillPoints,int attributePoints,Map<String,Integer> skills,Map<String,Integer> attrs,Map<Integer,String> bindings,Set<UUID> friends){
  if(level<1||!Double.isFinite(exp)||exp<0||skillPoints<0||attributePoints<0)throw new IllegalArgumentException("invalid persisted progress");
  this.level=level;this.exp=exp;this.skillPoints=skillPoints;this.attributePoints=attributePoints;
  skillLevels.clear();skills.forEach((k,v)->{if(v!=null&&v>=0)skillLevels.put(k.toLowerCase(Locale.ROOT),v);});
  attributes.clear();attrs.forEach((k,v)->{if(v!=null&&v>=0)attributes.put(k.toLowerCase(Locale.ROOT),v);});
  boundSlots.clear();bindings.forEach((k,v)->{if(k!=null&&k>=0&&k<=8&&v!=null)boundSlots.put(k,v);});
  this.friends.clear();this.friends.addAll(friends);
 }
}
