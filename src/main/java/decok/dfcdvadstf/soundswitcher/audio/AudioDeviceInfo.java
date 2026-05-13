package decok.dfcdvadstf.soundswitcher.audio;

/**
 * 音频设备信息类
 * 简化版，只存储设备名称（类似Windows声音设置）
 */
public class AudioDeviceInfo {

    private final String name;
    private final boolean isDefault;
    private final DeviceType type;

    public enum DeviceType {
        PLAYBACK("输出设备"),
        RECORDING("输入设备");

        private final String displayName;

        DeviceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public AudioDeviceInfo(String name, boolean isDefault, DeviceType type) {
        this.name = name;
        this.isDefault = isDefault;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public DeviceType getType() {
        return type;
    }

    /**
     * 简洁输出格式，类似Windows
     * 例如: "扬声器 (Realtek(R) Audio)" 或 "扬声器 (Realtek(R) Audio) [默认]"
     */
    @Override
    public String toString() {
        if (isDefault) {
            return name + " [默认]";
        }
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AudioDeviceInfo that = (AudioDeviceInfo) obj;
        return name.equals(that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + type.hashCode();
    }
}
