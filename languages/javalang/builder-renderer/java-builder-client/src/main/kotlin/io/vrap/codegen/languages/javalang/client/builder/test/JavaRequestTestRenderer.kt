package io.vrap.codegen.languages.javalang.client.builder.test

import io.vrap.codegen.languages.extensions.getMethodName
import io.vrap.codegen.languages.extensions.toRequestName
import io.vrap.codegen.languages.extensions.toResourceName
import io.vrap.codegen.languages.java.base.JavaSubTemplates
import io.vrap.codegen.languages.java.base.extensions.*
import io.vrap.codegen.languages.javalang.client.builder.ClientConstants

import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ResourceRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.types.ObjectInstance
import io.vrap.rmf.raml.model.types.QueryParameter
import io.vrap.rmf.raml.model.types.StringInstance

class JavaRequestTestRenderer constructor(override val vrapTypeProvider: VrapTypeProvider): ResourceRenderer, JavaEObjectTypeExtensions {
    private val resourcePackage = "Resource"

    override fun render(type: Resource): TemplateFile {
        val vrapType = vrapTypeProvider.doSwitch(type).toJavaVType() as VrapObjectType

        val content = """
            |package ${vrapType.`package`.toJavaPackage()};
            |
            |import com.commercetools.api.client.ApiRoot;
            |import com.commercetools.api.client.${type.toResourceName()};
            |import com.commercetools.api.defaultconfig.ApiFactory;
            |import com.commercetools.api.defaultconfig.ServiceRegion;
            |import io.vrap.rmf.base.client.oauth2.ClientCredentials;
            |import junitparams.JUnitParamsRunner;
            |import junitparams.Parameters;
            |import org.junit.Assert;
            |import org.junit.Test;
            |import org.junit.runner.RunWith;
            |import org.mockito.Mockito;
            |
            |<${JavaSubTemplates.generatedAnnotation}>
            |@RunWith(JUnitParamsRunner.class)
            |public class Resource${type.toResourceName()}Test {
            |    private final ApiHttpClient apiHttpClientMock = Mockito.mock(ApiHttpClient.class);
            |    private final String projectKey = "test_projectKey";
            |    private final ApiRoot apiRoot = createClient();
            |
            |    public final ApiRoot createClient() {  
            |        return ApiFactory.create(
            |           ClientCredentials.of().withClientId("your-client-id").withClientSecret("your-client-secret").withScopes("your-scopes").build(),
            |               ServiceRegion.GCP_EUROPE_WEST1.getOAuthTokenUrl(), ServiceRegion.GCP_EUROPE_WEST1.getApiUrl());
            |    }
            |
            |    ${if (type.methods.size > 0) """@Test
            |    @Parameters(method = "requestWithMethodParameters")
            |    public void withMethods(ApiHttpRequest request, String httpMethod, String uri) {
            |        Assert.assertEquals(httpMethod, request.getMethod().toString());
            |        Assert.assertEquals(uri, request.getUri().toString());
            |    }""".trimMargin() else ""}
            |    
            |    ${if (type.methods.size > 0) """@Test
            |    @Parameters(method = "resourcesParameters")
            |    public void resources(ApiHttpRequest request, String uri) {
            |        Assert.assertEquals(uri, request.getUri().toString());
            |    }""".trimMargin() else ""}
            |    
            |    ${if (type.methods.size > 0) """@Test
            |    @Parameters(method = "executeMethodParameters")
            |    public void executeWithNullPointerException(ApiHttpRequest httpRequest) throws Exception{
            |        Mockito.when(apiHttpClientMock.execute(httpRequest)).thenThrow(NullPointerException.class);   
            |    }""".trimMargin() else ""}
            |    
            |    private Object[] requestWithMethodParameters() {
            |       return new Object [] {
            |               ${type.methods.flatMap { method -> method.queryParameters.map { parameterTestProvider(type, method, it) } }.joinToString(",\n")}
            |       };
            |    }
            |    
            |    private Object[] resourcesParameters() {
            |       return new Object [] {
            |               ${type.resources.map { resourceTestProvider(it) }.joinToString(",\n")}
            |       };
            |       
            |    private Object[] executeMethodParameters() {
            |       return new Object [] {
            |               ${type.methods.flatMap { m -> m.responses.map { r -> requestTestProvider(type, m) }.plus(requestTestProvider(type, m)) }.joinToString(",\n")}
            |       };
            |    }
            |    
            |}
        """.trimMargin()

        val relativePath = "test/unit/" + "Resource" + type.toResourceName() + "Test.java"
        return TemplateFile(
                relativePath = relativePath,
                content = content
        )
    }

    private fun parameterTestProvider(resource: Resource, method: Method, parameter: QueryParameter): String {
        val anno = parameter.getAnnotation("placeholderParam", true)

        var paramName: String = parameter.name
        var template = parameter.template()
        if (anno != null) {
            val o = anno.value as ObjectInstance
            val placeholder = o.value.stream().filter { propertyValue -> propertyValue.name == "placeholder" }.findFirst().orElse(null).value as StringInstance
            val placeholderTemplate = o.value.stream().filter { propertyValue -> propertyValue.name == "template" }.findFirst().orElse(null).value as StringInstance
            paramName = placeholderTemplate.value.replace("<${placeholder.value}>", placeholder.value)
            template = "'${placeholder.value}', '${paramName}'"
        }

        val builderChain = resource.resourcePathList().map { r -> "${r.getMethodName()}(${if (r.relativeUri.paramValues().isNotEmpty()) "\'${r.relativeUri.paramValues().joinToString("\', \'") { p -> "test_$p"} }\'" else ""})" }
                .plus("${method.method}(${if (method.firstBody() != null) "null" else ""})")
                .plus("${parameter.methodName()}(${template})")
        return """
                |new Object[] {           
                |    apiRoot
                |    ${builderChain.joinToString("\n.", ".")}.createHttpRequest(),
                |    '${method.method}',
                |    '${resource.fullUri.expand(resource.fullUriParameters.map { it.name to "test_${it.name}" }.toMap()).trimStart('/')}?${paramName}=${paramName}',
                |}
            """.trimMargin()
    }

    private fun resourceTestProvider(resource: Resource): String {
        val builderChain = resource.resourcePathList().map { r -> "${r.getMethodName()}(${if (r.relativeUri.paramValues().isNotEmpty()) "\"${r.relativeUri.paramValues().joinToString("\", \"") { p -> "test_$p"} }\"" else ""})" }

        return """
            |new Object[] {           
            |    apiRoot
            |    ${builderChain.joinToString("\n.", ".")}.createHttpRequest(),
            |    '${resource.fullUri.expand(resource.fullUriParameters.map { it.name to "test_${it.name}" }.toMap()).trimStart('/')}',
            |}
        """.trimMargin()
    }

    private fun requestTestProvider(resource: Resource, method: Method): String {
        val builderChain = resource.resourcePathList().map { r -> "${r.getMethodName()}(${if (r.relativeUri.paramValues().isNotEmpty()) "\"${r.relativeUri.paramValues().joinToString("\", \"") }\"" else ""})" }
                .plus("${method.method}(${if (method.firstBody() != null) "null" else ""})")

        return """
            |new Object[] {
            |       new ApiHttpRequest(ApiHttpMethod.${method.method.name},
            |       new ${resource.toResourceName().plus(method.method)}((apiHttpClientMock, projectKey).createHttpRequest().getUri(), null, null)
            |    }
        """.trimMargin()
    }
}
