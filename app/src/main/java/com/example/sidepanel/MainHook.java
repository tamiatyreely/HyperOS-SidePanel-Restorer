package com.example.sidepanel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[SP-TRACE] ";

    private static final Set<String> SEEN =
            ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS =
            new ConcurrentHashMap<>();

    private static final String[] SERVICE_CLASSES = {
        "com.miui.gamebooster.service.VideoToolBoxService",
        "com.miui.gamebooster.service.GameBoosterService",
        "com.miui.gamebooster.service.IVideoToolBox"
    };

    private static final String[] KEYWORDS = {
        "sidebar", "gamebooster", "videotoolbox",
        "toolbox", "drag", "gesture", "region",
        "slide", "edge"
    };

    @Override
    public void handleLoadPackage(
            XC_LoadPackage.LoadPackageParam lp) {

        String pkg = lp.packageName;

        if (!pkg.equals("com.miui.securitycenter")
                && !pkg.equals("com.android.systemui")
                && !pkg.equals("miui.systemui.plugin")) {
            return;
        }

        XposedBridge.log(TAG + "LOADED PACKAGE: "
                + pkg + " PROCESS: " + lp.processName);

        if (pkg.equals("com.miui.securitycenter")) {
            scanServices(lp);
        }

        if (pkg.equals("com.android.systemui")
                || pkg.equals("miui.systemui.plugin")) {
            traceClassLoading();
        }
    }

    private static void traceClassLoading() {
        try {
            XposedBridge.hookAllMethods(
                ClassLoader.class,
                "loadClass",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(
                            MethodHookParam param) {

                        try {
                            Object result = param.getResult();
                            if (!(result instanceof Class<?>)) return;

                            Class<?> clazz = (Class<?>) result;
                            String name = clazz.getName();
                            String lower = name.toLowerCase();

                            boolean matched = false;

                            for (String keyword : KEYWORDS) {
                                if (lower.contains(keyword)) {
                                    matched = true;
                                    break;
                                }
                            }

                            if (matched && SEEN.add(name)) {
                                XposedBridge.log(TAG
                                    + "CLASS LOADED: " + name);
                            }

                        } catch (Throwable ignored) {
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "CLASS LOADER TRACE ACTIVE");

        } catch (Throwable t) {
            XposedBridge.log(TAG + "TRACE ERROR: " + t);
        }
    }

    private static void scanServices(
            XC_LoadPackage.LoadPackageParam lp) {

        for (String name : SERVICE_CLASSES) {
            try {
                Class<?> clazz = Class.forName(
                    name, false, lp.classLoader);

                XposedBridge.log(TAG + "FOUND SERVICE: " + name);

                Method[] methods = clazz.getDeclaredMethods();

                XposedBridge.log(TAG + "METHOD COUNT: "
                        + name + " = " + methods.length);

                for (final Method method : methods) {
                    String signature = describe(method);

                    XposedBridge.log(TAG + "METHOD: " + signature);

                    int mod = method.getModifiers();

                    if (Modifier.isAbstract(mod)
                            || Modifier.isNative(mod)) {
                        continue;
                    }

                    try {
                        method.setAccessible(true);

                        XposedBridge.hookMethod(
                            method,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(
                                        MethodHookParam param) {

                                    AtomicInteger count =
                                        COUNTS.computeIfAbsent(
                                            signature,
                                            k -> new AtomicInteger());

                                    int n = count.incrementAndGet();

                                    if (n <= 3) {
                                        XposedBridge.log(TAG
                                            + "SERVICE CALL #" + n
                                            + ": " + signature);
                                    }
                                }
                            }
                        );

                        XposedBridge.log(TAG + "HOOK OK: "
                                + signature);

                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "HOOK FAILED: "
                                + signature + " " + t);
                    }
                }

            } catch (ClassNotFoundException e) {
                XposedBridge.log(TAG + "SERVICE NOT FOUND: "
                        + name);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "SCAN ERROR: "
                        + name + " " + t);
            }
        }
    }

    private static String describe(Method m) {
        StringBuilder s = new StringBuilder();

        s.append(Modifier.toString(m.getModifiers()))
         .append(" ")
         .append(m.getReturnType().getName())
         .append(" ")
         .append(m.getDeclaringClass().getName())
         .append("#")
         .append(m.getName())
         .append("(");

        Class<?>[] p = m.getParameterTypes();

        for (int i = 0; i < p.length; i++) {
            if (i > 0) s.append(", ");
            s.append(p[i].getName());
        }

        return s.append(")").toString();
    }
}
