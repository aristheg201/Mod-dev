import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Reproducible bytecode patch for the binary-only ProtectYourPack workspace. */
public final class PatchPyp {
    private static final String PROTECT = "com/svframe/pyp/ProtectYourPack";
    private static final String GATE = "com/svframe/pyp/PackDelayGate";
    private static final String EASYAUTH_PLAYER_MIXIN = "xyz/nikitacartes/easyauth/mixin/ServerPlayerEntityMixin";
    private static final String HELPER = "com/svframe/pyp/runtime/PypEventScheduler";

    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String HANDLER = "net/minecraft/class_3244";
    private static final String PLAYER = "net/minecraft/class_3222";
    private static final String EVENT = "net/fabricmc/fabric/api/event/Event";
    private static final String SERVER_TICK_EVENTS = "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents";

    private static final Map<String, Integer> COUNTS = new HashMap<>();

    private PatchPyp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: PatchPyp <input.jar> <output.jar>");
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        if (containsEntry(input, HELPER + ".class")) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("ProtectYourPack jar already contains the event-driven scheduler; bytecode patch skipped.");
            return;
        }

        rewrite(input, output);
        assertCount("tick-registration-removed", 1);
        assertCount("join-hook", 1);
        assertCount("disconnect-hook", 1);
        assertCount("reload-hook", 1);
        assertCount("shutdown-hook", 1);
        if (COUNTS.getOrDefault("auth-hook-return", 0) < 1) {
            throw new IllegalStateException("No EasyAuth setAuthenticated return was hooked");
        }

        COUNTS.forEach((name, count) -> System.out.println(name + "=" + count));
    }

    private static boolean containsEntry(Path jar, String name) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(name) != null;
        }
    }

    private static void rewrite(Path input, Path output) throws Exception {
        try (ZipFile zip = new ZipFile(input.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = readAll(in);
                }

                if (name.equals(PROTECT + ".class")) {
                    bytes = patchProtect(bytes);
                } else if (name.equals(EASYAUTH_PLAYER_MIXIN + ".class")) {
                    bytes = patchEasyAuth(bytes);
                }

                ZipEntry replacement = new ZipEntry(name);
                replacement.setTime(entry.getTime());
                if (entry.getComment() != null) {
                    replacement.setComment(entry.getComment());
                }
                out.putNextEntry(replacement);
                out.write(bytes);
                out.closeEntry();
            }
        }
    }

    private static byte[] patchProtect(byte[] original) {
        ClassNode node = readNode(original);

        for (MethodNode method : node.methods) {
            if (method.name.equals("onInitialize") && method.desc.equals("()V")) {
                removeEndServerTickRegistration(method);
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof MethodInsnNode call) {
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals(GATE)
                            && call.name.equals("onJoin")) {
                        hookJoin(method, call);
                    } else if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals(GATE)
                            && call.name.equals("onDisconnect")) {
                        hookDisconnect(method, call);
                    } else if (method.name.equals("reloadConfig")
                            && call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals(GATE)
                            && call.name.equals("initialize")) {
                        InsnList hook = new InsnList();
                        hook.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                HELPER,
                                "onConfigReload",
                                "()V",
                                false));
                        method.instructions.insert(call, hook);
                        increment("reload-hook");
                    } else if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals("com/svframe/pyp/R2DeliveryService")
                            && call.name.equals("shutdown")) {
                        InsnList hook = new InsnList();
                        hook.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                HELPER,
                                "shutdown",
                                "()V",
                                false));
                        method.instructions.insert(call, hook);
                        increment("shutdown-hook");
                    }
                }
                instruction = next;
            }
        }

        return writeNode(node);
    }

    private static void removeEndServerTickRegistration(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.GETSTATIC
                    || !field.owner.equals(SERVER_TICK_EVENTS)
                    || !field.name.equals("END_SERVER_TICK")) {
                continue;
            }

            AbstractInsnNode end = instruction;
            while (end != null) {
                if (end instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(EVENT)
                        && call.name.equals("register")) {
                    break;
                }
                end = end.getNext();
            }
            if (end == null) {
                throw new IllegalStateException("Found END_SERVER_TICK but not its Event.register call");
            }

            AbstractInsnNode cursor = instruction;
            while (true) {
                AbstractInsnNode next = cursor.getNext();
                method.instructions.remove(cursor);
                if (cursor == end) {
                    break;
                }
                cursor = next;
            }
            increment("tick-registration-removed");
            return;
        }
    }

    private static void hookJoin(MethodNode method, MethodInsnNode call) {
        if (containsHelperCallAfter(call, "onJoin")) {
            return;
        }
        int handlerLocal = findArgumentLocal(method, HANDLER);
        int serverLocal = findArgumentLocal(method, SERVER);
        if (handlerLocal < 0 || serverLocal < 0) {
            throw new IllegalStateException("Could not locate join lambda handler/server locals in " + method.name + method.desc);
        }

        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, serverLocal));
        hook.add(new VarInsnNode(Opcodes.ALOAD, handlerLocal));
        hook.add(new VarInsnNode(Opcodes.ALOAD, handlerLocal));
        hook.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                HANDLER,
                "field_14140",
                "L" + PLAYER + ";"));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "onJoin",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                false));
        method.instructions.insert(call, hook);
        increment("join-hook");
    }

    private static void hookDisconnect(MethodNode method, MethodInsnNode call) {
        if (containsHelperCallAfter(call, "onDisconnect")) {
            return;
        }
        int handlerLocal = findArgumentLocal(method, HANDLER);
        if (handlerLocal < 0) {
            throw new IllegalStateException("Could not locate disconnect lambda handler local in " + method.name + method.desc);
        }

        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, handlerLocal));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "onDisconnect",
                "(Ljava/lang/Object;)V",
                false));
        method.instructions.insert(call, hook);
        increment("disconnect-hook");
    }

    private static byte[] patchEasyAuth(byte[] original) {
        ClassNode node = readNode(original);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("easyAuth$setAuthenticated") || !method.desc.equals("(Z)V")) {
                continue;
            }
            if (containsHelperCall(method, "onAuthChanged")) {
                continue;
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof InsnNode) || instruction.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new FieldInsnNode(
                        Opcodes.GETFIELD,
                        EASYAUTH_PLAYER_MIXIN,
                        "player",
                        "L" + PLAYER + ";"));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
                hook.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "onAuthChanged",
                        "(Ljava/lang/Object;Z)V",
                        false));
                method.instructions.insertBefore(instruction, hook);
                increment("auth-hook-return");
            }
        }
        return writeNode(node);
    }

    private static int findArgumentLocal(MethodNode method, String objectInternalName) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        int local = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type argument : arguments) {
            if (argument.getSort() == Type.OBJECT && objectInternalName.equals(argument.getInternalName())) {
                return local;
            }
            local += argument.getSize();
        }
        return -1;
    }

    private static boolean containsHelperCall(MethodNode method, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(HELPER) && call.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsHelperCallAfter(AbstractInsnNode start, String name) {
        int remaining = 8;
        for (AbstractInsnNode instruction = start.getNext(); instruction != null && remaining-- > 0; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(HELPER) && call.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode readNode(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private static byte[] writeNode(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private static void increment(String name) {
        COUNTS.merge(name, 1, Integer::sum);
    }

    private static void assertCount(String name, int expected) {
        int actual = COUNTS.getOrDefault(name, 0);
        if (actual != expected) {
            throw new IllegalStateException(name + " expected=" + expected + " actual=" + actual);
        }
    }
}
