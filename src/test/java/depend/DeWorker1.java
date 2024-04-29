package depend;



import com.cong.async.callback.ICallback;
import com.cong.async.callback.IWorker;
import com.cong.async.worker.WorkResult;
import com.cong.async.wrapper.WorkerWrapper;

import java.util.Map;

/**
 * de worker1
 *
 * @author cong
 * @date 2024/04/29
 */
public class DeWorker1 implements IWorker<WorkResult<User>, User>, ICallback<WorkResult<User>, User> {

    @Override
    public User action(WorkResult<User> result, Map<String, WorkerWrapper> allWrappers) {
        System.out.println("DeWorker1的入参来自于DeWorker的结果： " + result.getResult());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        return new User("user1");
    }


    @Override
    public User defaultValue() {
        return new User("默认用户啦");
    }

    @Override
    public void begin() {
        System.out.println(Thread.currentThread().getName() + "- start --" + System.currentTimeMillis());
    }

    @Override
    public void result(boolean success, WorkResult<User> param, WorkResult<User> workResult) {
        System.out.println("DeWorker1 的结果是：" + workResult.getResult());
    }

}
