import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Targeted, minimal metadata patch: preserve all original game logic.
 * The upstream class-target mixin incorrectly targets EVIncreaseItem, which is
 * an interface in Cobblemon 1.8.2. Redirect to its three concrete item classes.
 * Also use the unmapped API method name rather than a Mojmap-only descriptor:
 * runtime Cobblemon Fabric bytecode uses intermediary Minecraft class names.
 */
public final class PerfectPartnersMixinPatch {
    private static final String WRONG = "com.cobblemon.mod.common.item.interactive.EVIncreaseItem";
    private static final String[] CONCRETE = {
        "com.cobblemon.mod.common.item.interactive.FeatherItem",
        "com.cobblemon.mod.common.item.interactive.MochiItem",
        "com.cobblemon.mod.common.item.interactive.VitaminItem",
    };
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static int retargets;
    private static int injectionFixes;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <input.class> <output.class>");
        }
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        ClassReader reader = new ClassReader(source);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationVisitor result = super.visitAnnotation(descriptor, visible);
                if (!MIXIN_DESC.equals(descriptor)) {
                    return result;
                }
                return new AnnotationVisitor(Opcodes.ASM9, result) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        AnnotationVisitor array = super.visitArray(name);
                        if (!"targets".equals(name)) {
                            return array;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9, array) {
                            @Override
                            public void visit(String key, Object value) {
                                if (WRONG.equals(value)) {
                                    retargets++;
                                    for (String concrete : CONCRETE) {
                                        super.visit(key, concrete);
                                    }
                                } else {
                                    super.visit(key, value);
                                }
                            }
                        };
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("companionbonds$beautyFromSuccessfulPokemonItem")) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        AnnotationVisitor original = super.visitAnnotation(descriptor, visible);
                        if (!INJECT_DESC.equals(descriptor)) {
                            return original;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9, original) {
                            @Override
                            public AnnotationVisitor visitArray(String key) {
                                AnnotationVisitor array = super.visitArray(key);
                                if (!"method".equals(key)) {
                                    return array;
                                }
                                return new AnnotationVisitor(Opcodes.ASM9, array) {
                                    @Override
                                    public void visit(String name, Object value) {
                                        if (value instanceof String method &&
                                            method.startsWith("applyToPokemon(")) {
                                            // In 1.8.2 this unique method name is stable; the
                                            // old Mojmap signature cannot resolve in Fabric.
                                            injectionFixes++;
                                            super.visit(name, "applyToPokemon");
                                        } else {
                                            super.visit(name, value);
                                        }
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, 0);

        if (retargets != 1 || injectionFixes != 1) {
            throw new IllegalStateException(
                "Unexpected original mixin bytecode: retargets=" + retargets +
                " methodFixes=" + injectionFixes);
        }
        byte[] result = writer.toByteArray();
        Files.write(Path.of(args[1]), result);
        System.out.printf("FIXED: EVIncreaseItem interface -> %d concrete classes. Injector signature corrected. original=%d patched=%d bytes%n",
                          CONCRETE.length, source.length, result.length);
    }
}
