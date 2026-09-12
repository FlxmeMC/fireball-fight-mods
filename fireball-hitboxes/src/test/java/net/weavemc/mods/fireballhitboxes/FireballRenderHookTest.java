package net.weavemc.mods.fireballhitboxes;

import java.io.InputStream;
import net.weavemc.api.Hook;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public final class FireballRenderHookTest {
    private final Hook.AssemblerConfig config = new Hook.AssemblerConfig() {
        @Override public void computeFrames() { fail("Stack-neutral replacement does not need new frames"); }
    };
    @After public void clearStatus() { System.clearProperty(FireballRenderHook.STATUS_PROPERTY); }

    private ClassNode vanillaRenderer() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream stream = getClass().getResourceAsStream("/net/minecraft/client/renderer/entity/RenderManager.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private MethodNode debugMethod(ClassNode node) {
        for (MethodNode method : node.methods) if (method.name.equals("renderDebugBoundingBox")) return method;
        throw new AssertionError("Missing debug renderer");
    }
    private int correctedCalls(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("forDebugRender")) count++;
        }
        return count;
    }
    @Test public void patchesOnlyTheSingleVanillaDebugBoundsRead() throws Exception {
        ClassNode node = vanillaRenderer();
        new FireballRenderHook().transform(node, config);
        assertTrue(Boolean.getBoolean(FireballRenderHook.STATUS_PROPERTY));
        assertEquals(1, correctedCalls(debugMethod(node)));
        for (MethodNode method : node.methods) if (method != debugMethod(node)) assertEquals(0, correctedCalls(method));
    }
    @Test public void retainsLunarDownstreamDrawCustomization() throws Exception {
        ClassNode node = vanillaRenderer();
        // Lunar's installed legacy mixin redirects the draw operation after the bounds read.
        MethodInsnNode lunarDraw = null;
        for (AbstractInsnNode instruction : debugMethod(node).instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.equals("drawOutlinedBoundingBox")) {
                    call.owner = node.name;
                    call.name = "lunarStyledDraw";
                    lunarDraw = call;
                }
            }
        }
        assertNotNull(lunarDraw);
        new FireballRenderHook().transform(node, config);
        assertEquals("lunarStyledDraw", lunarDraw.name);
        assertEquals(1, correctedCalls(debugMethod(node)));
    }
    @Test public void unknownOrAmbiguousRendererIsLeftUntouched() throws Exception {
        ClassNode node = vanillaRenderer();
        MethodNode debug = debugMethod(node);
        debug.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity",
                "getEntityBoundingBox", "()Lnet/minecraft/util/AxisAlignedBB;", false));
        new FireballRenderHook().transform(node, config);
        assertFalse(Boolean.getBoolean(FireballRenderHook.STATUS_PROPERTY));
        assertEquals(0, correctedCalls(debug));
        node.methods.remove(debug);
        new FireballRenderHook().transform(node, config);
        assertFalse(Boolean.getBoolean(FireballRenderHook.STATUS_PROPERTY));
    }
}
