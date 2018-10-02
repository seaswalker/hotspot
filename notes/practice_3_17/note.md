第三章练习17--HSDB探究，新版的ensemble貌似没有Sample类了，所以下面都用Thread类作为示例。文件可以到这里下载:

[Ensemble](http://download.oracle.com/otndocs/products/javafx/8/samples/Ensemble/Ensemble.jar)

# OOP

这里使用图形界面启动:

```shell
sudo java -cp ,:/Library/Java/JavaVirtualMachines/jdk1.8.0_65.jdk/Contents/Home/lib/sa-jdi.jar sun.jvm.hotspot.HSDB
```

然后选择Window-console便可以用命令的方式操作。

查找OOP的过程比较简单，首先attach到进程：

```shell
attach 24694
```

然后获取堆内存分布:

```shell
universe
```

得到的结果如下所示:

```html
universe
Heap Parameters:
ParallelScavengeHeap 
[ PSYoungGen [ eden =  [0x00000001c0900000,0x00000001c248d680,0x00000001c8900000] , from =  [0x00000001c9380000,0x00000001c9380000,0x00000001c9e00000] , to =  [0x00000001c8900000,0x00000001c8900000,0x00000001c9380000]  ] 
PSOldGen [  [0x0000000115e00000,0x000000011700fc20,0x000000011e480000]  ]  ]
```

而后在老年代中查找Thread的对象(OOPS):

```shell
scanoops 0x0000000115e00000 0x000000011e480000 java/lang/Thread
```

得到的结果:

```html
0x0000000116646db8 java/lang/Thread
0x00000001166470e8 java/lang/Thread
0x0000000116705f70 java/lang/Thread
0x00000001167061b8 java/lang/ref/Finalizer$FinalizerThread
0x00000001167063f8 java/lang/ref/Reference$ReferenceHandler
0x0000000116706710 java/lang/Thread
0x0000000116707170 java/lang/Thread
0x0000000116723a48 java/lang/Thread
0x0000000116723c78 java/lang/Thread
0x0000000116725100 com/sun/glass/ui/InvokeLaterDispatcher
0x0000000116725e20 com/sun/javafx/tk/quantum/QuantumToolkit$1
0x00000001167263a8 java/lang/Thread
0x00000001167f8c08 java/lang/Thread
0x00000001167f8e48 java/lang/Thread
0x00000001167f9298 java/lang/Thread
0x00000001167f9868 java/lang/Thread
0x00000001167f9ee8 java/lang/Thread
0x0000000116bcb390 java/lang/Thread
0x0000000116fb60e0 java/util/logging/LogManager$Cleaner
```

我们以ReferenceHandler为例，查看其分布:

```shell
inspect 0x00000001167063f8
```

如下:

```html
instance of Oop for java/lang/ref/Reference$ReferenceHandler @ 0x00000001167063f8 @ 0x00000001167063f8 (size = 432)
_mark: 17
_metadata._klass: InstanceKlass for java/lang/ref/Reference$ReferenceHandler
name: [C @ 0x00000001167065a8 Oop for [C @ 0x00000001167065a8
priority: 10
threadQ: null null
eetop: 140687366494208
single_step: false
daemon: true
stillborn: false
target: null null
group: Oop for java/lang/ThreadGroup @ 0x0000000116646c98 Oop for java/lang/ThreadGroup @ 0x0000000116646c98
contextClassLoader: null null
inheritedAccessControlContext: Oop for java/security/AccessControlContext @ 0x00000001167065e8 Oop for java/security/AccessControlContext @ 0x00000001167065e8
threadLocals: null null
inheritableThreadLocals: null null
stackSize: 0
nativeParkEventPointer: 0
tid: 2
threadStatus: 401
parkBlocker: null null
blocker: null null
blockerLock: Oop for java/lang/Object @ 0x0000000116706630 Oop for java/lang/Object @ 0x0000000116706630
uncaughtExceptionHandler: null null
threadLocalRandomSeed: 0
threadLocalRandomProbe: 0
threadLocalRandomSecondarySeed: 0
```

# instanceKlass

OOPS的metadata中存有指向对象对应的instanceKlass的地址:

```shell
mem 0x00000001167063f8 2
```

得到:

```html
0x00000001167063f8: 0x0000000000000011 
0x0000000116706400: 0x000000021f43a240
```

内存地址0x000000021f43a240便是instanceKlass，使用命令:

```shell
inspect 0x000000021f43a240
```

部分结果如下:

```html
Type is InstanceKlass (size of 440)
juint Klass::_super_check_offset: 56
Klass* Klass::_secondary_super_cache: Klass @ null
Array<Klass*>* Klass::_secondary_supers: Array<Klass*> @ 0x000000021f3898c0
Klass* Klass::_primary_supers[0]: Klass @ 0x000000021f358c00
oop Klass::_java_mirror: Oop for java/lang/Class @ 0x0000000116f30dc8 Oop for java/lang/Class @ 0x0000000116f30dc8
```

注意，**HSDB不支持32位压缩指针**，所以必须使用JVM参数-XX:-UseCompressedOops将此特性关闭，参考:

[借HSDB来探索HotSpot VM的运行时数据](http://rednaxelafx.iteye.com/blog/1847971)

# 子类instanceKlass

通过_subklass属性