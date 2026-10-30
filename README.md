# FoPauseMenu

Configurable native pause menu actions for modern Paper servers.

<details>
<summary><strong>Developer API</strong></summary>

### Maven dependency example

Install the matching `FoPauseMenu-1.1.jar` locally if it is not in your Maven repository. These coordinates do not claim a published artifact. Use `provided` scope so the server supplies FoPauseMenu:

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoPauseMenu</artifactId>
  <version>1.1</version>
  <scope>provided</scope>
</dependency>
```

FoPauseMenu 1.1 exposes its integration service through Bukkit's `ServicesManager` and its action event in `me.foesio.foPauseMenu.api.event`. Compile against the **FoPauseMenu-1.1.jar** built by this project with `provided` or `compileOnly` scope. Do not shade its classes into a consumer plugin. FoPauseMenu uses Java 21 and modern Paper dialogs; a consumer must run on a compatible Paper server.

For a Paper consumer that requires FoPauseMenu, add this to `paper-plugin.yml`:

```yaml
dependencies:
  server:
    FoPauseMenu:
      load: BEFORE
      required: true
      join-classpath: true
```

For a `plugin.yml` consumer, use `depend: [FoPauseMenu]`. If FoPauseMenu is optional, use `required: false` in Paper metadata or `softdepend: [FoPauseMenu]` in Bukkit metadata. Keep direct references to FoPauseMenu types in a separate integration class that is loaded only after checking the provider is present and enabled.

For a Maven consumer, install the built jar into a local repository if it is not otherwise available:

```sh
mvn install:install-file -Dfile=/path/to/FoPauseMenu-1.1.jar -DgroupId=me.foesio -DartifactId=FoPauseMenu -Dversion=1.1 -Dpackaging=jar
```

Then use `me.foesio:FoPauseMenu:1.1` with `<scope>provided</scope>` in the consumer POM. This is a local artifact example, not a claim that FoPauseMenu is published to a remote Maven repository.

### Open the dialog

```java
FoPauseMenuApi api = Bukkit.getServicesManager().load(FoPauseMenuApi.class);
if (api != null) {
    FoPauseMenuApi.OpenResult result = api.open(player);
    // OPENED, PLAYER_OFFLINE, or UNAVAILABLE
}
```

`open(Player)` must be called on the Bukkit server thread. It opens the same registered dialog as the ESC button and returns `UNAVAILABLE` if the plugin or registry entry is unavailable. It does not change config or persist anything. Reacquire the service after a provider disable/re-enable; a cached reference to a disabled provider returns `UNAVAILABLE`.

### Attach a Java action to a configured button

Configure and enable a button in `config.yml` or the editor, then restart the server and let players rejoin so the dialog contains it. The action ID is the key under `actions:`. Set `type: none` if the button should do nothing without your plugin. By default, a Java handler runs **after** the configured player command, console command, or message. Use `REPLACE_CONFIGURED` if the Java handler should run instead. Unregistering or disabling the owner removes its handler without changing the configured action.

```java
FoPauseMenuApi api = Bukkit.getServicesManager().load(FoPauseMenuApi.class);
if (api != null) {
    boolean registered = api.registerCustomAction("shop", this, player -> openShop(player));
    if (!registered) {
        getLogger().warning("FoPauseMenu button 'shop' is unavailable or owned by another plugin");
    }
}

// To replace the configured action instead of running after it, use this call instead:
if (api != null) {
    api.registerCustomAction("shop", this, player -> openShop(player),
            CustomActionMode.REPLACE_CONFIGURED);
}

// Optional during this plugin's onDisable():
if (api != null) api.unregisterCustomAction("shop", this);
```

Register on the Bukkit server thread after both plugins enable. `registerCustomAction` returns `false` when the action ID is absent from the active dialog, either plugin is disabled, or another owner already registered a handler for that ID. The same owner can replace its handler. IDs are case-insensitive and limited to letters, digits, `_`, and `-`. The registration is runtime-only and is removed automatically when the owner disables. Handlers run on the Bukkit server thread after the button's configured permission check and after the pre-execute event. With `AFTER_CONFIGURED`, the configured behavior runs first. Handler exceptions are logged; they do not trigger another attempt at the configured action.

### Observe or cancel an action

```java
@EventHandler
public void onPauseAction(PauseMenuActionPreExecuteEvent event) {
    if (event.getActionId().equals("shop") && isInCombat(event.getPlayer())) {
        event.setCancelled(true);
    }
}
```

`PauseMenuActionPreExecuteEvent` fires on the Bukkit server thread one scheduled tick after the dialog closes, after the configured permission check, and before any configured or Java behavior. Cancellation prevents both; it does not reopen the dialog. The event reports the normalized action ID, configured type (`PLAYER_COMMAND`, `CONSOLE_COMMAND`, `MESSAGE`, or `NONE`), and `hasCustomAction()`. It does not fire for permission denial, buttons absent or disabled in the startup dialog, `none` buttons without a handler, or empty commands/messages without a handler. A handler unregistered during this event will not run for that click. Player commands subsequently pass through `PlayerCommandPreprocessEvent`; console commands and messages do not. Command dispatch does not prove the destination command succeeded. Event fields are read-only and no persistence is involved.

FoPauseMenu currently uses Bukkit's primary scheduler for click execution and does not promise Folia region-thread compatibility. Test consumer behavior on the actual server before relying on it in production.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoPauseMenuApi`

```java
FoPauseMenuApi.OpenResult open(Player);
boolean registerCustomAction(String, Plugin, CustomActionHandler);
boolean registerCustomAction(String, Plugin, CustomActionHandler, CustomActionMode);
boolean unregisterCustomAction(String, Plugin);
```

#### `CustomActionHandler`

```java
void execute(Player);
```

**Other public contract types:** `CustomActionMode`, `PauseMenuActionType`.

### Complete Bukkit event index

- `PauseMenuActionPreExecuteEvent`.

</details>
