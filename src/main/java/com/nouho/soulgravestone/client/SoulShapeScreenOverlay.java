package com.nouho.soulgravestone.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.nouho.soulgravestone.SoulGravestone;


// Handles rendering the Soul Shape vignette overlay on the screen when active
@EventBusSubscriber(modid = SoulGravestone.MODID, value = Dist.CLIENT)
public class SoulShapeScreenOverlay {
    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png");

    // Called after the GUI is rendered; draws the vignette if Soul Shape is active in first person
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // Only render vignette in first person view
        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.player != null && minecraft.player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT)) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();

            renderVignette(guiGraphics, screenWidth, screenHeight);
        }
    }

    // Renders the blue vignette overlay for the Soul Shape effect
    private static void renderVignette(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        RenderSystem.enableBlend();
        // Use additive blending to make the edges glow blue instead of darkening the center
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        // Light blue color for the vignette effect (matching ghost effect)
        guiGraphics.setColor(0.3f, 0.5f, 0.8f, 0.6f);
        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, screenWidth, screenHeight, screenWidth, screenHeight);

        // Reset color and blending
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
