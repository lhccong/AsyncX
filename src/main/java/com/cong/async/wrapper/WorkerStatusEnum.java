package com.cong.async.wrapper;

/**
 * 工作线程状态枚举
 *
 * @author cong
 * @date 2024/04/28
 */
public enum WorkerStatusEnum {
    INIT(0),
    FINISH(1),
    ERROR(2),
    WORKING(3);

    private final int value;

    WorkerStatusEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
