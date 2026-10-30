# FoPauseMenu

Configurable native pause menu actions for modern Paper servers.

<details>
<summary><strong>Developer API</strong></summary>

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoPauseMenu` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoPauseMenu</artifactId>
  <version>1.1</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foPauseMenu.api.FoPauseMenuApi;
import me.foesio.foPauseMenu.api.event.PauseMenuActionPreExecuteEvent;

FoPauseMenuApi api = Bukkit.getServicesManager().load(FoPauseMenuApi.class);
if (api != null) api.open(player);

@EventHandler
public void onPauseMenuActionPreExecuteEvent(PauseMenuActionPreExecuteEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoPauseMenuApi`: `open`, `registerCustomAction`, `unregisterCustomAction`.
- `CustomActionHandler`: `execute`.

**Other API types:** `CustomActionMode`, `PauseMenuActionType`.

**Events:** `PauseMenuActionPreExecuteEvent`.

[Browse API source](src/main/java/me/foesio/foPauseMenu/api) for signatures and event details.

</details>
