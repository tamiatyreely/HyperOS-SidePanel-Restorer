
package com.example.sidepanel;

import dalvik.system.DexFile;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * HyperOS SidePanel Restorer
 *
 * Diagnostic-only version.
 * Scans SecurityCenter DEX for sidebar-related classes
 * and records method calls for further analysis.
 */
public final class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[SP-NATIVE] ";

    private static final Set<String> HOOKED =
            ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS =
            new ConcurrentHashMap<>();

    private static final String[] IMPORTANT_CLASSES = {
            "com.miui.gamebooster.service.VideoToolBoxService",
            "com.miui.gamebooster.service.GameBoosterService",
            "com.miui.gamebooster.service.DockWindowManagerService",
            "com.miui.gamebooster.service.DockWindowManagerService$SidebarOverlayBinder"
    };

    private static final String[] METHOD_WORDS = {
            "sidebar",
            "toolbox",
            "slidebar",
            "drag",
            "region",
            "dock",
            "overlay",
            "show",
            "move",
            "line",
            "tips",
            "invoke",
            "gesture"
    };

    @Override
    public void handleLoadPackage(
            final XC_LoadPackage.LoadPackageParam lp) {

        if (!"com.miui.securitycenter".equals(lp.packageName)) {
            return;
        }

        XposedBridge.log(
                TAG + "SECURITYCENTER START process=" + lp.processName
        );

        for (String name : IMPORTANT_CLASSES) {
            hookNamedClass(lp, name);
        }

        scanDexForSidebarClasses(lp);
    }

    /**
     * Scan the main SecurityCenter APK DEX.
     *
     * Fixed: DexFile is not AutoCloseable in this
     * Android compilation environment.
     */
    private static void scanDexForSidebarClasses(
            XC_LoadPackage.LoadPackageParam lp) {

        String apk = lp.appInfo.sourceDir;
        DexFile dex = null;

        try {
            dex = new DexFile(apk);

            Enumeration<String> entries = dex.entries();
            int matched = 0;

            while (entries.hasMoreElements()) {

                String name = entries.nextElement();

                if (name.startsWith("com.miui.dock.sidebar.")
                        || name.startsWith("com.miui.sidebar.")) {

                    matched++;

                    hookNamedClass(lp, name);
                }
            }

            XposedBridge.log(
                    TAG + "SIDEBAR DEX CLASSES=" + matched
            );

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + "DEX SCAN FAILED: " + t
            );

        } finally {

            if (dex != null) {
                try {
                    dex.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Load a class and hook relevant methods.
     */
    private static void hookNamedClass(
            XC_LoadPackage.LoadPackageParam lp,
            String name) {

        if (!HOOKED.add(name)) {
            return;
        }

        try {

            Class<?> c = Class.forName(
                    name,
                    false,
                    lp.classLoader
            );

            int hooked = 0;

            for (final Method m : c.getDeclaredMethods()) {

                String mn = m.getName().toLowerCase();

                boolean match =
                        name.startsWith("com.miui.dock.sidebar.")
                                || name.startsWith("com.miui.sidebar.");

                if (!match) {

                    for (String word : METHOD_WORDS) {

                        if (mn.contains(word)) {
                            match = true;
                            break;
                        }
                    }
                }

                if (!match
                        || Modifier.isAbstract(m.getModifiers())
                        || Modifier.isNative(m.getModifiers())) {

                    continue;
                }

                final String sig = describe(m);

                try {

                    m.setAccessible(true);

                    XposedBridge.hookMethod(
                            m,
                            new XC_MethodHook() {

                                @Override
                                protected void beforeHookedMethod(
                                        MethodHookParam param) {

                                    AtomicInteger counter =
                                            COUNTS.computeIfAbsent(
                                                    sig,
                                                    k -> new AtomicInteger()
                                            );

                                    int count = counter.incrementAndGet();

                                    if (count <= 8) {

                                        XposedBridge.log(
                                                TAG + "CALL #"
                                                        + count
                                                        + " "
                                                        + sig
                                        );
                                    }
                                }
                            }
                    );

                    hooked++;

                    XposedBridge.log(
                            TAG + "HOOK OK " + sig
                    );

                } catch (Throwable t) {

                    XposedBridge.log(
                            TAG + "HOOK FAIL "
                                    + sig
                                    + " : "
                                    + t
                    );
                }
            }

            XposedBridge.log(
                    TAG + "CLASS "
                            + name
                            + " METHODS_HOOKED="
                            + hooked
            );

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + "CLASS LOAD FAIL "
                            + name
                            + " : "
                            + t
            );
        }
    }

    /**
     * Build a readable method signature.
     */
    private static String describe(Method m) {

        StringBuilder s = new StringBuilder();

        s.append(
                m.getDeclaringClass().getName()
        );

        s.append('#');

        s.append(m.getName());

        s.append('(');

        Class<?>[] params = m.getParameterTypes();

        for (int i = 0; i < params.length; i++) {

            if (i > 0) {
                s.append(',');
            }

            s.append(
                    params[i].getName()
            );
        }

        s.append(')');

        return s.toString();
    }
}
