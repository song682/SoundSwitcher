package decok.dfcdvadstf.soundswitcher;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import decok.dfcdvadstf.soundswitcher.audio.AudioDeviceMonitor;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@Mod(modid = Tags.MODID, name = Tags.NAME, version = Tags.VERSION, acceptableRemoteVersions = Tags.ACCEPTED_VERSION, acceptedMinecraftVersions = Tags.ACCEPTED_VERSION, useMetadata = true)
public class SoundSwitcher {

    public static Logger logger = LogManager.getLogger(Tags.NAME);

    // 音频设备监控器
    private static AudioDeviceMonitor deviceMonitor;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("PreInitializing the Sound Switcher.");

        // 初始化音频设备监控器
        deviceMonitor = new AudioDeviceMonitor();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("Sound Switcher Mod loaded successfully");

        // 启动音频设备监控器
        if (deviceMonitor != null) {
            deviceMonitor.start();
            logger.info("音频设备监控器已启动，设备变化将记录到 audio_devices.log");
        }
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        // 停止音频设备监控器
        if (deviceMonitor != null && deviceMonitor.isRunning()) {
            deviceMonitor.stop();
            logger.info("音频设备监控器已停止");
        }
    }

    /**
     * 获取音频设备监控器实例
     */
    public static AudioDeviceMonitor getDeviceMonitor() {
        return deviceMonitor;
    }
}
