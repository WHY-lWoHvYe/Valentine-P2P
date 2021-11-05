/*
 *  Copyright 2020-2022 lWoHvYe
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.lwohvye.modules.system.handler;

import com.lwohvye.modules.system.annotation.UserTypeHandlerAnno;
import com.lwohvye.modules.system.enums.UserTypeEnum;
import com.lwohvye.modules.system.service.AUserTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Objects;

/**
 * @author Hongyan Wang
 * @description 策略模式，处理type与实现类的映射关系
 * @date 2021年11月02日 16:34
 */
@Slf4j
@Component
public class AuthHandlerProcessor implements BeanFactoryPostProcessor {

    /**
     * 扫描@OrderTypeHandlerAnno注解，初始化HandlerContext，将其注册到spring容器
     *
     * @param configurableListableBeanFactory bean工厂
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        var handlerMap = new HashMap<Integer, AUserTypeHandler>();
        for (var temp : UserTypeEnum.values()) {
            var beanInstance = getBeansWithAnnotation(configurableListableBeanFactory, AUserTypeHandler.class, UserTypeHandlerAnno.class, temp.getType());
            if (!Objects.isNull(beanInstance))
                handlerMap.put(temp.getType(), beanInstance);
        }
        var context = new AuthHandlerContext(handlerMap);
        //单例注入
        configurableListableBeanFactory.registerSingleton(AuthHandlerContext.class.getName(), context);
    }

    /*
     * 通过父类+注解找到实体类
     */
    private <T> T getBeansWithAnnotation(ConfigurableListableBeanFactory beanFactory, Class<T> manager, Class<? extends UserTypeHandlerAnno> annotation, Integer userType) throws BeansException {
        if (ObjectUtils.isEmpty(userType))
            return null;

        var tCollection = beanFactory.getBeansOfType(manager).values();
        for (T t : tCollection) {
            var userTypeHandlerAnno = t.getClass().getAnnotation(annotation);
            if (ObjectUtils.isEmpty(userTypeHandlerAnno)) {
                log.warn(" {} 类的 {} 注解没有写入值 ", t.getClass().getSimpleName(), annotation.getSimpleName());
                return null;
            }
            //注解值是否与userType相等
            if (Objects.equals(userTypeHandlerAnno.value().getType(), userType))
                return t;
        }
        log.warn(" {} 没有对应的类 ", userType);
        return null;
    }
}