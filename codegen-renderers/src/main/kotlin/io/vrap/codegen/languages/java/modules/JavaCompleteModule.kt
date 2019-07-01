package io.vrap.codegen.languages.java.modules

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.vrap.codegen.languages.java.file.producers.JavaApiRootFileProducer
import io.vrap.codegen.languages.java.file.producers.JavaModelClassFileProducer
import io.vrap.codegen.languages.java.file.producers.JavaModelDraftBuilderFileProducer
import io.vrap.codegen.languages.java.model.JavaStringTypeRenderer
import io.vrap.codegen.languages.java.model.second.JavaModelInterfaceRenderer
import io.vrap.codegen.languages.java.requests.JavaHttpRequestRenderer
import io.vrap.codegen.languages.java.requests.JavaRequestBuilderResourceRenderer
import io.vrap.rmf.codegen.rendring.*

class JavaCompleteModule: AbstractModule() {
    override fun configure() {
        val objectTypeBinder = Multibinder.newSetBinder(binder(), ObjectTypeRenderer::class.java)
        objectTypeBinder.addBinding().to(JavaModelInterfaceRenderer::class.java)

        val stringTypeBinder = Multibinder.newSetBinder(binder(), StringTypeRenderer::class.java)
        stringTypeBinder.addBinding().to(JavaStringTypeRenderer::class.java)

        val fileTypeBinder = Multibinder.newSetBinder(binder(), FileProducer::class.java)
        fileTypeBinder.addBinding().to(JavaModelClassFileProducer::class.java)
        fileTypeBinder.addBinding().to(JavaModelDraftBuilderFileProducer::class.java)
        fileTypeBinder.addBinding().to(JavaApiRootFileProducer::class.java)

        val resourceTypeBinder = Multibinder.newSetBinder(binder(), ResourceRenderer::class.java)
        resourceTypeBinder.addBinding().to(JavaRequestBuilderResourceRenderer::class.java)
        
        val methodTypeBinder = Multibinder.newSetBinder(binder(), MethodRenderer::class.java)
        methodTypeBinder.addBinding().to(JavaHttpRequestRenderer::class.java)
    }
}