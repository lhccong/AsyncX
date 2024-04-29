package depend;



import com.cong.async.callback.ICallback;
import com.cong.async.callback.IWorker;
import com.cong.async.worker.WorkResult;
import com.cong.async.wrapper.WorkerWrapper;

import java.util.Map;

/**
 * de worker
 *
 * @author cong
 * @date 2024/04/29
 */
public class DeWorker implements IWorker<String, User>, ICallback<String, User> {

    @Override
    public User action(String object, Map<String, WorkerWrapper> allWrappers) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        return new User("我是聪");
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
    public void result(boolean success, String param, WorkResult<User> workResult) {
        System.out.println("worker 的结果是：" + workResult.getResult());
    }

}
