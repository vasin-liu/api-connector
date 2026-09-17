/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.jsonpath.RestrictedJsonPath;
import com.suntek.apiconnector.core.value.DataValue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Structured Condition AST. Compile output must not retain YAML maps.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public sealed interface Condition
        permits Condition.StatusCondition,
        Condition.HeaderCondition,
        Condition.JsonPathCondition,
        Condition.VariableCondition,
        Condition.AllCondition,
        Condition.AnyCondition,
        Condition.NotCondition {

    /**
     * HTTP status exact match or inclusive range.
     *
     * @param exact exact status when present
     * @param from  inclusive lower bound when ranging
     * @param to    inclusive upper bound when ranging
     */
    record StatusCondition(OptionalInt exact, OptionalInt from, OptionalInt to) implements Condition {

        /**
         * @param exact exact or empty
         * @param from  range start or empty
         * @param to    range end or empty
         */
        public StatusCondition {
            boolean ranging = from.isPresent() || to.isPresent();
            if (exact.isPresent() == ranging) {
                throw new IllegalArgumentException("status must be exact or a range, not both or neither");
            }
            if (ranging && (from.isEmpty() || to.isEmpty())) {
                throw new IllegalArgumentException("status range requires from and to");
            }
            if (ranging && from.getAsInt() > to.getAsInt()) {
                throw new IllegalArgumentException("VAL_STATUS_RANGE");
            }
        }

        /**
         * @param status exact HTTP status
         * @return condition
         */
        public static StatusCondition exact(int status) {
            return new StatusCondition(OptionalInt.of(status), OptionalInt.empty(), OptionalInt.empty());
        }

        /**
         * @param from inclusive
         * @param to   inclusive
         * @return condition
         */
        public static StatusCondition range(int from, int to) {
            return new StatusCondition(OptionalInt.empty(), OptionalInt.of(from), OptionalInt.of(to));
        }
    }

    /**
     * Header existence or case-sensitive value equality. Names match case-insensitively.
     *
     * @param name        header name
     * @param exists      when true, require a non-empty value
     * @param equalsValue when present, require any value to equal this literal
     */
    record HeaderCondition(String name, boolean exists, Optional<String> equalsValue) implements Condition {

        /**
         * @param name        header name
         * @param exists      exists mode
         * @param equalsValue equals mode
         */
        public HeaderCondition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("header name must not be blank");
            }
            if (exists == equalsValue.isPresent()) {
                throw new IllegalArgumentException("header must be exists or equals, not both or neither");
            }
            name = name.strip();
        }

        /**
         * @param name header name
         * @return exists condition
         */
        public static HeaderCondition exists(String name) {
            return new HeaderCondition(name, true, Optional.empty());
        }

        /**
         * @param name  header name
         * @param value literal value (case-sensitive)
         * @return equals condition
         */
        public static HeaderCondition equalsValue(String name, String value) {
            return new HeaderCondition(name, false, Optional.of(Objects.requireNonNull(value, "value")));
        }
    }

    /**
     * Restricted JSONPath against a BytesBody JSON document.
     *
     * @param path        validated JSONPath
     * @param exists      when true, path must resolve to a non-null value
     * @param equalsValue when present, path must resolve to this scalar
     */
    record JsonPathCondition(String path, boolean exists, Optional<DataValue> equalsValue) implements Condition {

        /**
         * @param path        JSONPath
         * @param exists      exists mode
         * @param equalsValue equals mode
         */
        public JsonPathCondition {
            RestrictedJsonPath.validate(path);
            if (exists == equalsValue.isPresent()) {
                throw new IllegalArgumentException("jsonpath must be exists or equals, not both or neither");
            }
            if (equalsValue.isPresent() && !ScalarEquals.isScalar(equalsValue.get())) {
                throw new IllegalArgumentException("jsonpath equals right-hand side must be a scalar");
            }
        }

        /**
         * @param path JSONPath
         * @return exists condition
         */
        public static JsonPathCondition exists(String path) {
            return new JsonPathCondition(path, true, Optional.empty());
        }

        /**
         * @param path  JSONPath
         * @param value expected scalar
         * @return equals condition
         */
        public static JsonPathCondition equalsValue(String path, DataValue value) {
            return new JsonPathCondition(path, false, Optional.of(Objects.requireNonNull(value, "value")));
        }

        /**
         * @param path  JSONPath
         * @param value expected string
         * @return equals condition
         */
        public static JsonPathCondition equalsString(String path, String value) {
            return equalsValue(path, new DataValue.StringValue(Objects.requireNonNull(value, "value")));
        }
    }

    /**
     * VariableRuntime lookup against committed values.
     *
     * @param scope       variable scope
     * @param name        variable name
     * @param exists      when true, require a committed non-null value
     * @param equalsValue when present, require scalar equality
     */
    record VariableCondition(
            VariableScope scope,
            String name,
            boolean exists,
            Optional<DataValue> equalsValue
    ) implements Condition {

        /**
         * @param scope       scope
         * @param name        name
         * @param exists      exists mode
         * @param equalsValue equals mode
         */
        public VariableCondition {
            Objects.requireNonNull(scope, "scope");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("variable name must not be blank");
            }
            if (exists == equalsValue.isPresent()) {
                throw new IllegalArgumentException("variable must be exists or equals, not both or neither");
            }
            if (equalsValue.isPresent() && !ScalarEquals.isScalar(equalsValue.get())) {
                throw new IllegalArgumentException("variable equals right-hand side must be a scalar");
            }
            name = name.strip();
        }

        /**
         * @param scope scope
         * @param name  name
         * @return exists condition
         */
        public static VariableCondition exists(VariableScope scope, String name) {
            return new VariableCondition(scope, name, true, Optional.empty());
        }

        /**
         * @param scope scope
         * @param name  name
         * @param value expected scalar
         * @return equals condition
         */
        public static VariableCondition equalsValue(VariableScope scope, String name, DataValue value) {
            return new VariableCondition(scope, name, false, Optional.of(Objects.requireNonNull(value, "value")));
        }
    }

    /**
     * Conjunction. Empty lists are illegal.
     *
     * @param children nested conditions
     */
    record AllCondition(List<Condition> children) implements Condition {

        /**
         * @param children nested conditions
         */
        public AllCondition {
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            if (children.isEmpty()) {
                throw new IllegalArgumentException("VAL_EMPTY_ALL");
            }
        }

        /**
         * @param children nested conditions
         * @return all condition
         */
        public static AllCondition of(Condition... children) {
            return new AllCondition(List.of(children));
        }
    }

    /**
     * Disjunction. Empty lists are illegal.
     *
     * @param children nested conditions
     */
    record AnyCondition(List<Condition> children) implements Condition {

        /**
         * @param children nested conditions
         */
        public AnyCondition {
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            if (children.isEmpty()) {
                throw new IllegalArgumentException("VAL_EMPTY_ANY");
            }
        }

        /**
         * @param children nested conditions
         * @return any condition
         */
        public static AnyCondition of(Condition... children) {
            return new AnyCondition(List.of(children));
        }
    }

    /**
     * Negation of exactly one child.
     *
     * @param child nested condition
     */
    record NotCondition(Condition child) implements Condition {

        /**
         * @param child nested condition
         */
        public NotCondition {
            Objects.requireNonNull(child, "child");
        }

        /**
         * @param child nested condition
         * @return not condition
         */
        public static NotCondition of(Condition child) {
            return new NotCondition(child);
        }
    }
}
