package nbd.lenovo.ar;

import android.util.Log;

/**
 * Created by xionglei on 16/7/25.
 */
public class LogUtil {
    public static void logE(String tag,String data)
    {
        Log.e(tag,data);
    }
    public static void logV(String tag,String data)
    {
        Log.v(tag,data);
    }
    public static void logW(String tag,String data)
    {
        Log.w(tag,data);
    }
}
