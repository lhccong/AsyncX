package com.cong.async.worker;


/**
 * 结果状态
 *
 * @author cong
 * @date 2024/04/28
 */
public enum ResultState {
    SUCCESS,//成功
    TIMEOUT,//超时
    EXCEPTION,//异常
    DEFAULT  //默认状态
}
