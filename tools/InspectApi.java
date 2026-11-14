import java.lang.reflect.*;

/** Local SDK inspection helper; never packaged in the plugin. */
public class InspectApi {
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            Class<?> type = Class.forName(name, false, InspectApi.class.getClassLoader());
            System.out.println("\n" + type);
            for (Field f : type.getDeclaredFields()) if (Modifier.isPublic(f.getModifiers()) || Modifier.isProtected(f.getModifiers())) System.out.println(f);
            for (Constructor<?> c : type.getDeclaredConstructors()) if (Modifier.isPublic(c.getModifiers()) || Modifier.isProtected(c.getModifiers())) System.out.println(c);
            for (Method m : type.getDeclaredMethods()) if (Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers())) System.out.println(m);
        }
    }
}
