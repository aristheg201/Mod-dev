# Trainer Phone persistence schema

Persistent fields:

- level cap
- current story
- current objective
- story flags
- badges
- contacts
- unread messages
- active side quests
- completed side quests

Phone apps:

1. Trainer Card
2. Objective
3. Current Story
4. Side Quests
5. Level Cap
6. Badges
7. Contacts
8. Messages
9. League
10. Faction

Notifications render as Minecraft/Cobblemon-style top-right toasts. Full conversations remain in Messages.


## Native smartphone + faction ownership

The Trainer Phone is implemented by Cobblemon World itself: item, model, icons, screens, contacts, messages, quests, League and Faction app.

The Faction app is backed by Cobblemon World's native faction persistence, not by an external faction mod. Native faction data includes:
- faction id/name
- owner/officer/member roles
- member list and invites
- create/invite/accept/leave/kick/promote/demote/transfer/disband operations
- friendly-fire policy
- weekly island Gate War, Conquest and Occupation ownership state
