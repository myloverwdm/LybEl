import com.lyb.BaseLybEl;
import com.lyb.ParamDto;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author LaiYongBin
 * @date 创建于 2022/6/13 13:14
 * @apiNote DO SOMETHING
 */
public class Test1 {
    public static void main(String[] args) throws Exception {
        int count = 10000000;
        // java代码, 若是代码中没有`return `, 则会自动添加, 如`a + b`会自动变为`return a+b`so最好自己保证代码的正确性
        String javaCode = "a = a + 10;b = b + 10;return a + b + 5;";
        BaseLybEl baseLybEl = new BaseLybEl(javaCode, Arrays.asList(
                // 一个参数 参数名为a, 参数类型为 int, 注意, 这些变量是有顺序的
                ParamDto.builder().paramClass(int.class).paramName("a").build(),
                // 一个参数 参数名为b, 参数类型为 string
                ParamDto.builder().paramClass(int.class).paramName("b").build()
        ));
        long start = System.currentTimeMillis();
        // 方式1: 使用Object[] 作为参数传进去, 这时需要保证参数的顺序与上面定义的顺序是一致的
        for (int i = 0; i < count; i++) {
            baseLybEl.execute(new Object[]{1, 5});
        }
        System.out.printf("使用数组循环%s次的时间为%s毫秒%n", count, System.currentTimeMillis() - start);

        // 方式2: 使用Map, 方法中会将其转化为Object[], 多了一步转换的操作, 故效率低些
        Map<String, Object> map = new HashMap<>(4);
        map.put("a", 5);
        map.put("b", 4);
        long startMap = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            baseLybEl.execute(map);
        }
        System.out.printf("使用Map循环%s次的时间为%s毫秒%n", count, System.currentTimeMillis() - startMap);
        // 若是没有参数, 及空参的情况下, 则直接使用
        // baseLybEl.execute();
    }
}
