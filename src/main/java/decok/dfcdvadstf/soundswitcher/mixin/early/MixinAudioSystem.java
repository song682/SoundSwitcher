package decok.dfcdvadstf.soundswitcher.mixin.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.sound.sampled.Mixer;
import java.util.ArrayList;
import java.util.List;

/**
 * Early Mixin for AudioSystem
 * 修复音频设备名称乱码问题和过滤虚拟设备
 */
@Mixin(value = javax.sound.sampled.AudioSystem.class, remap = false)
public class MixinAudioSystem {

    /**
     * 修复 getMixerInfo 返回的设备信息
     * 过滤掉不需要的虚拟设备
     */
    @Inject(method = "getMixerInfo", at = @At("RETURN"), cancellable = true)
    private static void onGetMixerInfo(CallbackInfoReturnable<Mixer.Info[]> cir) {
        Mixer.Info[] original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        List<Mixer.Info> filtered = new ArrayList<>();
        
        for (Mixer.Info info : original) {
            String name = info.getName();
            
            // 跳过 null 或空名称
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            
            // 过滤虚拟设备
            if (shouldFilterDevice(name)) {
                System.out.println("[SoundSwitcher] 过滤虚拟设备: " + name);
                continue;
            }
            
            // 修复编码问题 - 如果名称包含乱码特征，尝试修复
            String fixedName = fixEncoding(name);
            if (!fixedName.equals(name)) {
                System.out.println("[SoundSwitcher] 修复设备名称: " + name + " -> " + fixedName);
                // 创建新的 Mixer.Info 替换原来的
                info = createFixedMixerInfo(info, fixedName);
            }
            
            filtered.add(info);
        }
        
        cir.setReturnValue(filtered.toArray(new Mixer.Info[0]));
    }

    /**
     * 判断是否需要过滤掉该设备
     */
    private static boolean shouldFilterDevice(String deviceName) {
        String lowerName = deviceName.toLowerCase();
        
        // 扩展的过滤列表（与 AudioDeviceMonitor 保持一致）
        String[] filters = {
            "primary sound capture",   // 主录音设备
            "port",                     // MIDI 端口
            "microsoft gs wavetable",  // MIDI 合成器
            "java sound audio engine", // Java 虚拟引擎
            "unknown",                  // 未知设备
            "default",                  // 默认占位符
            "sndvol",                   // 音量控制虚拟设备
            "stereomix",               // 立体声混音（录音设备）
            "what u hear",             // 录音设备
            "wave",                    // 波形设备
            "aux",                     // 辅助设备
            "cd",                      // CD 设备
            "line in",                 // 线路输入
            "microphone",              // 麦克风输入
            "phone",                   // 电话设备
            "speaker",                 // 可能是虚拟扬声器
            "麦克风",                   // 中文麦克风
            "线路输入",                 // 中文线路输入
            "立体声混音",               // 中文立体声混音
        };
        
        for (String filter : filters) {
            if (lowerName.contains(filter)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 尝试修复设备名称的编码问题
     */
    private static String fixEncoding(String name) {
        // 如果名称已经是正常的 ASCII 或常见字符，直接返回
        if (isValidName(name)) {
            return name;
        }
        
        // 尝试修复常见的编码问题
        // 1. 替换乱码字符
        String fixed = name
            .replace("��", "")  // 替换未知字符
            .replace("�", "")   // 替换替换字符
            .replaceAll("[^\\x20-\\x7E\\u4e00-\\u9fa5]", ""); // 只保留 ASCII 可打印字符和中文
        
        return fixed.trim();
    }

    /**
     * 检查名称是否有效（不包含明显的乱码）
     */
    private static boolean isValidName(String name) {
        // 检查是否包含乱码特征字符
        for (char c : name.toCharArray()) {
            // 如果是控制字符（除了常见的制表符、换行等），可能是乱码
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
            // 如果是替换字符，说明有编码问题
            if (c == '\uFFFD') {
                return false;
            }
        }
        return true;
    }

    /**
     * 创建修复后的 Mixer.Info
     * 由于 Mixer.Info 是抽象类，我们需要创建一个新的实现
     */
    private static Mixer.Info createFixedMixerInfo(Mixer.Info original, String fixedName) {
        return new Mixer.Info(
            fixedName,
            original.getVendor(),
            original.getDescription(),
            original.getVersion()
        ) {};
    }
}
