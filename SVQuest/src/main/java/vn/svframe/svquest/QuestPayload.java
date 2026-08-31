package vn.svframe.svquest;

import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9135;
import net.minecraft.class_9139;
import net.minecraft.class_9129;

public record QuestPayload(String data) implements class_8710 {
    public static final class_8710.class_9154<QuestPayload> ID =
            new class_8710.class_9154<>(class_2960.method_60655("svquest", "bridge"));
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final class_9139<class_9129, QuestPayload> CODEC =
            (class_9139<class_9129, QuestPayload>) ((class_9139) class_9135.method_56364(1_048_576))
                    .method_56432(o -> new QuestPayload((String) o), q -> ((QuestPayload) q).data());
    @Override public class_8710.class_9154<? extends class_8710> method_56479() { return ID; }
}
