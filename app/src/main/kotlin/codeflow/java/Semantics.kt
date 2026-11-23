package codeflow.java

import codeflow.graph.Method
import codeflow.java.processors.GlobalContext
import com.sun.source.tree.AnnotationTree
import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.LiteralTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.NewArrayTree
import com.sun.source.tree.ParameterizedTypeTree
import com.sun.source.tree.Tree
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType

/**
 * Deterministic semantic roles harvested from javac's parsed AST and resolved elements.
 *
 * Everything here is read from annotations, declaring types, and supertypes written in the source
 * (never guessed from comments or heuristics on arbitrary text). Because codeflow runs without a
 * classpath, annotations like `@PostMapping`, `@Repository`, and `@Entity` are frequently
 * unresolved as `TypeElement`s; reading their parsed `AnnotationTree` names still works
 * deterministically because javac always parses annotations into the tree.
 */
object Semantics {

    const val ROLE_DB = "DB"
    const val ROLE_HTTP = "HTTP"
    const val ROLE_EVENT = "EVENT"
    const val ROLE_NOISE = "NOISE"

    const val CLASS_CONTROLLER = "CONTROLLER"
    const val CLASS_SERVICE = "SERVICE"
    const val CLASS_REPOSITORY = "REPOSITORY"
    const val CLASS_ENTITY = "ENTITY"

    private val SPRING_HTTP_MAPPINGS = mapOf(
        "GetMapping" to "GET",
        "PostMapping" to "POST",
        "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE",
        "PatchMapping" to "PATCH",
        "RequestMapping" to "HTTP"
    )

    private val JAXRS_HTTP_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH")

    private val CONTROLLER_ANNOTATIONS = setOf("RestController", "Controller", "Path")
    private val SERVICE_ANNOTATIONS = setOf("Service", "Component", "Named", "ApplicationScoped")
    private val REPOSITORY_ANNOTATIONS = setOf("Repository")
    private val ENTITY_ANNOTATIONS = setOf("Entity", "Table", "Document", "MappedSuperclass")

    private val DB_TYPE_NAMES = setOf(
        "JpaRepository", "CrudRepository", "PagingAndSortingRepository", "ListCrudRepository",
        "Repository", "JdbcTemplate", "NamedParameterJdbcTemplate", "EntityManager", "Session"
    )

    private val HTTP_TYPE_NAMES = setOf(
        "RestTemplate", "WebClient", "RestClient", "HttpClient", "OkHttpClient", "CloseableHttpClient"
    )

    private val EVENT_TYPE_NAMES = setOf(
        "ApplicationEventPublisher", "KafkaTemplate", "JmsTemplate", "RabbitTemplate", "EventBus", "Producer"
    )

    private val NOISE_TYPE_NAMES = setOf(
        "Logger", "Log", "LoggerFactory", "Objects", "Preconditions",
        "StringBuilder", "StringBuffer", "PrintStream", "Assert"
    )

    private val NOISE_LOG_METHODS = setOf("trace", "debug", "info", "warn", "error", "isDebugEnabled", "isTraceEnabled", "isInfoEnabled")
    private val NOISE_UTIL_METHODS = setOf("requireNonNull", "requireNonNullElse", "checkNotNull", "checkArgument", "checkState")

    /** Simple name of an annotation as written in source (`@org.foo.GetMapping` -> `GetMapping`). */
    fun annotationName(annotation: AnnotationTree): String = simpleTypeName(annotation.annotationType)

    private fun simpleTypeName(tree: Tree): String = when (tree) {
        is IdentifierTree -> tree.name.toString()
        is MemberSelectTree -> tree.identifier.toString()
        is ParameterizedTypeTree -> simpleTypeName(tree.type)
        else -> tree.toString().substringBefore('<').substringAfterLast('.')
    }

    /** First string literal carried by an annotation attribute (`value`, `path`, or the sole unnamed arg). */
    private fun annotationString(annotation: AnnotationTree, keys: Set<String> = setOf("value", "path")): String? {
        for (arg in annotation.arguments) {
            if (arg is AssignmentTree) {
                val key = (arg.variable as? IdentifierTree)?.name?.toString()
                if (key in keys) return firstStringLiteral(arg.expression)
            } else {
                firstStringLiteral(arg)?.let { return it }
            }
        }
        return null
    }

    private fun firstStringLiteral(expr: ExpressionTree): String? = when (expr) {
        is LiteralTree -> expr.value as? String
        is NewArrayTree -> expr.initializers?.firstNotNullOfOrNull { firstStringLiteral(it) }
        else -> null
    }

    /** High-level role of a source class from its annotations and supertypes, or null if plain. */
    fun classRole(tree: ClassTree): String? {
        val names = tree.modifiers.annotations.mapTo(HashSet()) { annotationName(it) }
        return when {
            names.any { it in CONTROLLER_ANNOTATIONS } -> CLASS_CONTROLLER
            names.any { it in REPOSITORY_ANNOTATIONS } -> CLASS_REPOSITORY
            names.any { it in ENTITY_ANNOTATIONS } -> CLASS_ENTITY
            names.any { it in SERVICE_ANNOTATIONS } -> CLASS_SERVICE
            hasSupertypeMatching(tree) { it in DB_TYPE_NAMES || it.endsWith("Repository") } -> CLASS_REPOSITORY
            else -> null
        }
    }

    /**
     * Boundary category of a source class (`DB`, `HTTP`, `EVENT`, `NOISE`), or null when the class
     * is ordinary application code. Used both for metadata tagging and `--auto-boundaries`.
     */
    fun boundaryRole(tree: ClassTree): String? {
        val names = tree.modifiers.annotations.mapTo(HashSet()) { annotationName(it) }
        val simpleName = tree.simpleName.toString()
        return when {
            names.any { it in REPOSITORY_ANNOTATIONS } ||
                hasSupertypeMatching(tree) { it in DB_TYPE_NAMES || it.endsWith("Repository") } ||
                simpleName.endsWith("Repository") -> ROLE_DB
            "FeignClient" in names ||
                hasSupertypeMatching(tree) { it in HTTP_TYPE_NAMES } ||
                simpleName.endsWith("HttpClient") -> ROLE_HTTP
            hasSupertypeMatching(tree) { it in EVENT_TYPE_NAMES } -> ROLE_EVENT
            simpleName in NOISE_TYPE_NAMES -> ROLE_NOISE
            else -> null
        }
    }

    private fun hasSupertypeMatching(tree: ClassTree, predicate: (String) -> Boolean): Boolean {
        val extendsName = tree.extendsClause?.let { simpleTypeName(it) }
        if (extendsName != null && predicate(extendsName)) return true
        return tree.implementsClause.any { predicate(simpleTypeName(it)) }
    }

    /**
     * Semantic entrypoint caption for an annotated method (`POST /loans/{id}/repay`, `SCHEDULED poll`,
     * `LISTENER onPayment`, `COMMAND REPAY_LOAN`), or null for an unannotated method.
     */
    fun entrypointLabel(method: Method, declaringClass: GlobalContext.SourceClass?): String? {
        val methodAnns = method.name.modifiers.annotations
        val classAnns = declaringClass?.tree?.modifiers?.annotations.orEmpty()
        val classPath = classAnns.firstNotNullOfOrNull { ann ->
            val name = annotationName(ann)
            if (name == "RequestMapping" || name == "Path") annotationString(ann) else null
        }

        // Spring MVC mappings
        for (ann in methodAnns) {
            val verb = SPRING_HTTP_MAPPINGS[annotationName(ann)] ?: continue
            val methodPath = annotationString(ann)
            val route = joinRoute(classPath, methodPath)
            return if (route.isNotEmpty()) "$verb $route" else "$verb ${method.displayName()}"
        }

        // JAX-RS mappings (`@GET`, `@POST`, ... optionally with `@Path`)
        val jaxVerb = methodAnns.map { annotationName(it) }.firstOrNull { it in JAXRS_HTTP_METHODS }
        if (jaxVerb != null) {
            val methodPath = methodAnns.firstNotNullOfOrNull { ann ->
                if (annotationName(ann) == "Path") annotationString(ann) else null
            }
            val route = joinRoute(classPath, methodPath)
            return if (route.isNotEmpty()) "$jaxVerb $route" else "$jaxVerb ${method.displayName()}"
        }

        // Scheduling, event/message listeners, and Fineract-style command handlers
        for (ann in methodAnns) {
            when (annotationName(ann)) {
                "Scheduled" -> return "SCHEDULED ${method.displayName()}"
                "EventListener", "TransactionalEventListener", "KafkaListener", "JmsListener", "RabbitListener" ->
                    return "LISTENER ${method.displayName()}"
            }
        }
        val cmdAnn = classAnns.firstOrNull { annotationName(it) == "CommandType" }
        if (cmdAnn != null && method.name.name.toString() == "processCommand") {
            val entity = annotationString(cmdAnn, setOf("entity"))
            val action = annotationString(cmdAnn, setOf("action"))
            if (action != null && entity != null) return "COMMAND $action $entity"
            return "COMMAND ${declaringClass?.tree?.simpleName ?: method.displayName()}"
        }
        return null
    }

    private fun joinRoute(prefix: String?, suffix: String?): String {
        val a = prefix?.trim()?.trimEnd('/').orEmpty()
        val b = suffix?.trim()?.trimStart('/').orEmpty()
        return when {
            a.isEmpty() && b.isEmpty() -> ""
            a.isEmpty() -> if (b.startsWith("/")) b else "/$b"
            b.isEmpty() -> if (a.startsWith("/")) a else "/$a"
            else -> "${if (a.startsWith("/")) a else "/$a"}/$b"
        }
    }

    /**
     * Boundary/noise role of a call (`DB`, `HTTP`, `EVENT`, `NOISE`), or null when ordinary.
     *
     * Checked against the resolved target's enclosing type first, then against source class
     * declarations, and finally against known JDK/SLF4J utility method names when the receiver
     * type itself is unresolved.
     */
    fun callRole(target: Element?, callName: String, globalCtx: GlobalContext): String? {
        val owner = (target as? ExecutableElement)?.enclosingElement ?: target?.enclosingElement
        if (owner != null) {
            globalCtx.findClass(owner)?.let { sourceClass ->
                boundaryRole(sourceClass.tree)?.let { return it }
            }
            val ownerSimple = owner.simpleName.toString()
            val ownerQualified = (owner as? TypeElement)?.qualifiedName?.toString() ?: ownerSimple
            roleOfTypeName(ownerSimple, ownerQualified)?.let { return it }
            if (owner is TypeElement) {
                for (iface in owner.interfaces) {
                    val ifaceElem = (iface as? DeclaredType)?.asElement() as? TypeElement ?: continue
                    roleOfTypeName(ifaceElem.simpleName.toString(), ifaceElem.qualifiedName.toString())?.let { return it }
                }
            }
        }
        val plain = callName.removePrefix("call ").trim()
        if (plain in NOISE_UTIL_METHODS) return ROLE_NOISE
        if (owner == null && plain in NOISE_LOG_METHODS) return ROLE_NOISE
        return null
    }

    private fun roleOfTypeName(simple: String, qualified: String): String? = when {
        simple in DB_TYPE_NAMES || simple.endsWith("Repository") -> ROLE_DB
        simple in HTTP_TYPE_NAMES || simple.endsWith("HttpClient") -> ROLE_HTTP
        simple in EVENT_TYPE_NAMES || qualified.contains(".kafka.") && simple.endsWith("Producer") -> ROLE_EVENT
        simple in NOISE_TYPE_NAMES || qualified.startsWith("org.slf4j.") || qualified.startsWith("java.util.logging.") -> ROLE_NOISE
        else -> null
    }

    /** Qualified declaring class name of a field element, or null when unavailable. */
    fun fieldOwnerName(element: Element?): String? {
        if (element == null || !element.kind.isField) return null
        val owner = element.enclosingElement
        return (owner as? TypeElement)?.qualifiedName?.toString() ?: owner?.simpleName?.toString()
    }
}
