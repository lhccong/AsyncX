package com.cong.async.wrapper;

import com.cong.async.callback.ICallback;
import com.cong.async.callback.IWorker;
import com.cong.async.worker.DependWrapper;

import java.util.List;

/**
 * 工作器包装器(对每个worker及callback进行包装，一对一)
 *
 * @author cong
 * @date 2024/04/28
 */
public class WorkerWrapper<T, V> {

    /**
     * 该wrapper的唯一标识
     */
    private String id;
    /**
     * worker将来要处理的param
     */
    private T param;

    /**
     * 执行
     */
    private IWorker<T, V> worker;

    /**
     * 回调
     */
    private ICallback<T, V> callback;

    /**
     * 在自己后面的wrapper，如果没有，自己就是末尾；如果有一个，就是串行；如果有多个，有几个就需要开几个线程</p>
     * -------2
     * 1
     * -------3
     * 如1后面有2、3
     */
    private List<WorkerWrapper<?, ?>> nextWrappers;

    /**
     * 依赖的wrappers，有2种情况，1:必须依赖的全部完成后，才能执行自己 2:依赖的任何一个、多个完成了，就可以执行自己
     * 通过must字段来控制是否依赖项必须完成
     * 1
     * -------3
     * 2
     * 1、2执行完毕后才能执行3
     */
    private List<DependWrapper> dependWrappers;
}
