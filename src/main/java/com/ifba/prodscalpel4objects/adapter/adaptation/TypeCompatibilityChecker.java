package com.ifba.prodscalpel4objects.adapter.adaptation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Verificador e resolvedor de compatibilidade de tipos Java entre os parâmetros
 * exigidos pelo Órgão e as variáveis/beans disponíveis no escopo do sistema Hospedeiro.
 *
 * <p>Suporta correspondência exata, boxing/unboxing de tipos primitivos,
 * alargamento de tipos numéricos (widening) e conversões seguras.</p>
 *
 * @author Giovane Neves
 */
public class TypeCompatibilityChecker {

    private static final Map<String, String> PRIMITIVE_TO_BOXED = new HashMap<>();
    private static final Map<String, String> BOXED_TO_PRIMITIVE = new HashMap<>();

    static {
        registerBoxedPair("int", "java.lang.Integer");
        registerBoxedPair("long", "java.lang.Long");
        registerBoxedPair("double", "java.lang.Double");
        registerBoxedPair("float", "java.lang.Float");
        registerBoxedPair("boolean", "java.lang.Boolean");
        registerBoxedPair("byte", "java.lang.Byte");
        registerBoxedPair("short", "java.lang.Short");
        registerBoxedPair("char", "java.lang.Character");
    }

    private static void registerBoxedPair(String primitive, String boxed) {
        PRIMITIVE_TO_BOXED.put(primitive, boxed);
        PRIMITIVE_TO_BOXED.put(primitive, getSimpleName(boxed));
        BOXED_TO_PRIMITIVE.put(boxed, primitive);
        BOXED_TO_PRIMITIVE.put(getSimpleName(boxed), primitive);
    }

    /**
     * Avalia o grau de compatibilidade entre o tipo exigido pelo órgão e o tipo disponível no host.
     *
     * @param organType Tipo exigido pelo órgão (ex: "java.lang.String" ou "int").
     * @param hostType Tipo disponível no host.
     * @return {@link MatchResult} contendo status de compatibilidade, pontuação (0.0 a 1.0) e expressão de conversão.
     */
    public MatchResult evaluate(String organType, String hostType) {
        if (organType == null || hostType == null) {
            return MatchResult.incompatible();
        }

        String normOrgan = normalizeType(organType);
        String normHost = normalizeType(hostType);

        // 1. Correspondência exata (tipo idêntico)
        if (normOrgan.equals(normHost)) {
            return new MatchResult(true, 1.0, "%s");
        }

        // 2. Boxing / Unboxing (ex: int <-> Integer)
        if (isBoxingCompatible(normOrgan, normHost)) {
            return new MatchResult(true, 0.95, "%s");
        }

        // 3. Alargamento numérico primitivo (ex: int -> long, float -> double)
        if (isNumericWidening(normOrgan, normHost)) {
            return new MatchResult(true, 0.90, "%s");
        }

        // 4. Conversão para String caso o órgão espere String e o host tenha qualquer objeto
        if ("String".equals(normOrgan) || "java.lang.String".equals(normOrgan)) {
            return new MatchResult(true, 0.70, "String.valueOf(%s)");
        }

        // 5. Polimorfismo / Subtipo (se os nomes sugerem herança ou implementação de interface)
        if (isSubtypeHeuristic(normOrgan, normHost)) {
            return new MatchResult(true, 0.85, "(%s) %%s".formatted(organType));
        }

        return MatchResult.incompatible();
    }

    private boolean isBoxingCompatible(String t1, String t2) {
        return Objects.equals(PRIMITIVE_TO_BOXED.get(t1), t2)
                || Objects.equals(BOXED_TO_PRIMITIVE.get(t1), t2)
                || Objects.equals(PRIMITIVE_TO_BOXED.get(t2), t1)
                || Objects.equals(BOXED_TO_PRIMITIVE.get(t2), t1);
    }

    private boolean isNumericWidening(String target, String source) {
        if ("long".equals(target) && "int".equals(source)) return true;
        if ("double".equals(target) && ("float".equals(source) || "int".equals(source) || "long".equals(source))) return true;
        if ("float".equals(target) && "int".equals(source)) return true;
        return false;
    }

    private boolean isSubtypeHeuristic(String organType, String hostType) {
        String simpleOrgan = getSimpleName(organType);
        String simpleHost = getSimpleName(hostType);
        // Ex: UserService e UserServiceImpl, List e ArrayList
        return simpleHost.startsWith(simpleOrgan) || simpleHost.endsWith(simpleOrgan);
    }

    private String normalizeType(String type) {
        String t = type.trim();
        if (t.startsWith("java.lang.")) {
            return t.substring("java.lang.".length());
        }
        return t;
    }

    private static String getSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot != -1 ? fullName.substring(lastDot + 1) : fullName;
    }

    public record MatchResult(
            boolean compatible,
            double score,
            String conversionPattern
    ) {
        public static MatchResult incompatible() {
            return new MatchResult(false, 0.0, null);
        }

        public String apply(String varName) {
            if (!compatible || conversionPattern == null) return varName;
            return conversionPattern.contains("%s") ? conversionPattern.formatted(varName) : varName;
        }
    }
}
