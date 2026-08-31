# SVQuest

Client + server Fabric 1.21.1 quest/progression hub for the SVFrame Cobblemon server.

## beta.3 networking fix

The payload id is `svquest:bridge`. beta.3 also fixes the 1.21.1 `PacketCodecs` linkage: `net.minecraft.class_9135` / `PacketCodecs` is an interface, so `string(int)` must be compiled against the real Minecraft intermediary type (or an interface-compatible compile stub), producing an `InterfaceMethodref` constant-pool entry.

The previous beta.2 jar used a class-shaped stub and crashed during `QuestPayload.<clinit>` with `IncompatibleClassChangeError`.

The mod is required on both client and server. Default quest GUI keybind: `J`.
