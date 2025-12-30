package cgytrus.delightmap.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow
    private boolean updateLightTexture;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private DynamicTexture lightTexture;

    @Shadow
    private float blockLightRedFlicker;

    @Shadow
    protected abstract float getDarknessGamma(float partialTick);

    @Shadow
    protected abstract float calculateDarknessScale(LivingEntity entity, float gamma, float partialTick);

    @Shadow
    @Final
    private GameRenderer renderer;

    @Shadow
    @Final
    private NativeImage lightPixels;

    /**
     * @author ConfiG
     * @reason Completely replaces the vanilla lightmap by design
     */
    @Overwrite
    public void updateLightTexture(float partialTicks) {
        if (!this.updateLightTexture)
            return;

        ClientLevel level = this.minecraft.level;
        LocalPlayer player = this.minecraft.player;
        if (level == null || player == null)
            return;

        this.updateLightTexture = false;

        this.minecraft.getProfiler().push("lightTex");

        float skyDarken = level.getSkyDarken(1.0f);

        float ambientLightFactor = level.dimensionType().ambientLight();
        float skyFactor = level.getSkyFlashTime() > 0 ? 1.0f : skyDarken * 0.95f + 0.05f;
        boolean useBrightLightmap = level.effects().forceBrightLightmap();
        float blockFactor = useBrightLightmap ? 1.4f : (this.blockLightRedFlicker + 1.5f);
        float nightVisionFactor = 0.0f;
        Vector3f skyLightColor = new Vector3f(skyDarken, skyDarken, 1.0f).lerp(new Vector3f(1.0f), 0.35f);
        float darknessScale;
        float darkenWorldFactor = Math.max(0.0f, this.renderer.getDarkenWorldAmount(partialTicks));
        float brightnessFactor;

        float waterVision = player.getWaterVision();
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            nightVisionFactor = GameRenderer.getNightVisionScale(player, partialTicks);
        }
        else if (waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER)) {
            nightVisionFactor = waterVision;
        }
        nightVisionFactor = Math.max(0.0f, nightVisionFactor);

        float darknessEffectScale = this.minecraft.options.darknessEffectScale().get().floatValue();
        float scaledDarknessGamma = this.getDarknessGamma(partialTicks) * darknessEffectScale;
        darknessScale = this.calculateDarknessScale(player, scaledDarknessGamma, partialTicks) * darknessEffectScale;

        brightnessFactor = Math.max(0.0f, this.minecraft.options.gamma().get().floatValue() - scaledDarknessGamma);

        Vector3f color = new Vector3f();
        Vector3f temp = new Vector3f();
        for(int y = 0; y < 16; ++y) {
            for (int x = 0; x < 16; ++x) {
                float blockLevel = (x + 0.5f) / 16.0f;
                float curvedBlockLevel = blockLevel / (3.0f - 2.0f * blockLevel);
                float blockBrightness = Mth.lerp(ambientLightFactor, Mth.clamp(curvedBlockLevel - 0.05f, 0.0f, 1.0f), 1.0f) * blockFactor;

                float skyLevel = (y + 0.5f) / 16.0f + 0.11f;
                skyLevel -= 0.3f;
                float curvedSkyLevel = (skyLevel * 3.1f) / (10.0f - 9.0f * 1.3f * skyLevel);
                float skyBrightness = Mth.lerp(ambientLightFactor, Mth.clamp(curvedSkyLevel, 0.0f, 1.0f), 1.0f) * skyFactor;

                if (useBrightLightmap) {
                    float adjustedBlockBrightness = Mth.clamp(blockBrightness - 0.2f, 0.05f, 0.7f);
                    color.set(
                        adjustedBlockBrightness * Math.fma(adjustedBlockBrightness * adjustedBlockBrightness, 0.2f, 0.8f) - 0.03f,
                        adjustedBlockBrightness * Math.fma(adjustedBlockBrightness, 0.2f, 0.8f),
                        adjustedBlockBrightness
                    );
                }
                else {
                    color.set(
                        blockBrightness,
                        blockBrightness * Math.fma(blockBrightness * blockBrightness, 0.39f, 0.61f),
                        blockBrightness * Math.fma(blockBrightness * blockBrightness, 0.88f, 0.12f)
                    );
                }

                if (useBrightLightmap) {
                    color.add(0.4f, 0.4f, 0.4f);
                }
                else {
                    color.add(
                        skyBrightness * Math.fma(skyBrightness * skyBrightness, 0.25f, 0.75f),
                        skyBrightness * Math.fma(skyBrightness * skyBrightness, 0.2f, 0.8f),
                        skyBrightness
                    );
                    color.mul(skyLightColor);
                }

                Vector3f darkenedColor = temp.set(color).mul(0.7f, 0.6f, 0.6f);
                color.lerp(darkenedColor, darkenWorldFactor);

                // adjust for night vision effect
                if (nightVisionFactor > 0.0f) {
                    Vector3f one = temp.set(1.0f);
                    color.lerp(one, nightVisionFactor);
                }
                // adjust for darkness effect
                if (darknessScale > 0.0f) {
                    color.sub(temp.set(Math.min(0.9f, darknessScale)));
                    color.x = Mth.clamp(color.x, 0.0f, 1.0f);
                    color.y = Mth.clamp(color.y, 0.0f, 1.0f);
                    color.z = Mth.clamp(color.z, 0.0f, 1.0f);
                }

                // adjust for brightness setting
                if (useBrightLightmap) {
                    color.add(temp.set((brightnessFactor * (brightnessFactor / 1.7f) - 0.3f) / 4.0f));
                }
                else {
                    color.add(temp.set((brightnessFactor - 0.2f) / 4.0f));
                }

                color.x = Mth.clamp(color.x, 0.0f, 1.0f);
                color.y = Mth.clamp(color.y, 0.0f, 1.0f);
                color.z = Mth.clamp(color.z, 0.0f, 1.0f);
                color.mul(255.0f);
                int r = (int)color.x;
                int g = (int)color.y;
                int b = (int)color.z;
                this.lightPixels.setPixelRGBA(x, y, FastColor.ABGR32.color(255, b, g, r));
            }
        }

        this.lightPixels.setPixelRGBA(15, 15, FastColor.ABGR32.color(255, 255, 255, 255));

        this.lightTexture.upload();
        this.minecraft.getProfiler().pop();
    }
}
