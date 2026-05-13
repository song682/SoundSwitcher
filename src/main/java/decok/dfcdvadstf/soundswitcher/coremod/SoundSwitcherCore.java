package decok.dfcdvadstf.soundswitcher.coremod;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import io.github.tox1cozz.mixinbooterlegacy.IEarlyMixinLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * SoundSwitcher CoreMod
 * 用于加载 Early Mixin 和修复底层音频问题
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001) // 在 Mixin 之后加载
@IFMLLoadingPlugin.Name("SoundSwitcherCore")
public class SoundSwitcherCore implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // 注入数据
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    // ==================== IEarlyMixinLoader ====================

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList("mixins.soundswitcher.early.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return true;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        System.out.println("[SoundSwitcher] Early Mixin config queued: " + mixinConfig);
    }
}
