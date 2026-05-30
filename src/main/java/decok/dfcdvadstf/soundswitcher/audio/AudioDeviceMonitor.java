package decok.dfcdvadstf.soundswitcher.audio;

import decok.dfcdvadstf.soundswitcher.SoundSwitcher;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 音频设备监控器
 * 利用项目中已有的音频库（paulscode、IBXM）使用的 JavaSound API
 * 实时检测系统中接入的音频输出设备
 * 不显示在游戏内，而是记录到独立的日志文件
 */
public class AudioDeviceMonitor {

    /**
     * 设备变化监听器接口
     * GUI 等组件注册此回调以实时响应设备变化
     */
    public interface DeviceChangeListener {
        void onDeviceListChanged();
    }

    // 检查间隔（毫秒）- 1秒实现近乎实时的检测
    private static final long CHECK_INTERVAL = 1000;
    // 日志文件路径
    private static final String LOG_FILE_NAME = "audio_devices.log";
    // 最大日志文件大小（字节）- 1MB
    private static final long MAX_LOG_SIZE = 1024 * 1024;

    // 音频格式 - 与 IBXM 和 paulscode 使用的一致
    private static final AudioFormat DEFAULT_FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false);

    private final Timer monitorTimer;
    private final File logFile;
    private Set<AudioDeviceInfo> lastPlaybackDevices;
    private Set<AudioDeviceInfo> lastRecordingDevices;
    private boolean isRunning;
    private final Object lock = new Object();

    // 设备切换相关
    private List<AudioDeviceInfo> playbackDeviceList;
    private int currentPlaybackDeviceIndex;
    private String preferredPlaybackDeviceName; // 用户偏好的设备名称

    // 设备变化监听器列表
    private final List<DeviceChangeListener> deviceChangeListeners = new ArrayList<>();

    public AudioDeviceMonitor() {
        this.monitorTimer = new Timer("AudioDeviceMonitor", true);
        this.logFile = new File(LOG_FILE_NAME);
        this.lastPlaybackDevices = new HashSet<>();
        this.lastRecordingDevices = new HashSet<>();
        this.isRunning = false;
        this.playbackDeviceList = new ArrayList<>();
        this.currentPlaybackDeviceIndex = 0;
        this.preferredPlaybackDeviceName = null;
    }

    /**
     * 注册设备变化监听器
     */
    public void addDeviceChangeListener(DeviceChangeListener listener) {
        synchronized (deviceChangeListeners) {
            if (!deviceChangeListeners.contains(listener)) {
                deviceChangeListeners.add(listener);
            }
        }
    }

    /**
     * 移除设备变化监听器
     */
    public void removeDeviceChangeListener(DeviceChangeListener listener) {
        synchronized (deviceChangeListeners) {
            deviceChangeListeners.remove(listener);
        }
    }

    /**
     * 通知所有监听器设备列表已变化
     */
    private void notifyDeviceChangeListeners() {
        synchronized (deviceChangeListeners) {
            for (DeviceChangeListener listener : deviceChangeListeners) {
                try {
                    listener.onDeviceListChanged();
                } catch (Exception e) {
                    SoundSwitcher.logger.error("Failed to notify device change listener: ", e);
                }
            }
        }
    }

    /**
     * 启动监控器
     */
    public void start() {
        synchronized (lock) {
            if (isRunning) {
                SoundSwitcher.logger.warn("Audio device monitor is already running");
                return;
            }

            isRunning = true;
            SoundSwitcher.logger.info("Audio device monitor started, check interval: " + CHECK_INTERVAL + "ms");

            // 立即执行一次检测
            performCheck();

            // 启动定时任务
            monitorTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    performCheck();
                }
            }, CHECK_INTERVAL, CHECK_INTERVAL);
        }
    }

    /**
     * 停止监控器
     */
    public void stop() {
        synchronized (lock) {
            if (!isRunning) {
                return;
            }

            isRunning = false;
            monitorTimer.cancel();
            SoundSwitcher.logger.info("Audio device monitor has been terminated");
        }
    }

    /**
     * 执行一次设备检测
     */
    private void performCheck() {
        performCheckInternal(false);
    }

    /**
     * 立即强制执行一次设备检测（供 GUI 打开时调用）
     * @param forceUpdate 即使设备没变化也强制更新播放设备列表
     */
    public void forceCheckImmediately() {
        performCheckInternal(true);
    }

    /**
     * 内部检测逻辑
     */
    private void performCheckInternal(boolean forceUpdate) {
        try {
            Set<AudioDeviceInfo> currentPlaybackDevices = getPlaybackDevices();
            Set<AudioDeviceInfo> currentRecordingDevices = getRecordingDevices();

            boolean playbackChanged = !currentPlaybackDevices.equals(lastPlaybackDevices);
            boolean recordingChanged = !currentRecordingDevices.equals(lastRecordingDevices);

            if (playbackChanged || recordingChanged || forceUpdate) {
                if (playbackChanged || recordingChanged) {
                    logDeviceChanges(currentPlaybackDevices, currentRecordingDevices);
                }
                lastPlaybackDevices = currentPlaybackDevices;
                lastRecordingDevices = currentRecordingDevices;
                
                // 更新播放设备列表
                updatePlaybackDeviceList(currentPlaybackDevices);
                
                // 通知监听器（GUI 等）
                notifyDeviceChangeListeners();
            }
        } catch (Exception e) {
            SoundSwitcher.logger.error("Failed to detect audio devices", e);
        }
    }

    /**
     * 更新播放设备列表
     */
    private void updatePlaybackDeviceList(Set<AudioDeviceInfo> devices) {
        synchronized (lock) {
            // 保存当前设备名称
            String currentDeviceName = null;
            if (!playbackDeviceList.isEmpty() && currentPlaybackDeviceIndex < playbackDeviceList.size()) {
                currentDeviceName = playbackDeviceList.get(currentPlaybackDeviceIndex).getName();
            }
            
            // 重新构建列表
            playbackDeviceList.clear();
            playbackDeviceList.addAll(devices);
            
            // 按名称排序，确保顺序一致
            Collections.sort(playbackDeviceList, (d1, d2) -> d1.getName().compareTo(d2.getName()));
            
            // 恢复之前的索引
            if (currentDeviceName != null) {
                for (int i = 0; i < playbackDeviceList.size(); i++) {
                    if (playbackDeviceList.get(i).getName().equals(currentDeviceName)) {
                        currentPlaybackDeviceIndex = i;
                        break;
                    }
                }
            } else if (!playbackDeviceList.isEmpty()) {
                // 如果没有之前的设备，选择默认设备
                for (int i = 0; i < playbackDeviceList.size(); i++) {
                    if (playbackDeviceList.get(i).isDefault()) {
                        currentPlaybackDeviceIndex = i;
                        break;
                    }
                }
            }
        }
    }

    /**
     * 获取所有播放设备（输出设备）
     * 利用与 IBXM、 paulscode 相同的 JavaSound API
     * 优化：不再逐个 open 线路测试（太慢，尤其蓝牙），
     * 依靠 isLineSupported() + 名称过滤来判断
     */
    private Set<AudioDeviceInfo> getPlaybackDevices() {
        Set<AudioDeviceInfo> devices = new HashSet<>();

        try {
            // 获取所有混音器信息 - 这是 JavaSound 的标准入口
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            
            for (Mixer.Info mixerInfo : mixerInfos) {
                try {
                    String mixerName = mixerInfo.getName();
                    
                    // 过滤掉常见的虚拟设备和无效设备
                    if (shouldFilterDevice(mixerName)) {
                        continue;
                    }
                    
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    
                    // 检查是否支持 SourceDataLine（播放输出）
                    // 与 IBXM Player 和 paulscode 使用的方式一致
                    DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, DEFAULT_FORMAT);
                    
                    if (mixer.isLineSupported(lineInfo)) {
                        boolean isDefault = isDefaultPlaybackDevice(mixerInfo);
                        AudioDeviceInfo device = new AudioDeviceInfo(
                            mixerName,
                            isDefault,
                            AudioDeviceInfo.DeviceType.PLAYBACK
                        );
                        devices.add(device);
                        
                        SoundSwitcher.logger.debug("Detected playback device: " + mixerName + 
                            (isDefault ? " [default]" : ""));
                    }
                } catch (Exception e) {
                    // 忽略无法访问的混音器
                }
            }
        } catch (Exception e) {
            SoundSwitcher.logger.error("Failed to get playback devices", e);
        }

        return devices;
    }

    /**
     * 判断是否过滤掉某些设备
     * 过滤掉虚拟设备和无效设备，与早期 Mixin 对齐
     */
    private boolean shouldFilterDevice(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            return true;
        }
        
        String lowerName = deviceName.toLowerCase();
        
        // 过滤掉常见的虚拟/系统/录音设备 - 与早期 Mixin 保持完全一致
        String[] filters = {
            "primary sound capture",   // 主录音设备（不是播放设备）
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
     * 获取所有录音设备（输入设备）
     * 利用 JavaSound API
     */
    private Set<AudioDeviceInfo> getRecordingDevices() {
        Set<AudioDeviceInfo> devices = new HashSet<>();

        try {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            
            for (Mixer.Info mixerInfo : mixerInfos) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    
                    // 检查是否支持 TargetDataLine（录音输入）
                    DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, DEFAULT_FORMAT);
                    
                    if (mixer.isLineSupported(lineInfo)) {
                        // 尝试实际打开线路来验证设备是否真正可用
                        boolean isActuallyAvailable = testRecordingDevice(mixer, lineInfo);
                        
                        if (isActuallyAvailable) {
                            boolean isDefault = isDefaultRecordingDevice(mixerInfo);
                            AudioDeviceInfo device = new AudioDeviceInfo(
                                mixerInfo.getName(),
                                isDefault,
                                AudioDeviceInfo.DeviceType.RECORDING
                            );
                            devices.add(device);
                        }
                    }
                } catch (Exception e) {
                    // 忽略无法访问的混音器
                }
            }
        } catch (Exception e) {
            SoundSwitcher.logger.error("Failed to get recording devices", e);
        }

        return devices;
    }

    /**
     * 测试播放设备是否真正可用
     * 模拟 IBXM 和 paulscode 实际使用设备的方式
     */
    private boolean testPlaybackDevice(Mixer mixer, DataLine.Info lineInfo) {
        SourceDataLine line = null;
        try {
            // 尝试从指定混音器获取线路
            line = (SourceDataLine) mixer.getLine(lineInfo);
            
            // 尝试打开线路（不实际播放，只是验证设备可用）
            // 这与 IBXM Player 的构造函数行为一致
            line.open(DEFAULT_FORMAT);
            
            // 如果能成功打开，说明设备可用
            return true;
        } catch (LineUnavailableException e) {
            // 设备不可用（可能被其他应用占用）
            return false;
        } catch (Exception e) {
            // 其他错误
            return false;
        } finally {
            // 确保关闭线路
            if (line != null) {
                try {
                    line.close();
                } catch (Exception e) {
                    // 忽略关闭错误
                }
            }
        }
    }

    /**
     * 测试录音设备是否真正可用
     */
    private boolean testRecordingDevice(Mixer mixer, DataLine.Info lineInfo) {
        TargetDataLine line = null;
        try {
            line = (TargetDataLine) mixer.getLine(lineInfo);
            line.open(DEFAULT_FORMAT);
            return true;
        } catch (LineUnavailableException e) {
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (line != null) {
                try {
                    line.close();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 判断是否为默认播放设备
     * 使用 AudioSystem.getSourceDataLine() 获取系统默认线路
     */
    private boolean isDefaultPlaybackDevice(Mixer.Info mixerInfo) {
        try {
            // 优先检查 "Java Sound Audio Engine"（paulscode 的默认选择）
            if ("Java Sound Audio Engine".equals(mixerInfo.getName())) {
                return true;
            }
            
            // 获取系统默认 SourceDataLine
            // 这与 IBXM 的 Player 构造函数使用的方式相同
            SourceDataLine defaultLine = AudioSystem.getSourceDataLine(DEFAULT_FORMAT);
            
            if (defaultLine != null) {
                // 获取默认线路的混音器
                Mixer defaultMixer = AudioSystem.getMixer(mixerInfo);
                
                // 检查默认混音器是否支持该线路
                if (defaultMixer.isLineSupported(new DataLine.Info(SourceDataLine.class, DEFAULT_FORMAT))) {
                    // 通过比较混音器名称来判断
                    return mixerInfo.getName().contains("Primary") || 
                           mixerInfo.getName().contains("默认") ||
                           mixerInfo.getName().contains("Default");
                }
            }
        } catch (Exception e) {
            // 无法确定默认设备
        }
        return false;
    }

    /**
     * 判断是否为默认录音设备
     */
    private boolean isDefaultRecordingDevice(Mixer.Info mixerInfo) {
        try {
            // 获取系统默认 TargetDataLine
            TargetDataLine defaultLine = AudioSystem.getTargetDataLine(DEFAULT_FORMAT);
            
            if (defaultLine != null) {
                Mixer defaultMixer = AudioSystem.getMixer(mixerInfo);
                
                if (defaultMixer.isLineSupported(new DataLine.Info(TargetDataLine.class, DEFAULT_FORMAT))) {
                    return mixerInfo.getName().contains("Primary") || 
                           mixerInfo.getName().contains("默认") ||
                           mixerInfo.getName().contains("Default");
                }
            }
        } catch (Exception e) {
            // 无法确定默认设备
        }
        return false;
    }

    /**
     * 记录设备变化到日志文件
     */
    private void logDeviceChanges(Set<AudioDeviceInfo> playbackDevices, 
                                   Set<AudioDeviceInfo> recordingDevices) {
        try {
            checkLogFileSize();

            PrintWriter writer = new PrintWriter(new FileWriter(logFile, true));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());

            writer.println("========================================");
            writer.println("Audio Device Detection - " + timestamp);
            writer.println("========================================");

            // 记录输出设备
            writer.println("\nPlayback Devices:\n");
            if (playbackDevices.isEmpty()) {
                writer.println("  (None)");
            } else {
                List<AudioDeviceInfo> sortedDevices = new ArrayList<>(playbackDevices);
                Collections.sort(sortedDevices, (d1, d2) -> {
                    if (d1.isDefault() != d2.isDefault()) {
                        return d1.isDefault() ? -1 : 1;
                    }
                    return d1.getName().compareTo(d2.getName());
                });

                for (AudioDeviceInfo device : sortedDevices) {
                    writer.println("  " + device.toString() + " (Playback)");
                }
            }

            // 记录输入设备
            writer.println("\nRecording Devices:");
            if (recordingDevices.isEmpty()) {
                writer.println("  (None)");
            } else {
                List<AudioDeviceInfo> sortedDevices = new ArrayList<>(recordingDevices);
                Collections.sort(sortedDevices, (d1, d2) -> {
                    if (d1.isDefault() != d2.isDefault()) {
                        return d1.isDefault() ? -1 : 1;
                    }
                    return d1.getName().compareTo(d2.getName());
                });

                for (AudioDeviceInfo device : sortedDevices) {
                    writer.println("  " + device.toString() + " (Recording)");
                }
            }

            writer.println("\n========================================\n");
            writer.close();

            SoundSwitcher.logger.info("Audio device changes have been logged to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            SoundSwitcher.logger.error("Failed to write audio device log", e);
        }
    }

    /**
     * 检查日志文件大小，如果过大则备份
     */
    private void checkLogFileSize() {
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            File backupFile = new File(LOG_FILE_NAME + ".old");
            if (backupFile.exists()) {
                backupFile.delete();
            }
            logFile.renameTo(backupFile);
            SoundSwitcher.logger.info("Audio device log file has been backed up");
        }
    }

    /**
     * 获取当前所有设备的快照
     */
    public Map<String, Set<AudioDeviceInfo>> getCurrentDevices() {
        Map<String, Set<AudioDeviceInfo>> result = new HashMap<>();
        result.put("playback", new HashSet<>(lastPlaybackDevices));
        result.put("recording", new HashSet<>(lastRecordingDevices));
        return result;
    }

    /**
     * 检查监控器是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    // ==================== 设备切换功能 ====================

    /**
     * 获取当前选中的播放设备名称
     * @return 当前设备名称，如果没有设备则返回 null
     */
    public String getCurrentPlaybackDeviceName() {
        synchronized (lock) {
            if (playbackDeviceList.isEmpty()) {
                return null;
            }
            if (currentPlaybackDeviceIndex >= playbackDeviceList.size()) {
                currentPlaybackDeviceIndex = 0;
            }
            return playbackDeviceList.get(currentPlaybackDeviceIndex).getName();
        }
    }

    /**
     * 获取所有可用的播放设备列表
     * @return 播放设备列表的副本
     */
    public List<AudioDeviceInfo> getPlaybackDeviceList() {
        synchronized (lock) {
            return new ArrayList<>(playbackDeviceList);
        }
    }

    /**
     * 切换到下一个播放设备
     * @return 切换后的设备名称，如果失败返回 null
     */
    public String switchToNextPlaybackDevice() {
        synchronized (lock) {
            if (playbackDeviceList.isEmpty()) {
                SoundSwitcher.logger.warn("No available playback devices");
                return null;
            }
            
            // 移动到下一个设备
            currentPlaybackDeviceIndex = (currentPlaybackDeviceIndex + 1) % playbackDeviceList.size();
            AudioDeviceInfo newDevice = playbackDeviceList.get(currentPlaybackDeviceIndex);
            
            // 记录用户偏好
            preferredPlaybackDeviceName = newDevice.getName();
            
            SoundSwitcher.logger.info("Switched to audio output device: " + newDevice.getName());
            
            // 尝试通过 paulscode 切换混音器
            switchMixer(newDevice);
            
            return newDevice.getName();
        }
    }

    /**
     * 切换到指定的播放设备
     * @param deviceName 设备名称
     * @return 是否切换成功
     */
    public boolean switchToPlaybackDevice(String deviceName) {
        synchronized (lock) {
            for (int i = 0; i < playbackDeviceList.size(); i++) {
                if (playbackDeviceList.get(i).getName().equals(deviceName)) {
                    currentPlaybackDeviceIndex = i;
                    preferredPlaybackDeviceName = deviceName;
                    
                    SoundSwitcher.logger.info("Switched to audio output device: " + deviceName);
                    
                    // 尝试通过 paulscode 切换混音器
                    switchMixer(playbackDeviceList.get(i));
                    
                    return true;
                }
            }
            SoundSwitcher.logger.warn("Failed to find audio device: " + deviceName + " (JavaSound)");
            return false;
        }
    }

    /**
     * 通过 paulscode LibraryJavaSound 切换混音器
     * 这是实际改变音频输出的关键方法
     */
    private void switchMixer(AudioDeviceInfo device) {
        try {
            // 获取 Mixer.Info
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            Mixer.Info targetMixerInfo = null;
            
            for (Mixer.Info info : mixerInfos) {
                if (info.getName().equals(device.getName())) {
                    targetMixerInfo = info;
                    break;
                }
            }
            
            if (targetMixerInfo == null) {
                SoundSwitcher.logger.error("Failed to find mixer: " + device.getName());
                return;
            }
            
            // 获取 Mixer
            Mixer mixer = AudioSystem.getMixer(targetMixerInfo);
            
            // 使用 paulscode 的 LibraryJavaSound 设置混音器
            // 这需要反射，因为 LibraryJavaSound 可能在运行时才可用
            switchMixerViaPaulscode(mixer);
            
        } catch (Exception e) {
            SoundSwitcher.logger.error("Failed to switch mixer: " + e.getMessage());
        }
    }

    /**
     * 尝试通过反射调用 paulscode LibraryJavaSound.setMixer()
     * LibraryJavaSound.setMixer() 内部会自动：
     *   1. 遍历所有已有 Channel，关闭旧线路
     *   2. 在新 mixer 上重建 Clip/SourceDataLine
     *   3. 重新 attach buffer
     * 所以我们不需要额外调用 stopSounds() 或 "刷新"——setMixer 本身就是 reload。
     */
    private void switchMixerViaPaulscode(Mixer mixer) {
        try {
            // 尝试加载 paulscode 的 LibraryJavaSound 类
            Class<?> libraryJavaSoundClass = Class.forName("paulscode.sound.libraries.LibraryJavaSound");
            
            // 调用 setMixer 方法（内部已自动处理所有 channel 重建）
            java.lang.reflect.Method setMixerMethod = libraryJavaSoundClass.getMethod("setMixer", Mixer.class);
            setMixerMethod.invoke(null, mixer);
            
            SoundSwitcher.logger.info("Successfully switched mixer via paulscode to: " + mixer.getMixerInfo().getName());
            
        } catch (ClassNotFoundException e) {
            // paulscode 不可用，尝试其他方式
            SoundSwitcher.logger.warn("paulscode LibraryJavaSound is unavailable, trying other methods");
            switchMixerViaJavaSound(mixer);
        } catch (Exception e) {
            SoundSwitcher.logger.error("Failed to switch mixer via paulscode: " + e.getMessage());
        }
    }

    /**
     * 通过 JavaSound 直接切换（备用方案）
     * 注意：这不会真正改变系统默认设备，只是记录偏好
     */
    private void switchMixerViaJavaSound(Mixer mixer) {
        // 这里我们只是记录用户的设备偏好
        // 由于 JavaSound 本身不支持切换系统默认设备
        // 因此这里没有实际切换设备
        SoundSwitcher.logger.info("Audio device preference recorded: " + mixer.getMixerInfo().getName());

    }
}
