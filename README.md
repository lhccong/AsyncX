<h1 align="center"><a href="https://github.com/lhccong/AsyncX" target="_blank">AsyncX</a></h1>
<p align="center">
  <a href="https://www.oracle.com/technetwork/java/javase/downloads/index.html" target="_blank"><img alt="JDK" src="https://img.shields.io/badge/JDK-1.8.0_162-orange.svg"/></a>
</p>

## 简介

> 解决任意的多线程并行、串行、阻塞、依赖、回调的并行框架，可以任意组合各线程的执行顺序，带全链路执行结果回调。多线程编排一站式解决方案。

```xml
<dependency>
  <groupId>com.cong.async</groupId>
  <artifactId>AsyncX</artifactId>
  <version>1.0.0</version>
</dependency>
```

## 特点

- 解决任意的多线程并行、串行、阻塞、依赖、回调的并发框架，可以任意组合各线程的执行顺序，带全链路回调和超时控制。

- 其中的A、B、C分别是一个最小执行单元（worker），可以是一段耗时代码、一次Rpc调用等，不局限于你做什么。

- 该框架可以将这些worker，按照你想要的各种执行顺序，加以组合编排。最终得到结果。 并且，该框架 为每一个worker都提供了执行结果的回调和执行失败后自定义默认值 。譬如A执行完毕后，A的监听器会收到回调，带着A的执行结果（成功、超时、异常）。

- 根据你的需求，将各个执行单元组合完毕后，开始在主线程执行并阻塞，直到最后一个执行完毕。并且 可以设置全组的超时时间 。

该框架支持后面的执行单元以前面的执行单元的结果为自己的入参 。譬如你的执行单元B的入参是ResultA，ResultA就是A的执行结果，那也可以支持。在编排时，就可以预先设定B或C的入参为A的result，即便此时A尚未开始执行。当A执行完毕后，自然会把结果传递到B的入参去。
```

## TODO

