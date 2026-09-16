# Content schema

Schema hiện tại: `1`.

## Page

```json
{
  "id": "how_to_play",
  "route": "guide/how-to-play",
  "category": "guide",
  "title": {"vi_vn": "Cách chơi", "en_us": "How to Play"},
  "theme": "pixel_wiki",
  "tags": ["guide", "beginner"],
  "showInNavigation": true,
  "visibility": {},
  "components": []
}
```

## Component types core

- `heading`
- `text`
- `markdown`
- `animated_text`
- `image`
- `pixel_image`
- `animated_image`
- `button`
- `separator`
- `notice`
- `grid`
- `search_box`
- `pokemon_model`
- `command_card`
- `link_card`
- `list`
- `collapse`
- `badge`
- `tooltip`
- `table`
- `widget`
- `spacer`

## Actions

Core action types:

- `open_page`
- `run_command`
- `copy_text`
- `open_url`
- `close`
- `back`

Custom server action type có thể đăng ký qua `SVHubApi.registerServerActionType(...)`.

## Visibility

```json
{
  "permission": "server.vip",
  "serverMod": "cobblemon",
  "clientMod": "some_client_mod",
  "editorOnly": false
}
```

Server xử lý server-side gates; client xử lý `clientMod` sau khi nhận projected snapshot.

## Fakemon

```json
{
  "id": "tyranitar_godzilla",
  "species": "cobblemon:tyranitar",
  "aspects": ["tyranitar_godzilla"],
  "displayName": {"vi_vn": "Tyranitar Godzilla"},
  "tags": ["fakemon", "mount-yeager"],
  "wikiPage": "wiki/fakemon/tyranitar-godzilla"
}
```

Model không nằm trong schema này; client resolve qua Cobblemon resources hiện tại.

## Generated/pixel asset

```json
{
  "type": "background",
  "source": "generated",
  "resource": "svhub:textures/gui/generated/custom.png",
  "width": 512,
  "height": 288,
  "generator": {
    "preset": "pixel_sky"
  },
  "tags": ["pixel", "background"]
}
```

Runtime animation ưu tiên sprite sheet/frame sequence thay vì decode GIF mỗi frame.
