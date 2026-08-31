package vn.svframe.svquest.quest;

import java.util.List;

public final class QuestCatalog {
    private QuestCatalog() {}

    public record Objective(String key, String label, int target) {}
    public record Quest(String id, String phase, String title, String description, List<Objective> objectives, List<String> rewards) {}

    public static final List<Quest> QUESTS = List.of(
            new Quest("first_partner", "KHỞI ĐẦU", "Đồng đội đầu tiên",
                    "Chọn starter và bắt Pokémon đầu tiên để bắt đầu hành trình gameplay.",
                    List.of(new Objective("starter", "Chọn starter", 1), new Objective("capture", "Bắt Pokémon hoang dã", 1)),
                    List.of("Poké Ball", "CobbleDollars", "BeastCoin")),
            new Quest("build_team", "KHỞI ĐẦU", "Xây dựng đội hình",
                    "Mở rộng đội hình bằng chính số Poké Ball vừa nhận.",
                    List.of(new Objective("capture", "Tổng Pokémon đã bắt", 3)),
                    List.of("EXP Candy", "Great Ball")),
            new Quest("training", "HUẤN LUYỆN", "Huấn luyện chuyên sâu",
                    "Tăng cấp và tiến hóa Pokémon để mở nhánh tối ưu đội hình.",
                    List.of(new Objective("pokemon_level", "Pokémon đạt cấp 25", 25), new Objective("evolve", "Tiến hóa Pokémon", 1)),
                    List.of("BeastCoin", "Tera Shard")),
            new Quest("trainer_power", "TRAINER", "Sức mạnh Trainer",
                    "Tăng cấp SVFrameMMO và bắt đầu dùng điểm build.",
                    List.of(new Objective("trainer_level", "Trainer đạt cấp 5", 5), new Objective("battle_win", "Thắng battle", 5)),
                    List.of("BeastCoin", "Skill Point")),
            new Quest("first_skill", "POKÉMON SKILLS", "Kỹ năng đầu tiên",
                    "Mua và bind Pokémon Skill đầu tiên; GUI có thể mở thẳng hệ kỹ năng.",
                    List.of(new Objective("skill_purchase", "Mua Pokémon Skill", 1), new Objective("skill_bind", "Bind Pokémon Skill", 1)),
                    List.of("Skill Point", "EXP Candy")),
            new Quest("economy", "KINH TẾ", "Giao dịch thực chiến",
                    "Làm quen shop, GTS và các loại tiền của server.",
                    List.of(new Objective("shop_purchase", "Mua hàng trong shop", 1), new Objective("gts_trade", "Hoàn tất giao dịch GTS", 1)),
                    List.of("CobbleDollars", "HunterCoin")),
            new Quest("combat", "HOẠT ĐỘNG", "Đấu trường và săn thưởng",
                    "Tham gia các hoạt động combat thật thay vì chỉ đọc hướng dẫn.",
                    List.of(new Objective("hunt_complete", "Hoàn thành Hunt", 1), new Objective("raid_complete", "Hoàn thành Raid", 1)),
                    List.of("BeastCoin", "Raid resources")),
            new Quest("optimization", "TỐI ƯU", "Pokémon hoàn chỉnh",
                    "Đi qua breeding, IV/EV, Tera và các hệ tối ưu đội hình.",
                    List.of(new Objective("hatch", "Ấp trứng", 1), new Objective("optimized", "Hoàn thiện build Pokémon", 1)),
                    List.of("Optimization resources")),
            new Quest("collection", "BỘ SƯU TẬP", "Mở rộng nội dung",
                    "Research, WonderTrade, STS, skin, showcase và các hệ collection.",
                    List.of(new Objective("research", "Hoàn thành Research", 1), new Objective("collection", "Hoàn thành hoạt động collection", 3)),
                    List.of("HunterCoin", "Cosmetic resources")),
            new Quest("endgame", "ENDGAME", "Trainer cấp cao",
                    "Ranked cao, raid cao cấp, Potara/Fusion và đội hình hoàn thiện.",
                    List.of(new Objective("endgame", "Hoàn thành mục tiêu endgame", 3)),
                    List.of("Endgame access"))
    );

    public static Quest byIndex(int index) {
        return QUESTS.get(Math.max(0, Math.min(index, QUESTS.size() - 1)));
    }
}
