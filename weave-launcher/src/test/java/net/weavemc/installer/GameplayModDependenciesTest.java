package net.weavemc.installer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public final class GameplayModDependenciesTest {
    @Test public void includesSharedLibrariesOnlyWhenAGameplayModIsSelected() throws Exception {
        ReleaseManifest.ModInfo fireball = new ReleaseManifest.ModInfo();
        fireball.id = "fireball-hitboxes";
        fireball.dependencies = Arrays.asList("server-context", "lunar-mod-api");
        ReleaseManifest.ModInfo timer = new ReleaseManifest.ModInfo();
        timer.id = "timer";
        timer.dependencies = Arrays.asList("lunar-mod-api", "server-context");
        ReleaseManifest manifest = new ReleaseManifest();
        manifest.mods = Arrays.asList(fireball, timer);
        Method resolver = InstallService.class.getDeclaredMethod("resolveComponents", ReleaseManifest.class, Set.class);
        resolver.setAccessible(true);
        InstallService service = new InstallService();
        assertEquals(new HashSet<String>(Arrays.asList("fireball-hitboxes", "server-context", "lunar-mod-api")),
                resolver.invoke(service, manifest, Collections.singleton("fireball-hitboxes")));
        assertEquals(new HashSet<String>(Arrays.asList("timer", "fireball-hitboxes", "lunar-mod-api", "server-context")),
                resolver.invoke(service, manifest, new HashSet<String>(Arrays.asList("timer", "fireball-hitboxes"))));
        assertEquals(Collections.emptySet(),
                resolver.invoke(service, manifest, Collections.<String>emptySet()));
    }
}
