
//TIP 要<b>运行</b>代码，请按 <shortcut actionId="Run"/> 或
// 点击装订区域中的 <icon src="AllIcons.Actions.Execute"/> 图标。
import el.*;
import el.structure.*;
import el.structure.ConceptPattern;
import static el.structure.PatternDSL.*;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;



        /*
        def：  concept names  : uppercase letter(s)  ex: A, B ,C

        def： variable names  : _uppercase letter(s)_  ex: _X_ ， _YY_

        def: existential restriction : ∃

        def: role : lowercase letter(s).  ex: r.  s.

        existential restriction with roles :  ∃r. ∃s.

        def： conjunction constructors :  ⊓

        def:  subsumption  :  ⊑  #subsumpted#  （This character cannot be printed）

        def:  Top Τ : Tau

        def:  el- TBox : TBox
        def:  matching problem set : 	Gamma


        */

public class Main {



    public static void main(String[] args) {
        System.out.println("ELK dependency loaded successfully.");

        ConceptPattern pattern = pattern(
                and(
                        some("has-child", and(concept("MALE"), variable("_X_"))),
                        some("has-child", and(concept("FEMALE"), variable("_X_")))
                )
        );

        System.out.println(pattern);

        ConceptPattern pattern2 = pattern(
                some("has-child",
                        and(
                                some("has-child", and(concept("FEMALE"), variable("_X_"))),
                                some("has-child", and(concept("MALE"), variable("_Y_"))),
                                concept("D")
                        )
                )
        );

        System.out.println(pattern2);


        ELAnalyze el = new ELAnalyze();
        DecAnalyze dec = new DecAnalyze(el);

        // 设置 mock subsumption A ⊑ B = true; C ⊑ D = false;
        el.setMockSubsumption(concept("A"), concept("B"), true);
        el.setMockSubsumption(concept("C"), concept("D"), false);

        // ===== 用结构化 API 调用 test =====
        test(dec, variable("_X_"), concept("A")); // Case 1
        test(dec,
                some("r", concept("C")),
                some("r", and(concept("D"), variable("_Y_")))
        ); // Case 2
        test(dec,
                some("r", concept("A")),
                some("s", concept("A"))
        ); // Case 3
        test(dec, concept("A"), some("r", concept("B"))); // Case 4
        test(dec, some("r", concept("A")), concept("B")); // Case 5
        test(dec, concept("A"), concept("B"));           // Case 6 (true)
        test(dec, concept("C"), concept("D"));           // Case 6 (false)

        // 复杂 Case 2
        ConceptPatternNode c = some("r", variable("_C_"));
        ConceptPatternNode d = some("r",
                and(
                        concept("A"),
                        some("s", concept("D")),
                        variable("_X_")
                )
        );
        System.out.println("\n=== Complex Case 2 ===");
        DecAnalyze.DecResult res = dec.dec(c, d);
        if (res == null) {
            System.out.println("FAIL");
        } else if (res.subGoals.isEmpty()) {
            System.out.println("SUCCESS (no sub-goals)");
        } else {
            System.out.println("Sub-goals:");
            for (SimpleEntry<ConceptPatternNode, ConceptPatternNode> e : res.subGoals) {
                System.out.println("  " + e.getKey() + " ⊑? " + e.getValue());
            }
        }

        // 1. 初始化 ELAnalyze 并注入 mock subsumption
        ELAnalyze elAnalyze = new ELAnalyze();
        // 设定 A ⊑ B 为 true，A ⊑ C 为 false
        ConceptPatternNode A = concept("A");
        ConceptPatternNode B = concept("B");
        ConceptPatternNode C = concept("C");
        elAnalyze.setMockSubsumption(A, B, true);
        elAnalyze.setMockSubsumption(A, C, false);

        // 2. 构造 GoalOrientedMatcher
        GoalOrientedMatcher matcher = new GoalOrientedMatcher(elAnalyze);

        // —— 测试用例列表 —— //
        // 每一个测试由一个 Gamma 和期望（可选）组成
        Object[][] Tests = {
                // 1) A ⊑? A  （结构化判断，总是 true）
                { new GammaBuilder().add(A, A).build(),       "A ⊑? A" },
                // 2) A ⊑? B  （mock→true）
                { new GammaBuilder().add(A, B).build(),       "A ⊑? B (mock true)" },
                // 3) A ⊑? C  （mock→false）
                { new GammaBuilder().add(A, C).build(),       "A ⊑? C (mock false)" },
                // 4) A ⊓ B ⊑? B  （分解规则应当成功）
                { new GammaBuilder().add(and(A, B), B).build(), "A ⊓ B ⊑? B" },
                // 5) ∃r.A ⊑? ∃r.B  （mock A⊑B 为 true，因此成功）
                { new GammaBuilder().add(some("r", A), some("r", B)).build(), "∃r.A ⊑? ∃r.B" },
                // 6) ∃r.A ⊓ C ⊑? ∃r.B  （分解+mutation 也可成功，只要 A⊑B）
                { new GammaBuilder().add(and(some("r", A), C), some("r", B)).build(),
                        "∃r.A ⊓ C ⊑? ∃r.B" }
        };

        // 3. 逐个运行并打印结果
        for (Object[] test : Tests) {
            Gamma gamma = (Gamma) test[0];
            String label = (String) test[1];

            boolean ok = matcher.match(gamma);
            System.out.printf("%-20s → %s%n", label, ok ? "SUCCESS" : "FAILURE");
        }






        String[] tests = {
                "A⊑_X_",                           // true
                "A ⊑ B",                           // true
                "∃r.A ⊓ B ⊑ ∃s.(C ⊓ ∃t.D)",        // true
                "∃r.A ⊓ B ⊑ ∃s.(C ⊓ ∃t._D_)",      // true
                "∃r.A⊓B ⊑∃s.(C⊓∃t._D_)",           // true
                "∃r.a⊓B ⊑∃s.(C⊓∃t._D_)",           // false
                "a⊓B ⊑∃s.(C⊓∃t._D_)",              // false
                "⊓ A B ⊑ C",                       // false (⊓ 非二元)
                "A ⊑ B ⊓",                         // false (⊓ 非二元)
                "A ⊑ ∃r",                          // false (缺少‘.’后面pattern)
                "A ⊑ B ⊑ C"                        // false (多于一个⊑)
        };
        for (String test : tests) {
            System.out.printf("%-30s → %b%n", test, ELSyntaxChecker.isValid(test));
        }


        String gammaFile = "/Users/roy/Desktop/RPtask/task1/src/gamma.txt";
        String tboxFile  = "/Users/roy/Desktop/RPtask/task1/src/tbox.txt";

        String rightGroundGammaFile = "/Users/roy/Desktop/RPtask/task1/src/rightGroundGamma.txt";

        ELAnalyze repo = new ELAnalyze();
        try {
            repo.loadGamma(gammaFile);
            repo.loadTBox(tboxFile);

            System.out.println("=== Valid Gamma Lines ===");
            for (String g : repo.getGammaLines()) {
                System.out.println(g);
            }
            System.out.println("=== replaced left ground  Gamma Lines ===");
            repo.replaceLeftGroundGammaVariablesWithTop();
            for (String g : repo.getGammaLines()) {
                System.out.println(g);
            }

            System.out.println("=== Valid TBox Lines ===");
            for (String t : repo.getTBoxLines()) {
                System.out.println(t);
            }
        } catch (IOException e) {
            System.err.println("Error reading files: " + e.getMessage());
            e.printStackTrace();
        }



        ELAnalyze el_bottom = new ELAnalyze();
        try {
            el_bottom.loadGamma(rightGroundGammaFile);
            el_bottom.loadTBox(tboxFile);

            System.out.println("=== Valid Gamma Lines ===");
            for (String g : el_bottom.getGammaLines()) {
                System.out.println(g);
            }

            System.out.println("=== Valid TBox Lines ===");
            for (String t : el_bottom.getTBoxLines()) {
                System.out.println(t);
            }

            el_bottom.computeBottom();
            System.out.println("=== Bottom ===");
            System.out.println(el_bottom.getBottom());


            System.out.println("=== replaced right ground Gamma Lines ===");
            el_bottom.replaceRightGroundGammaVariablesWithBottom();
            for (String g : el_bottom.getGammaLines()) {
                System.out.println(g);
            }



        } catch (IOException e) {
            System.err.println("Error reading files: " + e.getMessage());
            e.printStackTrace();
        }

    }


    /** 已改为接收 ConceptPatternNode，输出结构化子式 */
    static void test(DecAnalyze dec,
                     ConceptPatternNode left,
                     ConceptPatternNode right) {
        System.out.println("\n=== Testing Dec(" + left + " ⊑? " + right + ") ===");
        DecAnalyze.DecResult result = dec.dec(left, right);
        if (result == null) {
            System.out.println("⇒ FAIL");
        } else if (result.subGoals.isEmpty()) {
            System.out.println("⇒ SUCCESS (no new subsumptions)");
        } else {
            System.out.println("⇒ Generated sub-goals:");
            for (SimpleEntry<ConceptPatternNode, ConceptPatternNode> e : result.subGoals) {
                System.out.println("   " + e.getKey() + " ⊑? " + e.getValue());
            }
        }
    }

    /**
     * 简易的 Gamma 构造器，支持链式 add(...)
     */
    static class GammaBuilder {
        private final Gamma gamma = new Gamma();
        GammaBuilder add(ConceptPatternNode left, ConceptPatternNode right) {
            gamma.add(left, right);
            return this;
        }
        Gamma build() {
            return gamma;
        }
    }

}








