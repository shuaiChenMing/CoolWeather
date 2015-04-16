package com.ming.coolweather.util;

/**
 * Created by ming on 2015/4/14.
 */
public interface HttpCallbackListener {
    void OnFinish(String response);
    void OnError(Exception e);
}
