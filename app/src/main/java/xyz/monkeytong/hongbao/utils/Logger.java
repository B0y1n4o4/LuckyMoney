package xyz.monkeytong.hongbao.utils;
import android.util.Log;

public class Logger {
    private static int LOG_LEVEL = 0;

    private static String SELF_TAG = "Logger:";

    public static void d(String paramString1, String paramString2) {
        if (LOG_LEVEL > 4) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(SELF_TAG);
            stringBuilder.append(paramString1);
            Log.d(stringBuilder.toString(), paramString2);
            return;
        }
    }

    public static void e(String paramString1, String paramString2) {
        if (LOG_LEVEL > 1) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(SELF_TAG);
            stringBuilder.append(paramString1);
            Log.e(stringBuilder.toString(), paramString2);
            return;
        }
    }

    public static void i(String paramString1, String paramString2) {
        if (LOG_LEVEL > 3) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(SELF_TAG);
            stringBuilder.append(paramString1);
            Log.i(stringBuilder.toString(), paramString2);
            return;
        }
    }

    public static void v(String paramString1, String paramString2) {
        if (LOG_LEVEL > 5) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(SELF_TAG);
            stringBuilder.append(paramString1);
            Log.v(stringBuilder.toString(), paramString2);
            return;
        }
    }

    public static void w(String paramString1, String paramString2) {
        if (LOG_LEVEL > 2) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(SELF_TAG);
            stringBuilder.append(paramString1);
            Log.w(stringBuilder.toString(), paramString2);
            return;
        }
    }
}
