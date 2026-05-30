package decok.dfcdvadstf.soundswitcher.mixin;

import decok.dfcdvadstf.soundswitcher.SoundSwitcher;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.SoundSystemConfig;

import java.util.LinkedList;

/**
 * Mixin for SoundManager to force LibraryJavaSound as the primary audio library.
 *
 * Minecraft 1.7.10 uses LibraryLWJGLOpenAL by default, which makes
 * LibraryJavaSound.setMixer() useless (instance == null, no channel migration).
 * This Mixin inserts LibraryJavaSound at the front of the library list
 * after the constructor finishes, so SoundSystem tries it before OpenAL.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    /**
     * Inject at the end of the SoundManager constructor.
     * By now addLibrary(LibraryLWJGLOpenAL.class) has already been called,
     * so we prepend LibraryJavaSound to make it the first-tried library.
     */
    @Inject(method = "<init>(Lnet/minecraft/client/audio/SoundHandler;Lnet/minecraft/client/settings/GameSettings;)V",
            at = @At("TAIL"))
    private void onConstructTail(SoundHandler handler, GameSettings settings, CallbackInfo ci) {
        try {
            Class<?> javaSoundLib = Class.forName("paulscode.sound.libraries.LibraryJavaSound");
            LinkedList<Class> libs = SoundSystemConfig.getLibraries();
            if (libs != null) {
                // Remove if already present (shouldn't be, but just in case)
                libs.remove(javaSoundLib);
                // Add to front so SoundSystem tries it before OpenAL
                libs.addFirst(javaSoundLib);
                SoundSwitcher.logger.info("LibraryJavaSound set as primary audio library");
            }
        } catch (Exception e) {
            SoundSwitcher.logger.warn("Failed to configure LibraryJavaSound as primary library", e);
        }
    }
}
