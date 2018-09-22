package chapter2;

import java.util.concurrent.TimeUnit;

/**
 * 测试{@link Thread#suspend()}.
 *
 * @author skywalker
 */
public class Suspend {

    public static void main(String[] args) throws InterruptedException {
        Thread child = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                System.out.println(i);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        child.start();

        TimeUnit.SECONDS.sleep(2);

        System.out.println("挂起子线程");
        child.suspend();
        System.out.println("挂起后子线程状态: " + child.getState());
        TimeUnit.SECONDS.sleep(5);
        System.out.println("解除子线程挂载");
        child.resume();

        child.join();
    }

}
