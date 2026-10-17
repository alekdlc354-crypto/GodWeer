package net.minecraft.client.renderer.rendertype;

import pub.godweer.auxiliary.render.pipline.PiplinePost;

/**
 * GodWeer chams RenderTypes. Lives in the vanilla package to access
 * the package-private RenderType.create.
 *
 * Each mode has a depth-tested pass and a depth-ignoring "through walls"
 * twin (same fragment shader, ALWAYS_PASS depth compare).
 */
public final class ChamsRenderTypes {
    public static final RenderType CHAMS_GLASS = RenderType.create("godweer_chams_glass",
            RenderSetup.builder(PiplinePost.CHAMS_ENTITY_GLASS).createRenderSetup());

    public static final RenderType CHAMS_GLASS_THROUGH = RenderType.create("godweer_chams_glass_through",
            RenderSetup.builder(PiplinePost.CHAMS_ENTITY_GLASS_THROUGH).createRenderSetup());

    public static final RenderType CHAMS_GLOW = RenderType.create("godweer_chams_glow",
            RenderSetup.builder(PiplinePost.CHAMS_ENTITY_GLOW).createRenderSetup());

    public static final RenderType CHAMS_GLOW_THROUGH = RenderType.create("godweer_chams_glow_through",
            RenderSetup.builder(PiplinePost.CHAMS_ENTITY_GLOW_THROUGH).createRenderSetup());

    public static final RenderType CHAMS_PLASMA = RenderType.create("godweer_chams_plasma",
            RenderSetup.builder(PiplinePost.CHAMS_ENTITY_PLASMA).createRenderSetup());

    public static final RenderType CHAMS_PLASMA_THROUGH = RenderType.create("godweer_chams_plasma_through",
            RenderSetup.builder(PiplinePost.CHAMS_ENTITY_PLASMA_THROUGH).createRenderSetup());

    private ChamsRenderTypes() {
    }
}
