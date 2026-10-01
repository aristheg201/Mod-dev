package vn.svarcade.tcg.client.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svarcade.tcg.client.component.Rect;
import vn.svarcade.tcg.duel.Duel;

import java.util.*;

/**
 * 3D duel presentation over the physical Duel Realm board.
 * Face-up monsters in Attack and Defense are real Cobblemon entities. Face-down
 * monsters and Spell/Trap cards are thin textured card objects.
 */
public final class DuelWorldScene {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds-duel-scene");
    private static final int REALM_SPACING = 192;
    private static final double REALM_BOARD_SURFACE_Y = 97.0;
    private static int NEXT_CLIENT_ENTITY_ID = -2_100_000_000;

    private record Actor(PokemonEntity entity, String species, List<String> aspects, int controller, long born, double scale, Vec3d anchor) {}
    private static final class CardActor {
        Duel.VisibleCard card;
        boolean faceDown;long cueKey;
        Vec3d center = Vec3d.ZERO;
        float yaw, width, depth;
        net.minecraft.util.Identifier texture;
    }
    private final Map<String, Vec3d> defenseBases = new LinkedHashMap<>();
    private final DuelCardMeshes meshes = new DuelCardMeshes();
    private record PileKey(int controller, Duel.Zone zone) {}
    private record PileActor(int count, net.minecraft.util.Identifier top, Vec3d base, float yaw) {}
    private final Map<PileKey, PileActor> piles = new LinkedHashMap<>();
    private static DuelWorldScene active;
    private static boolean renderHook;
    private long settledSince;
    private String visualSignature = "";
    private final DuelVfxTimeline timeline=new DuelVfxTimeline();
    private final DuelVfxRenderer vfx=new DuelVfxRenderer();
    private record Departing(PokemonEntity entity,long until) {}
    private final Map<String,Departing> departing=new LinkedHashMap<>();
    private final Map<String,Vec3d> positions=new LinkedHashMap<>();
    private final Map<String,CardActor> activationCards=new LinkedHashMap<>();
    private Duel.View lastView;
    private final Map<String,PokemonDuelAnimationResolver.Resolution> animationProofs=new HashMap<>();

    public DuelWorldScene() {
        if (!renderHook) {
            renderHook = true;
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(context -> {
                if (active != null && active.world == MinecraftClient.getInstance().world) active.renderMeshes(context);
            });
        }
    }
    private void renderMeshes(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (realmMode) meshes.board(context, origin);
        defenseBases.values().forEach(base -> meshes.defense(context, base));
        fieldCards.values().forEach(card -> meshes.card(context, card.texture, card.center, card.yaw, card.width, card.depth));
        long now=System.currentTimeMillis();
        supportCards.values().forEach(card -> renderSupport(context,card,now));
        activationCards.forEach((token,card)->{if(!supportCards.containsKey(token)&&timeline.card(token)!=null)renderSupport(context,card,now);});
        piles.values().forEach(pile -> {
            // Bounded paper-thin layers, never display entities or a block-shaped slab.
            int layers = Math.min(pile.count(), 12);
            for (int i = 0; i < layers; i++) {
                var texture = i == layers - 1 ? pile.top() : DuelCardMeshes.BACK;
                meshes.card(context, texture, pile.base().add(0, .015 + i * .021, 0), pile.yaw(), 2.1f, 3f);
            }
        });
        for(CardActor card:supportCards.values())if(!card.faceDown&&card.card!=null&&Set.of("trainer","stadium").contains(card.card.category()))vfx.persistent(context,card.center,0x5592BCE6,now);
        vfx.render(context,timeline.active(),now);
    }
    private void renderSupport(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context,CardActor card,long now) {
        var effect=card.card==null?timeline.active().stream().filter(v->v.cue.sequence()==card.cueKey).findFirst().orElse(null):timeline.card(card.card.token());
        if(effect==null){meshes.card(context,card.texture,card.center,card.yaw,card.width,card.depth);return;}
        double t=effect.progress(now),lift=Math.sin(Math.PI*t)*1.35;
        float tilt=effect.cue.semantic().equals("TRAP_REVEAL")?(float)(Math.PI*(1-Math.min(1,t*2.4))):(float)(-.75*Math.sin(Math.PI*t));
        meshes.card(context,card.texture,card.center.add(0,lift,0),card.yaw,card.width,card.depth,tilt);
    }
    private PokemonEntity entity(String token) {
        Actor actor=actors.get(token);if(actor!=null)return actor.entity();
        Departing old=departing.get(token);return old==null?null:old.entity();
    }
    private void startVfx(DuelVfxTimeline.Instance instance) {
        String semantic=instance.cue.semantic(),intent=switch(semantic){
            case "ATTACK_PHYSICAL" -> "ATTACK_PHYSICAL";case "ATTACK_SPECIAL" -> "ATTACK_SPECIAL";
            case "CAST_STATUS" -> "CAST_STATUS";case "CHARGE" -> "CHARGE";
            case "IMPACT","DAMAGE" -> "HIT";case "DESTROY","SEND_GRAVE" -> "FAINT";case "BANISH" -> "TRANSFORM";
            case "SUMMON_EVOLUTION" -> "EVOLVE";case "SUMMON_TRANSFORM" -> "TRANSFORM";
            default -> semantic.startsWith("SUMMON")||semantic.equals("REVIVE")?"SPAWN":"IDLE";
        };
        PokemonEntity source=entity(instance.cue.source()),target=entity(instance.cue.target());
        if(intent.equals("HIT")){if(target!=null)animationProofs.put(instance.cue.target(),PokemonDuelAnimationResolver.play(target,"HIT"));}
        else if(source!=null&&!intent.equals("IDLE")){
            var resolved=PokemonDuelAnimationResolver.play(source,intent);animationProofs.put(instance.cue.source(),resolved);
            if(instance.preview&&resolved.nativeAnimation())instance.duration=Math.clamp((int)(resolved.duration()*1000),650,instance.profile.maxLifetime());
        }
        if(Set.of("DRAW","SEARCH","DISCARD","MILL","RETURN_DECK","RETURN_HAND").contains(semantic)){
            CardActor card=activationCards.computeIfAbsent("cue:"+instance.cue.sequence(),t->createCardActor());card.cueKey=instance.cue.sequence();card.texture=DuelCardMeshes.BACK;card.faceDown=true;
            setCardPosition(card,instance.source.add(0,.05,0),arenaYaw,2.1f,.1f,3f);
        }
        if(Set.of("SPELL_ACTIVATE","TRAP_REVEAL","CHAIN_LINK","CHAIN_RESOLVE","CHAIN_NEGATE").contains(semantic)&&lastView!=null){
            var card=lastView.cards().stream().filter(c->c.token().equals(instance.cue.source())&&!c.category().equals("pokemon")&&!c.category().startsWith("facedown")).findFirst();
            if(card.isPresent()){
                CardActor actor=activationCards.computeIfAbsent(card.get().token(),t->createCardActor());setCardAppearance(actor,card.get(),false);
                setCardPosition(actor,instance.source.add(0,.015,0),arenaYaw,2.7f,.1f,3.85f);
            }
        }
    }
    public boolean settled() {
        return cameraRig != null && timeline.settled() && departing.isEmpty() && System.currentTimeMillis() - settledSince >= 1200
            && actors.values().stream().allMatch(a -> System.currentTimeMillis() - a.born() >= 1200)
            && fieldCards.values().stream().allMatch(a -> a.texture != null);
    }
    public int pokemonActors() { return actors.size(); }
    public int frontCards() { return (int)fieldCards.values().stream().filter(a -> !a.faceDown).count(); }
    public int backCards() { return (int)fieldCards.values().stream().filter(a -> a.faceDown).count(); }
    private void clearVfxFocus(){vfxFocus=null;}
    public void proofCamera() { clearVfxFocus();orbitYaw = 0; orbitPitch = 37; cameraDistance = 14; updateCamera(); }
    public void groundingCamera() { clearVfxFocus();orbitYaw = 18; orbitPitch = 36; cameraDistance = 27; updateCamera(); }
    public void creationCamera() { clearVfxFocus();orbitYaw = 32; orbitPitch = 27; cameraDistance = 18; updateCamera(); }
    public void pileCamera() { clearVfxFocus();orbitYaw = 0; orbitPitch = 48; cameraDistance = 34; updateCamera(); }
    private void vfxCamera(Vec3d source,Vec3d target) {
        vfxFocus=source.lerp(target,.5).add(0,1.0,0);
        orbitYaw=8;orbitPitch=28;cameraDistance=14.5;updateCamera();
        LOG.info("CARDWORLDS_VFX_CAMERA distance={} pitch={} focus={}",cameraDistance,orbitPitch,vfxFocus);
    }
    public void previewVfx(String semantic,String source,String target) {
        if(!Boolean.getBoolean("cardworlds.qa"))throw new IllegalStateException("VFX preview requires the QA driver");
        PokemonEntity pokemon=entity(source);String element=DuelVfxProfile.normalizeKey(pokemon==null?"psychic":pokemon.getPokemon().getPrimaryType().getName());
        Vec3d a=positions.getOrDefault(source,local(0,0,-4)),b=positions.getOrDefault(target,local(0,0,4));
        if(source.isBlank())a=local(-6,.015,-9);
        if(target.isBlank())b=a;
        var animation=new vn.svarcade.tcg.data.EffectSpec.Animation(semantic,.25,.46,.72,.9);
        var presentation=new vn.svarcade.tcg.data.EffectSpec.Presentation(semantic.equals("ATTACK_PHYSICAL")?"MELEE":semantic.equals("ATTACK_SPECIAL")?"BEAM":"STATUS",element,2000,animation,null,null,null,null);
        var cue=new Duel.Cue(-1,semantic,source,target,lastView==null?0:lastView.you(),element,presentation,semantic.equals("TRAP_REVEAL")?3:0);
        timeline.preview(cue,a,b,System.currentTimeMillis(),this::startVfx);vfxCamera(a,b);
    }
    public int vfxCaptureDelay(){return timeline.active().isEmpty()?600:(int)(timeline.active().getFirst().duration*.82);}
    public void previewChainBreak(String source,String target) {
        var cue=new Duel.Cue(-2,"CHAIN_NEGATE",source,target,lastView.you(),"psychic",null,2);
        timeline.appendPreview(cue,positions.getOrDefault(source,local(-6,0,-9)),positions.getOrDefault(target,local(-6,0,9)),System.currentTimeMillis(),this::startVfx);
    }
    public void verifyNativePose(String token,String intent) {
        var entity=entity(token);var proof=animationProofs.get(token);
        if(entity==null||proof==null||!proof.nativeAnimation()||!proof.intent().equals(intent))throw new AssertionError("Native animation unresolved for "+intent);
        if(Set.of("ATTACK_PHYSICAL","ATTACK_SPECIAL","CAST_STATUS","HIT","HEAVY_HIT").contains(intent)&&proof.animation().equalsIgnoreCase("cry"))
            throw new AssertionError("Combat semantic incorrectly resolved to cry: "+intent);
        var state=(com.cobblemon.mod.common.client.entity.PokemonClientDelegate)entity.getDelegate();
        double animated=DuelModelBounds.measure(entity).poseHash();
        var primary=state.getPrimaryAnimation();var activeAnimations=new ArrayList<>(state.getActiveAnimations());
        double idle;
        try {state.setPrimaryAnimation(null);state.getActiveAnimations().clear();idle=DuelModelBounds.measure(entity).poseHash();}
        finally {state.setPrimaryAnimation(primary);state.getActiveAnimations().clear();state.getActiveAnimations().addAll(activeAnimations);}
        double deformation=Math.abs(animated-idle);
        if(deformation<.00001)throw new AssertionError("Resolved native animation did not deform rendered model vertices: "+intent);
        LOG.info("CARDWORLDS_NATIVE_POSE_PROOF intent={} animation={} deformation={}",intent,proof.animation(),deformation);
    }
    public void verifyAnimationFallback(String token) {
        var entity=entity(token);if(entity==null)throw new AssertionError("Fallback QA entity missing");
        var result=PokemonDuelAnimationResolver.play(entity,"__missing_optional_animation__");
        var state=(com.cobblemon.mod.common.client.entity.PokemonClientDelegate)entity.getDelegate();
        if(result.nativeAnimation()||state.getCurrentPose()==null)throw new AssertionError("Missing semantic did not safely preserve provider idle");
        LOG.info("CARDWORLDS_ANIMATION_FALLBACK_PROOF pose={} provider={}",state.getCurrentPose(),entity.getPokemon().getSpecies().getResourceIdentifier());
    }
    public void verifyPileActors() {
        if (piles.size() != 8 || piles.values().stream().filter(p -> p.top().equals(DuelCardMeshes.BACK)).count() != 4)
            throw new AssertionError("Expected eight occupied piles: four hidden decks and four public top cards");
    }


    private final Map<String, Actor> actors = new LinkedHashMap<>();
    private final Map<String, CardActor> fieldCards = new LinkedHashMap<>();
    private final Map<String, CardActor> supportCards = new LinkedHashMap<>();
    private final List<Entity> arenaEntities = new ArrayList<>();
    private ClientWorld world;
    private ArmorStandEntity cameraRig;
    private Entity previousCamera;
    private Perspective previousPerspective;
    private boolean previousHudHidden;
    /** Board surface origin, not camera origin. */
    private Vec3d origin = Vec3d.ZERO;
    private Vec3d vfxFocus;
    private Vec3d forward = new Vec3d(0, 0, 1);
    private Vec3d right = new Vec3d(-1, 0, 0);
    private float arenaYaw;
    private String attacking = "";
    private long attackAt;
    private float orbitYaw;
    private float orbitPitch = 30f;
    private double cameraDistance = 36.0;
    private boolean realmMode;
    private boolean spectatorMode;

    public void setAttack(String token, long when) {
        attacking = token == null ? "" : token;
        attackAt = when;
    }

    public void sync(Duel.View view, int zones, boolean spectator) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (view == null || client.world == null || client.player == null) {
            close();
            return;
        }
        if (world != client.world || cameraRig == null || cameraRig.isRemoved() || spectatorMode != spectator) {
            close();
            begin(client, view.you(), spectator);
        }

        active = this;
        lastView=view;
        String signature = view.deckCounts() + "|" + view.extraCounts() + "|" + view.cards();
        if (!signature.equals(visualSignature)) { visualSignature = signature; settledSince = System.currentTimeMillis(); }
        List<Duel.VisibleCard> field = view.cards().stream().filter(c -> c.zone() == Duel.Zone.FIELD).toList();
        Set<String> liveActors = new HashSet<>();
        Set<String> liveFieldCards = new HashSet<>();
        Map<Integer, Integer> slotByController = new HashMap<>();
        Map<Integer, Long> fieldCountByController = new HashMap<>();
        field.forEach(c -> fieldCountByController.merge(c.controller(), 1L, Long::sum));
        defenseBases.clear();
        long now = System.currentTimeMillis();

        for (Duel.VisibleCard card : field) {
            int slot = slotByController.merge(card.controller(), 1, Integer::sum) - 1;
            int total = Math.min(Math.max(1, zones), fieldCountByController.getOrDefault(card.controller(), 1L).intValue());
            double spacing = realmMode ? 6.0 : 4.60;
            double x = zoneIndex(slot,total) * spacing;
            boolean mine = card.controller() == view.you();
            double z = mine ? (realmMode ? -4.0 : -5.80) : (realmMode ? 4.0 : 5.80);
            Vec3d base = local(x, 0, z);
            positions.put(card.token(),base);

            String position = card.position() == null || card.position().isBlank() ? "ATTACK" : card.position();
            boolean faceDown = "FACE_DOWN_DEFENSE".equals(position) || card.category().startsWith("facedown");
            boolean pokemonVisible = ("ATTACK".equals(position) || "DEFENSE".equals(position)) && !faceDown && card.species() != null && !card.species().isBlank();

            if (pokemonVisible) {
                liveActors.add(card.token());
                if ("DEFENSE".equals(position)) defenseBases.put(card.token(), base);
                removeCard(fieldCards.remove(card.token()));
                Actor actor = actors.get(card.token());
                if (actor == null || !actor.species().equals(card.species()) || !actor.aspects().equals(card.aspects())) {
                    if (actor != null) remove(actor.entity());
                    actor = createPokemon(card.species(), card.aspects(), card.controller(), now);
                    actors.put(card.token(), actor);
                }

                Vec3d pos = base;
                pos=pos.add(timeline.motion(card.token(),now));

                PokemonEntity entity = actor.entity();
                // Entity position is the feet anchor. Keeping Y on the actual board surface
                // guarantees every model touches the board instead of floating above it.
                double yawRad = Math.toRadians((mine ? arenaYaw : arenaYaw + 180f) - 180f);
                Vec3d anchor = actor.anchor();
                entity.setInvisible(timeline.active().stream().anyMatch(v->v.cue.source().equals(card.token())&&v.cue.semantic().startsWith("SUMMON")&&v.progress(now)<.25));
                entity.setOnGround(true);
                entity.setPosition(pos.x + anchor.x * Math.cos(yawRad) - anchor.z * Math.sin(yawRad),
                    pos.y + anchor.y, pos.z + anchor.x * Math.sin(yawRad) + anchor.z * Math.cos(yawRad));
                float yaw = mine ? arenaYaw : arenaYaw + 180f;
                entity.setYaw(yaw);
                entity.setHeadYaw(yaw);
                entity.setBodyYaw(yaw);
                if ("DEFENSE".equals(position) && now - actor.born() < 100)
                    LOG.info("CARDWORLDS_FACEUP_DEFENSE_POKEMON_RENDERED token={} species={} pokemonActorPresent=true",card.token(),card.species());
            } else {
                liveFieldCards.add(card.token());
                Actor old = actors.remove(card.token());
                if (old != null) remove(old.entity());
                CardActor display = fieldCards.computeIfAbsent(card.token(), t -> createCardActor());
                setCardAppearance(display, card, faceDown);
                float yaw = arenaYaw + 90f; // Defense cards lie sideways like the anime/TCG.
                setCardPosition(display, base.add(0, 0.015, 0), yaw, 2.85f, 0.10f, 4.00f);
            }
        }

        timeline.observe(view,token->{
            Vec3d cached=positions.get(token);if(cached!=null)return cached;
            var card=view.cards().stream().filter(c->c.token().equals(token)).findFirst();
            if(card.isPresent()&&(card.get().zone()==Duel.Zone.SUPPORT||card.get().zone()==Duel.Zone.STADIUM)){
                var c=card.get();boolean mine=c.controller()==view.you();
                int slot=view.cards().stream().filter(q->q.controller()==c.controller()&&q.zone()==Duel.Zone.SUPPORT).toList().indexOf(c);
                return local(c.zone()==Duel.Zone.STADIUM?(mine?-18:18):(slot-2)*6,0,c.zone()==Duel.Zone.STADIUM?(mine?-10:10):(mine?-9:9));
            }return null;
        },origin,(controller,targetPoint)->targetPoint?local(0,1,controller==view.you()?4:-4):local(controller==view.you()?18:-18,.1,controller==view.you()?-10:10),now);
        timeline.tick(now,this::startVfx);
        for(var instance:timeline.active())if(!instance.hit&&instance.progress(now)>=instance.fraction("impact")&&Set.of("ATTACK_PHYSICAL","ATTACK_SPECIAL").contains(instance.cue.semantic())){
            instance.hit=true;PokemonEntity victim=entity(instance.cue.target());if(victim!=null)animationProofs.put(instance.cue.target(),PokemonDuelAnimationResolver.play(victim,"HIT"));
        }
        vfx.tick(timeline.active(),now);
        for (Iterator<Map.Entry<String, Actor>> it = actors.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!liveActors.contains(entry.getKey())) {
                if(timeline.departing(entry.getKey()))departing.put(entry.getKey(),new Departing(entry.getValue().entity(),now+2800));
                else remove(entry.getValue().entity());
                it.remove();
            }
        }
        for (Iterator<Map.Entry<String, CardActor>> it = fieldCards.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!liveFieldCards.contains(entry.getKey())) {
                removeCard(entry.getValue());
                it.remove();
            }
        }

        Set<String> liveSupport = new HashSet<>();
        Map<Integer,Integer> supportSlots = new HashMap<>();
        for (Duel.VisibleCard card : view.cards().stream().filter(c -> c.zone()==Duel.Zone.SUPPORT || c.zone()==Duel.Zone.STADIUM).toList()) {
            liveSupport.add(card.token());
            CardActor display = supportCards.computeIfAbsent(card.token(), t -> createCardActor());
            boolean facedown = card.category().startsWith("facedown");
            setCardAppearance(display, card, facedown);
            boolean mine = card.controller()==view.you();
            int slot = supportSlots.merge(card.controller(),1,Integer::sum)-1;
            double x = card.zone()==Duel.Zone.STADIUM ? (mine?-18.0:18.0) : (slot-2)*6.0;
            double z = card.zone()==Duel.Zone.STADIUM ? (mine?-10.0:10.0) : (mine?-9.0:9.0);
            setCardPosition(display, local(x,0.015,z), arenaYaw, 2.70f,0.10f,3.85f);
            positions.put(card.token(),local(x,0,z));
        }
        for (Iterator<Map.Entry<String,CardActor>> it=supportCards.entrySet().iterator(); it.hasNext();) {
            var e=it.next();
            if(!liveSupport.contains(e.getKey())) { removeCard(e.getValue()); it.remove(); }
        }
        syncPiles(view);
        for(var it=departing.entrySet().iterator();it.hasNext();){var old=it.next();if(now>=old.getValue().until()){remove(old.getValue().entity());it.remove();}}
        activationCards.entrySet().removeIf(e->e.getValue().card==null?timeline.active().stream().noneMatch(v->v.cue.sequence()==e.getValue().cueKey):timeline.card(e.getKey())==null);
        while(positions.size()>256)positions.remove(positions.keySet().iterator().next());
        updateCamera();
    }

    private void syncPiles(Duel.View view) {
        Set<PileKey> live = new HashSet<>();
        for (int controller = 0; controller < view.deckCounts().size(); controller++) {
            boolean mine = controller == view.you();
            double side = mine ? -1 : 1;
            for (Duel.Zone zone : List.of(Duel.Zone.DECK, Duel.Zone.EXTRA, Duel.Zone.DISCARD, Duel.Zone.BANISHED)) {
                final int seat = controller;
                List<Duel.VisibleCard> visible = view.cards().stream()
                    .filter(c -> c.controller() == seat && c.zone() == zone).toList();
                int count = switch (zone) {
                    case DECK -> view.deckCounts().get(controller);
                    case EXTRA -> view.extraCounts().get(controller);
                    default -> visible.size();
                };
                if (count == 0) continue;
                PileKey key = new PileKey(controller, zone); live.add(key);
                // Hidden decks use counts only; their identity never enters the front baker.
                var top = DuelCardMeshes.BACK;
                if (zone == Duel.Zone.DISCARD || zone == Duel.Zone.BANISHED) {
                    Duel.VisibleCard card = visible.getLast();
                    if (!card.category().startsWith("facedown")) top = meshes.front(card);
                }
                double x = switch (zone) {
                    case EXTRA -> side * 18;
                    default -> -side * 18;
                };
                double z = switch (zone) {
                    case DECK -> side * 10;
                    case EXTRA, DISCARD -> side * 5;
                    default -> 0;
                };
                piles.put(key, new PileActor(count, top, local(x, 0, z), mine ? arenaYaw : arenaYaw + 180f));
            }
        }
        piles.keySet().retainAll(live);
    }

    public Rect hitBox(String token, int logicalWidth, int logicalHeight) {
        if (cameraRig == null) return null;
        Actor actor = actors.get(token);
        if (actor != null) {
            Vec3d point = actor.entity().getBoundingBox().getCenter().add(0, actor.entity().getHeight()*0.08, 0);
            return project(point, logicalWidth, logicalHeight);
        }
        CardActor card = fieldCards.get(token);
        if (card == null) card = supportCards.get(token);
        return card == null ? null : project(card.center.add(0,0.025,0), logicalWidth, logicalHeight);
    }

    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Actor actor : actors.values()) remove(actor.entity());
        actors.clear();
        departing.values().forEach(a->remove(a.entity()));departing.clear();positions.clear();activationCards.clear();animationProofs.clear();timeline.clear();lastView=null;
        for (CardActor card : fieldCards.values()) removeCard(card);
        fieldCards.clear();
        for (CardActor card : supportCards.values()) removeCard(card);
        supportCards.clear();
        for (Entity entity : arenaEntities) remove(entity);
        arenaEntities.clear();
        if (cameraRig != null) remove(cameraRig);
        if (client != null) {
            if (previousCamera != null && !previousCamera.isRemoved()) client.setCameraEntity(previousCamera);
            else if (client.player != null) client.setCameraEntity(client.player);
            if (previousPerspective != null) client.options.setPerspective(previousPerspective);
            client.options.hudHidden = previousHudHidden;
        }
        defenseBases.clear(); piles.clear(); meshes.close(); if (active == this) active = null; visualSignature="";
        world=null;cameraRig=null;previousCamera=null;previousPerspective=null;vfxFocus=null;attacking="";realmMode=false;spectatorMode=false;
    }

    private void begin(MinecraftClient client, int viewerSeat, boolean spectator) {
        world=client.world;previousCamera=client.getCameraEntity();previousPerspective=client.options.getPerspective();previousHudHidden=client.options.hudHidden;
        client.options.hudHidden=true;client.options.setPerspective(Perspective.FIRST_PERSON);spectatorMode=spectator;

        realmMode=client.world.getRegistryKey().getValue().toString().equals("svarcade_tcg:duel_realm");
        if (realmMode) {
            double cx=Math.round(client.player.getX()/REALM_SPACING)*REALM_SPACING+0.5;
            double cz=Math.round(client.player.getZ()/REALM_SPACING)*REALM_SPACING+0.5;
            boolean southView=spectator||viewerSeat==0;
            forward=southView?new Vec3d(0,0,-1):new Vec3d(0,0,1);
            right=forward.crossProduct(new Vec3d(0,1,0)).normalize();
            arenaYaw=southView?180f:0f;
            origin=new Vec3d(cx,REALM_BOARD_SURFACE_Y,cz);
        } else {
            arenaYaw=client.player.getYaw();double yawRad=Math.toRadians(arenaYaw);
            forward=new Vec3d(-Math.sin(yawRad),0,Math.cos(yawRad)).normalize();right=forward.crossProduct(new Vec3d(0,1,0)).normalize();
            origin=client.player.getPos().add(forward.multiply(12.0)).add(0,0.1,0);
        }

        cameraRig=new ArmorStandEntity(world,origin.x,origin.y,origin.z);cameraRig.setId(nextId());cameraRig.setInvisible(true);cameraRig.setInvulnerable(true);cameraRig.setNoGravity(true);cameraRig.setSilent(true);
        cameraRig.setYaw(arenaYaw);cameraRig.setHeadYaw(arenaYaw);cameraRig.setBodyYaw(arenaYaw);cameraRig.setPitch(30f);world.addEntity(cameraRig);client.setCameraEntity(cameraRig);
        orbitYaw=spectator&&realmMode?90f:0f;orbitPitch=realmMode?32f:25f;cameraDistance=realmMode?(spectator?46.0:36.0):22.5;updateCamera();
        if(!realmMode)buildArena();
    }

    public void orbit(double deltaX,double deltaY){orbitYaw=(float)((orbitYaw-deltaX*0.34)%360.0);orbitPitch=(float)Math.clamp(orbitPitch+deltaY*0.24,12.0,67.0);updateCamera();}
    public void zoom(double wheel){cameraDistance=Math.clamp(cameraDistance-wheel*(realmMode?2.25:1.55),realmMode?14.0:14.0,realmMode?64.0:34.0);updateCamera();}
    public void resetView(){clearVfxFocus();orbitYaw=spectatorMode&&realmMode?90f:0f;orbitPitch=realmMode?32f:25f;cameraDistance=realmMode?(spectatorMode?46.0:36.0):22.5;updateCamera();}

    private void updateCamera() {
        if(cameraRig==null||cameraRig.isRemoved())return;
        double pitch=Math.toRadians(orbitPitch),yaw=Math.toRadians(orbitYaw);double horizontal=cameraDistance*Math.cos(pitch),vertical=cameraDistance*Math.sin(pitch);
        Vec3d back=forward.multiply(-Math.cos(yaw)).add(right.multiply(Math.sin(yaw))).normalize();
        Vec3d target=vfxFocus!=null?vfxFocus:origin.add(0,realmMode?2.15:1.45,0);Vec3d pos=target.add(back.multiply(horizontal)).add(0,vertical+timeline.cameraPunch(System.currentTimeMillis()),0);Vec3d look=target.subtract(pos).normalize();
        float viewYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));float viewPitch=(float)Math.toDegrees(-Math.asin(look.y));
        cameraRig.setPosition(pos.x,pos.y,pos.z);cameraRig.setYaw(viewYaw);cameraRig.setHeadYaw(viewYaw);cameraRig.setBodyYaw(viewYaw);cameraRig.setPitch(viewPitch);
    }

    /** Fallback arena for non-Realm/dev contexts. Production Duel Realm uses physical server blocks. */
    private void buildArena() {
        addDisplay(Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),local(0,-0.25,0),37.2f,0.40f,24.6f);
        for(int side=0;side<2;side++)for(int row=0;row<2;row++)for(int i=0;i<5;i++){
            double z=(side==0?-1:1)*(row==0?4.0:9.0),x=(i-2)*5.2;
            addDisplay(row==0?Blocks.SMOOTH_SANDSTONE.getDefaultState():Blocks.POLISHED_TUFF.getDefaultState(),local(x,-0.02,z),4.2f,0.12f,3.4f);
        }
    }

    private Actor createPokemon(String species,List<String> aspects,int controller,long born) {
        if (species.toLowerCase(java.util.Locale.ROOT).contains("arceus")) {
            boolean megaShowdown = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mega_showdown");
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(net.minecraft.util.Identifier.of("cobblemon", "bedrock/pokemon/models/0493_arceus/arceus.geo.json"));
            if (Boolean.getBoolean("cardworlds.qa") && (!megaShowdown || resource.isEmpty()))
                throw new IllegalStateException("Mega Showdown Arceus model provider missing");
            LOG.info("CARDWORLDS_ARCEUS_RESOURCE pack={}",resource.map(r -> r.getPackId()).orElse("missing"));
            LOG.info(
                "CARDWORLDS_ARCEUS_PROVIDER megaShowdownLoaded={} model=assets/cobblemon/bedrock/pokemon/models/0493_arceus/arceus.geo.json",
                megaShowdown
            );
        }

        try {
            PokemonProperties props=PokemonProperties.Companion.parse(species);props.setAspects(new HashSet<>(aspects));PokemonEntity entity=props.createEntity(world);
            entity.setId(nextId());entity.setAiDisabled(true);entity.setInvulnerable(true);entity.setNoGravity(true);entity.setSilent(true);
            entity.setOnGround(true);
            var placement = DuelModelBounds.measure(entity);
            double natural = placement.height();
            double scale = Math.min(Math.clamp(natural * 1.35, 0.85, 4.8) / natural,
                4.9 / Math.max(0.25, placement.span()));
            scale = Math.min(scale, 3.0);
            var duelScale=entity.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
            if(duelScale!=null)duelScale.setBaseValue(scale);
            Vec3d anchor = new Vec3d(-placement.centerX()*scale, -placement.minY()*scale, -placement.centerZ()*scale);
            world.addEntity(entity);
            LOG.info("CARDWORLDS_DUEL_ACTOR species={} naturalHeight={} scale={} boardY={}",species,natural,scale,origin.y);
            if(species.toLowerCase(Locale.ROOT).contains("arceus"))LOG.info("CARDWORLDS_ARCEUS_RENDERED species={} scale={} boardY={}",species,scale,origin.y);
            return new Actor(entity,species,List.copyOf(aspects),controller,born,scale,anchor);
        } catch(RuntimeException ex){LOG.error("Unable to create duel PokemonEntity for {} {}",species,aspects,ex);throw ex;}
    }

    private static double zoneIndex(int slot,int total){
        return switch(total){
            case 1 -> 0.0;
            case 2 -> slot==0?-1.0:1.0;
            case 3 -> slot-1.0;
            case 4 -> switch(slot){case 0->-2.0;case 1->-1.0;case 2->1.0;default->2.0;};
            default -> Math.clamp(slot-2.0,-2.0,2.0);
        };
    }

    private CardActor createCardActor() { return new CardActor(); }
    private void setCardAppearance(CardActor actor, Duel.VisibleCard card, boolean faceDown) {
        String old = actor.card == null ? "" : actor.card.toString();
        if (old.equals(card.toString()) && actor.faceDown == faceDown && actor.texture != null) return;
        actor.card = card;
        actor.faceDown = faceDown;
        // The back path never reads species, name, type, or aspects, even for the owner.
        actor.texture = faceDown ? DuelCardMeshes.BACK : meshes.front(card);
        if (faceDown) LOG.info("CARDWORLDS_CARD_BACK_RENDERED token={} position={} category={} pokemonActorPresent={}",
            card.token(), card.position(), card.category(), actors.containsKey(card.token()));
        else if ("DEFENSE".equals(card.position())) LOG.info("CARDWORLDS_FACEUP_DEFENSE_CARD_RENDERED token={} species={} pokemonActorPresent={}",
            card.token(), card.species(), actors.containsKey(card.token()));
    }
    private void setCardPosition(CardActor actor, Vec3d center, float yaw, float width, float height, float depth) {
        actor.center = center; actor.yaw = yaw; actor.width = width * 0.9f; actor.depth = actor.width * 10f / 7f;
    }
    private void removeCard(CardActor card) { /* No entities: six connected textured faces form one 0.018m card. */ }

    private void addDisplay(BlockState state,Vec3d center,float width,float height,float depth){
        DisplayEntity.BlockDisplayEntity display=new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,world);display.setId(nextId());display.setBlockState(state);display.setPosition(center.x,center.y,center.z);display.setYaw(arenaYaw);display.setInvulnerable(true);display.setNoGravity(true);display.setSilent(true);display.setViewRange(12.0f);display.setShadowRadius(0);display.setShadowStrength(0);
        display.setTransformation(new AffineTransformation(new Vector3f(-width/2f,-height/2f,-depth/2f),new Quaternionf(),new Vector3f(width,height,depth),new Quaternionf()));world.addEntity(display);arenaEntities.add(display);
    }
    private Vec3d local(double x,double y,double z){return origin.add(right.multiply(x)).add(0,y,0).add(forward.multiply(z));}

    private Rect project(Vec3d worldPoint,int width,int height){
        if(cameraRig==null)return null;Vec3d cam=cameraRig.getCameraPosVec(1f);Vec3d f=Vec3d.fromPolar(cameraRig.getPitch(),cameraRig.getYaw()).normalize();Vec3d r=f.crossProduct(new Vec3d(0,1,0)).normalize();Vec3d u=r.crossProduct(f).normalize();Vec3d rel=worldPoint.subtract(cam);double z=rel.dotProduct(f);if(z<=0.25)return null;
        double fov=Math.toRadians(MinecraftClient.getInstance().options.getFov().getValue()),tan=Math.tan(fov*0.5),aspect=width/(double)Math.max(1,height);double ndcX=rel.dotProduct(r)/(z*tan*aspect),ndcY=rel.dotProduct(u)/(z*tan);if(Math.abs(ndcX)>1.25||Math.abs(ndcY)>1.25)return null;
        int sx=(int)Math.round((ndcX*0.5+0.5)*width),sy=(int)Math.round((0.5-ndcY*0.5)*height);double k=Math.clamp(19.0/z,0.82,1.80);int w=(int)Math.round(86*k),h=(int)Math.round(116*k);return new Rect(sx-w/2,sy-h/2,w,h);
    }

    private static int nextId(){return NEXT_CLIENT_ENTITY_ID++;}
    private void remove(Entity entity){if(entity==null)return;try{ClientWorld w=world;if(w!=null)w.removeEntity(entity.getId(),Entity.RemovalReason.DISCARDED);else entity.discard();}catch(RuntimeException ignored){entity.discard();}}
}
