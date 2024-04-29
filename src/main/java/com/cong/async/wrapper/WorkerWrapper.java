package com.cong.async.wrapper;

import com.cong.async.callback.DefaultCallback;
import com.cong.async.callback.ICallback;
import com.cong.async.callback.IWorker;
import com.cong.async.exception.SkippedException;
import com.cong.async.executor.time.SystemClock;
import com.cong.async.worker.DependWrapper;
import com.cong.async.worker.ResultState;
import com.cong.async.worker.WorkResult;

import java.util.*;
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
    private final String id;
    /**
     * worker将来要处理的param
     */
    private T param;

    /**
     * 执行
     */
    private final IWorker<T, V> worker;

    /**
     * 回调
     */
    private final ICallback<T, V> callback;

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
    private final AtomicInteger state = new AtomicInteger(0);

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
        //如果在执行前需要校验nextWrapper的状态
        if (needCheckNextWrapperResult) {
            //如果自己的next链上有已经出结果或已经开始执行的任务了，自己就不用继续了
            if (!checkNextWrapperResult()) {
                fastFail(WorkerStatusEnum.INIT.getValue(), new SkippedException());
                beginNext(executorService, now, remainTime);
                return;
            }
        }

        //如果没有任何依赖，说明自己就是第一批要执行的
        if (dependWrappers == null || dependWrappers.isEmpty()) {
            //执行自己的job
            fire();
            beginNext(executorService, now, remainTime);
            return;
        }

        /*如果有前方依赖，存在两种情况
         一种是前面只有一个wrapper。即 A  ->  B
        一种是前面有多个wrapper。A C D ->   B。需要A、C、D都完成了才能轮到B。但是无论是A执行完，还是C执行完，都会去唤醒B。
        所以需要B来做判断，必须A、C、D都完成，自己才能执行 */

        //只有一个依赖
        if (dependWrappers.size() == 1) {
            doDependsOneJob(fromWrapper);
            beginNext(executorService, now, remainTime);
        } else {
            //有多个依赖，需要等待所有依赖都完成
            doDependsMoreJob(executorService, dependWrappers, fromWrapper, now, remainTime);
        }
    }


    public void work(ExecutorService executorService, long remainTime, Map<String, WorkerWrapper> forParamUseWrappers) {
        work(executorService, null, remainTime, forParamUseWrappers);
    }

    private void addNext(WorkerWrapper<?, ?> workerWrapper) {
        if (nextWrappers == null) {
            nextWrappers = new ArrayList<>();
        }
        //避免添加重复
        for (WorkerWrapper wrapper : nextWrappers) {
            if (workerWrapper.equals(wrapper)) {
                return;
            }
        }
        nextWrappers.add(workerWrapper);
    }
    /**
     * 进行下一个任务
     */
    private void beginNext(ExecutorService executorService, long now, long remainTime) {
        //如果有下一个wrapper，就开始执行
        if (nextWrappers == null || nextWrappers.isEmpty()) {
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
    public void setParam(T param) {
        this.param = param;
    }

    private void addDepend(WorkerWrapper<?, ?> workerWrapper, boolean must) {
        addDepend(new DependWrapper(workerWrapper, must));
    }
    private void addDepend(DependWrapper dependWrapper) {
        if (dependWrappers == null) {
            dependWrappers = new ArrayList<>();
        }
        //如果依赖的是重复的同一个，就不重复添加了
        for (DependWrapper wrapper : dependWrappers) {
            if (wrapper.equals(dependWrapper)) {
                return;
            }
        }
        dependWrappers.add(dependWrapper);
    }

    /**
     * 判断自己下游链路上，是否存在已经出结果的或已经开始执行的
     * 如果没有返回true，如果有返回false
     */
    private boolean checkNextWrapperResult() {
        //如果自己就是最后一个，或者后面有并行的多个，就返回true
        if (nextWrappers == null || nextWrappers.size() != 1) {
            return getState() == WorkerStatusEnum.FINISH.getValue();
        }
        WorkerWrapper<?, ?> nextWrapper = nextWrappers.get(0);
        //继续校验自己的next的状态
        return nextWrapper.getState() == WorkerStatusEnum.FINISH.getValue() && nextWrapper.checkNextWrapperResult();
    }

    /**
     * 总控制台超时，停止所有任务
     */
    public void stopNow() {
        if (getState() == WorkerStatusEnum.INIT.getValue() || getState() == WorkerStatusEnum.WORKING.getValue()) {
            fastFail(getState(), null);
        }
    }

    /**
     * 执行自己的job.具体地执行是在另一个线程里,但判断阻塞超时是在work线程
     */
    private void fire() {
        //阻塞取结果
        workResult = workerDoJob();
    }

    /**
     * 具体的单个 worker 执行任务
     */
    private WorkResult<V> workerDoJob() {
        //避免重复执行
        if (!checkIsNullResult()) {
            return workResult;
        }
        try {
            //判断 worker 是否已经在执行中。保证任务不被重复执行
            if (cASState(WorkerStatusEnum.INIT.getValue()
                    , WorkerStatusEnum.WORKING.getValue())) {
                return workResult;
            }
            //执行任务触发监听
            callback.begin();

            //执行任务(action 交由 worker 实现)
            V resultValue = worker.action(param, forParamUseWrappers);

            //修改任务状态，从working到finish。如果状态不是在working,说明别的地方已经修改了
            if (cASState(WorkerStatusEnum.WORKING.getValue()
                    , WorkerStatusEnum.FINISH.getValue())) {
                return workResult;
            }

            //设置结果
            workResult.setResultState(ResultState.SUCCESS);
            workResult.setResult(resultValue);
            //回调成功
            callback.result(true, param, workResult);

            return workResult;
        } catch (Exception e) {
            //避免重复回调
            if (!checkIsNullResult()) {
                return workResult;
            }
            fastFail(WorkerStatusEnum.WORKING.getValue(), e);
            return workResult;
        }
    }

    private void doDependsOneJob(WorkerWrapper dependWrapper) {
        //超时 快速失败
        if (ResultState.TIMEOUT == dependWrapper.getWorkResult().getResultState()) {
            workResult = defaultResult();
            fastFail(WorkerStatusEnum.INIT.getValue(), null);
        } else if (ResultState.EXCEPTION == dependWrapper.getWorkResult().getResultState()) {
            workResult = defaultExResult(dependWrapper.getWorkResult().getEx());
            fastFail(WorkerStatusEnum.INIT.getValue(), null);
        } else {
            //如果依赖任务正常，自己开始执行
            fire();
        }
    }

    private void doDependsMoreJob(ExecutorService executorService, List<DependWrapper> dependWrappers, WorkerWrapper fromWrapper, long now, long remainTime) {
        //如果当前任务已经完成了，依赖的其他任务拿到锁再进来时，不需要执行下面的逻辑了。
        if (getState() != WorkerStatusEnum.INIT.getValue()) {
            return;
        }
        boolean nowDependIsMust = false;
        //创建必须完成的上游 wrapper 集合
        Set<DependWrapper> mustWrapper = new HashSet<>();

        //遍历依赖项
        for (DependWrapper dependWrapper : dependWrappers) {
            if (dependWrapper.isMust()) {
                mustWrapper.add(dependWrapper);
            }
            if (dependWrapper.getDepWrapper().equals(fromWrapper)) {
                nowDependIsMust = dependWrapper.isMust();
            }
        }
        //如果都是不必须的条件，则自己开始执行
        if (mustWrapper.isEmpty()) {
            if (ResultState.TIMEOUT == fromWrapper.getWorkResult().getResultState()) {
                fastFail(WorkerStatusEnum.INIT.getValue(), null);
            } else {
                fire();
            }
            beginNext(executorService, now, remainTime);
            return;
        }

        //如果存在需要必须完成的，且fromWrapper不是必须的，就什么也不干
        if (!nowDependIsMust) {
            return;
        }

        //如果fromWrapper是必须的
        boolean existNoFinish = false;
        boolean hasError = false;

        //先判断前面必须要执行的依赖任务的执行结果，如果有任何一个失败，那就不用走action了，直接给自己设置为失败，进行下一步就是了
        checkExecResult(executorService, now, remainTime, mustWrapper, existNoFinish, hasError);
    }

    private void checkExecResult(ExecutorService executorService, long now, long remainTime, Set<DependWrapper> mustWrapper, boolean existNoFinish, boolean hasError) {
        for (DependWrapper dependWrapper : mustWrapper) {
            WorkerWrapper<?, ?> depWrapper = dependWrapper.getDepWrapper();
            WorkResult<?> tempWorkResult = depWrapper.getWorkResult();

            //如果为 init 和 working 则说明还没执行完
            if (depWrapper.getState() == WorkerStatusEnum.INIT.getValue() || depWrapper.getState() == WorkerStatusEnum.WORKING.getValue()) {
                existNoFinish = true;    //存在还没执行完的依赖任务
                break;
            }
            //如果是超时设置默认结果
            if (ResultState.TIMEOUT == tempWorkResult.getResultState()) {
                workResult = defaultResult();
                hasError = true;    //存在执行失败的依赖任务
                break;
            }
            //如果为 error 则说明执行失败了
            if (depWrapper.getState() == WorkerStatusEnum.ERROR.getValue()) {
                workResult = defaultExResult(depWrapper.getWorkResult().getEx());
                hasError = true;    //存在执行失败的依赖任务
                break;
            }
        }
        //存在失败的
        if (hasError) {
            fastFail(WorkerStatusEnum.INIT.getValue(), null);
            beginNext(executorService, now, remainTime);
            return;
        }

        //如果上游都没有失败，分为两种情况，一种是都finish了，一种是有的在working
        //都finish的话
        if (!existNoFinish) {
            //上游都finish了，进行自己
            fire();
            beginNext(executorService, now, remainTime);
        }
    }

    private void fastFail(int expect, Exception e) {
        //试图将它从expect状态,改成Error
        if (cASState(expect, WorkerStatusEnum.ERROR.getValue())) {
            return;
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
    }

    /**
     * 检查结果为空
     *
     * @return boolean
     */
    private boolean checkIsNullResult() {
        return ResultState.DEFAULT == workResult.getResultState();
    }

    /**
     * 比较和设置状态
     *
     * @param expect 期望
     * @param update 更新
     * @return boolean
     */
    private boolean cASState(int expect, int update) {
        return !this.state.compareAndSet(expect, update);
    }

    private WorkResult<V> defaultResult() {
        workResult.setResultState(ResultState.TIMEOUT);
        workResult.setResult(worker.defaultValue());
        return workResult;
    }

    private WorkResult<V> defaultExResult(Exception ex) {
        workResult.setResultState(ResultState.EXCEPTION);
        workResult.setResult(worker.defaultValue());
        workResult.setEx(ex);
        return workResult;
    }

    public WorkResult<V> getWorkResult() {
        return workResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)  {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkerWrapper<?, ?> that = (WorkerWrapper<?, ?>) o;
        return needCheckNextWrapperResult == that.needCheckNextWrapperResult &&
                Objects.equals(param, that.param) &&
                Objects.equals(worker, that.worker) &&
                Objects.equals(callback, that.callback) &&
                Objects.equals(nextWrappers, that.nextWrappers) &&
                Objects.equals(dependWrappers, that.dependWrappers) &&
                Objects.equals(state, that.state) &&
                Objects.equals(workResult, that.workResult);
    }
    @Override
    public int hashCode() {
        return Objects.hash(param, worker, callback, nextWrappers, dependWrappers, state, workResult, needCheckNextWrapperResult);
    }
}
