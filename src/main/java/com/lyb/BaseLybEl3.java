package com.lyb;

import com.lyb.classLoader.DynamicClassLoader;
import com.lyb.classLoader.DynamicCompiler;
import com.sun.istack.internal.NotNull;

import java.lang.reflect.Method;
import java.util.*;

/**
 * @author LaiYongBin
 * @date 创建于 2022/6/12 23:54
 * @apiNote DO SOMETHING
 */
public class BaseLybEl3 {
    private final Object o;
    private final Method method;
    private final List<String> paramList = new ArrayList<>();

    public BaseLybEl3(String javaCode, List<ParamDto> paramDtoList) throws Exception {
        javaCode = javaCode.trim();
        DynamicCompiler compiler = new DynamicCompiler();
        DynamicClassLoader classLoader = new DynamicClassLoader();
        String uuid = "A" + UUID.randomUUID().toString().replace("-", "");
        compiler.addJavaSource("com.lyb." + uuid, "package com.lyb;\n" + "\n" + "public class " + uuid + " {\n" + "   " + " public Object filter(" + buildParam(paramDtoList) + ") {\n" + "        " + buildSource(javaCode) + "    }\n" + "}");
        Map<String, byte[]> compileResult = compiler.compile();
        compileResult.forEach(classLoader::putClass);
        Class<?> aClass = classLoader.loadClass("com.lyb." + uuid);
        o = aClass.newInstance();
        Class[] classes = buildClass(paramDtoList);
        if (classes == null) {
            method = aClass.getMethod("filter");
        } else {
            method = aClass.getMethod("filter", classes);
        }
    }

    /**
     * 没有参数的代码
     */
    public BaseLybEl(String javaCode) throws Exception {
        this(javaCode, null);
    }

    public Object execute() throws Exception {
        return method.invoke(o);
    }

    public Object execute(Object[] objArray) throws Exception {
        return method.invoke(o, objArray);
    }

    public Object execute(@NotNull Map<String, Object> mapParam) throws Exception {
        if (paramList.size() == 0) {
            return execute();
        }
        Object[] objArray = new Object[paramList.size()];
        for (int i = 0; i < paramList.size(); i++) {
            objArray[i] = mapParam.get(paramList.get(i));
        }
        return execute(objArray);
    }

    private String buildParam(List<ParamDto> paramDtoList) {
        StringJoiner sj = new StringJoiner(", ");
        if (paramDtoList == null) {
            return sj.toString();
        }
        for (ParamDto p : paramDtoList) {
            sj.add(p.getParamClass().getName() + " " + p.getParamName());
        }
        return sj.toString();
    }

    private String buildSource(String javaCode) {
        if (!javaCode.endsWith(";")) {
            javaCode = javaCode + ";";
        }
        if (javaCode.contains("return ")) {
            return javaCode;
        }
        return "return " + javaCode;
    }

    private Class[] buildClass(List<ParamDto> paramDtoList) {
        if (paramDtoList == null || paramDtoList.size() == 0) {
            return null;
        }
        Class[] res = new Class[paramDtoList.size()];
        for (int i = 0; i < paramDtoList.size(); i++) {
            ParamDto dto = paramDtoList.get(i);
            res[i] = dto.getParamClass();
            paramList.add(dto.getParamName());
        }
        return res;
    }

}
