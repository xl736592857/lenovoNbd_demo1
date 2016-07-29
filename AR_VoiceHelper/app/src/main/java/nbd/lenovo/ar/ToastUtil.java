package nbd.lenovo.ar;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by xionglei on 16/7/27.
 */
public class ToastUtil {
    private static Toast toast=null;
    public static void showToast(Context context,String text) {
        if (toast == null) {
            toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
            ViewGroup linearLayout = (ViewGroup) toast.getView();
            if (linearLayout.getChildCount() > 0 && linearLayout.getChildAt(0) instanceof TextView) {
                TextView messageTextView = (TextView) linearLayout.getChildAt(0);
                messageTextView.setTextSize(40);
            }

        }
        toast.setText(text);
        toast.show();
    }
}
