# DailyChest

Fabric server-side mod for Minecraft 1.21.1.

Push these files to the `main` branch. Open **Actions**, select the successful **Build DailyChest** run, and download its `DailyChest-mod` artifact.

Commands (OP level 2):
- `/dailychest create` while looking at a single chest
- `/dailychest remove` while looking at a registered chest
- `/dailychest reload` to reload the saved data

Each player can claim each registered chest once per calendar day in `America/Los_Angeles` time. Items are copied, not removed from the template chest. Inventory overflow drops beside the chest. Data is stored in `dailychest.json` in the world save folder.

This initial version supports single chests, not double-chest inventories.
