package com.cong.async.wrapper;

import com.cong.async.callback.DefaultCallback;
import com.cong.async.callback.ICallback;
import com.cong.async.callback.IWorker;
import com.cong.async.executor.time.SystemClock;
import com.cong.async.worker.DependWrapper;
import com.cong.async.worker.ResultState;
import com.cong.async.worker.WorkResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 标记该事件是否已经被处理过了，譬如已经超时返回false了，后续rpc又收到返回值了，则不再二次回调
     * 经试验,volatile并不能保证"同一毫秒"内,多线程对该值的修改和拉取
     * <p>
     * 1-finish, 2-error, 3-working
     */
    private AtomicInteger state = new AtomicInteger(0);

    /**
     * 该map存放所有wrapper的id和wrapper映射
     */
    private Map<String, WorkerWrapper> forParamUseWrappers;

    /**
     * 也是个钩子变量，用来存临时的结果
     */
    private volatile WorkResult<V> workResult = WorkResult.defaultResult();

    /**
     * 是否在执行自己前，去校验nextWrapper的执行结果<p>
     * 注意，该属性仅在nextWrapper数量<=1时有效，>1时的情况是不存在的
     */
    private volatile boolean needCheckNextWrapperResult = true;

    private WorkerWrapper(String id, IWorker<T, V> worker, T param, ICallback<T, V> callback) {
        if (worker == null) {
            throw new NullPointerException("async.worker is null");
        }
        this.worker = worker;
        this.param = param;
        this.id = id;
        //允许不设置回调
        if (callback == null) {
            callback = new DefaultCallback<>();
        }
        this.callback = callback;
    }
    /**
     * 开始工作(主要实现)
     * fromWrapper代表这次work是由哪个上游wrapper发起的
     */
    private void work(ExecutorService executorService, WorkerWrapper fromWrapper, long remainTime, Map<String, WorkerWrapper> forParamUseWrappers) {
        this.forParamUseWrappers = forParamUseWrappers;
        //将自己放到所有wrapper的集合里去
        forParamUseWrappers.put(id, this);
        //获取当前时间
        long now = SystemClock.now();
        //总的已经超时了，就快速失败，进行下一个
        if (remainTime <= 0) {
            fastFail(WorkerStatusEnum.INIT.getValue(), null);
            beginNext(executorService, now, remainTime);
            return;
        }
        //如果自己已经执行过了。
        //可能有多个依赖，其中的一个依赖已经执行完了，并且自己也已开始执行或执行完毕。当另一个依赖执行完毕，又进来该方法时，就不重复处理了
        if (getState() == WorkerStatusEnum.FINISH.getValue() || getState() == WorkerStatusEnum.ERROR.getValue()) {
            beginNext(executorService, now, remainTime);
            return;
        }
    }

    public void work(ExecutorService executorService, long remainTime, Map<String, WorkerWrapper> forParamUseWrappers) {
        work(executorService, null, remainTime, forParamUseWrappers);
    }
    /**
     * 进行下一个任务
     */
    private void beginNext(ExecutorService executorService, long now, long remainTime) {
        //如果有下一个wrapper，就开始执行
        if (nextWrappers == null || nextWrappers.isEmpty()){
            return;
        }
        //花费的时间
        long costTime = SystemClock.now() - now;
        //只有一个wrapper，就直接开始执行然后结束掉
        if (nextWrappers.size() == 1) {
            nextWrappers.get(0).work(executorService, this, remainTime - costTime, forParamUseWrappers);
            return;
        }
        //有多个wrapper,就开始异步执行
        CompletableFuture[] futures = new CompletableFuture[nextWrappers.size()];
        for (int i = 0; i < nextWrappers.size(); i++) {
            int finalI = i;
            futures[i] = CompletableFuture.runAsync(() ->
                nextWrappers.get(finalI).work(executorService, this, remainTime, forParamUseWrappers));
        }
        try {
            CompletableFuture.allOf(futures).get(remainTime - costTime, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<WorkerWrapper<?, ?>> getNextWrappers() {
        return nextWrappers;
    }

    private int getState() {
        return state.get();
    }

    /**
     * 总控制台超时，停止所有任务
     */
    public void stopNow() {
        if (getState() == WorkerStatusEnum.INIT.getValue()|| getState() == WorkerStatusEnum.WORKING.getValue()) {
            fastFail(getState(), null);
        }
    }
    private boolean checkIsNullResult() {
        return ResultState.DEFAULT == workResult.getResultState();
    }

    private boolean compareAndSetState(int expect, int update) {
        return this.state.compareAndSet(expect, update);
    }

    /**
     * 快速失败
     */
    private boolean fastFail(int expect, Exception e) {
        //试图将它从expect状态,改成Error
        if (!compareAndSetState(expect, WorkerStatusEnum.ERROR.getValue())) {
            return false;
        }

        //尚未处理过结果
        if (checkIsNullResult()) {
            if (e == null) {
                workResult = defaultResult();
            } else {
                workResult = defaultExResult(e);
            }
        }

        callback.result(false, param, workResult);
        return true;
    }

    /**
     * 默认结果
     *
     * @return {@link WorkResult}<{@link V}>
     */
    private WorkResult<V> defaultResult() {
        workResult.setResultState(ResultState.TIMEOUT);
        workResult.setResult(worker.defaultValue());
        return workResult;
    }

    /**
     * 默认结果(带异常)
     *
     * @param ex 前任
     * @return {@link WorkResult}<{@link V}>
     */
    private WorkResult<V> defaultExResult(Exception ex) {
        workResult.setResultState(ResultState.EXCEPTION);
        workResult.setResult(worker.defaultValue());
        workResult.setEx(ex);
        return workResult;
    }
}
