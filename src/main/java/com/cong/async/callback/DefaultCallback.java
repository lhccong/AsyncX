package com.cong.async.callback;


import com.cong.async.worker.WorkResult;

/**
 * 默认回调类，如果不设置的话，会默认给这个回调
 * @author wuweifeng wrote on 2019-11-19.
 */
public class DefaultCallback<T, V> implements ICallback<T, V> {
    @Override
    public void begin() {
        // 回调为空，不做任何事情
    }

    @Override
    public void result(boolean success, T param, WorkResult<V> workResult) {
        // 回调为空，不做任何事情
    }

}
