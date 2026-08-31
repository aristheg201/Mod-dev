package vn.svframe.svquest.quest;

import java.util.List;
import java.util.Set;

/**
 * Server gameplay progression. This intentionally contains no storyline/lore chapter gates.
 * Objective keys are emitted only by server-side gameplay integrations.
 */
public final class QuestCatalog {
    private QuestCatalog() {}

    public record Objective(String key, String label, int target, String featureId) {
        public Objective(String key, String label, int target) { this(key, label, target, ""); }
    }

    public record Quest(String id, String phase, String title, String description,
                        List<Objective> objectives, List<String> rewards) {}

    /** Metrics which legitimately carry across quest boundaries. */
    public static final Set<String> CARRY_OVER = Set.of("capture", "pokemon_level", "trainer_level");

    public static final List<Quest> QUESTS = List.of(
            new Quest("first_partner", "KHỞI ĐẦU", "Đồng đội đầu tiên",
                    "Chọn starter và tự tay bắt Pokémon đầu tiên.",
                    List.of(
                            new Objective("starter", "Chọn starter", 1),
                            new Objective("capture", "Bắt Pokémon hoang dã", 1)
                    ),
                    List.of("8 Poké Ball", "25.000 CobbleDollars", "20 BeastCoin")),

            new Quest("build_team", "KHỞI ĐẦU", "Xây dựng đội hình",
                    "Dùng tài nguyên vừa nhận để mở rộng party trước khi bước vào huấn luyện.",
                    List.of(new Objective("capture", "Tổng Pokémon đã bắt", 3)),
                    List.of("6 EXP Candy S", "4 Great Ball")),

            new Quest("training", "HUẤN LUYỆN", "Huấn luyện chuyên sâu",
                    "Tăng cấp và tiến hóa một Pokémon bằng gameplay thật.",
                    List.of(
                            new Objective("pokemon_level", "Có Pokémon đạt Lv.25", 25),
                            new Objective("evolve", "Tiến hóa Pokémon", 1)
                    ),
                    List.of("40 BeastCoin", "Tera Shard hỗ trợ bước sau")),

            new Quest("trainer_power", "TRAINER", "Sức mạnh Trainer",
                    "Tăng SVFrameMMO level và thắng battle để mở build Pokémon Skill.",
                    List.of(
                            new Objective("trainer_level", "SVFrameMMO đạt Lv.5", 5),
                            new Objective("battle_win", "Thắng Pokémon battle", 5)
                    ),
                    List.of("100 BeastCoin", "1 Skill Point")),

            new Quest("first_skill", "POKÉMON SKILLS", "Kỹ năng đầu tiên",
                    "Mua thật một Pokémon Skill rồi bind nó vào skill bar.",
                    List.of(
                            new Objective("skill_purchase", "Mua Pokémon Skill", 1, "pokemon_skills"),
                            new Objective("skill_bind", "Bind Pokémon Skill", 1, "pokemon_skills")
                    ),
                    List.of("1 Skill Point", "8 EXP Candy M")),

            new Quest("economy", "KINH TẾ", "Giao dịch thực chiến",
                    "Dùng shop và GTS thật; chỉ giao dịch thành công mới được tính.",
                    List.of(
                            new Objective("shop_purchase", "Mua thành công trong SkiesShop", 1, "shop"),
                            new Objective("gts_listing", "Đăng Pokémon lên GTS", 1, "gts"),
                            new Objective("gts_trade", "Mua Pokémon trên GTS", 1, "gts")
                    ),
                    List.of("50.000 CobbleDollars", "20 HunterCoin")),

            new Quest("breeding", "BREEDING", "Thế hệ tiếp theo",
                    "Trải nghiệm breeding bằng egg thật, không tính việc chỉ mở menu.",
                    List.of(
                            new Objective("collect_egg", "Nhận/collect Egg", 1, "breeding"),
                            new Objective("hatch", "Ấp nở Egg", 1, "breeding")
                    ),
                    List.of("Breeding resources", "60 BeastCoin")),

            new Quest("tera_mega", "TỐI ƯU", "Sức mạnh biến đổi",
                    "Thực hiện Tera và Mega trong battle để xác nhận đã dùng mechanic.",
                    List.of(
                            new Objective("tera_use", "Terastallize trong battle", 1),
                            new Objective("mega_use", "Mega Evolution trong battle", 1)
                    ),
                    List.of("Optimization resources", "80 BeastCoin")),

            new Quest("hunts_raids", "HOẠT ĐỘNG", "Săn và Raid",
                    "Bước vào content combat server sau khi đội hình đã có nền tảng.",
                    List.of(
                            new Objective("hunt_complete", "Hoàn thành Hunt", 1, "hunts"),
                            new Objective("raid_complete", "Hoàn thành NovaRaid", 1, "raids")
                    ),
                    List.of("Raid resources", "100 BeastCoin")),

            new Quest("competitive", "THỬ THÁCH", "Tower & Ranked",
                    "Tiến vào PvE/PvP có thứ hạng.",
                    List.of(
                            new Objective("battle_tower_win", "Thắng Battle Tower", 3, "battle_tower"),
                            new Objective("ranked_win", "Thắng Ranked", 3, "ranked")
                    ),
                    List.of("Competitive resources", "30 HunterCoin")),

            new Quest("research_collection", "KHÁM PHÁ", "Nghiên cứu & trao đổi",
                    "Mở rộng collection qua Research và các hệ trade utility.",
                    List.of(
                            new Objective("research_complete", "Hoàn thành Research Task", 1, "research"),
                            new Objective("wonder_trade", "Hoàn tất WonderTrade", 1, "wonder_trade"),
                            new Objective("sts_trade", "Hoàn tất STS", 1, "sts")
                    ),
                    List.of("Collection resources", "40 HunterCoin")),

            new Quest("server_activities", "HOẠT ĐỘNG", "Nội dung mở rộng",
                    "Khám phá các hoạt động dài hạn ngoài battle truyền thống.",
                    List.of(
                            new Objective("expedition_complete", "Hoàn thành Expedition", 1, "expeditions"),
                            new Objective("showcase_complete", "Tham gia/hoàn tất Showcase", 1, "showcase"),
                            new Objective("minigame_complete", "Hoàn thành Minigame", 1)
                    ),
                    List.of("Activity resources", "50 HunterCoin")),

            new Quest("seasonal", "MÙA", "Nhịp chơi hằng ngày",
                    "Dùng Daily, Calendar và Battle Pass như vòng lặp progression thường xuyên.",
                    List.of(
                            new Objective("daily_claim", "Nhận Daily Reward", 1, "daily"),
                            new Objective("battlepass_progress", "Tăng Battle Pass", 1, "battle_pass")
                    ),
                    List.of("Season resources", "100 BeastCoin")),

            new Quest("endgame", "ENDGAME", "Trainer cấp cao",
                    "Đội hình hoàn chỉnh tiến tới Fusion/Potara và content endgame.",
                    List.of(
                            new Objective("fusion_complete", "Hoàn tất Fusion/Potara", 1, "fusion"),
                            new Objective("ranked_high", "Đạt mốc Ranked cao", 1, "ranked"),
                            new Objective("endgame_raid", "Hoàn thành raid endgame", 1, "raids")
                    ),
                    List.of("Endgame rewards", "Hoàn tất lộ trình SVQuest"))
    );

    public static Quest byIndex(int index) {
        return QUESTS.get(Math.max(0, Math.min(index, QUESTS.size() - 1)));
    }

    public static boolean currentAccepts(int questIndex, String key) {
        if (CARRY_OVER.contains(key)) return true;
        return byIndex(questIndex).objectives().stream().anyMatch(o -> o.key().equals(key));
    }
}
