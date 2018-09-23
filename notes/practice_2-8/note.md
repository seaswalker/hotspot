jdk7u-hotspot-jdk7u6-b08/src/share/vm/runtime/globals.hpp中的ThreadPriorityPolicy定义已经说得非常清楚了:

```c++
product(intx, ThreadPriorityPolicy, 0,                                    \
          "0 : Normal.                                                     "\
          "    VM chooses priorities that are appropriate for normal       "\
          "    applications. On Solaris NORM_PRIORITY and above are mapped "\
          "    to normal native priority. Java priorities below NORM_PRIORITY"\
          "    map to lower native priority values. On Windows applications"\
          "    are allowed to use higher native priorities. However, with  "\
          "    ThreadPriorityPolicy=0, VM will not use the highest possible"\
          "    native priority, THREAD_PRIORITY_TIME_CRITICAL, as it may   "\
          "    interfere with system threads. On Linux thread priorities   "\
          "    are ignored because the OS does not support static priority "\
          "    in SCHED_OTHER scheduling class which is the only choice for"\
          "    non-root, non-realtime applications.                        "\
          "1 : Aggressive.                                                 "\
          "    Java thread priorities map over to the entire range of      "\
          "    native thread priorities. Higher Java thread priorities map "\
          "    to higher native thread priorities. This policy should be   "\
          "    used with care, as sometimes it can cause performance       "\
          "    degradation in the application and/or the entire system. On "\
          "    Linux this policy requires root privilege.")         
```

默认就是0，在Linux上为线程指定优先级其实没有意义，因为默认的分时调度策略(CFS调度算法)根本就不是静态优先级，而是基于nice值的权重再加上动态计算。

三种调度策略如下:

- SCHED_OTHER 分时调度策略(默认)
- SCHED_FIFO实时调度策略
- SCHED_RR实时调度策略，时间片轮转

参考: [linux进程/线程调度策略(SCHED_OTHER,SCHED_FIFO,SCHED_RR)](https://blog.csdn.net/u012007928/article/details/40144089)