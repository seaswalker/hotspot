使用的系统环境: Fedora25 32位版，openjdk-8-src-b132-03_mar_2014。

# 准备

## jdk7

OpenJDK8的编译需要jdk7作为引导jdk，Fedora自带了jdk8，需要先将其卸载:

Fedora25采用dnf作为默认的包管理器，但yum同样可以使用，且两者的命令格式几乎一致。

```shell
dnf list installed | grep java
```

查找得到自带jdk包名，然后使用命令:

```shell
dnf remove java-1.8.0-openjdk-headless.i686(在我的32机上为此名)
```

官方现已不再提供jdk7的下载，下列地址可用:

[jdk-7-linux-i586.rpm](http://download.csdn.net/download/fujx333/4261506)

使用命令`rpm -ivh jdk-7-linux-i586.rpm `安装即可，如遇到缺少依赖包，在联网的状况下使用dnf或yum命令安装即可。

## 依赖

在OpenJDK源码路径下执行:

```shell
./configure --with-debug-level=slowdebug
```

缺少依赖时根据提示的包名使用dnf或yum安装即可。

# 编译

`make all CONF=linux-x86-normal-server-slowdebug`

注意，不同版本机器上的配置名(?)不一致，在32系统上为linux-x86-normal-server-slowdebug，此名称在configure完成之后的提示中可以看到。下面便细数过程中遇到的坑。

## 内核版本

Fedora 25的内核版本为4.X，默认情况不支持此版本，我们可以修改文件hotspot/make/linux/Makefile的第228行，由:

```shell
SUPPORTED_OS_VERSION = 2.4% 2.5% 2.6% 3%
```

修改为:

```shell
SUPPORTED_OS_VERSION = 2.4% 2.5% 2.6% 3% 4%
```

即可。

## 编译警告提升

默认进行编译时编译器会将警告当做错误来处理致使编译不通过，打开文件hotspot/make/linux/makefiles/gcc.make，将第209行注释掉即可:

```shell
# Compiler warnings are treated as errors
# WARNINGS_ARE_ERRORS = -Werror
```

## 类型转换错误

hotspot源码中存在从unsigned int到int(即jint)类型的强制装换，这在C++11中会报错，而目前新版系统的gcc都是以C++11标准进行编译，我们需要在hotspot/make/linux/makefiles/gcc.make的开头加上下面一行:

```shell
CFLAGS += -Wno-narrowing
```

## 负数左移

C++11标准会对针对负数的左移操作进行报错，如下所示:

> error: left operand of shift expression ‘(-1 << 28)’ is negative [-fpermissive]

同样在hotspot/make/linux/makefiles/gcc.make的开头加上:

```shell
CFLAGS += -fpermissive
```

## 非法参数

如在make时遇到下列错误:

>/usr/bin/make: invalid option -- '/'
>/usr/bin/make: invalid option -- 'a'
>/usr/bin/make: invalid option -- '/'
>/usr/bin/make: invalid option -- 'c'

这是由高版本的make引起的hotspot bug，解决办法是修改文件hotspot/make/linux/makefiles/adjust-mflags.sh的第67行，由:

```shell
s/ -\([^        ][^    ]*\)j/ -\1 -j/
```

修改为:

```shell
s/ -\([^        I][^    I]*\)j/ -\1 -j/
```

## 宏定义错误

C++11规定宏定义中字符串和变量之间必须用空格分隔，hotspot源码并未遵循C++11标准，假设错误输出如下:

>/home/skywalker/softwares/openjdk-8-src-b132-03_mar_2014/hotspot/src/share/vm/prims/unsafe.cpp:1321:17: 错误：unable to find string literal operator ‘operator""OBJ’ with ‘const char [40]’, ‘unsigned int’ arguments
>
>\#define CLS LANG"Class;"

打开unsafe.cpp的1321行，将内容由:

```c++
#define CLS LANG"Class;"
```

修改为:

```c++
#define CLS LANG "Class;"
```

特别注意unsafe.cpp报的这个错误，对于其它文件可以只修改报错的那一行，但是unsafe.cpp不行，这里需要**将整个文件中所有未加空格的地方补上空格**，只要有一处未修正便会出现上述错误，这个问题已经在jdk9中得到了修正:

[[PATCH RFC 4/5] fix build errors with gcc6](http://mail.openjdk.java.net/pipermail/build-dev/2016-May/017171.html)

但jdk8并未进行更新，jdk9修正后的此文件的地址为: 

[unsafe.cpp @ 12774:385668275400](http://hg.openjdk.java.net/jdk9/jdk9/hotspot/file/385668275400/src/share/vm/prims/unsafe.cpp)

注意，这里不能直接将jdk9的此文件替换进入jdk8的源码，因为改动不止这一处。

## UNIXProcess.java.linux

>gmake[2]: *** No rule to make target '/home/skywalker/softwares/openjdk-8-src-b132-03_mar_2014/jdk/src/solaris/classes/java/lang/UNIXProcess.java.linux', needed by '/home/skywalker/softwares/openjdk-8-src-b132-03_mar_2014/build/linux-x86-normal-server-slowdebug/jdk/gensrc/java/lang/UNIXProcess.java'。 停止。
>BuildJdk.gmk:55: recipe for target 'gensrc-only' failed
>gmake[1]: *** [gensrc-only] Error 2
>/home/skywalker/softwares/openjdk-8-src-b132-03_mar_2014//make/Main.gmk:115: recipe for target 'jdk-only' failed
>make: *** [jdk-only] Error 2