package chapter4;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 产生{@link LinkageError}.
 *
 * @author skywalker
 */
public class LinkageErrorExample {

    /**
     * 仅加载给定的类，其余的交给父加载器来.
     */
    private static class CustomClassLoader extends ClassLoader {

        private final String name;
        private final Set<String> loadableClasses;

        private static Logger logger = Logger.getLogger(CustomClassLoader.class.getName());

        private CustomClassLoader(String name, Set<String> loadableClasses) {
            this.name = name;
            this.loadableClasses = loadableClasses;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!loadableClasses.contains(name)) {
                logger.info("Class: " + name + "交由父加载器加载.");
                return super.findClass(name);
            }

            final String path = name.replaceAll("\\.", "/") + ".class";
            try (InputStream is = LinkageErrorExample.class.getResourceAsStream(path)) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                IOUtils.copy(is, os);
                byte[] data = os.toByteArray();
                return defineClass(name, data, 0, data.length);
            } catch (IOException e) {
                logger.severe("读取类文件失败，类名: " + name + ".");
                e.printStackTrace();
            }

            throw new ClassNotFoundException(name);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> loaded;
            if ((loaded = findLoadedClass(name)) != null) {
                logger.info("类: " + name + "已经被加载.");
                return loaded;
            }

            if (loadableClasses.contains(name)) {
                return findClass(name);
            }

            return super.loadClass(name);
        }

    }

}
