package decok.dfcdvadstf.soundswitcher.mixin;

import decok.dfcdvadstf.soundswitcher.SoundSwitcher;
import decok.dfcdvadstf.soundswitcher.audio.AudioDeviceMonitor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for GuiScreenOptionsSounds to add audio output device button
 * Implements DeviceChangeListener to respond to real-time device changes
 */
@Mixin(GuiScreenOptionsSounds.class)
public abstract class GuiScreenOptionsSoundsMixin extends GuiScreen
        implements AudioDeviceMonitor.DeviceChangeListener {

    private static final int AUDIO_DEVICE_BUTTON_ID = 300;
    private GuiButton audioDeviceButton;
    private volatile boolean deviceListChanged = false;

    /**
     * Inject into initGui to add our custom button
     */
    @Inject(method = "initGui", at = @At("TAIL"))
    private void onInitGui(CallbackInfo ci) {
        // 注册为设备变化监听器（先取消旧注册，防止重复）
        if (SoundSwitcher.getDeviceMonitor() != null) {
            SoundSwitcher.getDeviceMonitor().removeDeviceChangeListener(this);
            SoundSwitcher.getDeviceMonitor().addDeviceChangeListener(this);
            
            // 立即触发设备扫描，确保打开界面时显示最新设备状态
            SoundSwitcher.getDeviceMonitor().forceCheckImmediately();
        }
        
        // Add button below the "Done" button
        // The Done button is at: this.width / 2 - 100, this.height / 6 + 168
        int buttonY = this.height / 6 + 144; // 24 pixels above Done button
        
        String buttonText = getAudioDeviceButtonText();
        
        audioDeviceButton = new GuiButton(AUDIO_DEVICE_BUTTON_ID, 
            this.width / 2 - 100, buttonY, 200, 20, buttonText);
        
        @SuppressWarnings("unchecked")
        java.util.List<GuiButton> buttons = this.buttonList;
        buttons.add(audioDeviceButton);
    }

    /**
     * DeviceChangeListener 回调 - 设备列表变化时触发
     * 由 AudioDeviceMonitor 的后台线程调用，只设标志位，
     * 实际 UI 更新在 drawScreen 的渲染线程中完成
     */
    @Override
    public void onDeviceListChanged() {
        deviceListChanged = true;
    }

    /**
     * Inject into actionPerformed to handle our button click
     */
    @Inject(method = "actionPerformed", at = @At("TAIL"))
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.enabled && button.id == AUDIO_DEVICE_BUTTON_ID) {
            // 切换到下一个音频输出设备
            switchToNextAudioDevice();
        }
    }

    /**
     * 切换到下一个音频输出设备
     */
    private void switchToNextAudioDevice() {
        if (SoundSwitcher.getDeviceMonitor() != null) {
            String newDevice = SoundSwitcher.getDeviceMonitor().switchToNextPlaybackDevice();
            if (newDevice != null) {
                // 播放按钮点击音效
                GuiScreenOptionsSounds soundScreen = (GuiScreenOptionsSounds) (Object) this;
                soundScreen.mc.getSoundHandler().playSound(
                    net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
                        new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                
                // 刷新按钮显示
                refreshAudioDeviceButton();
            }
        }
    }

    /**
     * Inject into drawScreen to update button text dynamically
     */
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // 如果设备列表有变化（由后台线程通知），刷新按钮文字
        if (deviceListChanged) {
            deviceListChanged = false;
            refreshAudioDeviceButton();
        }
        
        // 持续检查当前设备名是否有变化
        if (audioDeviceButton != null) {
            String currentText = getAudioDeviceButtonText();
            if (!audioDeviceButton.displayString.equals(currentText)) {
                audioDeviceButton.displayString = currentText;
            }
        }
    }

    /**
     * Get the text for the audio device button
     * Format: "sound output device: [device name]"
     * Uses localization key: soundswitcher.gui.audio_output_device
     */
    private String getAudioDeviceButtonText() {
        String prefix = I18n.format("soundswitcher.gui.audio_output_device", new Object[0]);
        String deviceName = getCurrentAudioDeviceName();
        return prefix + "[" + deviceName + "]";
    }

    /**
     * Get the name of the current selected audio output device
     */
    private String getCurrentAudioDeviceName() {
        try {
            if (SoundSwitcher.getDeviceMonitor() != null) {
                String deviceName = SoundSwitcher.getDeviceMonitor().getCurrentPlaybackDeviceName();
                if (deviceName != null) {
                    return deviceName;
                }
            }
        } catch (Exception e) {
            // Fall back to unknown
        }
        
        return I18n.format("soundswitcher.gui.unknown_device", new Object[0]);
    }

    /**
     * Refresh the audio device button text
     */
    private void refreshAudioDeviceButton() {
        if (audioDeviceButton != null) {
            audioDeviceButton.displayString = getAudioDeviceButtonText();
        }
    }
}
