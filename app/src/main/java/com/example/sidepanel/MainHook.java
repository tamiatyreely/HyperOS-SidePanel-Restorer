package com.example.sidepanel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * HyperOS 4 SidePanel diagnostic build.
 * This build ONLY records candidate native methods. It does not alter gestures,
 * views, services, app allowlists, or SystemUI behavior.
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "[SidePanelRestorer] ";
    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.miui.securitycenter",
            "com.android.systemui",
            "miui.systemui.plugin"
    ));
    private static final String[] TARGET_CLASSES = {
            "com.miui.gamebooster.service.VideoToolBoxService",
            "com.miui.gamebooster.service.GameBoosterService",
            "com.miui.gamebooster.service.IVideoToolBox",
            "com.miui.gamebooster.service.GameBoosterServices"
    };
    private static final Set<String> TARGET_METHODS = new HashSet<>(Arrays.asList(
            "showSidebarDragTips",
            "showSlideBarSlidOutTipsIfNeed",
            "handleMovingSidebarLine",
            "showBubble",
            "conditionStartRegionSamling",
            "startVideoToolBoxService"
    ));

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) return;

        XposedBridge.log(TAG + "loaded: " + lpparam.packageName
                + " process=" + lpparam.processName);

        if (!"com.miui.securitycenter".equals(lpparam.packageName)) return;

        for (String className : TARGET_CLASSES) {
            inspectClass(lpparam, className);
        }
    }

    private static void inspectClass(XC_LoadPackage.LoadPackageParam lp, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, lp.classLoader);
            XposedBridge.log(TAG + "FOUND CLASS " + className);
            for (final Method method : clazz.getDeclaredMethods()) {
                if (!TARGET_METHODS.contains(method.getName())
                        && !method.getName().toLowerCase().contains("sidebar")
                        && !method.getName().toLowerCase().contains("toolbox")) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.log(TAG + "METHOD " + describe(method));
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "CALL " + describe(method));
                    }
                });
            }
        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + "CLASS NOT FOUND " + className);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "INSPECT ERROR " + className + ": " + t);
        }
    }

    private static String describe(Method m) {
        StringBuilder out = new StringBuilder();
        out.append(Modifier.toString(m.getModifiers())).append(' ')
                .append(m.getReturnType().getName()).append(' ')
                .append(m.getDeclaringClass().getName()).append('#')
                .append(m.getName()).append('(');
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) out.append(", ");
            out.append(params[i].getName());
        }
        return out.append(')').toString();
    }
}
