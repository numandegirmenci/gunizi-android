package com.gunizi.app;
/** Progress events from the on-device audio and language models. */
@FunctionalInterface
public interface AiProgress {
    void update(int percent,String message);
}
