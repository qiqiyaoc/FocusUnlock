package com.focusunlock;

import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FocusUnlock implements IXposedHookLoadPackage {

    private static final String TAG = "[FocusUnlock]";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.xiaomi.xmsf".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + " Hooking com.xiaomi.xmsf");

        // 策略1：Hook ContentProvider.call (canShowFocus)
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContentProvider",
                lpparam.classLoader,
                "call",
                String.class,
                String.class,
                String.class,
                String.class,
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String method = (String) param.args[2];
                        if ("canShowFocus".equals(method)) {
                            Bundle extras = (Bundle) param.args[4];
                            String pkg = extras != null ? extras.getString("package") : "unknown";

                            Bundle result = new Bundle();
                            result.putBoolean("canShowFocus", true);
                            param.setResult(result);

                            XposedBridge.log(TAG + " Bypassed canShowFocus for: " + pkg);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " Hook ContentProvider.call success");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " Hook ContentProvider.call failed: " + t.getMessage());
        }

        // 策略2：Hook 可能的内部鉴权方法
        String[] classNames = {
            "com.xiaomi.xmsf.push.notification.NotificationController",
            "com.xiaomi.xmsf.push.utils.FocusNotificationUtils",
            "com.xiaomi.xmsf.push.notification.FocusNotificationHelper",
            "com.xiaomi.xmsf.push.notification.FocusAuthManager",
            "com.xiaomi.push.service.FocusNotificationManager"
        };

        for (String className : classNames) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                    String name = m.getName().toLowerCase();
                    Class<?> ret = m.getReturnType();

                    boolean isTarget = (ret == boolean.class) && (
                        name.contains("focus") ||
                        name.contains("permission") ||
                        name.contains("allowed") ||
                        name.contains("check") ||
                        name.contains("enable") ||
                        name.contains("support")
                    );

                    if (isTarget) {
                        XposedHelpers.findAndHookMethod(clazz, m.getName(), m.getParameterTypes(),
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    param.setResult(true);
                                }
                            }
                        );
                        XposedBridge.log(TAG + " Hooked " + className + "." + m.getName() + " -> force true");
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 策略3：Hook Bundle.getBoolean 兜底
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Bundle",
                lpparam.classLoader,
                "getBoolean",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if ("canShowFocus".equals(key)) {
                            param.setResult(true);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " Hook Bundle.getBoolean(canShowFocus) success");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " Hook Bundle.getBoolean failed: " + t.getMessage());
        }
    }
}
