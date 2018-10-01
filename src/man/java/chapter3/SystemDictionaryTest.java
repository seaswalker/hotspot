package chapter3;

import sun.jvm.hotspot.memory.SystemDictionary;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.tools.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 获取{@link sun.jvm.hotspot.memory.SystemDictionary}.
 *
 * @author skywalker
 */
public class SystemDictionaryTest extends Tool {

    public static void main(String[] args) {
        SystemDictionaryTest test = new SystemDictionaryTest();
        test.start();
    }

    /**
     * 练习13：统计每种状态的类的数量.
     */
    private synchronized void groupByStatus() {
        final List<Klass> classes = new ArrayList<>();

        SystemDictionary systemDictionary = VM.getVM().getSystemDictionary();
        systemDictionary.classesDo(klass -> {
            if (klass instanceof InstanceKlass) {
                classes.add(klass);
            }
        });

        classes.sort((left, right) -> {
            Symbol leftSymbol = left.getName();
            Symbol rightSymbol = right.getName();
            return leftSymbol.asString().compareTo(rightSymbol.asString());
        });

        classes.forEach(klass -> {
            klass.getName().printValueOn(System.out);
            System.out.println();
        });
    }

    @Override
    public void run() {
        groupByStatus();
    }
}
