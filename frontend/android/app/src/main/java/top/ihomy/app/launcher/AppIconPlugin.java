package top.ihomy.app.launcher;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "IhomyAppIcon")
public class AppIconPlugin extends Plugin {
    private enum Icon {
        WINDOW_DARK("window-dark", "WindowDarkLauncher"),
        WINDOW_LIGHT("window-light", "WindowLightLauncher"),
        LEAF_DARK("leaf-dark", "LeafDarkLauncher"),
        LEAF_LIGHT("leaf-light", "LeafLightLauncher");

        final String id;
        final String alias;

        Icon(String id, String alias) {
            this.id = id;
            this.alias = alias;
        }
    }

    private ComponentName component(Icon icon) {
        return ComponentName.createRelative(getContext(), "." + icon.alias);
    }

    private boolean isEnabled(Icon icon) {
        int state = getContext().getPackageManager().getComponentEnabledSetting(component(icon));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == Icon.WINDOW_DARK);
    }

    private static void resolve(PluginCall call, Icon icon) {
        JSObject selection = new JSObject();
        selection.put("id", icon.id);
        call.resolve(selection);
    }

    @PluginMethod
    public void getIcon(PluginCall call) {
        for (Icon icon : Icon.values()) {
            if (isEnabled(icon)) {
                resolve(call, icon);
                return;
            }
        }
        call.reject("Launcher icon unavailable");
    }

    @PluginMethod
    public void setIcon(PluginCall call) {
        String id = call.getString("id");
        for (Icon icon : Icon.values()) {
            if (icon.id.equals(id)) {
                try {
                    switchTo(icon);
                    resolve(call, icon);
                } catch (RuntimeException error) {
                    call.reject("Could not change the launcher icon", error);
                }
                return;
            }
        }
        call.reject("Unknown launcher icon");
    }

    private void switchTo(Icon selected) {
        PackageManager manager = getContext().getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<PackageManager.ComponentEnabledSetting> changes = new ArrayList<>();
            for (Icon icon : Icon.values()) {
                boolean enable = icon == selected;
                if (enable != isEnabled(icon)) {
                    int state = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    changes.add(new PackageManager.ComponentEnabledSetting(
                        component(icon), state, PackageManager.DONT_KILL_APP));
                }
            }
            if (!changes.isEmpty()) manager.setComponentEnabledSettings(changes);
            return;
        }

        // Android 10–12 cannot switch atomically. Keep a working launcher at every step.
        if (!isEnabled(selected)) {
            manager.setComponentEnabledSetting(component(selected),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        for (Icon icon : Icon.values()) {
            if (icon != selected && isEnabled(icon)) {
                manager.setComponentEnabledSetting(component(icon),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }
}
