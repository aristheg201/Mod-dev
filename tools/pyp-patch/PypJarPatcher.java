import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PypJarPatcher implements Opcodes {
    private static final String GATE = "com/svframe/pyp/PackDelayGate";
    private static final String STATE = "com/svframe/pyp/PackDelayGate$SessionState";
    private static final String RUNTIME = "com/svframe/pyp/PypRuntimeScheduler";
    private static final String AUTH_MIXIN = "xyz/nikitacartes/easyauth/mixin/ServerPlayerEntityMixin";
    private static final String PROTECT = "com/svframe/pyp/ProtectYourPack";
    private static final String SERVER_TICK_EVENTS = "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents";
    private static final String EVENT = "net/fabricmc/fabric/api/event/Event";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("usage: PypJarPatcher <input.jar> <runtime.class> <output.jar>");
        Path input = Path.of(args[0]);
        Path runtimeClass = Path.of(args[1]);
        Path output = Path.of(args[2]);
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        Manifest manifest;
        try (JarFile jar = new JarFile(input.toFile())) {
            manifest = jar.getManifest();
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.isDirectory() || "META-INF/MANIFEST.MF".equalsIgnoreCase(e.getName())) continue;
                try (InputStream in = jar.getInputStream(e)) { entries.put(e.getName(), in.readAllBytes()); }
            }
        }

        patch(entries, GATE + ".class", PypJarPatcher::patchGate);
        patch(entries, STATE + ".class", PypJarPatcher::patchState);
        patch(entries, AUTH_MIXIN + ".class", PypJarPatcher::patchAuthMixin);
        patch(entries, PROTECT + ".class", PypJarPatcher::patchProtect);
        entries.put(RUNTIME + ".class", Files.readAllBytes(runtimeClass));

        try (JarOutputStream out = manifest == null
                ? new JarOutputStream(Files.newOutputStream(output))
                : new JarOutputStream(Files.newOutputStream(output), manifest)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                JarEntry je = new JarEntry(e.getKey());
                je.setTime(0L);
                out.putNextEntry(je);
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        System.out.println("Patched " + input + " -> " + output);
    }

    private interface NodePatch { void apply(ClassNode node); }

    private static void patch(Map<String, byte[]> entries, String name, NodePatch patch) {
        byte[] bytes = entries.get(name);
        if (bytes == null) throw new IllegalStateException("missing class: " + name);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        patch.apply(node);
        ClassWriter writer = new SafeWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        entries.put(name, writer.toByteArray());
    }

    private static void patchGate(ClassNode c) {
        for (FieldNode f : c.fields) {
            if (f.name.equals("SESSIONS") || f.name.equals("config")) f.access &= ~ACC_PRIVATE;
        }
        for (MethodNode m : c.methods) {
            if (m.name.equals("releaseBetterServerPacks") || m.name.equals("queueR2Send")
                    || m.name.equals("secondsToNanos") || m.name.equals("saturatingAdd")) {
                m.access &= ~ACC_PRIVATE;
            }
            if (m.name.equals("tick") && m.desc.equals("(Lnet/minecraft/server/MinecraftServer;)V")) {
                m.instructions.clear();
                m.tryCatchBlocks.clear();
                if (m.localVariables != null) m.localVariables.clear();
                m.instructions.add(new VarInsnNode(ALOAD, 0));
                m.instructions.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "tickRetry", "(Lnet/minecraft/server/MinecraftServer;)V", false));
                m.instructions.add(new InsnNode(RETURN));
            } else {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; ) {
                    AbstractInsnNode next = insn.getNext();
                    if (insn instanceof FieldInsnNode f && f.getOpcode() == PUTFIELD
                            && f.owner.equals(STATE) && f.name.equals("retryAfterNanos") && f.desc.equals("J")) {
                        m.instructions.insert(insn, new MethodInsnNode(INVOKESTATIC, RUNTIME, "markRetryNeeded", "()V", false));
                    }
                    insn = next;
                }
            }
        }
    }

    private static void patchState(ClassNode c) {
        c.access &= ~ACC_PRIVATE;
        boolean hasGeneration = false;
        for (FieldNode f : c.fields) {
            if (Set.of("joinedAtNanos", "authenticatedAtNanos", "initialPackSent", "sendPending",
                    "retryAfterNanos", "bspUnavailableLogged").contains(f.name)) {
                f.access &= ~ACC_PRIVATE;
            }
            if (f.name.equals("authGeneration")) hasGeneration = true;
        }
        if (!hasGeneration) c.fields.add(new FieldNode(0, "authGeneration", "J", null, null));
    }

    private static void patchAuthMixin(ClassNode c) {
        MethodNode target = null;
        for (MethodNode m : c.methods) {
            if (m.name.equals("easyAuth$setAuthenticated") && m.desc.equals("(Z)V")) { target = m; break; }
        }
        if (target == null) throw new IllegalStateException("EasyAuth setter not found");
        if (containsCall(target, RUNTIME, "onAuthStateChanged")) return;
        for (AbstractInsnNode insn = target.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != RETURN) continue;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(ALOAD, 0));
            hook.add(new FieldInsnNode(GETFIELD, AUTH_MIXIN, "player", "Lnet/minecraft/class_3222;"));
            hook.add(new VarInsnNode(ALOAD, 0));
            hook.add(new FieldInsnNode(GETFIELD, AUTH_MIXIN, "field_13995", "Lnet/minecraft/server/MinecraftServer;"));
            hook.add(new VarInsnNode(ILOAD, 1));
            hook.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "onAuthStateChanged", "(Ljava/lang/Object;Ljava/lang/Object;Z)V", false));
            target.instructions.insertBefore(insn, hook);
        }
    }

    private static void patchProtect(ClassNode c) {
        for (MethodNode m : c.methods) {
            if (m.name.equals("onInitialize") && m.desc.equals("()V")) removeTickRegistration(m);

            if (m.name.equals("reloadConfig")) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESTATIC
                            && mi.owner.equals(GATE) && mi.name.equals("initialize")) {
                        if (!containsCall(m, RUNTIME, "onConfigReload")) {
                            m.instructions.insert(i, new MethodInsnNode(INVOKESTATIC, RUNTIME, "onConfigReload", "()V", false));
                        }
                        break;
                    }
                }
            }

            boolean joinLambda = false, disconnectLambda = false, stoppingLambda = false;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode mi) {
                    if (mi.owner.equals(GATE) && mi.name.equals("onJoin")) joinLambda = true;
                    if (mi.owner.equals(GATE) && mi.name.equals("onDisconnect")) disconnectLambda = true;
                    if (mi.owner.equals("com/svframe/pyp/R2DeliveryService") && mi.name.equals("shutdown")) stoppingLambda = true;
                }
            }
            if ((joinLambda && containsCall(m, RUNTIME, "onJoin"))
                    || (disconnectLambda && containsCall(m, RUNTIME, "onDisconnect"))
                    || (stoppingLambda && containsCall(m, RUNTIME, "shutdown"))) {
                continue;
            }
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i.getOpcode() != RETURN) continue;
                InsnList hook = new InsnList();
                if (joinLambda && m.desc.equals("(Lnet/minecraft/class_3244;Lnet/fabricmc/fabric/api/networking/v1/PacketSender;Lnet/minecraft/server/MinecraftServer;)V")) {
                    hook.add(new VarInsnNode(ALOAD, 0));
                    hook.add(new VarInsnNode(ALOAD, 2));
                    hook.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "onJoin", "(Lnet/minecraft/class_3244;Lnet/minecraft/server/MinecraftServer;)V", false));
                } else if (disconnectLambda && m.desc.equals("(Lnet/minecraft/class_3244;Lnet/minecraft/server/MinecraftServer;)V")) {
                    hook.add(new VarInsnNode(ALOAD, 0));
                    hook.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "onDisconnect", "(Lnet/minecraft/class_3244;)V", false));
                } else if (stoppingLambda) {
                    hook.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "shutdown", "()V", false));
                }
                if (hook.size() > 0) m.instructions.insertBefore(i, hook);
            }
        }
    }

    private static void removeTickRegistration(MethodNode m) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof FieldInsnNode f) || f.getOpcode() != GETSTATIC
                    || !f.owner.equals(SERVER_TICK_EVENTS) || !f.name.equals("END_SERVER_TICK")) continue;
            AbstractInsnNode end = i;
            while (end != null) {
                if (end instanceof MethodInsnNode mi && mi.getOpcode() == INVOKEVIRTUAL
                        && mi.owner.equals(EVENT) && mi.name.equals("register")) break;
                end = end.getNext();
            }
            if (end == null) throw new IllegalStateException("END_SERVER_TICK register call not found");
            AbstractInsnNode cursor = i;
            while (true) {
                AbstractInsnNode next = cursor.getNext();
                m.instructions.remove(cursor);
                if (cursor == end) break;
                cursor = next;
            }
            return;
        }
    }

    private static boolean containsCall(MethodNode m, String owner, String name) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode mi && mi.owner.equals(owner) && mi.name.equals(name)) return true;
        }
        return false;
    }

    private static final class SafeWriter extends ClassWriter {
        SafeWriter(int flags) { super(flags); }
        @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
    }
}
