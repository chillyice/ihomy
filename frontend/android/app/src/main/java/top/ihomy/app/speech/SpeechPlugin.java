package top.ihomy.app.speech;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 语音识别(插件名 {@code IhomySpeech}):说一句话给记账本,识别完把文本交回前端解析。
 *
 * <p>用系统 {@link SpeechRecognizer}(设备上装了识别服务才有,华为/HMS 与 Google 机型都自带),
 * 不自带离线模型。前端只拿文本:金额/分类怎么拆是纯前端逻辑(utils/voiceRecord.js),
 * 原生侧不猜业务。
 */
@CapacitorPlugin(
        name = "IhomySpeech",
        permissions = { @Permission(alias = "mic", strings = { Manifest.permission.RECORD_AUDIO }) })
public class SpeechPlugin extends Plugin {

    private static final String TAG = "IhomySpeech";

    private SpeechRecognizer recognizer;
    private PluginCall pendingCall;

    /** 本机是否有识别服务(没有就不在页面上显示麦克风按钮) */
    @PluginMethod
    public void available(PluginCall call) {
        boolean ok = false;
        try {
            ok = SpeechRecognizer.isRecognitionAvailable(getContext());
        } catch (Exception ignored) {
            // 取不到就当没有
        }
        JSObject ret = new JSObject();
        ret.put("available", ok);
        call.resolve(ret);
    }

    /** 开始一次识别;权限不足时会先申请录音权限 */
    @PluginMethod
    public void start(PluginCall call) {
        if (pendingCall != null) {
            call.reject("busy");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            call.reject("no_service");
            return;
        }
        if (getPermissionState("mic") != PermissionState.GRANTED) {
            requestPermissionForAlias("mic", call, "micResult");
            return;
        }
        listen(call);
    }

    @PermissionCallback
    private void micResult(PluginCall call) {
        if (getPermissionState("mic") == PermissionState.GRANTED) {
            listen(call);
        } else {
            call.reject("permission");
        }
    }

    /** 用户主动取消(页面关掉/再点一次):停掉识别并让本次调用以 cancel 结束 */
    @PluginMethod
    public void cancel(PluginCall call) {
        stopRecognizer();
        finishError("cancel");
        call.resolve();
    }

    private void listen(final PluginCall call) {
        pendingCall = call;
        try {
            getActivity().runOnUiThread(() -> {
                try {
                    destroyRecognizer();
                    recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
                    recognizer.setRecognitionListener(new RecognitionListener() {
                        @Override
                        public void onReadyForSpeech(Bundle params) {}

                        @Override
                        public void onBeginningOfSpeech() {}

                        @Override
                        public void onRmsChanged(float rmsdB) {}

                        @Override
                        public void onBufferReceived(byte[] buffer) {}

                        @Override
                        public void onEndOfSpeech() {}

                        @Override
                        public void onError(int error) {
                            finishError(errorName(error));
                        }

                        @Override
                        public void onResults(Bundle results) {
                            ArrayList<String> list =
                                    results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            String text = list == null || list.isEmpty() ? "" : list.get(0);
                            finishOk(text == null ? "" : text);
                        }

                        @Override
                        public void onPartialResults(Bundle partialResults) {}

                        @Override
                        public void onEvent(int eventType, Bundle params) {}
                    });
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
                    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                    recognizer.startListening(intent);
                } catch (Exception e) {
                    Log.w(TAG, "启动语音识别失败", e);
                    finishError("failed");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "语音识别初始化失败", e);
            finishError("failed");
        }
    }

    private void finishOk(String text) {
        PluginCall call = takePending();
        stopRecognizer();
        if (call == null) return;
        JSObject ret = new JSObject();
        ret.put("text", text);
        call.resolve(ret);
    }

    private void finishError(String message) {
        PluginCall call = takePending();
        stopRecognizer();
        if (call == null) return;
        call.reject(message);
    }

    private PluginCall takePending() {
        PluginCall call = pendingCall;
        pendingCall = null;
        return call;
    }

    private void stopRecognizer() {
        try {
            if (recognizer != null) {
                recognizer.stopListening();
                recognizer.cancel();
            }
        } catch (Exception ignored) {
            // 已经结束了再停会抛,忽略
        }
        destroyRecognizer();
    }

    private void destroyRecognizer() {
        try {
            if (recognizer != null) recognizer.destroy();
        } catch (Exception ignored) {
            // 同上
        }
        recognizer = null;
    }

    @Override
    protected void handleOnDestroy() {
        stopRecognizer();
        finishError("cancel");
        super.handleOnDestroy();
    }

    private static String errorName(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "no_match";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "permission";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "network";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "busy";
            case SpeechRecognizer.ERROR_CLIENT:
                return "cancel";
            default:
                return "failed";
        }
    }
}
