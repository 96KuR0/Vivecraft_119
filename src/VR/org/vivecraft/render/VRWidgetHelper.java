package org.vivecraft.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import java.util.Random;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.optifine.model.QuadBounds;
import org.lwjgl.opengl.GL11;

import org.vivecraft.gameplay.trackers.CameraTracker;
import org.vivecraft.settings.VRHotkeys;
import org.vivecraft.settings.VRSettings;
import org.vivecraft.utils.Utils;

public class VRWidgetHelper
{
    public static boolean debug = false;
    private static final RandomSource random = RandomSource.create();

    public static void renderVRThirdPersonCamWidget()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.vrSettings.mixedRealityRenderCameraModel)
        {
            if ((minecraft.currentPass == RenderPass.LEFT || minecraft.currentPass == RenderPass.RIGHT) && (minecraft.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY || minecraft.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON))
            {
                float f = 0.35F;

                if (minecraft.interactTracker.isInCamera() && !VRHotkeys.isMovingThirdPersonCam())
                {
                    f *= 1.03F;
                }

                renderVRCameraWidget(-0.748F, -0.438F, -0.06F, f, RenderPass.THIRD, GameRenderer.thirdPersonCameraModel, GameRenderer.thirdPersonCameraDisplayModel, () ->
                {
                    minecraft.vrRenderer.framebufferMR.bindRead();
                    RenderSystem.setShaderTexture(0, minecraft.vrRenderer.framebufferMR.getColorTextureId());
                }, (face) ->
                {
                    if (face == Direction.NORTH)
                    {
                        return VRWidgetHelper.DisplayFace.MIRROR;
                    }
                    else {
                        return face == Direction.SOUTH ? VRWidgetHelper.DisplayFace.NORMAL : VRWidgetHelper.DisplayFace.NONE;
                    }
                });
            }
        }
    }

    public static void renderVRHandheldCameraWidget()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.currentPass != RenderPass.CAMERA && minecraft.cameraTracker.isVisible())
        {
            float f = 0.25F;

            if (minecraft.interactTracker.isInHandheldCamera() && !minecraft.cameraTracker.isMoving())
            {
                f *= 1.03F;
            }

            renderVRCameraWidget(-0.5F, -0.25F, -0.22F, f, RenderPass.CAMERA, CameraTracker.cameraModel, CameraTracker.cameraDisplayModel, () ->
            {
                if (minecraft.getEntityRenderDispatcher().getItemInHandRenderer().getNearOpaqueBlock(minecraft.vrPlayer.vrdata_world_render.getEye(RenderPass.CAMERA).getPosition(), (double)minecraft.gameRenderer.minClipDistance) == null)
                {
                    minecraft.vrRenderer.cameraFramebuffer.bindRead();
                    RenderSystem.setShaderTexture(0, minecraft.vrRenderer.cameraFramebuffer.getColorTextureId());
                }
                else {
                	RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/black.png"));
                }
            }, (face) ->
            {
                return face == Direction.SOUTH ? VRWidgetHelper.DisplayFace.NORMAL : VRWidgetHelper.DisplayFace.NONE;
            });
        }
    }

    public static void renderVRCameraWidget(float offsetX, float offsetY, float offsetZ, float scale, RenderPass renderPass, ModelResourceLocation model, ModelResourceLocation displayModel, Runnable displayBindFunc, Function<Direction, VRWidgetHelper.DisplayFace> displayFaceFunc)
    {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        minecraft.gameRenderer.applyVRModelView(minecraft.currentPass, poseStack);

        Vec3 vec3 = minecraft.vrPlayer.vrdata_world_render.getEye(renderPass).getPosition();
        Vec3 vec31 = minecraft.vrPlayer.vrdata_world_render.getEye(minecraft.currentPass).getPosition();
        Vec3 vec32 = vec3.subtract(vec31);

        poseStack.translate(vec32.x, vec32.y, vec32.z);
        poseStack.mulPoseMatrix(minecraft.vrPlayer.vrdata_world_render.getEye(renderPass).getMatrix().toMCMatrix());
        scale = scale * minecraft.vrPlayer.vrdata_world_render.worldScale;
        poseStack.scale(scale, scale, scale);

        if (debug)
        {
        	poseStack.rotateDeg(180.0F, 0.0F, 1.0F, 0.0F);
            minecraft.gameRenderer.renderDebugAxes(0, 0, 0, 0.08F);
            poseStack.rotateDeg(180.0F, 0.0F, 1.0F, 0.0F);
        }

        poseStack.translate(offsetX, offsetY, offsetZ);
        RenderSystem.applyModelViewMatrix();

        BlockPos blockpos = new BlockPos(minecraft.vrPlayer.vrdata_world_render.getEye(renderPass).getPosition());
        int i = Utils.getCombinedLightWithMin(minecraft.level, blockpos, 0);

        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        
        if (minecraft.level != null)
            RenderSystem.setShader(GameRenderer::getRendertypeCutoutShader);
        else
            RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
        
        minecraft.gameRenderer.lightTexture().turnOnLightLayer();

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(Mode.QUADS, DefaultVertexFormat.BLOCK);
        minecraft.getBlockRenderer().getModelRenderer().renderModel((new PoseStack()).last(), bufferbuilder, (BlockState)null, minecraft.getModelManager().getModel(model), 1.0F, 1.0F, 1.0F, i, OverlayTexture.NO_OVERLAY);
    	BufferUploader.drawWithShader(bufferbuilder.end());

        minecraft.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.disableBlend();

        displayBindFunc.run();
        RenderSystem.setShader(GameRenderer::getRendertypeCutoutShader);
        BufferBuilder bufferbuilder1 = Tesselator.getInstance().getBuilder();
        bufferbuilder1.begin(Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (BakedQuad bakedquad : minecraft.getModelManager().getModel(displayModel).getQuads((BlockState)null, (Direction)null, random))
        {
            if (displayFaceFunc.apply(bakedquad.getDirection()) != VRWidgetHelper.DisplayFace.NONE && bakedquad.getSprite().getName().equals(new ResourceLocation("vivecraft:transparent")))
            {
            	QuadBounds quadbounds = bakedquad.getQuadBounds();
            	boolean flag = displayFaceFunc.apply(bakedquad.getDirection()) == VRWidgetHelper.DisplayFace.MIRROR;
                int j = LightTexture.pack(15, 15);
                bufferbuilder1.vertex(flag ? (double)quadbounds.getMaxX() : (double)quadbounds.getMinX(), (double)quadbounds.getMinY(), (double)quadbounds.getMinZ()).color(1.0F, 1.0F, 1.0F, 1.0F).uv(flag ? 1.0F : 0.0F, 0.0F).uv2(j).normal(0.0F, 0.0F, flag ? -1.0F : 1.0F).endVertex();
                bufferbuilder1.vertex(flag ? (double)quadbounds.getMinX() : (double)quadbounds.getMaxX(), (double)quadbounds.getMinY(), (double)quadbounds.getMinZ()).color(1.0F, 1.0F, 1.0F, 1.0F).uv(flag ? 0.0F : 1.0F, 0.0F).uv2(j).normal(0.0F, 0.0F, flag ? -1.0F : 1.0F).endVertex();
                bufferbuilder1.vertex(flag ? (double)quadbounds.getMinX() : (double)quadbounds.getMaxX(), (double)quadbounds.getMaxY(), (double)quadbounds.getMinZ()).color(1.0F, 1.0F, 1.0F, 1.0F).uv(flag ? 0.0F : 1.0F, 1.0F).uv2(j).normal(0.0F, 0.0F, flag ? -1.0F : 1.0F).endVertex();
                bufferbuilder1.vertex(flag ? (double)quadbounds.getMaxX() : (double)quadbounds.getMinX(), (double)quadbounds.getMaxY(), (double)quadbounds.getMinZ()).color(1.0F, 1.0F, 1.0F, 1.0F).uv(flag ? 1.0F : 0.0F, 1.0F).uv2(j).normal(0.0F, 0.0F, flag ? -1.0F : 1.0F).endVertex();
            }
        }
        
    	BufferUploader.drawWithShader(bufferbuilder1.end());
        RenderSystem.enableBlend();
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public static enum DisplayFace
    {
        NONE,
        NORMAL,
        MIRROR;
    }
}
