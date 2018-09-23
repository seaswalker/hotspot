此部分关于练习2-7。

# 调用链

核心实现为jdk7u-hotspot-jdk7u6-b08/src/os/linux/vm/os_linux.cpp的SR_initialize函数，被同一个文件中os::init_2函数调用。

# 意义

在os_linux.cpp的3447行开始的一段注释准确的说明了其意义:

>  suspend/resume support
>  the low-level signal-based suspend/resume support is a remnant from the
>  old VM-suspension that used to be for java-suspension, safepoints etc,
>  within hotspot. Now there is a single use-case for this:
>
>    - calling get_thread_pc() on the VMThread by the flat-profiler task
>      that runs in the watcher thread.
>       The remaining code is greatly simplified from the more general suspension
>       code that used to be used.
>       The protocol is quite simple:
>  - suspend:
>      - sends a signal to the target thread
>      - polls the suspend state of the osthread using a yield loop
>      - target thread signal handler (SR_handler) sets suspend state
>        and blocks in sigsuspend until continued
>  - resume:
>      - sets target osthread state to continue
>      - sends signal to end the sigsuspend loop in the SR_handler
>         Note that the SR_lock plays no role in this suspend/resume protocol.

很显然，在目前的JVM中其实只有一个用途:

Watcher线程的flat-profiler任务会调用VMThread的get_thread_pc方法，从而将VMThread暂时挂起，目的可能是获取其PC寄存器的位置?

# 分析

```c++
ExtendedPC os::get_thread_pc(Thread* thread) {
  // Make sure that it is called by the watcher for the VMThread
  assert(Thread::current()->is_Watcher_thread(), "Must be watcher");
  assert(thread->is_VM_thread(), "Can only be called for VMThread");

  ExtendedPC epc;

  OSThread* osthread = thread->osthread();
  if (do_suspend(osthread)) {
    if (osthread->ucontext() != NULL) {
      epc = os::Linux::ucontext_get_pc(osthread->ucontext());
    } else {
      // NULL context is unexpected, double-check this is the VMThread
      guarantee(thread->is_VM_thread(), "can only be called for VMThread");
    }
    do_resume(osthread);
  }
  // failure means pthread_kill failed for some reason - arguably this is
  // a fatal problem, but such problems are ignored elsewhere

  return epc;
}
```

do_suspend实现:

```c++
static bool do_suspend(OSThread* osthread) {
  // mark as suspended and send signal
  osthread->sr.set_suspend_action(SR_SUSPEND);
  int status = pthread_kill(osthread->pthread_id(), SR_signum);
  // ...
}
```

pthread_kill是库函数，向目标线程(其实就是VMThread)发送这个我们可以通过_JAVA_SR_SIGNUM环境变量指定的信号。