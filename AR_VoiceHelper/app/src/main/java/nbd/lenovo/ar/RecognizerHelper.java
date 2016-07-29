package nbd.lenovo.ar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;

import com.baidu.speech.VoiceRecognitionService;

/**
 * Created by xionglei on 16/7/27.
 * 语音识别
 */
public class RecognizerHelper implements RecognitionListener {
    private static RecognizerHelper mRecognizerHelper=null;//负责语音识别
    private SpeechRecognizer speechRecognizer=null;
    private CallbackRecognizer mCallbackRecognizer;
    private RecognizerHelper(Context context,CallbackRecognizer mCallbackRecognizer)
    {
        this.mCallbackRecognizer=mCallbackRecognizer;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context, new ComponentName(context, VoiceRecognitionService.class));
        speechRecognizer.setRecognitionListener(this);
    }
    public static synchronized RecognizerHelper getInstance(Context context,CallbackRecognizer mCallbackRecognizer)
    {
        if(mRecognizerHelper==null)
        {
            mRecognizerHelper=new RecognizerHelper(context,mCallbackRecognizer);
        }

        return mRecognizerHelper;
    }
    public void release()
    {
        speechRecognizer.destroy();
        mRecognizerHelper=null;
    }
    @Override
    public void onReadyForSpeech(Bundle bundle) {
        mCallbackRecognizer.onReadyForSpeech(bundle);
    }
    /**
     * 开始识别
     */
    public void startRecognition() {
        Intent intent = new Intent();
        intent.putExtra("api", true);
        speechRecognizer.startListening(intent);
    }

    /**
     * 停止识别录音
     */
    public void stopRecognizerRecord() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }
    /**
     * 停止识别
     */
    public void cancleRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        mCallbackRecognizer.onBeginningOfSpeech();
    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {
        mCallbackRecognizer.onEndOfSpeech();
    }

    @Override
    public void onError(int i) {
        mCallbackRecognizer.onError(i);
    }

    @Override
    public void onResults(Bundle bundle) {
    mCallbackRecognizer.onResults(bundle);
    }

    @Override
    public void onPartialResults(Bundle bundle) {

    }

    @Override
    public void onEvent(int i, Bundle bundle) {

    }
    public interface CallbackRecognizer
    {
        void onError(int what);
        void onReadyForSpeech(Bundle bundle);
        void onResults(Bundle bundle);
        void onBeginningOfSpeech();
        void onEndOfSpeech();
    }

}
