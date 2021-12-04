/*
 *  Copyright 2019-2022 Zheng Jie
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
package com.lwohvye.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReflectUtil;
import com.lwohvye.annotation.DataPermission;
import com.lwohvye.annotation.Query;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.Id;
import javax.persistence.criteria.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Zheng Jie, lWoHvYe
 * @date 2019-6-4 14:59:48
 */
@Slf4j
// @SuppressWarnings 抑制警告
//@SuppressWarnings({"unchecked", "all"})
public class QueryHelp {

    /**
     * @param root  Root根对象对应于from后面的表
     * @param query Q 外部的criteria对象
     * @param cb    CriteriaBuilder工厂类，用于创建查询的criteriaQuery对象
     *              Predicate查询条件的拼接对应于where后面的添加表达式
     * @return javax.persistence.criteria.Predicate
     * @description 解析属性上的查询注解。贫瘠相应的查询
     * @date 2021/3/31 11:57
     */
    public static <R, Q> Predicate getPredicate(Root<R> root, Q query, CriteriaBuilder cb) {
        var list = new ArrayList<Predicate>();
        if (query == null) {
            return cb.and(list.toArray(new Predicate[0]));
        }
        // 数据权限验证
        analyzeDataPermission(root, query, list);
        try {
            var fields = getAllFields(query.getClass(), new ArrayList<>());
            for (var field : fields) {
//                field.canAccess(filed对应的查询器实例)
                var accessible = field.canAccess(query);
//                boolean accessible = field.isAccessible();
//                if (ObjectUtil.notEqual(accessible, field.isAccessible()))
//                    throw new RuntimeException("编码有误" + field.toString() + accessible);
                // 设置对象的访问权限，保证对private的属性的访
                field.setAccessible(true);
                Query q = field.getAnnotation(Query.class);
                if (q != null) {
                    var propName = q.propName();
                    var joinName = q.joinName();
                    var blurry = q.blurry();
                    var attributeName = isBlank(propName) ? field.getName() : propName;
                    var fieldType = field.getType();
                    var val = field.get(query);
                    if (Objects.isNull(val) || Objects.equals("", val)) {
                        continue;
                    }
                    Join<R, ?> join = null;
                    // 模糊多字段
                    if (StringUtils.isNotBlank(blurry)) {
                        var blurrys = blurry.split(",");
                        var orPredicate = new ArrayList<Predicate>();
                        for (String s : blurrys) {
                            orPredicate.add(cb.like(root.get(s)
                                    .as(String.class), "%" + val + "%"));
                        }
                        var p = new Predicate[orPredicate.size()];
                        list.add(cb.or(orPredicate.toArray(p)));
                        continue;
                    }
//                    解析join类型
                    join = analyzeJoinType(root, q, joinName, val, join);
//                    解析查询类型
                    analyzeQueryType(root, cb, list, q, attributeName, (Class<? extends Comparable>) fieldType, val, join);
                }
                field.setAccessible(accessible);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        int size = list.size();
        return cb.and(list.toArray(new Predicate[size]));
    }

    /**
     * @param root  /
     * @param query /
     * @param list  /
     * @description 数据权限验证
     * @date 2021/11/7 4:36 下午
     */
    private static <R, Q> void analyzeDataPermission(Root<R> root, Q query, ArrayList<Predicate> list) {
        DataPermission permission = query.getClass().getAnnotation(DataPermission.class);
        if (permission != null) {
            // 获取数据权限
            var dataScopes = SecurityUtils.getCurrentUserDataScope();
            if (CollUtil.isNotEmpty(dataScopes)) {
                if (StringUtils.isNotBlank(permission.joinName()) && StringUtils.isNotBlank(permission.fieldName())) {
                    // 因为首先处理这部分，不必担心join重复。这里用var的化，join的类型会是Join<Object, Object>
                    Join<R, ?> join = root.join(permission.joinName(), JoinType.LEFT);
                    list.add(getExpression(permission.fieldName(), join, root).in(dataScopes));
                } else if (StringUtils.isBlank(permission.joinName()) && StringUtils.isNotBlank(permission.fieldName())) {
                    list.add(getExpression(permission.fieldName(), null, root).in(dataScopes));
                }
            }
        }
    }

    /**
     * @param root     /
     * @param q        /
     * @param joinName /
     * @param val      /
     * @param join     /
     * @return javax.persistence.criteria.Join
     * @description 解析joinType
     * @date 2021/6/24 10:52 上午
     */
    private static <R> Join<R, ?> analyzeJoinType(Root<R> root, Query q, String joinName, Object val, Join<R, ?> join) {
        if (StringUtils.isNotBlank(joinName)) {
            // 首先获取已经设置的join。只用一次的话，使用聚合会降低性能，所以再次调整为循环的方式
//            var existJoinNames = root.getJoins().stream().collect(Collectors.toMap(rJoin -> rJoin.getAttribute().getName(), rJoin -> rJoin, (o, o2) -> o2));
            // 这里支持属性套属性。比如查User时，配置了连Role表 joinName = "roles"，若需要用Role中的Menus属性做一些过滤，则 joinName = "roles>menus" 这样配置即可，此时会连上sys_roles_menus和sys_menu两张表
            var joinNames = joinName.split(">");

            for (var entity : joinNames) {
                // 若join已经有值了，就不走下面这段逻辑了。这里还保证了如果使用了>，只有第一层会走进来，避免一些问题，比如 roles>dept 和 dept。这俩个dept是不应用同一个join的
                checkJoin:
                {
                    if (Objects.isNull(join)) {
//                    var rJoin = existJoinNames.get(entity);
                        for (var rJoin : root.getJoins()) {
                            // 若已经设置过该joinName，则将已设置的rJoin赋值给join，开启下一循环
                            if (Objects.equals(rJoin.getAttribute().getName(), entity)) {
                                join = rJoin;
                                break checkJoin;
                            }
                        }
                    }
                    switch (q.join()) {
                        case LEFT:
                            if (Objects.nonNull(join) && Objects.nonNull(val)) {
                                join = join.join(entity, JoinType.LEFT);
                            } else {
                                join = root.join(entity, JoinType.LEFT);
                            }
                            break;
                        case RIGHT:
                            if (Objects.nonNull(join) && Objects.nonNull(val)) {
                                join = join.join(entity, JoinType.RIGHT);
                            } else {
                                join = root.join(entity, JoinType.RIGHT);
                            }
                            break;
                        case INNER:
                            if (Objects.nonNull(join) && Objects.nonNull(val)) {
                                join = join.join(entity, JoinType.INNER);
                            } else {
                                join = root.join(entity, JoinType.INNER);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        return join;
    }

    /**
     * @param root          /
     * @param cb            /
     * @param list          /
     * @param q             /
     * @param attributeName /
     * @param fieldType     /
     * @param val           /
     * @param join          /
     * @description 解析query.type()。抽取主要为了方便调用
     * @date 2021/6/24 10:52 上午
     */
    private static <R> void analyzeQueryType(Root<R> root, CriteriaBuilder cb, ArrayList<Predicate> list, Query q, String attributeName, Class<? extends Comparable> fieldType, Object val, Join<R, ?> join) {
        switch (q.type()) {
            case EQUAL:
                list.add(cb.equal(getExpression(attributeName, join, root).as(fieldType), val));
                break;
            case GREATER_THAN:
                list.add(cb.greaterThanOrEqualTo(getExpression(attributeName, join, root).as(fieldType), (Comparable) val));
                break;
            case LESS_THAN:
                list.add(cb.lessThanOrEqualTo(getExpression(attributeName, join, root).as(fieldType), (Comparable) val));
                break;
            case LESS_THAN_NQ:
                list.add(cb.lessThan(getExpression(attributeName, join, root).as(fieldType), (Comparable) val));
                break;
            case INNER_LIKE:
                list.add(cb.like(getExpression(attributeName, join, root).as(String.class), "%" + val.toString() + "%"));
                break;
            case LEFT_LIKE:
                list.add(cb.like(getExpression(attributeName, join, root).as(String.class), "%" + val.toString()));
                break;
            case RIGHT_LIKE:
                list.add(cb.like(getExpression(attributeName, join, root).as(String.class), val.toString() + "%"));
                break;
            case IN:
                if (CollUtil.isNotEmpty((Collection<? extends Comparable>) val)) {
                    list.add(getExpression(attributeName, join, root).in((Collection<? extends Comparable>) val));
                }
                break;
            case NOT_EQUAL:
                list.add(cb.notEqual(getExpression(attributeName, join, root), val));
                break;
            case NOT_NULL:
                list.add(cb.isNotNull(getExpression(attributeName, join, root)));
                break;
            case IS_NULL:
                list.add(cb.isNull(getExpression(attributeName, join, root)));
                break;
            case BETWEEN:
                var between = new ArrayList<>((List<Object>) val);
                list.add(cb.between(getExpression(attributeName, join, root).as((Class<? extends Comparable>) between.get(0).getClass()), (Comparable) between.get(0), (Comparable) between.get(1)));
                break;
            case NOT_IN:
                if (CollUtil.isNotEmpty((Collection<? extends Comparable>) val)) {
                    list.add(cb.not(getExpression(attributeName, join, root).in((Collection<? extends Comparable>) val)));
                }
                break;
            case LIKE_STR:
                list.add(cb.like(getExpression(attributeName, join, root).as(String.class), val.toString()));
                break;
            case IN_OR_ISNULL:
                if (CollUtil.isNotEmpty((Collection<? extends Comparable>) val)) {
                    list.add(
//                                        在集合中
                            cb.or(getExpression(attributeName, join, root).in((Collection<? extends Comparable>) val)
//                                                或值为null
                                    , cb.isNull(getExpression(attributeName, join, root))
//                                                或值为空字符串
                                    , cb.equal(getExpression(attributeName, join, root).as(String.class), ""))
                    );
                }
                break;
            case IS_OR_NULL:
                list.add((Long) val == -1L ?
                        cb.isNull(getExpression(attributeName, join, root).as(fieldType)) :
                        cb.equal(getExpression(attributeName, join, root).as(fieldType), val));
                break;
            case IN_INNER_LIKE:
                if (val instanceof List objList) {
//                                构建数组
                    var predicates = new Predicate[objList.size()];
                    for (int i = 0; i < objList.size(); i++) {
                        var obj = objList.get(i);
                        predicates[i] = cb.like(getExpression(attributeName, join, root)
                                .as(String.class), "%" + obj.toString() + "%");
                    }
//                                设置or查询
                    list.add(cb.or(predicates));
                }
                break;
            case EQUAL_IN_MULTI:
                var predicates = new Predicate[4];
//                            like val
                predicates[0] = cb.like(getExpression(attributeName, join, root).as(String.class), val.toString());
//                            like val,%
                predicates[1] = cb.like(getExpression(attributeName, join, root).as(String.class), val + ",%");
//                            like %,val,%
                predicates[2] = cb.like(getExpression(attributeName, join, root).as(String.class), "%," + val + ",%");
//                            like %,val
                predicates[3] = cb.like(getExpression(attributeName, join, root).as(String.class), "%," + val);
//                            设置查询
                list.add(cb.or(predicates));
                break;
            case EQUAL_IN_MULTI_JOIN:
//                            该注解只针对Join查询。非join不处理
                if (Objects.isNull(join))
                    break;
                var arrayList = new ArrayList<Predicate>();
//                            val是一个实体。里面有多个属性。将其中非空的属性配置进去
                for (Field fieldInVal : ReflectUtil.getFields(val.getClass())) {
                    var fieldValue = ReflectUtil.getFieldValue(val, fieldInVal);
                    if (Objects.nonNull(fieldValue)) {
                        Predicate predicate = null;
//                                    Id注解，只会出现在主键上
                        var idAnnotation = fieldInVal.getAnnotation(Id.class);
//                                    In查询通过Query注解的propName指定映射的属性
                        var queryAnnotation = fieldInVal.getAnnotation(Query.class);
//                                    如果在实体属性上配置了Query注解，需解析Query注解，确定查询方式
                        if (Objects.nonNull(queryAnnotation)) {
                            var queryType = queryAnnotation.type();
                            var queryAttrName = isBlank(queryAnnotation.propName()) ? fieldInVal.getName() : queryAnnotation.propName();
                            predicate = switch (queryType) {
                                case EQUAL -> cb.equal(getExpression(queryAttrName, join, root).as((Class<? extends Comparable>) fieldInVal.getType()), fieldValue);
                                case NOT_EQUAL -> cb.notEqual(getExpression(queryAttrName, join, root), fieldValue);
                                case INNER_LIKE -> cb.like(getExpression(queryAttrName, join, root).as(String.class), "%" + fieldValue.toString() + "%");
                                case IN -> getExpression(queryAttrName, join, root).in((Collection<? extends Comparable>) fieldValue);
                                case NOT_IN -> cb.not(getExpression(queryAttrName, join, root).in((Collection<? extends Comparable>) fieldValue));
                                default -> throw new RuntimeException("暂不支持该类型，请期待后续支持：" + queryType);
                            };
//                                    String类型使用Inner like
                        } else if (fieldValue instanceof String str) {
                            predicate = cb.like(getExpression(fieldInVal.getName(), join, root).as(String.class), "%" + str + "%");
                            // 传-1L时。做is null查询。额外限制为当对应属性上有id注解的时候。
                            // 2021/4/2 当使用IS NULL查询时，同join的其他查询条件会导致无结果。故先只让该is null查询生效。
                            // 2021/4/6 经考虑，IS NULL类查询更建议使用其他的方式来完成。 EQUAL_IN_MULTI_JOIN注解主要用在多条件join上（不包括is null）
                            // 针对与is null的需求，可以考虑视图。这种一般不需要太多张表。后续会探讨如何将join的相关筛选放在on 后面
                            // 2021/11/07 解决了多join问题后，该注解的功能可由原配置多个join来实现。不再进行扩展
                        } else if (fieldValue instanceof Long && Objects.equals(fieldValue, -1L) && Objects.nonNull(idAnnotation)) {
                            predicate = cb.isNull(getExpression(fieldInVal.getName(), join, root).as((Class<? extends Comparable>) fieldInVal.getType()));
                            // 下面这三行，主体是因为，若使用了isNull，则不能再设置该join实体的其他查询，所以跳出
                            list.add(predicate);
//                              安全起见。清空一下。避免在循环结束的list.add()那里，再被加进去
                            arrayList.clear();
                            break;
                        } else {
//                                    其他的走等于
                            predicate = cb.equal(getExpression(fieldInVal.getName(), join, root).as((Class<? extends Comparable>) fieldInVal.getType()), fieldValue);
                        }
                        if (Objects.nonNull(predicate))
                            arrayList.add(predicate);
                    }
                }
                if (CollUtil.isNotEmpty(arrayList))
                    list.add(cb.and(arrayList.toArray(new Predicate[0])));
                break;
//            case FUNCTION_FROM_BASE64:
            // where (from_base64(user0_.description) like to_base64(user0_.description))。如何设置to_base64的参数为 fieldValue，是接下来的事情
//                list.add(cb.like(cb.function("from_base64", fieldType, getExpression(attributeName, join, root)), cb.function("to_base64", fieldType, getExpression(attributeName, join, root))));
            // where (from_base64(user0_.description) like '%ABC%') 。已基本可以使用
//                list.add(cb.like(cb.function("from_base64", fieldType, getExpression(attributeName, join, root)).as(String.class), "%" + val.toString() + "%"));
//                break;
            case FUNCTION_4_EQUAL:
                list.add(cb.equal(cb.function(q.functionName(), fieldType, getExpression(attributeName, join, root)), val));
                break;
            default:
                break;
        }
    }

    private static <T, R> Expression<T> getExpression(String attributeName, Join<R, ?> join, Root<R> root) {
        // 处理的维度是field维度的，每个field初始化一个join，若join有值，证明该field是join的实体中的，所以要从join中取，即join.get()。
        if (Objects.nonNull(join)) {
            return join.get(attributeName);
        } else {
            // 非join的，从root中取，root.get()。
            return root.get(attributeName);
        }
    }

    private static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static <T> List<Field> getAllFields(Class<T> clazz, List<Field> fields) {
        if (clazz != null) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            getAllFields(clazz.getSuperclass(), fields);
        }
        return fields;
    }

    // 2021/11/6 使用JPA 2.1 引入的 CriteriaUpdate 和 CriteriaDelete 进行批量更新/删除。不是很实用，期待后续的使用场景
    public static <R, Q> void criteria4Update(Root<R> root, Q query, CriteriaBuilder cb) {
//        var em = new EntityManager();
        var criteriaUpdate = cb.createCriteriaUpdate(root.getJavaType());
        criteriaUpdate.set("fieldName", "newFieldValue");
        criteriaUpdate.where(getPredicate(root, query, cb));
//        em.createQuery(criteriaUpdate).executeUpdate();
    }

    public static <R, Q> void criteriaDelete(Root<R> root, Q query, CriteriaBuilder cb) {
//        var em = new EntityManager();
        var criteriaDelete = cb.createCriteriaDelete(root.getJavaType());
        criteriaDelete.where(getPredicate(root, query, cb));
//        em.createQuery(criteriaDelete).executeUpdate();
    }
}
