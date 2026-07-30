package top.nkbe.art.engine;

import java.io.File;

/**
 * Event listener callback for JiaguEngine execution events.
 */
public interface JiaguListener {
    void onLog(String message);
    void onProgress(int step, int totalSteps, String statusText);
    void onSuccess(File protectedApk);
    void onError(Throwable throwable);
}
