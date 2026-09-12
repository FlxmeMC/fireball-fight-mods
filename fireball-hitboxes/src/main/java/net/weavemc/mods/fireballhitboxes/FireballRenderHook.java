package net.weavemc.mods.fireballhitboxes;

import net.weavemc.api.Hook;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Patches the bounds read shared by vanilla debug rendering and Lunar's styled draw redirect. */
public final class FireballRenderHook extends Hook {
    public static final String STATUS_PROPERTY = "weave.fireballHitboxes.rendererCompatible";

    public FireballRenderHook() { super("net/minecraft/client/renderer/entity/RenderManager"); }

    @Override public void transform(ClassNode node, AssemblerConfig config) {
        MethodInsnNode target = null;
        int matches = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("renderDebugBoundingBox")
                    || !method.desc.equals("(Lnet/minecraft/entity/Entity;DDDFF)V")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals("net/minecraft/entity/Entity")
                        && call.name.equals("getEntityBoundingBox")
                        && call.desc.equals("()Lnet/minecraft/util/AxisAlignedBB;")) {
                    target = call;
                    matches++;
                }
            }
        }
        boolean compatible = matches == 1;
        System.setProperty(STATUS_PROPERTY, Boolean.toString(compatible));
        if (!compatible) {
            System.err.println("[Fireball Hitboxes] Unsupported debug renderer: expected one bounds read, found " + matches);
            return;
        }
        target.setOpcode(Opcodes.INVOKESTATIC);
        target.owner = "net/weavemc/mods/fireballhitboxes/FireballBounds";
        target.name = "forDebugRender";
        target.desc = "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/AxisAlignedBB;";
        target.itf = false;
    }
}
