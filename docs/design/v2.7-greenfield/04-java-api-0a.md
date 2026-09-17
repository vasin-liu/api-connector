# 0a Java API 草图

包建议：`com.suntek.apiconnector.core.*`（类型）+ `runtime.*`（执行）。Phase 0 单模块即可。

对应源设计 §5–6、§11、§35–36、§54。

---

## E1. 值与 Outcome

```java
public sealed interface DataValue
        permits StringValue, NumberValue, BooleanValue, BytesValue,
                JsonValue, ObjectValue, ListValue, SecretValue, NullValue {}

public record StringValue(String value) implements DataValue {}
public record NumberValue(Number value) implements DataValue {}
public record BooleanValue(boolean value) implements DataValue {}
public record BytesValue(byte[] value) implements DataValue {}
public record JsonValue(String json) implements DataValue {}
public record ObjectValue(Map<String, DataValue> fields) implements DataValue {}
public record ListValue(List<DataValue> items) implements DataValue {}
public record NullValue() implements DataValue {}

public non-sealed interface SecretValue extends DataValue {
    SecretMetadata metadata();
    // 禁止 String reveal();
    void use(SecretConsumer consumer);
}

public enum StepOutcomeType {
    SUCCESS, FAILURE, CHALLENGE, RETRYABLE_FAILURE, CANCELLED, TIMEOUT
}

public record StepOutcome(
        StepOutcomeType type,
        Optional<Integer> httpStatus,      // transport 成功才有
        boolean transportCompleted,
        String reasonCode
) {}
```

0a 的 `SecretValue.use` 可以先只支持「写入指定 header/query」。0d 再换成 SinkPolicy。

---

## E2. Snapshot / 执行句柄（对外，A2）

```java
public record ExecutionSnapshot(
        String executionId,
        String apiId,
        String definitionRevision,
        String planId,
        Instant startedAt
) {}

public record ExecuteCommand(
        String apiId,
        String flowId,                       // 默认 "business"
        Map<String, DataValue> input,
        Optional<String> revision,
        ExecuteOptions options
) {}

public record ExecuteOptions(
        Duration timeout,
        boolean allowReplay,
        Map<String, String> traceBaggage
) {}

public interface ApiClient {
    ExecutionHandle execute(ExecuteCommand command);
    void cancel(String executionId);
}

public interface ExecutionHandle {
    String executionId();
    ExecutionSnapshot snapshot();
    CompletionStage<ExecutionResult> result();
}

public record ExecutionResult(
        StepOutcomeType outcome,
        Optional<Integer> httpStatus,
        ResponseBody body,
        DecisionTrace trace,
        Optional<SessionSnapshot> session
) {}
```

规则：`input` 只能进 EXECUTION；出现 Secret 字面量 → 拒绝。`allowReplay=true` 也不能覆盖 `replayability=UNSAFE|UNKNOWN` 的默认禁止。

---

## E3. 编译管线（对内）

```java
public record ApiDefinition(
        String id,
        String revision,
        String authProfile,
        Map<String, CredentialDefinition> credentials,
        Map<String, VariableDefinition> variables,
        Map<String, RequestTemplate> requests,
        Map<String, PipelineGraph> pipelines,
        Map<String, FlowDefinition> flows,
        Limits limits,
        SessionPolicy session,
        ExecutionPolicy policy
) {}

public interface DefinitionValidator {
    ValidationResult validate(ApiDefinition definition);
}

public interface PlanCompiler {
    ExecutionPlan compile(ApiDefinition definition);
}

public record ExecutionPlan(
        String planId,
        String definitionId,
        String definitionRevision,
        CompiledFlow business,
        Optional<CompiledFlow> authentication,
        CompiledPipelines pipelines
) {}

public interface PlanCache {
    ExecutionPlan getOrCompile(String definitionId, String revision);
}
```

完整 `ExecutionPlan` 字段（含 capabilities、SessionKey 材料）见 [07-plan-compiler.md](07-plan-compiler.md)。

0a 的 `PipelineGraph` 只需要 `passthrough` 和 `codec.json`。图结构类型先留着，Compile 遇 cycle/未知端口就失败。

---

## E4. Flow / State（0a 最小）

```java
public enum VariableScope { GLOBAL, SESSION, EXECUTION, FLOW, LOCAL }

public interface StateMutation {
    void set(String name, DataValue value);
    void remove(String name);
}

public interface VariableRuntime {
    DataValue get(VariableScope scope, String name);
    StateMutation beginLocal();
    void commit(StateMutation mutation, StepOutcomeType outcome);
    void discard(StateMutation mutation);
}

public interface FlowRuntime {
    StepOutcome run(ExecutionContext context, CompiledFlow flow);
}

public interface TransitionEvaluator {
    Optional<Transition> firstMatch(ConditionContext ctx, List<CompiledTransition> transitions);
}

public sealed interface Condition
        permits StatusCondition, HeaderCondition, JsonPathCondition,
                VariableCondition, AllCondition, AnyCondition, NotCondition {}
```

Commit 策略按源设计 §10 写死在 runtime，不要让 YAML 覆盖 SUCCESS/FAILURE/CANCELLED/TIMEOUT；只允许 `commitOn: CHALLENGE` 这种显式放行。

---

## E5. Transport（0a + 流式预留）

```java
public interface HttpTransport {
    RawHttpResponse execute(RawHttpRequest request, TransportContext context);
}

public record RawHttpRequest(
        String method,
        URI uri,
        Map<String, List<String>> headers,
        Optional<byte[]> body
) {}

public record RawHttpResponse(
        int status,
        Map<String, List<String>> headers,
        ResponseBody body,
        boolean completed
) {}

public sealed interface ResponseBody permits BytesBody, StreamBody, EmptyBody {}

public record BytesBody(byte[] bytes, Optional<String> contentType) implements ResponseBody {}
public record EmptyBody() implements ResponseBody {}
public record StreamBody(ReadableByteChannel channel, Optional<String> contentType)
        implements ResponseBody {}

public interface ResponseClassifier {
    StepOutcome classify(RawHttpResponse response, CompiledStep step);
}
```

`completed=false`（写出后断连）→ 不得进 Classifier 的 Challenge 路径，直接 `UNKNOWN_OUTCOME`/`TIMEOUT`。`StreamBody` 同样不进 Classifier（源设计 §36）；0a 可以遇到 StreamBody 就 `SUCCESS`+透传或直接拒绝该 request 配置。

---

## E6. DecisionTrace 占位（字段名冻结）

```java
public record DecisionTrace(List<DecisionRecord> decisions) {}

public record DecisionRecord(
        String decisionId,
        String type,
        String action,
        String reasonCode,
        Map<String, String> facts   // 已脱敏
) {}
```

0a 空列表也行，但不要 0d 再换字段名。

---

## E7. 0a 明确不出现的类型

属于 0b–0d，见 [02-phase0-increments.md](02-phase0-increments.md)。
