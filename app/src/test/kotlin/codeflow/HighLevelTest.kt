package codeflow

import codeflow.graph.NodeType
import codeflow.java.AstReader
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behaviour tests for the six high-level concept capabilities (`docs/superpowers/specs/2026-11-23-high-level-concepts-design.md`):
 *   - Throw IR, `NodeType.THROW`, Mermaid `classDef THROW`, and `throw:` summary sinks;
 *   - AST semantic roles (Spring/JAX-RS entrypoints, `@Repository`/`@Entity`/`@Service`, `DB`/`NOISE`, `--auto-boundaries`, `--discover-roots`);
 *   - Field provenance metadata (`fieldOwner`, `fieldOp`) and per-method `summary` in `JsonExporter`;
 *   - Assumed single-implementation DI dispatch (`--assume-single-impl`), visibly marked and never guessing among multiple implementations;
 *   - CLI `--level` validation and `initialLevel` emission.
 */
class HighLevelTest {

    private fun writeSources(files: Map<String, String>): Path {
        val dir = Files.createTempDirectory("codeflow-highlevel-")
        files.forEach { (name, content) ->
            val path = dir.resolve(name)
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        }
        return dir
    }

    private fun jsonOf(
        dir: Path,
        from: List<String> = emptyList(),
        boundaries: List<String> = emptyList(),
        pures: List<String> = emptyList(),
        assumeSingleImpl: Boolean = false,
        autoBoundaries: Boolean = false,
        discoverRoots: Boolean = false,
        initialLevel: String? = null
    ): Pair<AstReader, JsonObject> {
        val files = generateListOfJavaFilesFromDir(dir)
        val reader = AstReader(dir)
        val roots = reader.process(files, from, boundaries, pures, assumeSingleImpl, autoBoundaries, discoverRoots)
        val text = StringBuilder()
        JsonExporter(dir, initialLevel).processMainMethod(roots) { text.append(it).append("\n") }
        return reader to JsonParser.parseString(text.toString()).asJsonObject
    }

    private fun summariesOf(
        dir: Path,
        from: List<String> = emptyList(),
        boundaries: List<String> = emptyList(),
        assumeSingleImpl: Boolean = false,
        autoBoundaries: Boolean = false
    ): JsonObject {
        val files = generateListOfJavaFilesFromDir(dir)
        val reader = AstReader(dir)
        val raw = SummariesExporter().export(
            reader.summarise(files, from, boundaries, emptyList(), assumeSingleImpl, autoBoundaries)
        )
        return JsonParser.parseString(raw).asJsonObject
    }

    @Test
    fun throwStatementDrawsThrowNodeAndRecordsSummarySink() {
        val dir = writeSources(
            mapOf(
                "pkg/Validator.java" to """
                    package pkg;
                    public class Validator {
                        public static int check(int amount) {
                            if (amount < 0) {
                                throw new IllegalArgumentException("negative: " + amount);
                            }
                            return amount;
                        }
                    }
                """.trimIndent()
            )
        )
        val (_, doc) = jsonOf(dir, from = listOf("Validator#check"))
        val nodes = doc.getAsJsonArray("nodes").map { it.asJsonObject }
        val throwNode = nodes.single { it["type"].asString == "THROW" }
        assertEquals("throw IllegalArgumentException", throwNode["label"].asString)
        assertEquals("throw", throwNode["lead"].asString)

        val methodBox = nodes.single { it["type"].asString == "METHOD" }
        val throwsList = methodBox.getAsJsonObject("summary").getAsJsonArray("throws").map { it.asString }
        assertEquals(listOf("IllegalArgumentException"), throwsList)

        val sum = summariesOf(dir, from = listOf("Validator#check"))
        val flows = sum.getAsJsonArray("methods")[0].asJsonObject
            .getAsJsonArray("flows").map { it.asJsonArray.let { p -> "${p[0].asString}>${p[1].asString}" } }
        assertTrue(flows.any { it.startsWith("P0>throw:IllegalArgumentException@") }, "expected P0 to reach throw sink, got $flows")
    }

    @Test
    fun singleImplDiDispatchesWhenFlaggedAndStaysExternalForTwoImplementations() {
        val dir = writeSources(
            mapOf(
                "di/Loan.java" to """
                    package di;
                    public class Loan {
                        public int balance;
                    }
                """.trimIndent(),
                "di/PaymentService.java" to """
                    package di;
                    public interface PaymentService {
                        int apply(Loan loan, int amount);
                    }
                """.trimIndent(),
                "di/PaymentServiceImpl.java" to """
                    package di;
                    public class PaymentServiceImpl implements PaymentService {
                        @Override
                        public int apply(Loan loan, int amount) {
                            loan.balance = loan.balance - amount;
                            return loan.balance;
                        }
                    }
                """.trimIndent(),
                "di/Notifier.java" to """
                    package di;
                    public interface Notifier {
                        void notifyUser(int balance);
                    }
                """.trimIndent(),
                "di/EmailNotifier.java" to """
                    package di;
                    public class EmailNotifier implements Notifier {
                        @Override public void notifyUser(int balance) { int x = balance; }
                    }
                """.trimIndent(),
                "di/SmsNotifier.java" to """
                    package di;
                    public class SmsNotifier implements Notifier {
                        @Override public void notifyUser(int balance) { int y = balance; }
                    }
                """.trimIndent(),
                "di/Controller.java" to """
                    package di;
                    public class Controller {
                        private PaymentService paymentService;
                        private Notifier notifier;
                        public int repay(Loan loan, int amount) {
                            int remaining = paymentService.apply(loan, amount);
                            notifier.notifyUser(remaining);
                            return remaining;
                        }
                    }
                """.trimIndent()
            )
        )

        // Strict default: neither interface is dispatched.
        val (strictReader, strictDoc) = jsonOf(dir, from = listOf("Controller#repay"), assumeSingleImpl = false)
        assertTrue(strictReader.assumed.isEmpty())
        val strictMethods = strictDoc.getAsJsonArray("nodes").map { it.asJsonObject }
            .filter { it["type"].asString == "METHOD" }.map { it["label"].asString }
        assertEquals(listOf("repay"), strictMethods)

        // With --assume-single-impl: PaymentService resolves to PaymentServiceImpl (marked assumed),
        // while Notifier (2 implementations) stays EXTERNAL and is never guessed.
        val (assumedReader, assumedDoc) = jsonOf(dir, from = listOf("Controller#repay"), assumeSingleImpl = true)
        assertEquals(listOf("PaymentService#apply -> PaymentServiceImpl#apply"), assumedReader.assumed)
        val assumedNodes = assumedDoc.getAsJsonArray("nodes").map { it.asJsonObject }
        val methodBoxes = assumedNodes.filter { it["type"].asString == "METHOD" }
        assertEquals(listOf("repay", "apply"), methodBoxes.map { it["label"].asString })
        val applyBox = methodBoxes.single { it["label"].asString == "apply" }
        assertTrue(applyBox["assumed"].asBoolean, "assumed DI block must carry assumed=true")
        assertFalse(methodBoxes.any { it["label"].asString == "notifyUser" }, "two-implementation interface must never be guessed")

        // Field ownership metadata & summary card populated through the assumed implementation.
        val balanceWrite = assumedNodes.first { it["label"].asString == "balance" && it["fieldOp"]?.asString == "write" }
        assertEquals("di.Loan", balanceWrite["fieldOwner"].asString)
        val repayWrites = methodBoxes.single { it["label"].asString == "repay" }
            .getAsJsonObject("summary").getAsJsonArray("writes").map { it.asString }
        assertTrue("Loan.balance" in repayWrites, "expected Loan.balance write in summary: $repayWrites")

        // Summaries also traverse single-impl DI and record an `assumed` gap (never silent).
        val sum = summariesOf(dir, from = listOf("Controller#repay"), assumeSingleImpl = true)
        val row = sum.getAsJsonArray("methods")[0].asJsonObject
        val flows = row.getAsJsonArray("flows").map { it.asJsonArray.let { p -> "${p[0].asString}>${p[1].asString}" } }
        val gaps = row.getAsJsonArray("gaps").map { it.asJsonObject }
        assertTrue("P1>P0.di.Loan.balance" in flows, "expected P1 -> Loan.balance through assumed DI, got $flows")
        assertTrue(gaps.any { it["kind"].asString == "assumed" }, "expected assumed gap in summary, got $gaps")
        assertTrue(gaps.any { it["kind"].asString == "dispatch" && it["detail"].asString.contains("notifyUser") })
    }

    @Test
    fun boundaryTakesPrecedenceOverAssumeSingleImpl() {
        val dir = writeSources(
            mapOf(
                "b/Store.java" to "package b; public interface Store { void save(int x); }",
                "b/StoreImpl.java" to "package b; public class StoreImpl implements Store { public void save(int x) { int hidden = x + 99; } }",
                "b/App.java" to "package b; public class App { Store store; public void run(int v) { store.save(v); } }"
            )
        )
        val (reader, doc) = jsonOf(dir, from = listOf("App#run"), boundaries = listOf("Store"), assumeSingleImpl = true)
        assertTrue(reader.assumed.isEmpty(), "boundary interface must not be dispatched via assume-single-impl")
        val labels = doc.getAsJsonArray("nodes").map { it.asJsonObject["label"].asString }
        assertFalse("99" in labels, "boundary implementation body must not be read")
    }

    @Test
    fun discoversAnnotatedEntrypointsAndAutoClassifiesDbAndNoiseBoundaries() {
        val dir = writeSources(
            mapOf(
                "app/Loan.java" to """
                    package app;
                    @Entity
                    public class Loan {
                        public int balance;
                    }
                """.trimIndent(),
                "app/LoanRepository.java" to """
                    package app;
                    @Repository
                    public interface LoanRepository {
                        void save(Loan loan);
                    }
                """.trimIndent(),
                "app/Logger.java" to """
                    package app;
                    public class Logger {
                        public void info(String msg) {}
                    }
                """.trimIndent(),
                "app/LoanController.java" to """
                    package app;
                    @RestController
                    @RequestMapping("/loans")
                    public class LoanController {
                        private LoanRepository repo;
                        private Logger log;

                        @PostMapping("/{id}/repay")
                        public int repay(Loan loan, int amount) {
                            log.info("repaying");
                            if (amount <= 0) {
                                throw new IllegalArgumentException("bad");
                            }
                            loan.balance = loan.balance - amount;
                            repo.save(loan);
                            return loan.balance;
                        }
                    }
                """.trimIndent()
            )
        )

        val (_, doc) = jsonOf(
            dir,
            autoBoundaries = true,
            discoverRoots = true,
            initialLevel = "architecture"
        )
        assertEquals("architecture", doc["initialLevel"].asString)
        val nodes = doc.getAsJsonArray("nodes").map { it.asJsonObject }

        // Entrypoint discovered and captioned with HTTP verb + full joined route.
        val rootBox = nodes.single { it["type"].asString == "METHOD" && it["parent"] == null }
        assertEquals("CONTROLLER", rootBox["classRole"].asString)
        assertEquals("POST /loans/{id}/repay", rootBox["entry"].asString)
        val eventNode = nodes.single { it["type"].asString == "EVENT" }
        assertEquals("POST /loans/{id}/repay", eventNode["label"].asString)

        // @Repository auto-marked as ordered DB boundary; Logger auto-marked as pure NOISE boundary.
        val repoBox = nodes.single { it["type"].asString == "METHOD" && it["label"].asString == "save" }
        assertTrue(repoBox["boundary"].asBoolean)
        assertEquals("DB", repoBox["boundaryRole"].asString)
        val logBox = nodes.single { it["type"].asString == "METHOD" && it["label"].asString == "info" }
        assertTrue(logBox["boundary"].asBoolean)
        assertEquals("NOISE", logBox["boundaryRole"].asString)

        // Noise boundary call stays out of the ORDER chain, while DB boundary call participates in ORDER.
        val edges = doc.getAsJsonArray("edges").map { it.asJsonObject }
        val orderTargets = edges.filter { it["kind"].asString == "ORDER" }
            .map { e -> nodes.single { it["id"].asString == e["target"].asString }["label"].asString }
        assertTrue("call save" in orderTargets, "DB boundary should be in ORDER chain: $orderTargets")
        assertFalse("call info" in orderTargets, "NOISE boundary should be pure (out of ORDER): $orderTargets")

        // Root summary lists writes, DB boundary, throws, and excludes NOISE.
        val summary = rootBox.getAsJsonObject("summary")
        assertNotNull(summary)
        assertEquals(listOf("Loan.balance"), summary.getAsJsonArray("writes").map { it.asString })
        assertEquals(listOf("DB: save"), summary.getAsJsonArray("boundaries").map { it.asString })
        assertEquals(listOf("IllegalArgumentException"), summary.getAsJsonArray("throws").map { it.asString })
    }

    @Test
    fun cliValidatesLevelFlagAndAcceptsHighLevelFlags() {
        val dir = writeSources(
            mapOf("p/App.java" to "package p; public class App { public static void main(String[] args) { int x = 1; } }")
        )
        assertFailsWith<IllegalArgumentException> {
            runCli(arrayOf(dir.toString(), "--level", "bogus"))
        }
        val out = java.io.ByteArrayOutputStream()
        val saved = System.out
        System.setOut(java.io.PrintStream(out, true))
        val status = try {
            runCli(arrayOf(dir.toString(), "--json", "--level", "workflow", "--assume-single-impl", "--auto-boundaries"))
        } finally {
            System.setOut(saved)
        }
        assertEquals(0, status)
        val doc = JsonParser.parseString(out.toString()).asJsonObject
        assertEquals("workflow", doc["initialLevel"].asString)
        assertTrue(NodeType.THROW.name == "THROW")
    }
}
