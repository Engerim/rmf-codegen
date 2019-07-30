package io.vrap.codegen.languages.php.model

import com.google.common.net.MediaType
import com.google.inject.Inject
import com.google.inject.name.Named
import io.vrap.codegen.languages.php.PhpSubTemplates
import io.vrap.codegen.languages.php.extensions.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.MethodRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.responses.Body
import io.vrap.rmf.raml.model.responses.Response
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.types.impl.TypesFactoryImpl
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.eclipse.emf.ecore.EObject
import javax.management.Query

class PhpMethodRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : MethodRenderer, EObjectTypeExtensions {

    @Inject
    @BasePackageName
    lateinit var packagePrefix:String

    private val resourcePackage = "Resource";

    override fun render(type: Method): TemplateFile {
        val vrapType = vrapTypeProvider.doSwitch(type as EObject) as VrapObjectType

        val resultTypes = type.responses.asSequence().filter { it.bodies.asSequence().filter { body -> MediaType.JSON_UTF_8.`is`(body.contentMediaType) }.toList().isNotEmpty() };
        val importTypes = resultTypes.map { response -> "use ${response.bodies.asSequence().filter { body -> MediaType.JSON_UTF_8.`is`(body.contentMediaType) }.first().returnType().returnTypeModelFullClass().escapeAll()};" }
                .plus(resultTypes.map { response -> "use ${response.bodies.asSequence().filter { body -> MediaType.JSON_UTF_8.`is`(body.contentMediaType) }.first().returnType().returnTypeFullClass().escapeAll()};" })
                .plus("use ${packagePrefix.toNamespaceName()}\\Base\\JsonObject;".escapeAll())
                .plus("use ${packagePrefix.toNamespaceName()}\\Base\\JsonObjectModel;".escapeAll())
                .distinct().sorted()
        val returnTypes = resultTypes.map { response -> response.bodies.asSequence().filter { body -> MediaType.JSON_UTF_8.`is`(body.contentMediaType) }.first().returnType().returnTypeClass() }
                .plus("JsonObject")
                .distinct().sorted()


        val content = """
            |<?php
            |${PhpSubTemplates.generatorInfo}
            |namespace ${vrapType.`package`.toNamespaceName().escapeAll()}\\$resourcePackage;
            |
            |use GuzzleHttp\\Client;
            |use GuzzleHttp\\Exception\\ServerException;
            |use GuzzleHttp\\Exception\\ClientException;
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Base\\MapperInterface;
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Base\\ResultMapper;
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Exception\\InvalidArgumentException;
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Exception\\ApiServerException;
            |use ${packagePrefix.toNamespaceName().escapeAll()}\\Exception\\ApiClientException;
            |use ${vrapType.`package`.toNamespaceName().escapeAll()}\\ApiRequest;
            |${importTypes.joinToString("\n")}
            |${if (type.firstBody()?.type is FileType) "use Psr\\Http\\Message\\UploadedFileInterface;".escapeAll() else ""}
            |use Psr\\Http\\Message\\ResponseInterface;
            |
            |/** @psalm-suppress PropertyNotSetInConstructor */
            |class ${type.toRequestName()} extends ApiRequest
            |{
            |    /**
            |     <<${type.allParams()?.asSequence()?.map { "* @psalm-param scalar $$it" }?.joinToString(separator = "\n") ?: "*"}>>
            |     * @param ${if (type.firstBody()?.type is FileType) "?UploadedFileInterface " else "?object"} $!body
            |     * @psalm-param array<string, scalar|scalar[]> $!headers
            |     * @param array $!headers
            |     */
            |    public function __construct(${type.allParams()?.asSequence()?.map { "$$it, "  }?.joinToString(separator = "") ?: ""}${if (type.firstBody()?.type is FileType) "UploadedFileInterface " else ""}$!body = null, array $!headers = [], Client $!client = null)
            |    {
            |        $!uri = str_replace([${type.allParams()?.asSequence()?.map { "'{$it}'"  }?.joinToString(separator = ", ") ?: ""}], [${type.allParams()?.asSequence()?.map { "$$it"  }?.joinToString(separator = ", ") ?: ""}], '${type.resource().fullUri.template}');
            |        <<${type.firstBody()?.ensureContentType() ?: ""}>>
            |        <<${type.headers.filter { it.type?.default != null }.map { "\$headers = \$this->ensureHeader(\$headers, '${it.name}', '${it.type.default.value}');" }.joinToString("\n\n")}>>
            |        parent::__construct($!client, '${type.methodName.toUpperCase()}', $!uri, $!headers, ${type.firstBody()?.serialize()?: "!is_null(\$body) ? json_encode(\$body) : null"});
            |    }
            |
            |    /**
            |     * @template T of JsonObject
            |     * @psalm-param ?class-string<T> $!resultType
            |     * @return ${returnTypes.joinToString("|")}|null
            |     */
            |    public function mapFromResponse(?ResponseInterface $!response, string $!resultType = null)
            |    {
            |        if (is_null($!response)) {
            |            return null;
            |        }
            |        $!mapper = new ResultMapper();
            |        if (is_null($!resultType)) {
            |            switch ($!response->getStatusCode()) {
            |                <<${resultTypes.map { response -> "case \"${response.statusCode}\": $!resultType = ${response.bodies[0].returnType().returnTypeModelClass()}::class; break;" }.joinToString("\n")}>>
            |                default:
            |                    $!resultType = JsonObjectModel::class; break;
            |            }
            |        }
            |        return $!mapper->mapResponseToClass($!resultType, $!response);
            |    }
            |    
            |    /**
            |     * @template T of JsonObject
            |     * @psalm-param ?class-string<T> $!resultType
            |     * @return ${returnTypes.joinToString("|")}|null
            |     */
            |    public function execute(string $!resultType = null)
            |    {
            |        try {
            |           $!response = $!this->send();
            |        } catch(ServerException $!e) {
            |            $!result = $!this->mapFromResponse($!e->getResponse());
            |            throw new ApiServerException($!e->getMessage(), $!result, $!this, $!e->getResponse(), $!e, []);
            |        } catch(ClientException $!e) {
            |            $!result = $!this->mapFromResponse($!e->getResponse());
            |            throw new ApiClientException($!e->getMessage(), $!result, $!this, $!e->getResponse(), $!e, []);
            |        }
            |        return $!this->mapFromResponse($!response, $!resultType);
            |    }
            |
            |   <<${type.queryParameters.map { it.withParam(type) }.joinToString("\n\n")}>>
            |}
        """.trimMargin().keepIndentation("<<", ">>").forcedLiteralEscape()
        val relativeTypeNamespace = vrapType.`package`.toNamespaceName().replace(packagePrefix.toNamespaceName() + "\\", "").replace("\\", "/") + "/$resourcePackage"
        val relativePath = "src/" + relativeTypeNamespace + "/" + type.toRequestName() + ".php"
        return TemplateFile(
                relativePath = relativePath,
                content = content
        )
    }

    private fun QueryParameter.methodName(): String {
        val anno = this.getAnnotation("placeholderParam");

        if (anno != null) {
            val o = anno.getValue() as ObjectInstance
            val paramName = o.value.stream().filter { propertyValue -> propertyValue.name == "paramName" }.findFirst().orElse(null).value as StringInstance
            return "with" + StringCaseFormat.UPPER_CAMEL_CASE.apply(paramName.value)
        }
        return "with" + StringCaseFormat.UPPER_CAMEL_CASE.apply(this.name.replace(".", "-"))
    }

    private fun QueryParameter.methodParam(): String {
        val anno = this.getAnnotation("placeholderParam");

        if (anno != null) {
            val o = anno.value as ObjectInstance
            val placeholder = o.value.stream().filter { propertyValue -> propertyValue.name == "placeholder" }.findFirst().orElse(null).value as StringInstance
            val paramName = o.value.stream().filter { propertyValue -> propertyValue.name == "paramName" }.findFirst().orElse(null).value as StringInstance
            return "$" + StringCaseFormat.LOWER_CAMEL_CASE.apply(placeholder.value) + ", $" + paramName.value
        }
        return "$" + StringCaseFormat.LOWER_CAMEL_CASE.apply(this.name.replace(".", "-"))
    }

    private fun QueryParameter.paramName(): String {
        val anno = this.getAnnotation("placeholderParam");

        if (anno != null) {
            val o = anno.value as ObjectInstance
            val paramName = o.value.stream().filter { propertyValue -> propertyValue.name == "paramName" }.findFirst().orElse(null).value as StringInstance
            return "$" + paramName.value
        }
        return "$" + StringCaseFormat.LOWER_CAMEL_CASE.apply(this.name.replace(".", "-"))
    }

    private fun QueryParameter.template(): String {
        val anno = this.getAnnotation("placeholderParam");

        if (anno != null) {
            val o = anno.value as ObjectInstance
            val template = o.value.stream().filter { propertyValue -> propertyValue.name == "template" }.findFirst().orElse(null).value as StringInstance
            val placeholder = o.value.stream().filter { propertyValue -> propertyValue.name == "placeholder" }.findFirst().orElse(null).value as StringInstance
            return "sprintf('" + template.value.replace("<" + placeholder.value + ">", "%s") + "', $" + placeholder.value + ")"
        }
        return "'" + this.name + "'"
    }

    private fun QueryParameter.placeholderDocBlock(): String {
        val anno = this.getAnnotation("placeholderParam");

        if (anno != null) {
            val o = anno.value as ObjectInstance
            val placeholder = o.value.stream().filter { propertyValue -> propertyValue.name == "placeholder" }.findFirst().orElse(null).value as StringInstance
            return "@psalm-param scalar $" + placeholder.value
        }
        return ""
    }

    private fun QueryParameter.withParam(type: Method): String {
        return """
            |/**
            | * ${this.placeholderDocBlock()}
            | * @psalm-param scalar ${this.paramName()}
            | */
            |public function ${this.methodName()}(${this.methodParam()}): ${type.toRequestName()}
            |{
            |    return $!this->withQueryParam(${this.template()}, ${this.paramName()});
            |}
        """.trimMargin()
    }

    private fun Body.ensureContentType(): String {
        if (this.type !is FileType) {
            return "";
        }
        return """
            |if (!is_null($!body)) {
            |    $!mediaType = $!body->getClientMediaType();
            |    if (!is_null($!mediaType)) {
            |        $!headers = $!this->ensureHeader($!headers, 'Content-Type', $!mediaType);
            |    }
            |}
        """.trimMargin()
    }
    private fun Body.serialize(): String {
        if (this.type is FileType) {
            return "!is_null(\$body) ? \$body->getStream() : null"
        }
        return "!is_null(\$body) ? json_encode(\$body) : null"
    }





//    fun getAllParamNames(): List<String>? {
//        val params = getAbsoluteUri().getComponents().stream()
//                .filter({ uriTemplatePart -> uriTemplatePart is Expression })
//                .flatMap({ uriTemplatePart -> (uriTemplatePart as Expression).varSpecs.stream().map<String>(Function<VarSpec, String> { it.getVariableName() }) })
//                .collect(Collectors.toList<String>())
//        return if (params.size > 0) {
//            params
//        } else null
//    }
//
//    fun getQueryParameters(): List<QueryParameter> {
//        return method.getQueryParameters().stream().filter({ parameter ->
//            val placeholderParam = parameter.getAnnotation("placeholderParam") == null
//            placeholderParam
//        }).collect(Collectors.toList<QueryParameter>())
//    }

//    fun ResourceCollection.methods(): String {
//        return this.resources
//                .flatMap { resource -> resource.methods.map { javaBody(resource, it) } }
//                .joinToString(separator = "\n\n")
//    }

//    fun javaBody(resource: Resource, method: Method): String {
//        val methodReturnType = vrapTypeProvider.doSwitch(method.retyurnType())
//        val body = """
//            |${method.toComment().escapeAll()}
//            |@Retryable(
//            |          value = { ConnectException.class },
//            |          maxAttemptsExpression = "#{${'$'}{retry.${method.method.name}.maxAttempts}}",
//            |          backoff = @Backoff(delayExpression = "#{1}", maxDelayExpression = "#{5}", multiplierExpression = "#{2}"))
//            |public ${methodReturnType.fullClassName().escapeAll()} ${method.method.name.toLowerCase()}(${methodParameters(resource, method)}) {
//            |
//            |    final Map\<String, Object\> parameters = new HashMap\<\>();
//            |
//            |    <${resource.allUriParameters.map { "parameters.put(\"${it.name}\",${it.name});" }.joinToString(separator = "\n")}>
//            |
//            |    <${method.mediaType().escapeAll()}>
//            |
//            |    final ParameterizedTypeReference\<${method.retyurnType().toVrapType().fullClassName().escapeAll()}\> type = new ParameterizedTypeReference\<${method.retyurnType().toVrapType().fullClassName().escapeAll()}\>() {};
//            |    final String fullUri = baseUri + "${resource.fullUri.template}";
//            |
//            |    return restTemplate.exchange(fullUri, HttpMethod.${method.method.name.toUpperCase()}, entity, type, parameters).getBody();
//            |
//            |}
//                """.trimMargin()
//        return body
//    }
//
//    fun methodParameters(resource: Resource, method: Method): String {
//
//        val paramList = resource.allUriParameters
//                .map { "final ${it.type.toVrapType().simpleName()} ${it.name}" }
//                .toMutableList()
//
//        if (method.bodies?.isNotEmpty() ?: false) {
//            paramList.add(method.bodies[0].type.toVrapType().fullClassName())
//        }
//        return paramList.joinToString(separator = ", ")
//    }
//
//    fun Method.mediaType(): String {
//        if (this.bodies?.isNotEmpty() ?: false) {
//            val result = """
//                |final HttpHeaders headers = new HttpHeaders();
//                |headers.setContentType(MediaType.parseMediaType(("${this.bodies[0].contentType}")));
//                |final HttpEntity<${this.bodies[0].type.toVrapType().fullClassName()}> entity = new HttpEntity<>(body, headers);
//        """.trimMargin()
//            return result
//
//        }
//        return "final HttpEntity<?> entity = null;"
//
//    }
//
//    fun Method.retyurnType(): AnyType {
//        return this.responses
//                .filter { it.isSuccessfull() }
//                .filter { it.bodies?.isNotEmpty() ?: false }
//                .first()
//                .let { it.bodies[0].type }
//                ?: TypesFactoryImpl.eINSTANCE.createNilType()
//    }


    fun Response.isSuccessfull(): Boolean = this.statusCode.toInt() in (200..299)

    fun Body.returnType(): AnyType {
        return this.type
                ?: TypesFactoryImpl.eINSTANCE.createNilType()
    }

    fun Method.returnType(): AnyType {
        return this.responses
                .filter { it.isSuccessfull() }
                .filter { it.bodies?.isNotEmpty() ?: false }
                .firstOrNull()
                ?.let { it.bodies[0].type }
                ?: TypesFactoryImpl.eINSTANCE.createNilType()
    }

    fun AnyType.returnTypeClass(): String {
        val vrapType = this.toVrapType()
        if (vrapType.isScalar())
            return "JsonObject"
        return when (vrapType) {
            is VrapObjectType -> vrapType.simpleName()
            else -> "JsonObject"
        }
    }

    fun AnyType.returnTypeModelClass(): String {
        val vrapType = this.toVrapType()
        if (vrapType.isScalar())
            return "JsonObjectModel"
        return when (vrapType) {
            is VrapObjectType -> vrapType.simpleName() + "Model"
            else -> "JsonObjectModel"
        }
    }

    fun AnyType.returnTypeFullClass(): String {
        val vrapType = this.toVrapType()
        if (vrapType.isScalar())
            return "${packagePrefix.toNamespaceName()}\\Base\\JsonObject"
        return when (vrapType) {
            is VrapObjectType -> vrapType.fullClassName()
            else -> "${packagePrefix.toNamespaceName()}\\Base\\JsonObject"
        }
    }

    fun AnyType.returnTypeModelFullClass(): String {
        val vrapType = this.toVrapType()
        if (vrapType.isScalar())
            return "${packagePrefix.toNamespaceName()}\\Base\\JsonObjectModel"
        return when (vrapType) {
            is VrapObjectType -> vrapType.fullClassName() + "Model"
            else -> "${packagePrefix.toNamespaceName()}\\Base\\JsonObjectModel"
        }
    }

    fun Method.returnTypeClass(): String {
        return this.returnType().returnTypeClass();
    }

    fun Method.returnTypeModelClass(): String {
        val vrapType = this.returnType().toVrapType()
        if (vrapType.isScalar())
            return "JsonObjectModel"
        return when (vrapType) {
            is VrapObjectType -> vrapType.simpleName() + "Model"
            else -> "JsonObjectModel"
        }
    }

    fun Method.returnTypeFullClass(): String {
        val vrapType = this.returnType().toVrapType()
        if (vrapType.isScalar())
            return "${packagePrefix.toNamespaceName()}\\Base\\JsonObject"
        return when (vrapType) {
            is VrapObjectType -> vrapType.fullClassName()
            else -> "${packagePrefix.toNamespaceName()}\\Base\\JsonObject"
        }
    }

    fun Method.returnTypeModelFullClass(): String {
        val vrapType = this.returnType().toVrapType()
        if (vrapType.isScalar())
            return "${packagePrefix.toNamespaceName()}\\Base\\JsonObjectModel"
        return when (vrapType) {
            is VrapObjectType -> vrapType.fullClassName() + "Model"
            else -> "${packagePrefix.toNamespaceName()}\\Base\\JsonObjectModel"
        }
    }


    fun <T> EObject.getParent(parentClass: Class<T>): T? {
        if (this.eContainer() == null) {
            return null
        }
        return if (parentClass.isInstance(this.eContainer())) {
            this.eContainer() as T
        } else this.eContainer().getParent(parentClass)
    }
}
