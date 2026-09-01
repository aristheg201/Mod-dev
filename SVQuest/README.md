# SVQuest

Client + server Fabric 1.21.1 quest/progression hub for the SVFrame Cobblemon server.

The mod is required on both client and server. Default quest GUI keybind: `J`.

## Config-driven quests

Quest definitions are not compiled into Java. The authoritative server loads every UTF-8 JSON file in:

`config/svquest/quests/*.json`

On first boot SVQuest creates `config/svquest/quests/00-starter.json` from the bundled editable template. Add more files or edit existing files, then run:

`/svquest reload`

A reload is transactional: malformed UTF-8, malformed JSON, duplicate IDs, missing objectives, dangling prerequisites, self prerequisites, or prerequisite cycles reject the new catalog and keep the previous valid runtime catalog active.

Each file may contain a single quest object, a JSON array of quest objects, or `{ "quests": [ ... ] }`.

```json
{
  "id": "example_capture",
  "enabled": true,
  "category": "progression",
  "phase": "KHỞI ĐẦU",
  "title": "Bắt Pokémon đầu tiên",
  "description": "Bắt một Pokémon bất kỳ.",
  "objectives": [
    {
      "type": "CAPTURE",
      "target": "*",
      "metaKey": "species",
      "amount": 1,
      "mode": "sum",
      "label": "Bắt 1 Pokémon",
      "featureId": ""
    }
  ],
  "rewards": [
    {
      "type": "COMMAND",
      "command": "give %player% minecraft:rare_candy 1",
      "label": "Rare Candy x1"
    }
  ],
  "prerequisites": []
}
```

`type` is an event key. SVQuest does not infer a quest type or feature from the quest ID. Any integration can emit its own event key through `QuestApi`, and config objectives match that key plus optional metadata.

`featureId` is also explicit config data. Generic feature command routing lives in `config/svquest/features.json` and is reloaded by the same `/svquest reload` command.

The server compresses and chunks the authoritative catalog to clients, so adding a server quest does not require rebuilding the JAR or manually copying the quest JSON to every client.
