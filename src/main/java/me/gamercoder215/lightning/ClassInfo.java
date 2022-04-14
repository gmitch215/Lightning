package me.gamercoder215.lightning;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClassInfo {

    public final String name;
    public final String full_name;
    public final MethodInfo[] methods;
    public final String type;
    public final Map<String, Object> modifiers;
    public final AnnotationInfo[] annotations;
    public final FieldInfo[] fields;
    public final ConstructorInfo[] constructors;
    public final TypeInfo[] type_parameters;

    private static String getType(Class<?> clazz) {
        if (clazz.isAnnotation()) return "annotation";
        if (clazz.isInterface()) return "interface";
        if (clazz.isEnum()) return "enum";
        if (clazz.isRecord()) return "record";

        return "class";
    }

    public ClassInfo(Class<?> clazz) {
        this.name = clazz.getSimpleName();
        this.full_name = clazz.getName();
        this.type = getType(clazz);

        List<MethodInfo> methods = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) methods.add(new MethodInfo(m));

        List<TypeInfo> typeParams = new ArrayList<>();
        for (TypeVariable<?> type : clazz.getTypeParameters()) typeParams.add(new TypeInfo(type));
        this.type_parameters = typeParams.toArray(new TypeInfo[0]);

        this.methods = methods.toArray(new MethodInfo[0]);

        int mod = clazz.getModifiers();
        Map<String, Object> modifiers = new HashMap<>();
        modifiers.put("static", Modifier.isStatic(mod));
        modifiers.put("abstract", Modifier.isAbstract(mod));
        modifiers.put("strictfp", Modifier.isStrict(mod));
        modifiers.put("synchronized", Modifier.isSynchronized(mod));
        modifiers.put("final", Modifier.isFinal(mod));
        modifiers.put("transient", Modifier.isTransient(mod));
        modifiers.put("volatile", Modifier.isVolatile(mod));
        modifiers.put("visibility", getVisibility(clazz));
        modifiers.put("native", Modifier.isNative(mod));

        this.modifiers = modifiers;

        List<AnnotationInfo> annotations = new ArrayList<>();
        for (Annotation a : clazz.getAnnotations()) annotations.add(new AnnotationInfo(a));
        this.annotations = annotations.toArray(new AnnotationInfo[0]);

        List<FieldInfo> fields = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) fields.add(new FieldInfo(f));
        this.fields = fields.toArray(new FieldInfo[0]);

        List<ConstructorInfo> constructors = new ArrayList<>();
        for (Constructor<?> c : clazz.getDeclaredConstructors()) constructors.add(new ConstructorInfo(c));
        this.constructors = constructors.toArray(new ConstructorInfo[0]);
    }

    public final static class TypeInfo {

        public final String type_name;
        public final AnnotationInfo[] annotations;

        public TypeInfo(TypeVariable<?> type) {
            this.type_name = type.getTypeName();

            List<AnnotationInfo> annotations = new ArrayList<>();
            for (Annotation a : type.getAnnotations()) annotations.add(new AnnotationInfo(a));
            this.annotations = annotations.toArray(new AnnotationInfo[0]);
    
        }

    }

    public final static class FieldInfo {

        public final String type;
        public final String name;
        public final Map<String, Object> modifiers;
        public final AnnotationInfo[] annotations;
        public final String declaring_class;

        public Object value;

        public FieldInfo(Field f) {
            this.type = f.getType().getName();
            this.name = f.getName();
            this.declaring_class = f.getDeclaringClass().getName();

            if (Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                try {
                    this.value = f.get(null);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else this.value = null;

            int mod = f.getModifiers();
            Map<String, Object> modifiers = new HashMap<>();
            modifiers.put("static", Modifier.isStatic(mod));
            modifiers.put("abstract", Modifier.isAbstract(mod));
            modifiers.put("strictfp", Modifier.isStrict(mod));
            modifiers.put("synchronized", Modifier.isSynchronized(mod));
            modifiers.put("final", Modifier.isFinal(mod));
            modifiers.put("transient", Modifier.isTransient(mod));
            modifiers.put("volatile", Modifier.isVolatile(mod));
            modifiers.put("visibility", getVisibility(f));
            modifiers.put("native", Modifier.isNative(mod));

            this.modifiers = modifiers;

            List<AnnotationInfo> ainfo = new ArrayList<>();
            for (Annotation a : f.getAnnotations()) ainfo.add(new AnnotationInfo(a));

            this.annotations = ainfo.toArray(new AnnotationInfo[0]);
        }

    }

    public static final class AnnotationInfo {

        public final String type;
        public final FieldInfo[] fields;
        public final MethodInfo[] methods;

        public AnnotationInfo(Annotation a) {
            this.type = a.annotationType().getName();
            
            List<FieldInfo> fields = new ArrayList<>();
            for (Field f : a.annotationType().getDeclaredFields()) fields.add(new FieldInfo(f));
            this.fields = fields.toArray(new FieldInfo[0]);

            List<MethodInfo> methods = new ArrayList<>();
            for (Method m : a.annotationType().getDeclaredMethods()) methods.add(new MethodInfo(m));
            this.methods = methods.toArray(new MethodInfo[0]);
        }

    }

    private static String getVisibility(Class<?> clazz) {
        int mod = clazz.getModifiers();
        if (Modifier.isPublic(mod)) return "public";
        if (Modifier.isPrivate(mod)) return "private";
        if (Modifier.isProtected(mod)) return "protected";

        return "package_private";   
    }

    private static String getVisibility(Method m) {
        int mod = m.getModifiers();
        if (Modifier.isPublic(mod)) return "public";
        if (Modifier.isPrivate(mod)) return "private";
        if (Modifier.isProtected(mod)) return "protected";

        return "package_private";
    }

    private static String getVisibility(Field m) {
        int mod = m.getModifiers();
        if (Modifier.isPublic(mod)) return "public";
        if (Modifier.isPrivate(mod)) return "private";
        if (Modifier.isProtected(mod)) return "protected";

        return "package_private";
    }

    private static String getVisibility(Constructor<?> m) {
        int mod = m.getModifiers();
        if (Modifier.isPublic(mod)) return "public";
        if (Modifier.isPrivate(mod)) return "private";
        if (Modifier.isProtected(mod)) return "protected";

        return "package_private";
    }

    public final static class MethodInfo {

        public final String name;
        public final ParameterInfo[] parameters;
        public final int parameter_size;
        public final String return_type;
        public final AnnotationInfo[] annotations;
        public final TypeInfo[] type_parameters;
        public final String declaring_class;

        public final Map<String, Object> modifiers;

        public MethodInfo(Method m) {
            this.name = m.getName();
            this.parameter_size = m.getParameterCount();
            this.return_type = m.getReturnType().getName();
            this.declaring_class = m.getDeclaringClass().getName();

            List<TypeInfo> typeParams = new ArrayList<>();
            for (TypeVariable<?> type : m.getTypeParameters()) typeParams.add(new TypeInfo(type));
            this.type_parameters = typeParams.toArray(new TypeInfo[0]);

            int mod = m.getModifiers();
            Map<String, Object> modifiers = new HashMap<>();
            modifiers.put("static", Modifier.isStatic(mod));
            modifiers.put("abstract", Modifier.isAbstract(mod));
            modifiers.put("strictfp", Modifier.isStrict(mod));
            modifiers.put("synchronized", Modifier.isSynchronized(mod));
            modifiers.put("final", Modifier.isFinal(mod));
            modifiers.put("transient", Modifier.isTransient(mod));
            modifiers.put("volatile", Modifier.isVolatile(mod));
            modifiers.put("visibility", getVisibility(m));
            modifiers.put("native", Modifier.isNative(mod));
        
            this.modifiers = modifiers;

            List<ParameterInfo> params = new ArrayList<>();
            for (Parameter p : m.getParameters()) params.add(new ParameterInfo(p));

            this.parameters = params.toArray(new ParameterInfo[0]);

            List<AnnotationInfo> ainfo = new ArrayList<>();
            for (Annotation a : m.getAnnotations()) ainfo.add(new AnnotationInfo(a));

            this.annotations = ainfo.toArray(new AnnotationInfo[0]);
        }
    }

    public static class ParameterInfo {

        public final String name;
        public final String type;

        public ParameterInfo(Parameter p) {
            this.name = p.getName();
            this.type = p.getType().getName();
        }

    }

    public static class ConstructorInfo {

        public final ParameterInfo[] parameters;
        public final Map<String, Object> modifiers;
        public final AnnotationInfo[] annotations;

        public ConstructorInfo(Constructor<?> constr) {
            int mod = constr.getModifiers();
            Map<String, Object> modifiers = new HashMap<>();
            modifiers.put("static", Modifier.isStatic(mod));
            modifiers.put("abstract", Modifier.isAbstract(mod));
            modifiers.put("strictfp", Modifier.isStrict(mod));
            modifiers.put("synchronized", Modifier.isSynchronized(mod));
            modifiers.put("final", Modifier.isFinal(mod));
            modifiers.put("transient", Modifier.isTransient(mod));
            modifiers.put("volatile", Modifier.isVolatile(mod));
            modifiers.put("visibility", getVisibility(constr));
            modifiers.put("native", Modifier.isNative(mod));

            this.modifiers = modifiers;

            List<ParameterInfo> params = new ArrayList<>();
            for (Parameter p : constr.getParameters()) params.add(new ParameterInfo(p));

            this.parameters = params.toArray(new ParameterInfo[0]);

            List<AnnotationInfo> ainfo = new ArrayList<>();
            for (Annotation a : constr.getAnnotations()) ainfo.add(new AnnotationInfo(a));

            this.annotations = ainfo.toArray(new AnnotationInfo[0]);   
        }

    }

}
