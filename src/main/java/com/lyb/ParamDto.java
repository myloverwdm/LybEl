package com.lyb;

/**
 * @author LaiYongBin
 * @date 创建于 2022/6/12 23:55
 * @apiNote DO SOMETHING
 */
public class ParamDto {

    private Class paramClass;
    private String paramName;

    public ParamDto(Class paramClass, String paramName) {
        this.paramClass = paramClass;
        this.paramName = paramName;
    }

    public Class getParamClass() {
        return paramClass;
    }

    public void setParamClass(Class paramClass) {
        this.paramClass = paramClass;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public static ParamDtoBuilder builder() {
        return new ParamDtoBuilder();
    }

    public static class ParamDtoBuilder {
        private final ParamDto paramDto = new ParamDto(null, null);

        public ParamDtoBuilder paramClass(Class classParam) {
            paramDto.setParamClass(classParam);
            return this;
        }

        public ParamDtoBuilder paramName(String paramName) {
            paramDto.setParamName(paramName);
            return this;
        }

        public ParamDto build() {
            return paramDto;
        }

    }
}
