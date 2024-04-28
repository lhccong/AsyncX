package com.cong.async.worker;

import com.cong.async.wrapper.WorkerWrapper;

/**
 * 对依赖的 wrapper 的封装
 *
 * @author cong
 * @date 2024/04/28
 */
public class DependWrapper {

    private WorkerWrapper<?, ?> depWrapper;

    /**
     * 是否该依赖必须完成后才能执行自己.<p>
     * 因为存在一个任务，依赖于多个任务，是让这多个任务全部完成后才执行自己，还是某几个执行完毕就可以执行自己
     */
    private boolean must = true;

    public DependWrapper(WorkerWrapper<?, ?> depWrapper, boolean must) {
        this.depWrapper = depWrapper;
        this.must = must;
    }
    public DependWrapper() {
    }

    public WorkerWrapper<?, ?> getDepWrapper() {
        return depWrapper;
    }

    public void setDepWrapper(WorkerWrapper<?, ?> depWrapper) {
        this.depWrapper = depWrapper;
    }

    public boolean isMust() {
        return must;
    }

    public void setMust(boolean must) {
        this.must = must;
    }

    @Override
    public String toString() {
        return "DependWrapper{" +
                "dependWrapper=" + depWrapper +
                ", must=" + must +
                '}';
    }

}
