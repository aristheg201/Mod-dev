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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.attribute.EntityAttributes;
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
 * Real 3D duel presentation. The arena is made from client-side block display entities and
 * every field combatant is a real Cobblemon PokemonEntity rendered by Cobblemon's normal
 * world entity renderer. The UI layer is HUD only; there is no fake 2D duel board.
 */
public final class DuelWorldScene {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds-duel-scene");
    private static int NEXT_CLIENT_ENTITY_ID = -2_100_000_000;

    private record Actor(PokemonEntity entity, String species, List<String> aspects, int controller, long born) {}

    private final Map<String, Actor> actors = new LinkedHashMap<>();
    private final List<Entity> arenaEntities = new ArrayList<>();
    private ClientWorld world;
    private ArmorStandEntity cameraRig;
    private Entity previousCamera;
    private Perspective previousPerspective;
    private boolean previousHudHidden;
    private Vec3d origin = Vec3d.ZERO;
    private Vec3d forward = new Vec3d(0, 0, 1);
    private Vec3d right = new Vec3d(-1, 0, 0);
    private float arenaYaw;
    private String attacking = "";
    private long attackAt;
    private float orbitYaw;
    private float orbitPitch = 25f;
    private double cameraDistance = 22.5;

    public void setAttack(String token, long when) {
        attacking = token == null ? "" : token;
        attackAt = when;
    }

    public void sync(Duel.View view, int zones) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (view == null || client.world == null || client.player == null) {
            close();
            return;
        }
        if (world != client.world || cameraRig == null || cameraRig.isRemoved()) {
            close();
            begin(client);
        }

        List<Duel.VisibleCard> field = view.cards().stream().filter(c -> c.zone() == Duel.Zone.FIELD).toList();
        Set<String> alive = new HashSet<>();
        Map<Integer, Integer> slotByController = new HashMap<>();
        long now = System.currentTimeMillis();

        for (Duel.VisibleCard card : field) {
            alive.add(card.token());
            Actor actor = actors.get(card.token());
            if (actor == null || !actor.species().equals(card.species()) || !actor.aspects().equals(card.aspects())) {
                if (actor != null) remove(actor.entity());
                PokemonEntity entity = createPokemon(card.species(), card.aspects());
                actor = new Actor(entity, card.species(), List.copyOf(card.aspects()), card.controller(), now);
                actors.put(card.token(), actor);
            }

            int slot = slotByController.merge(card.controller(), 1, Integer::sum) - 1;
            int cappedZones = Math.max(1, zones);
            double x = (slot - (cappedZones - 1) / 2.0) * 4.60;
            boolean mine = card.controller() == view.you();
            double z = mine ? -5.80 : 5.80;
            Vec3d pos = local(x, 0.30, z);

            long age = now - actor.born();
            if (age < 700) {
                double t = Math.clamp(age / 700.0, 0.0, 1.0);
                double eased = 1.0 - Math.pow(1.0 - t, 3.0);
                pos = pos.add(0, -1.5 * (1.0 - eased), 0);
            }
            if (card.token().equals(attacking) && now - attackAt < 650) {
                double t = (now - attackAt) / 650.0;
                double lunge = Math.sin(Math.PI * t) * 3.20;
                pos = pos.add(forward.multiply(mine ? lunge : -lunge));
            }

            PokemonEntity entity = actor.entity();
            entity.setPosition(pos.x, pos.y, pos.z);
            float yaw = mine ? arenaYaw : arenaYaw + 180f;
            entity.setYaw(yaw);
            entity.setHeadYaw(yaw);
            entity.setBodyYaw(yaw);
        }

        for (Iterator<Map.Entry<String, Actor>> it = actors.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!alive.contains(entry.getKey())) {
                remove(entry.getValue().entity());
                it.remove();
            }
        }
    }

    public Rect hitBox(String token, int logicalWidth, int logicalHeight) {
        Actor actor = actors.get(token);
        if (actor == null || cameraRig == null) return null;
        Vec3d point = actor.entity().getBoundingBox().getCenter().add(0, actor.entity().getHeight() * 0.12, 0);
        return project(point, logicalWidth, logicalHeight);
    }

    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Actor actor : actors.values()) remove(actor.entity());
        actors.clear();
        for (Entity entity : arenaEntities) remove(entity);
        arenaEntities.clear();
        if (cameraRig != null) remove(cameraRig);
        if (client != null) {
            if (previousCamera != null && !previousCamera.isRemoved()) client.setCameraEntity(previousCamera);
            else if (client.player != null) client.setCameraEntity(client.player);
            if (previousPerspective != null) client.options.setPerspective(previousPerspective);
            client.options.hudHidden = previousHudHidden;
        }
        world = null;
        cameraRig = null;
        previousCamera = null;
        previousPerspective = null;
        attacking = "";
    }

    private void begin(MinecraftClient client) {
        world = client.world;
        previousCamera = client.getCameraEntity();
        previousPerspective = client.options.getPerspective();
        previousHudHidden = client.options.hudHidden;
        client.options.hudHidden = true;
        client.options.setPerspective(Perspective.FIRST_PERSON);

        arenaYaw = client.player.getYaw();
        double yawRad = Math.toRadians(arenaYaw);
        forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        origin = client.player.getPos().add(forward.multiply(12.0)).add(0, 4.5, 0);

        Vec3d cameraPos = origin;
        cameraRig = new ArmorStandEntity(world, cameraPos.x, cameraPos.y, cameraPos.z);
        cameraRig.setId(nextId());
        cameraRig.setInvisible(true);
        cameraRig.setInvulnerable(true);
        cameraRig.setNoGravity(true);
        cameraRig.setSilent(true);
        cameraRig.setYaw(arenaYaw);
        cameraRig.setHeadYaw(arenaYaw);
        cameraRig.setBodyYaw(arenaYaw);
        cameraRig.setPitch(25f);
        world.addEntity(cameraRig);
        client.setCameraEntity(cameraRig);
        orbitYaw = 0f;
        orbitPitch = 25f;
        cameraDistance = 22.5;
        updateCamera();

        buildArena();
    }

    public void orbit(double deltaX, double deltaY) {
        orbitYaw = (float)((orbitYaw - deltaX * 0.34) % 360.0);
        orbitPitch = (float)Math.clamp(orbitPitch + deltaY * 0.24, 12.0, 62.0);
        updateCamera();
    }

    public void zoom(double wheel) {
        cameraDistance = Math.clamp(cameraDistance - wheel * 1.55, 14.0, 34.0);
        updateCamera();
    }

    public void resetView() {
        orbitYaw = 0f;
        orbitPitch = 25f;
        cameraDistance = 22.5;
        updateCamera();
    }

    private void updateCamera() {
        if (cameraRig == null || cameraRig.isRemoved()) return;

        double pitch = Math.toRadians(orbitPitch);
        double yaw = Math.toRadians(orbitYaw);
        double horizontal = cameraDistance * Math.cos(pitch);
        double vertical = cameraDistance * Math.sin(pitch);

        Vec3d back = forward.multiply(-Math.cos(yaw)).add(right.multiply(Math.sin(yaw))).normalize();
        Vec3d target = origin.add(0, 1.45, 0);
        Vec3d pos = target.add(back.multiply(horizontal)).add(0, vertical, 0);
        Vec3d look = target.subtract(pos).normalize();

        float viewYaw = (float)Math.toDegrees(Math.atan2(-look.x, look.z));
        float viewPitch = (float)Math.toDegrees(-Math.asin(look.y));
        cameraRig.setPosition(pos.x, pos.y, pos.z);
        cameraRig.setYaw(viewYaw);
        cameraRig.setHeadYaw(viewYaw);
        cameraRig.setBodyYaw(viewYaw);
        cameraRig.setPitch(viewPitch);
    }

    private void buildArena() {
        addDisplay(Blocks.POLISHED_BLACKSTONE.getDefaultState(), local(0, -0.26, 0), 34.0f, 0.46f, 20.0f);
        addDisplay(Blocks.DARK_PRISMARINE.getDefaultState(), local(0, 0.01, 0), 31.5f, 0.12f, 0.46f);
        addDisplay(Blocks.OXIDIZED_COPPER.getDefaultState(), local(-16.55, -0.05, 0), 0.26f, 0.22f, 19.5f);
        addDisplay(Blocks.OXIDIZED_COPPER.getDefaultState(), local(16.55, -0.05, 0), 0.26f, 0.22f, 19.5f);

        for (int row = 0; row < 2; row++) {
            double z = row == 0 ? -5.80 : 5.80;
            for (int i = 0; i < 5; i++) {
                double x = (i - 2) * 4.60;
                BlockState state = row == 0 ? Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
                addDisplay(state, local(x, 0.02, z), 3.85f, 0.18f, 3.55f);
            }
        }
        addDisplay(Blocks.SEA_LANTERN.getDefaultState(), local(0, -0.02, -9.15), 23.0f, 0.12f, 0.20f);
        addDisplay(Blocks.REDSTONE_LAMP.getDefaultState(), local(0, -0.02, 9.15), 23.0f, 0.12f, 0.20f);
    }

    private PokemonEntity createPokemon(String species, List<String> aspects) {
        try {
            PokemonProperties props = PokemonProperties.Companion.parse(species);
            props.setAspects(new HashSet<>(aspects));
            PokemonEntity entity = props.createEntity(world);
            entity.setId(nextId());
            entity.setAiDisabled(true);
            entity.setInvulnerable(true);
            entity.setNoGravity(true);
            entity.setSilent(true);
            var duelScale = entity.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
            if (duelScale != null) duelScale.setBaseValue(2.00);
            world.addEntity(entity);
            return entity;
        } catch (RuntimeException ex) {
            LOG.error("Unable to create duel PokemonEntity for {} {}", species, aspects, ex);
            throw ex;
        }
    }

    private void addDisplay(BlockState state, Vec3d center, float width, float height, float depth) {
        DisplayEntity.BlockDisplayEntity display = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        display.setId(nextId());
        display.setBlockState(state);
        display.setPosition(center.x, center.y, center.z);
        display.setYaw(arenaYaw);
        display.setInvulnerable(true);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setViewRange(7.0f);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.setTransformation(new AffineTransformation(
            new Vector3f(-width / 2f, -height / 2f, -depth / 2f),
            new Quaternionf(),
            new Vector3f(width, height, depth),
            new Quaternionf()
        ));
        world.addEntity(display);
        arenaEntities.add(display);
    }

    private Vec3d local(double x, double y, double z) {
        return origin.add(right.multiply(x)).add(0, y, 0).add(forward.multiply(z));
    }

    private Rect project(Vec3d worldPoint, int width, int height) {
        if (cameraRig == null) return null;
        Vec3d cam = cameraRig.getCameraPosVec(1f);
        Vec3d f = Vec3d.fromPolar(cameraRig.getPitch(), cameraRig.getYaw()).normalize();
        Vec3d r = f.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d u = r.crossProduct(f).normalize();
        Vec3d rel = worldPoint.subtract(cam);
        double z = rel.dotProduct(f);
        if (z <= 0.25) return null;

        double fov = Math.toRadians(MinecraftClient.getInstance().options.getFov().getValue());
        double tan = Math.tan(fov * 0.5);
        double aspect = width / (double)Math.max(1, height);
        double ndcX = rel.dotProduct(r) / (z * tan * aspect);
        double ndcY = rel.dotProduct(u) / (z * tan);
        if (Math.abs(ndcX) > 1.25 || Math.abs(ndcY) > 1.25) return null;

        int sx = (int)Math.round((ndcX * 0.5 + 0.5) * width);
        int sy = (int)Math.round((0.5 - ndcY * 0.5) * height);
        double k = Math.clamp(19.0 / z, 0.82, 1.80);
        int w = (int)Math.round(86 * k);
        int h = (int)Math.round(116 * k);
        return new Rect(sx - w / 2, sy - h / 2, w, h);
    }

    private static int nextId() { return NEXT_CLIENT_ENTITY_ID++; }

    private void remove(Entity entity) {
        if (entity == null) return;
        try {
            ClientWorld w = world;
            if (w != null) w.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
            else entity.discard();
        } catch (RuntimeException ignored) {
            entity.discard();
        }
    }
}
