package com.cong.async.executor;

import com.cong.async.wrapper.WorkerWrapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 类入口，可以根据自己情况调整 core 线程的数量
 *
 * @author cong
 * @date 2024/04/28
 */
public class Async {
    /**
     * 默认不定长线程池
     */
    private static final ThreadPoolExecutor COMMON_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    /**
     * 注意，这里是个static，也就是只能有一个线程池。用户自定义线程池时，也只能定义一个
     */
    private static ExecutorService executorService;

    /**
     * 开始
     *
     * @param timeout        超时
     * @param executor       执行者
     * @param workerWrappers 工人包装器
     * @return boolean
     * @throws ExecutionException   执行异常
     * @throws InterruptedException 中断异常
     */
    public static boolean start(long timeout, ExecutorService executor, List<WorkerWrapper> workerWrappers) throws ExecutionException, InterruptedException {
        if(workerWrappers == null || workerWrappers.isEmpty()) {
            return false;
        }
        //保存线程池变量
        Async.executorService = executor;
        //定义一个map，存放所有的wrapper，key为wrapper的唯一id，value是该wrapper，可以从value中获取wrapper的result
        Map<String, WorkerWrapper> forParamUseWrappers = new ConcurrentHashMap<>();
        CompletableFuture[] futures = new CompletableFuture[workerWrappers.size()];


        //遍历workerWrappers，创建CompletableFuture，并添加到futures数组中
        for (int i = 0; i < workerWrappers.size(); i++) {
            WorkerWrapper wrapper = workerWrappers.get(i);
            futures[i] = CompletableFuture.runAsync(() -> wrapper.work(executor,timeout,forParamUseWrappers), executor);
        }

        try {
            CompletableFuture.allOf(futures).get(timeout, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            Set<WorkerWrapper> set = new HashSet<>();
            totalWorkers(workerWrappers, set);
            for (WorkerWrapper wrapper : set) {
                wrapper.stopNow();
            }
            return false;
        }
    }

    /**
     * 如果想自定义线程池，请传 pool。不自定义的话，就走默认的 COMMON_POOL
     */
    public static boolean start(long timeout, ExecutorService executorService, WorkerWrapper... workerWrapper) throws ExecutionException, InterruptedException {
        if(workerWrapper == null || workerWrapper.length == 0) {
            return false;
        }
        List<WorkerWrapper> workerWrappers =  Arrays.stream(workerWrapper).collect(Collectors.toList());
        return start(timeout, executorService, workerWrappers);
    }

    /**
     * 同步阻塞,直到所有都完成,或失败（用默认线程池）
     */
    public static boolean start(long timeout, WorkerWrapper... workerWrapper) throws ExecutionException, InterruptedException {
        return start(timeout, COMMON_POOL, workerWrapper);
    }

    /**
     * 总共多少个执行单元
     */
    @SuppressWarnings("unchecked")
    private static void totalWorkers(List<WorkerWrapper> workerWrappers, Set<WorkerWrapper> set) {
        set.addAll(workerWrappers);
        for (WorkerWrapper wrapper : workerWrappers) {
            if (wrapper.getNextWrappers() == null) {
                continue;
            }
            List<WorkerWrapper> wrappers = wrapper.getNextWrappers();
            totalWorkers(wrappers, set);
        }

    }
}
