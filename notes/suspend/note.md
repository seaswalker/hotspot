# 说明

在读到《Hotspot实战》第二章的练习7时，因为题目的主题是JVM信号初始化过程，而且信号的初始化函数名为SR_initialize，SR的含义为Suspend/Resume，所以想到了一个问题：

这与Java中Thread的Suspend/Resume方法有什么关系呢，下面首先探究一下Java到底是如何实现Suspend/Resume语义的。

# 用法

简单写了一个使用方法，位于chapter2/Suspend中。

# 废弃

原因是线程被suspend后不会自动释放锁，从而极易导致死锁。

# Suspend

## 调用链

### Java

Java层面其实只是调用了对应的native方法：

```java
@Deprecated
public final void suspend() {
    suspend0();
}
```

### Native

位于src/share/native/java/lang/Thread.c中，如下:

```c++
static JNINativeMethod methods[] = {
    {"suspend0",         "()V",        (void *)&JVM_SuspendThread},
    {"resume0",          "()V",        (void *)&JVM_ResumeThread}
};
```

注册函数:

```c++
JNIEXPORT void JNICALL
Java_java_lang_Thread_registerNatives(JNIEnv *env, jclass cls)
{
    (*env)->RegisterNatives(env, cls, methods, ARRAY_LENGTH(methods));
}
```

### JVM

像此种JVM开头的函数实现均位于jdk7u-hotspot-jdk7u6-b08/src/share/vm/prims/jvm.cpp，如下:

```c++
JVM_ENTRY(void, JVM_SuspendThread(JNIEnv* env, jobject jthread))
  oop java_thread = JNIHandles::resolve_non_null(jthread);
  JavaThread* receiver = java_lang_Thread::thread(java_thread);

  if (receiver != NULL) {
    {
      MutexLockerEx ml(receiver->SR_lock(), Mutex::_no_safepoint_check_flag);
      if (receiver->is_external_suspend()) {
        // Don't allow nested external suspend requests. We can't return
        // an error from this interface so just ignore the problem.
        return;
      }
      if (receiver->is_exiting()) { // thread is in the process of exiting
        return;
      }
      receiver->set_external_suspend();
    }

    // java_suspend() will catch threads in the process of exiting
    // and will ignore them.
    receiver->java_suspend();
  }
JVM_END
```

## 实现分析

由两个核心逻辑，**第一步是修改线程的挂起标志，第二步是使线程进入SafePoint**。

### 挂起标志

jdk7u-hotspot-jdk7u6-b08/src/share/vm/runtime/thread.hpp中实现:

```c++
void set_external_suspend()     { set_suspend_flag  (_external_suspend); }
```

set_suspend_flag接收一个SuspendFlags枚举类型，定义如下:

```c++
enum SuspendFlags {
    _external_suspend       = 0x20000000U, // thread is asked to self suspend
    _ext_suspended          = 0x40000000U, // thread has self-suspended
    _deopt_suspend          = 0x10000000U, // thread needs to self suspend for deopt

    _has_async_exception    = 0x00000001U, // there is a pending async exception
    _critical_native_unlock = 0x00000002U  // Must call back to unlock JNI critical lock
};
```

set_suspend_flags:

```c++
void set_suspend_flag(SuspendFlags f) {
    uint32_t flags;
    do {
        flags = _suspend_flags;
    }
    while (Atomic::cmpxchg((jint)(flags | f),
                            (volatile jint*)&_suspend_flags,
                            (jint)flags) != (jint)flags);
}
```

就是将我们传入的标志位用CAS的方式更新到每个线程的属性_suspend_flags中，定义如下:

```c++
volatile uint32_t _suspend_flags;
```

所以，我们可以得出结论：suspend是一个多线程互相协作的过程。

### SafePoint

引用官方的解释:

> A point during program execution at which all GC roots are known and all heap object contents are consistent. From a global point of view, all threads must block at a safepoint before the GC can run.

也就是说：

- 在进行GC之前，所有线程必须停在SafePoint中
- 只有到达SafePoint后线程才可以安全挂起

此操作实现位于jdk7u-hotspot-jdk7u6-b08/src/share/vm/runtime/thread.cpp的java_suspend函数:

```c++
void JavaThread::java_suspend() {
    // ...省略
    VM_ForceSafepoint vm_suspend;
    VMThread::execute(&vm_suspend);
}
```

这里又引入了一个JVM中核心的组件：VMThread，JVM中各种(可能)核心操作均通过此线程来完成或触发，比如垃圾回收、打印线程堆栈。

其内部含有一个任务队列，类型为VMOperationQueue，所有需要通过VMThread执行的操作均要通过此队列来传递，所以这是一个典型的生产者消费者模式。

由VMThread执行的操作均是VM_Operation的子类，比如ThreadDump、CMS_Initial_Mark。

VM_ForceSafepoint的定义如下:

```c++
// dummy vm op, evaluated just to force a safepoint
class VM_ForceSafepoint: public VM_Operation {
 public:
  VM_ForceSafepoint() {}
  void doit()         {}
  VMOp_Type type() const { return VMOp_ForceSafepoint; }
};
```

进入SafePoint的具体代码非常复杂，不再展开，可参考:

1. [JVM源码分析之安全点safepoint](https://www.jianshu.com/p/c79c5e02ebe6)
2. [聊聊JVM（六）理解JVM的safepoint](https://blog.csdn.net/iter_zc/article/details/41847887)

### 疑问

看了上面的源码，可以发现，suspend并没有对目标线程执行任何直接的挂起操作，那是如何让目标线程停下的呢?

其实JVM中每个Java线程都会去检查我们上面设置的挂起标志。

### 自我挂起

当目标线程发现挂起标志被设置时，便会进入thread.cpp的java_suspend_self函数:

```c++
int JavaThread::java_suspend_self() {
  int ret = 0;

  // we are in the process of exiting so don't suspend
  if (is_exiting()) {
     clear_external_suspend();
     return ret;
  }

  MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);

  if (this->is_suspend_equivalent()) {
    this->clear_suspend_equivalent();
  }

  while (is_external_suspend()) {
    ret++;
    this->set_ext_suspended();

    // _ext_suspended flag is cleared by java_resume()
    while (is_ext_suspended()) {
      this->SR_lock()->wait(Mutex::_no_safepoint_check_flag);
    }
  }

  return ret;
}
```

那么这个标志何时才会被检查呢?

这一点从thread.hpp的注释中有体现:

>The external_suspend
>flag is checked by has_special_runtime_exit_condition() and java thread
>will self-suspend when handle_special_runtime_exit_condition() is
>called. Most uses of the _thread_blocked state in JavaThreads are
>considered the same as being externally suspended; if the blocking
>condition lifts, the JavaThread will self-suspend. Other places
>where VM checks for external_suspend include:
>
>  + mutex granting (do not enter monitors when thread is suspended)
>  + state transitions from _thread_in_native
>
>In general, java_suspend() does not wait for an external suspend
>request to complete. When it returns, the only guarantee is that
>the _external_suspend field is true.

重点关注最后一句，就是java_suspend_self返回时只保证标志已被设置，不能保证suspend请求已经完成，但是java_suspend方法上的注释又有如下的说明:

>Guarantees on return:
>  + Target thread will not execute any new bytecode (that's why we need to force a safepoint)
>  + Target thread will not enter any new monitors

所以，猜测**一次强制的进入-退出safe point的过程会让目标线程注意到挂起标志已被设置，所以不会有新的字节码被执行，但是此时又不能确定挂起的操作已经完成**。

### Wait

等待由ObjectMonitor::wait实现，这里的实现方式其实和JUC里面的AQS类似，不再详细说明，可以参考:

[JVM源码分析之Object.wait/notify实现](https://www.jianshu.com/p/f4454164c017)

值得一提的是对于SR操作，monitor对象定义于thread.hpp中，如下:

```c++
// suspend/resume lock: used for self-suspend
Monitor* _SR_lock;
```

# Resume

调用链和suspend大体一致，这里直接看其JVM实现:

```c++
void JavaThread::java_resume() {
  assert_locked_or_safepoint(Threads_lock);

  // Sanity check: thread is gone, has started exiting or the thread
  // was not externally suspended.
  if (!Threads::includes(this) || is_exiting() || !is_external_suspend()) {
    return;
  }

  MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);

  clear_external_suspend();

  if (is_ext_suspended()) {
    clear_ext_suspended();
    SR_lock()->notify_all();
  }
}
```

就是常见的在锁的保护下通知等待线程的操作。

# 参考

- [JVM安全点介绍](https://www.ezlippi.com/blog/2018/01/safepoint.html)
- [形形色色的锁2](https://www.jianshu.com/p/5fa358431b68)
- [JVM 内部运行线程介绍](http://ifeve.com/jvm-thread/)
- 