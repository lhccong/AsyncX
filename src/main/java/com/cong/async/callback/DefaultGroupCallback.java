package com.cong.async.callback;


import com.cong.async.wrapper.WorkerWrapper;

import java.util.List;

/**
 * 默认组回调
 *
 * @author cong
 * @date 2024/04/28
 */
public class DefaultGroupCallback implements IGroupCallback {
    @Override
    public void success(List<WorkerWrapper> workerWrappers) {
        // 默认实现，不做任何事情
    }

    @Override
    public void failure(List<WorkerWrapper> workerWrappers, Exception e) {
        // 默认实现，不做任何事情
    }
}
