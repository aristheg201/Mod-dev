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
        boolean faceDown;
        Vec3d center = Vec3d.ZERO;
        float yaw, width, depth;
        net.minecraft.util.Identifier texture;
    }
    private final Map<String, Vec3d> defenseBases = new LinkedHashMap<>();
    private final DuelCardMeshes meshes = new DuelCardMeshes();
    private static DuelWorldScene active;
    private static boolean renderHook;
    private long settledSince;
    private String visualSignature = "";

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
        supportCards.values().forEach(card -> meshes.card(context, card.texture, card.center, card.yaw, card.width, card.depth));
    }
    public boolean settled() {
        return cameraRig != null && System.currentTimeMillis() - settledSince >= 1200
            && actors.values().stream().allMatch(a -> System.currentTimeMillis() - a.born() >= 1200)
            && fieldCards.values().stream().allMatch(a -> a.texture != null);
    }
    public int pokemonActors() { return actors.size(); }
    public int frontCards() { return (int)fieldCards.values().stream().filter(a -> !a.faceDown).count(); }
    public int backCards() { return (int)fieldCards.values().stream().filter(a -> a.faceDown).count(); }
    public void proofCamera() { orbitYaw = 0; orbitPitch = 48; cameraDistance = 20; updateCamera(); }
    public void creationCamera() { orbitYaw = 18; orbitPitch = 28; cameraDistance = 23; updateCamera(); }


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
        String signature = view.cards().stream().filter(c -> c.zone() == Duel.Zone.FIELD || c.zone() == Duel.Zone.SUPPORT)
            .map(c -> c.token() + c.position() + c.species() + c.aspects()).reduce("", String::concat);
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
                if (card.token().equals(attacking) && now - attackAt < 650) {
                    double t = (now - attackAt) / 650.0;
                    double lunge = Math.sin(Math.PI * t) * 3.20;
                    pos = pos.add(forward.multiply(mine ? lunge : -lunge));
                }

                PokemonEntity entity = actor.entity();
                // Entity position is the feet anchor. Keeping Y on the actual board surface
                // guarantees every model touches the board instead of floating above it.
                double yawRad = Math.toRadians((mine ? arenaYaw : arenaYaw + 180f) - 180f);
                Vec3d anchor = actor.anchor();
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
                setCardPosition(display, base.add(0, 0.035, 0), yaw, 2.85f, 0.10f, 4.00f);
            }
        }

        for (Iterator<Map.Entry<String, Actor>> it = actors.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!liveActors.contains(entry.getKey())) {
                remove(entry.getValue().entity());
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
            setCardPosition(display, local(x,0.055,z), arenaYaw, 2.70f,0.10f,3.85f);
        }
        for (Iterator<Map.Entry<String,CardActor>> it=supportCards.entrySet().iterator(); it.hasNext();) {
            var e=it.next();
            if(!liveSupport.contains(e.getKey())) { removeCard(e.getValue()); it.remove(); }
        }
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
        defenseBases.clear(); meshes.close(); if (active == this) active = null; visualSignature="";
        world=null;cameraRig=null;previousCamera=null;previousPerspective=null;attacking="";realmMode=false;spectatorMode=false;
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
    public void resetView(){orbitYaw=spectatorMode&&realmMode?90f:0f;orbitPitch=realmMode?32f:25f;cameraDistance=realmMode?(spectatorMode?46.0:36.0):22.5;updateCamera();}

    private void updateCamera() {
        if(cameraRig==null||cameraRig.isRemoved())return;
        double pitch=Math.toRadians(orbitPitch),yaw=Math.toRadians(orbitYaw);double horizontal=cameraDistance*Math.cos(pitch),vertical=cameraDistance*Math.sin(pitch);
        Vec3d back=forward.multiply(-Math.cos(yaw)).add(right.multiply(Math.sin(yaw))).normalize();
        Vec3d target=origin.add(0,realmMode?2.15:1.45,0);Vec3d pos=target.add(back.multiply(horizontal)).add(0,vertical,0);Vec3d look=target.subtract(pos).normalize();
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
