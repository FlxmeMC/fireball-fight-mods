package net.weavemc.installer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public final class GameplayModDependenciesTest {
    @Test public void includesSharedGateOnlyWhenAGameplayModIsSelected() throws Exception {
        ReleaseManifest.ModInfo fireball = new ReleaseManifest.ModInfo();
        fireball.id = "fireball-hitboxes";
        fireball.dependencies = Collections.singletonList("server-context");
        ReleaseManifest.ModInfo timer = new ReleaseManifest.ModInfo();
        timer.id = "timer";
        timer.dependencies = Arrays.asList("hud-editor", "server-context");
        ReleaseManifest manifest = new ReleaseManifest();
        manifest.mods = Arrays.asList(fireball, timer);
        Method resolver = InstallService.class.getDeclaredMethod("resolveComponents", ReleaseManifest.class, Set.class);
        resolver.setAccessible(true);
        InstallService service = new InstallService();
        assertEquals(new HashSet<String>(Arrays.asList("fireball-hitboxes", "server-context")),
                resolver.invoke(service, manifest, Collections.singleton("fireball-hitboxes")));
        assertEquals(new HashSet<String>(Arrays.asList("timer", "hud-editor", "server-context")),
                resolver.invoke(service, manifest, Collections.singleton("timer")));
        assertEquals(Collections.singleton("hud-editor"),
                resolver.invoke(service, manifest, Collections.singleton("hud-editor")));
    }
}
