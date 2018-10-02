第四章练习2，接口方法定位。

源码为jdk7u-hotspot-jdk7u6-b08/src/share/vm/oops/instanceKlass.cpp的lookup_method_in_all_interfaces方法:

```c++
// lookup a method in all the interfaces that this class implements
methodOop instanceKlass::lookup_method_in_all_interfaces(Symbol* name,
                                                         Symbol* signature) const {
  objArrayOop all_ifs = instanceKlass::cast(as_klassOop())->transitive_interfaces();
  int num_ifs = all_ifs->length();
  instanceKlass *ik = NULL;
  for (int i = 0; i < num_ifs; i++) {
    ik = instanceKlass::cast(klassOop(all_ifs->obj_at(i)));
    methodOop m = ik->lookup_method(name, signature);
    if (m != NULL) {
      return m;
    }
  }
  return NULL;
}
```

即klass中的transitive_interfaces的属性保存着类实现的接口(间接)，所以这里就是遍历查找的过程。这里还有个为解的疑问:

transitive_interfaces和local_interfaces的区别是什么?