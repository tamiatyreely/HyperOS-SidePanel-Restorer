package com.example.sidepanel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[SidePanelRestorer] ";

    private static final String TARGET_PACKAGE =
            "com.miui.securitycenter";

    private static final String[] TARGET_CLASSES = {
            "com.miui.gamebooster.service.VideoToolBoxService",
            "com.miui.gamebooster.service.GameBoosterService",
            "com.miui.gamebooster.service.IVideoToolBox",
            "com.miui.gamebooster.service.GameBoosterServices"
    };

    private static final Set<String> SCANNED_CLASSES =
            new HashSet<>();

    private static final ConcurrentHashMap<String, AtomicInteger>
            CALL_COUNTS = new ConcurrentHashMap<>();

    @Override
    public void handleLoadPackage(
            final XC_LoadPackage.LoadPackageParam lp) {

        if (!TARGET_PACKAGE.equals(lp.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "================================");
        XposedBridge.log(TAG + "MODULE LOADED");
        XposedBridge.log(TAG + "Package: " + lp.packageName);
        XposedBridge.log(TAG + "Process: " + lp.processName);
        XposedBridge.log(TAG + "================================");

        for (String className : TARGET_CLASSES) {
            inspectClass(lp, className);
        }
    }

    private static synchronized void inspectClass(
            XC_LoadPackage.LoadPackageParam lp,
            String className) {

        if (SCANNED_CLASSES.contains(className)) {
            return;
        }

        SCANNED_CLASSES.add(className);

        try {
            Class<?> clazz = Class.forName(
                    className,
                    false,
                    lp.classLoader
            );

            XposedBridge.log(TAG + "FOUND CLASS: " + className);

            Method[] methods = clazz.getDeclaredMethods();

            Arrays.sort(methods, new Comparator<Method>() {
                @Override
                public int compare(Method a, Method b) {
                    return a.getName().compareTo(b.getName());
                }
            });

            XposedBridge.log(TAG + "METHOD COUNT: "
                    + methods.length);

            for (final Method method : methods) {

                final String signature = describe(method);

                // 输出所有方法的完整签名
                XposedBridge.log(TAG + "METHOD: " + signature);

                int modifiers = method.getModifiers();

                // 抽象方法和 Native 方法只记录，不尝试 Hook
                if (Modifier.isAbstract(modifiers)
                        || Modifier.isNative(modifiers)) {
                    XposedBridge.log(TAG
                            + "SKIP NON-EXECUTABLE: "
                            + signature);
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

                                    AtomicInteger counter =
                                            CALL_COUNTS.get(signature);

                                    if (counter == null) {
                                        AtomicInteger fresh =
                                                new AtomicInteger(0);

                                        AtomicInteger old =
                                                CALL_COUNTS.putIfAbsent(
                                                        signature,
                                                        fresh
                                                );

                                        counter = old == null
                                                ? fresh : old;
                                    }

                                    int count = counter.incrementAndGet();

                                    // 每个方法最多记录前三次调用
                                    if (count <= 3) {
                                        XposedBridge.log(
                                                TAG + "CALL #"
                                                + count + ": "
                                                + signature
                                        );
                                    }
                                }
                            }
                    );

                    XposedBridge.log(TAG + "HOOK OK: "
                            + signature);

                } catch (Throwable hookError) {
                    XposedBridge.log(TAG + "HOOK FAILED: "
                            + signature + " ERROR: " + hookError);
                }
            }

            XposedBridge.log(TAG + "SCAN COMPLETE: "
                    + className);

        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + "CLASS NOT FOUND: "
                    + className);

        } catch (Throwable t) {
            XposedBridge.log(TAG + "SCAN ERROR: "
                    + className + " ERROR: " + t);
        }
    }

    private static String describe(Method method) {

        StringBuilder out = new StringBuilder();

        out.append(Modifier.toString(method.getModifiers()))
                .append(" ")
                .append(method.getReturnType().getName())
                .append(" ")
                .append(method.getDeclaringClass().getName())
                .append("#")
                .append(method.getName())
                .append("(");

        Class<?>[] params = method.getParameterTypes();

        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                out.append(", ");
            }

            out.append(params[i].getName());
        }

        out.append(")");

        return out.toString();
    }
}
