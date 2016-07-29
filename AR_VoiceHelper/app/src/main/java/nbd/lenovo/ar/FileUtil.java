package nbd.lenovo.ar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by xionglei on 16/7/28.
 */
public class FileUtil {


    /**
     * @param data
     * @param outFile 写入的文件
     * @return 返回结果
     * @throws IOException
     */
    public static boolean writeDataToFile(byte[] data, File outFile)
            throws IOException {
        boolean isSucc = false;
        try {
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            FileOutputStream out = new FileOutputStream(outFile);
            try {
                out.write(data);
                isSucc = true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return isSucc;

    }
}
