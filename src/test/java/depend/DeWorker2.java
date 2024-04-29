package depend;



import com.cong.async.callback.ICallback;
import com.cong.async.callback.IWorker;
import com.cong.async.worker.WorkResult;
import com.cong.async.wrapper.WorkerWrapper;

import java.util.Map;

/**
 * @author wuweifeng wrote on 2019-11-20.
 */
public class DeWorker2 implements IWorker<WorkResult<User>, String>, ICallback<WorkResult<User>, String> {

    @Override
    public String action(WorkResult<User> result, Map<String, WorkerWrapper> allWrappers) {
        System.out.println("DeWorker2的入参来自于DeWorker1： " + result.getResult());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        return result.getResult().getName();
    }


    @Override
    public String defaultValue() {
        return "默认用户值";
    }

    @Override
    public void begin() {
        System.out.println(Thread.currentThread().getName() + "- start --" + System.currentTimeMillis());
    }

    @Override
    public void result(boolean success, WorkResult<User> param, WorkResult<String> workResult) {
        System.out.println("DeWorker2 的结果是：" + workResult.getResult());
    }

}
