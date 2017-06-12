# 入口

不同于hotspots实战一书中所说的gamma调试入口，openjdk8已经将此launcher取消。openjdk8的入口位于于hotspot目录同级的jdk目录下，在我的Mac上此目录为:

/Users/skywalker/softwares/openjdk-8-src-b132-03_mar_2014/jdk/src/share/bin/main.c

去掉为不同平台而设置的各种条件编译，main函数源码整理如下:

```c
int main(int argc, char **argv) {
    int margc;
    char** margv;
    const jboolean const_javaw = JNI_FALSE;
  	margc = argc;
    margv = argv;
    return JLI_Launch(margc, margv,
                   sizeof(const_jargs) / sizeof(char *), const_jargs,
                   sizeof(const_appclasspath) / sizeof(char *), const_appclasspath,
                   FULL_VERSION,
                   DOT_VERSION,
                   (const_progname != NULL) ? const_progname : *margv,
                   (const_launcher != NULL) ? const_launcher : *margv,
                   (const_jargs != NULL) ? JNI_TRUE : JNI_FALSE,
                   const_cpwildcard, const_javaw, const_ergo_class);
}
```

