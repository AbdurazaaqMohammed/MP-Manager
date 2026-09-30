package io.github.abdurazaaqmohammed.packs.device;

/**
 * Offscreen EGL probe for GPU vendor/renderer/version. No surface, no
 * permissions; everything guarded because EGL availability varies.
 */
public final class EglGpu {

    public static class Info {
        public String vendor = DeviceSys.UNKNOWN;
        public String renderer = DeviceSys.UNKNOWN;
        public String version = DeviceSys.UNKNOWN;
        public String shading = DeviceSys.UNKNOWN;
        public String extensions = "";
    }

    private EglGpu() {
    }

    public static Info probe() {
        Info info = new Info();
        Object display = null;
        Object surface = null;
        Object eglContext = null;
        try {
            Class<?> egl14 = Class.forName("android.opengl.EGL14");
            Class<?> egl10 = Class.forName("android.opengl.EGL10");
            Object eglNoDisplay = egl14.getField("EGL_NO_DISPLAY").get(null);
            display = egl14.getMethod("eglGetDisplay", Object.class)
                    .invoke(null, eglNoDisplay);
            int[] version = new int[2];
            Object ok = egl14.getMethod("eglInitialize", Object.class, int[].class, int.class, int[].class, int.class)
                    .invoke(null, display, version, 0, version, 1);
            if (!Boolean.TRUE.equals(ok)) return info;
            int[] attribs = {
                    getInt(egl10, "EGL_RED_SIZE", 8), 8,
                    getInt(egl10, "EGL_GREEN_SIZE", 8), 8,
                    getInt(egl10, "EGL_BLUE_SIZE", 8), 8,
                    getInt(egl10, "EGL_RENDERABLE_TYPE", 4), 4,
                    getInt(egl10, "EGL_NONE", 12344), getInt(egl10, "EGL_NONE", 12344)};
            Object[] configs = new Object[1];
            int[] num = new int[1];
            ok = egl14.getMethod("eglChooseConfig", Object.class, int[].class, int.class,
                    Object[].class, int.class, int[].class, int.class)
                    .invoke(null, display, attribs, 0, configs, 0, 1, num, 0);
            if (!Boolean.TRUE.equals(ok) || num[0] <= 0) return info;
            int[] surfaceAttribs = {
                    getInt(egl10, "EGL_WIDTH", 12375), 64,
                    getInt(egl10, "EGL_HEIGHT", 12374), 64,
                    getInt(egl10, "EGL_NONE", 12344)};
            surface = egl14.getMethod("eglCreatePbufferSurface", Object.class, Object.class, int[].class, int.class)
                    .invoke(null, display, configs[0], surfaceAttribs, 0);
            int[] ctxAttribs = {
                    getInt(egl14, "EGL_CONTEXT_CLIENT_VERSION", 12440), 2,
                    getInt(egl10, "EGL_NONE", 12344)};
            eglContext = egl14.getMethod("eglCreateContext", Object.class, Object.class, Object.class, int[].class, int.class)
                    .invoke(null, display, configs[0],
                            egl14.getField("EGL_NO_CONTEXT").get(null), ctxAttribs, 0);
            ok = egl14.getMethod("eglMakeCurrent", Object.class, Object.class, Object.class, Object.class)
                    .invoke(null, display, surface, surface, eglContext);
            if (!Boolean.TRUE.equals(ok)) return info;
            Class<?> gles20 = Class.forName("android.opengl.GLES20");
            info.vendor = glString(gles20, "GL_VENDOR", info.vendor);
            info.renderer = glString(gles20, "GL_RENDERER", info.renderer);
            info.version = glString(gles20, "GL_VERSION", info.version);
            info.shading = glString(gles20, "GL_SHADING_LANGUAGE_VERSION", info.shading);
            info.extensions = glString(gles20, "GL_EXTENSIONS", "");
        } catch (Exception ignored) {
        } finally {
            try {
                if (display != null) {
                    Class<?> egl14 = Class.forName("android.opengl.EGL14");
                    try {
                        egl14.getMethod("eglMakeCurrent", Object.class, Object.class, Object.class, Object.class)
                                .invoke(null, display,
                                        egl14.getField("EGL_NO_SURFACE").get(null),
                                        egl14.getField("EGL_NO_SURFACE").get(null),
                                        egl14.getField("EGL_NO_CONTEXT").get(null));
                    } catch (Exception ignored) {
                    }
                    try {
                        if (surface != null) {
                            egl14.getMethod("eglDestroySurface", Object.class, Object.class)
                                    .invoke(null, display, surface);
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        if (eglContext != null) {
                            egl14.getMethod("eglDestroyContext", Object.class, Object.class)
                                    .invoke(null, display, eglContext);
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        egl14.getMethod("eglTerminate", Object.class).invoke(null, display);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return info;
    }

    private static int getInt(Class<?> cls, String name, int fallback) {
        try {
            return cls.getField(name).getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String glString(Class<?> gles20, String constName, String fallback) {
        try {
            Class<?> gl10 = Class.forName("javax.microedition.khronos.opengles.GL10");
            int code = gl10.getField(constName).getInt(null);
            Object v = gles20.getMethod("glGetString", int.class).invoke(null, code);
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
